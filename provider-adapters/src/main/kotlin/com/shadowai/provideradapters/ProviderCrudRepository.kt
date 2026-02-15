package com.shadowai.provideradapters

import android.content.Context
import com.shadowai.core.ProviderId
import com.shadowai.core.providers.Provider
import com.shadowai.core.providers.ProviderAuth
import com.shadowai.core.providers.AuthType
import com.shadowai.core.providers.ModelInfo
import com.shadowai.core.Capability
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles provider CRUD operations and persistence to SharedPreferences.
 */
@Singleton
class ProviderCrudRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val applicationScope: CoroutineScope,
) {
    companion object {
        private const val PREFS_NAME = "providers"
        private const val KEY_PROVIDERS = "providers"
        private val PROVIDER_LIST_TYPE = object : TypeToken<List<Provider>>() {}.type
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    internal fun loadProviders(): List<Provider> {
        val defaults = ProviderConfigurationService.getDefaultProviders(context)
        val providersJson = prefs.getString(KEY_PROVIDERS, null)
        if (!providersJson.isNullOrBlank()) {
            return try {
                val stored = gson.fromJson<List<Provider>>(providersJson, PROVIDER_LIST_TYPE) ?: emptyList()
                mergeProviders(stored, defaults)
            } catch (e: JsonSyntaxException) {
                defaults
            }
        }
        return defaults
    }

    internal fun persistProviders(providers: List<Provider>) {
        prefs.edit().putString(KEY_PROVIDERS, gson.toJson(providers)).apply()
    }

    private fun mergeProviders(stored: List<Provider>, defaults: List<Provider>): List<Provider> {
        val storedById = stored.associateBy { it.id }
        val mergedDefaults = defaults.map { defaultProvider ->
            val storedProvider = storedById[defaultProvider.id] ?: return@map defaultProvider
            defaultProvider.copy(
                name = storedProvider.name.takeIf { it.isNotBlank() } ?: defaultProvider.name,
                enabled = storedProvider.enabled,
                baseUrl = storedProvider.baseUrl.takeIf { it.isNotBlank() } ?: defaultProvider.baseUrl,
                auth = if (storedProvider.auth.hasCredential || storedProvider.auth.credentialAlias != null) {
                    storedProvider.auth
                } else {
                    defaultProvider.auth
                },
                capabilities = (defaultProvider.capabilities + storedProvider.capabilities).distinct(),
                models = if (storedProvider.models.isNotEmpty()) storedProvider.models else defaultProvider.models,
                selectedModels = storedProvider.selectedModels ?: defaultProvider.selectedModels,
                customModels = storedProvider.customModels ?: defaultProvider.customModels,
            )
        }

        // Preserve legacy/unknown providers that are not part of the default catalog.
        val legacyProviders = stored.filter { storedProvider ->
            defaults.none { defaultProvider -> defaultProvider.id == storedProvider.id }
        }

        return mergedDefaults + legacyProviders
    }

    // --- Suspend API ---

    suspend fun getAllProviders(): List<Provider> = withContext(Dispatchers.IO) {
        loadProviders()
    }

    suspend fun getProviderById(id: ProviderId): Provider? = withContext(Dispatchers.IO) {
        loadProviders().find { it.id == id }
    }

    suspend fun saveProviders(providers: List<Provider>) = withContext(Dispatchers.IO) {
        persistProviders(providers)
    }

    suspend fun saveProvider(provider: Provider) = withContext(Dispatchers.IO) {
        val providers = loadProviders().toMutableList()
        val existingIndex = providers.indexOfFirst { it.id == provider.id }
        if (existingIndex >= 0) {
            providers[existingIndex] = provider
        } else {
            providers.add(provider)
        }
        persistProviders(providers)
    }

    suspend fun listProvidersSync(): List<Provider> = getAllProviders()

    // --- Synchronous API for existing UI (read-only operations only) ---

    fun listProviders(): List<Provider> = loadProviders()

    fun getProvider(providerId: ProviderId): Provider? = loadProviders().firstOrNull { it.id == providerId }

    @Deprecated("Use saveProvider() suspend function instead. Fire-and-forget pattern causes race conditions.")
    fun setProviderEnabled(providerId: ProviderId, enabled: Boolean) {
        throw IllegalStateException("Use suspend saveProvider() instead. Fire-and-forget pattern removed to prevent race conditions.")
    }

    suspend fun setProviderEnabledSync(providerId: ProviderId, enabled: Boolean) = withContext(Dispatchers.IO) {
        val provider = getProvider(providerId) ?: return@withContext
        if (provider.enabled != enabled) {
            val providers = loadProviders().toMutableList()
            val index = providers.indexOfFirst { it.id == providerId }
            if (index >= 0) {
                providers[index] = provider.copy(enabled = enabled)
                persistProviders(providers)
            }
        }
    }

    @Deprecated("Use saveProvider() suspend function instead")
    internal fun updateProviderList(mutator: (MutableList<Provider>) -> Unit) {
        throw IllegalStateException("Use suspend saveProvider() instead. Fire-and-forget pattern removed to prevent race conditions.")
    }

    suspend fun updateProviderListSync(mutator: (MutableList<Provider>) -> Unit) = withContext(Dispatchers.IO) {
        val providers = loadProviders().toMutableList()
        mutator(providers)
        persistProviders(providers)
    }

    suspend fun saveAndAwait(provider: Provider) {
        saveProvider(provider)
    }

    @Deprecated("Use saveProvider() suspend function instead")
    fun saveSync(provider: Provider) {
        applicationScope.launch {
            saveProvider(provider)
        }
    }
}
