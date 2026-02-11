package com.shadowai.hotswapping

import android.content.Context
import com.shadowai.core.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Config-driven provider manager for hot-swapping providers at runtime.
 */
class ProviderHotSwapManager(
    private val context: Context,
    private val loader: ProviderConfigLoader,
    private val validator: ProviderConfigValidator,
    private val migrator: ProviderConfigMigrator,
    private val registry: ProviderRegistry,
    private val backupManager: ProviderBackupManager
) {
    private val mutex = Mutex()
    private val configFile = File(context.filesDir, "hot_swapping/provider_configs.json")

    private val _snapshot = MutableStateFlow(ProviderConfigSnapshot(version = 1, providers = emptyList()))
    val snapshot: StateFlow<ProviderConfigSnapshot> = _snapshot.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * Loads configuration from disk.
     */
    suspend fun refresh(): Result<Unit> = mutex.withLock {
        loader.load(configFile)
            .map { migrator.migrate(it) }
                .onSuccess { applySnapshot(it) }
            .onFailure { _lastError.value = it.message }
            .map { Unit }
    }

    /**
     * Adds a new provider configuration.
     */
    suspend fun addProvider(config: ProviderConfig): Result<Unit> = updateSnapshot { snapshot ->
        snapshot.copy(
            providers = snapshot.providers + config,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Updates an existing provider configuration.
     */
    suspend fun updateProvider(config: ProviderConfig): Result<Unit> = updateSnapshot { snapshot ->
        val updated = snapshot.providers.map {
            if (it.providerId == config.providerId) config else it
        }
        snapshot.copy(providers = updated, updatedAt = System.currentTimeMillis())
    }

    /**
     * Removes a provider configuration.
     */
    suspend fun removeProvider(providerId: ProviderId): Result<Unit> = updateSnapshot { snapshot ->
        snapshot.copy(
            providers = snapshot.providers.filter { it.providerId != providerId },
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Enables or disables a provider.
     */
    suspend fun setProviderEnabled(providerId: ProviderId, enabled: Boolean): Result<Unit> = updateSnapshot { snapshot ->
        val updated = snapshot.providers.map {
            if (it.providerId == providerId) it.copy(isEnabled = enabled) else it
        }
        snapshot.copy(providers = updated, updatedAt = System.currentTimeMillis())
    }

    /**
     * Exports the current snapshot as JSON or YAML.
     */
    fun export(format: ConfigFormat = ConfigFormat.JSON): String {
        return loader.serialize(_snapshot.value, format)
    }

    /**
     * Imports a snapshot from raw text.
     */
    suspend fun import(raw: String, format: ConfigFormat): Result<Unit> = updateSnapshot {
        migrator.migrate(loader.parse(raw, format))
    }

    /**
     * Creates a backup of the current snapshot.
     */
    suspend fun backup(format: ConfigFormat = ConfigFormat.JSON): Result<File> {
        return backupManager.createBackup(_snapshot.value, format)
    }

    /**
     * Restores the most recent backup.
     */
    suspend fun restoreLatestBackup(): Result<Unit> = mutex.withLock {
        backupManager.restoreLatest()
            .map { migrator.migrate(it) }
                .onSuccess { applySnapshot(it) }
            .map { Unit }
    }

    /**
     * Returns validation issues for the current snapshot.
     */
    fun getValidationIssues(): List<ProviderConfigValidationIssue> {
        return validator.validate(_snapshot.value)
    }

    private suspend fun updateSnapshot(
        mutator: (ProviderConfigSnapshot) -> ProviderConfigSnapshot
    ): Result<Unit> = mutex.withLock {
        return@withLock withContext(Dispatchers.Default) {
            runCatching {
                val updated = mutator(_snapshot.value)
                val issues = validator.validate(updated)
                if (issues.isNotEmpty()) {
                    throw IllegalArgumentException(issues.joinToString { it.message })
                }
                applySnapshot(updated)
            }
        }
    }

    private suspend fun applySnapshot(snapshot: ProviderConfigSnapshot) {
        _snapshot.value = snapshot
        val enabledProviders = snapshot.providers.filter { it.isEnabled }
        registry.sync(enabledProviders)
        val saveResult = loader.save(configFile, snapshot, ConfigFormat.JSON)
        saveResult.onFailure { _lastError.value = it.message }
        if (saveResult.isSuccess) {
            _lastError.value = null
        }
    }
}
