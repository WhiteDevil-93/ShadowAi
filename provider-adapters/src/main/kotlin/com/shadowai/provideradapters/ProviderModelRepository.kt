package com.shadowai.provideradapters

import android.content.Context
import com.shadowai.core.ProviderId
import com.shadowai.core.providers.Provider
import com.shadowai.core.providers.ModelInfo
import com.shadowai.core.Capability
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shadowai.core.security.SecretBytes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages model selection and custom model configurations per provider.
 */
@Singleton
class ProviderModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val crudRepository: ProviderCrudRepository,
    private val secretRepository: ProviderSecretRepository,
) {
    companion object {
        private const val PREFS_NAME = "providers"
        private const val KEY_SELECTED_MODELS_PREFIX = "selected_models_"
        private const val KEY_CUSTOM_MODELS_PREFIX = "custom_models_"
        private val STRING_LIST_TYPE = object : TypeToken<List<String>>() {}.type
        private val MODEL_INFO_LIST_TYPE = object : TypeToken<List<ModelInfo>>() {}.type
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedModels(providerId: ProviderId): List<String> =
        readStringList("$KEY_SELECTED_MODELS_PREFIX${providerId.name}")

    suspend fun setSelectedModels(providerId: ProviderId, models: List<String>) = withContext(Dispatchers.IO) {
        writeStringList("$KEY_SELECTED_MODELS_PREFIX${providerId.name}", models)
        crudRepository.getProvider(providerId)?.let {
            crudRepository.saveProvider(it.copy(selectedModels = models))
        }
    }

    fun getCustomModels(providerId: ProviderId, fallbackCapabilities: List<Capability> = emptyList()): List<ModelInfo> {
        val key = "$KEY_CUSTOM_MODELS_PREFIX${providerId.name}"
        val cached = readModelInfoList(key)
        if (cached.isNotEmpty()) return cached
        val provider = crudRepository.getProvider(providerId)
        if (provider != null && provider.models.isNotEmpty()) return provider.models
        return provider?.let {
            listOf(
                ModelInfo(
                    id = it.name,
                    displayName = it.name,
                    provider = providerId,
                    capabilities = fallbackCapabilities.toSet().ifEmpty { it.capabilities.toSet() },
                )
            )
        } ?: emptyList()
    }

    suspend fun saveCustomModels(providerId: ProviderId, modelIds: List<String>) = withContext(Dispatchers.IO) {
        val key = "$KEY_CUSTOM_MODELS_PREFIX${providerId.name}"
        if (modelIds.isEmpty()) {
            prefs.edit().remove(key).apply()
        } else {
            val capabilities = crudRepository.getProvider(providerId)?.capabilities?.toSet() ?: emptySet()
            val models = modelIds.map { id ->
                ModelInfo(
                    id = id,
                    displayName = id,
                    provider = providerId,
                    capabilities = capabilities,
                )
            }
            writeModelInfoList(key, models)
        }

        crudRepository.getProvider(providerId)?.let {
            crudRepository.saveProvider(
                it.copy(
                    customModels = modelIds,
                    enabled = modelIds.isNotEmpty() || secretRepository.getApiKey(providerId.name)?.withSecretBytes { bytes -> String(bytes).isNotBlank() } == true,
                )
            )
        }
    }

    // --- Serialization helpers ---

    private fun readStringList(key: String): List<String> {
        val json = prefs.getString(key, null)
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<String>>(json, STRING_LIST_TYPE) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeStringList(key: String, values: List<String>) {
        prefs.edit().putString(key, gson.toJson(values)).apply()
    }

    private fun readModelInfoList(key: String): List<ModelInfo> {
        val json = prefs.getString(key, null)
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<ModelInfo>>(json, MODEL_INFO_LIST_TYPE) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeModelInfoList(key: String, models: List<ModelInfo>) {
        prefs.edit().putString(key, gson.toJson(models)).apply()
    }
}
