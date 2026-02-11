package com.shadowai.app.providers

import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.tasks.TaskType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderSelector @Inject constructor(
    private val activeProviderManager: ActiveProviderManager,
    private val adminRepository: AdminRepository,
    private val providerRepository: ProviderRepository
) {
    private val indexMutex = Mutex()
    private var localIndex = 0
    private var cloudIndex = 0

    suspend fun nextLocal(taskType: TaskType): ActiveProviderConfig? {
        val configs = getProviders(taskType, local = true)
        return pickConfig(configs, local = true)
    }

    suspend fun nextCloud(taskType: TaskType): ActiveProviderConfig? {
        val configs = getProviders(taskType, local = false)
        return pickConfig(configs, local = false)
    }

    suspend fun getLocalProviders(taskType: TaskType): List<ActiveProviderConfig> {
        return getProviders(taskType, local = true)
    }

    suspend fun getCloudProviders(taskType: TaskType): List<ActiveProviderConfig> {
        return getProviders(taskType, local = false)
    }

    private suspend fun getProviders(taskType: TaskType, local: Boolean): List<ActiveProviderConfig> {
        val neededCapability = requiredCapability(taskType)
        return activeProviderManager.getActiveConfigs(null)
            .asSequence()
            .filter { config -> isLocalStyle(config.apiStyle) == local }
            .filter { config -> neededCapability in config.capabilities }
            .toList()
    }

    private suspend fun pickConfig(configs: List<ActiveProviderConfig>, local: Boolean): ActiveProviderConfig? {
        if (configs.isEmpty()) return null
        val preferred = adminRepository.getActiveProvider()
        val preferredConfig = preferred?.let { id -> configs.firstOrNull { it.providerId == id } }
        if (preferredConfig != null) return preferredConfig
        val idx = indexMutex.withLock {
            val next = if (local) localIndex else cloudIndex
            val normalized = if (next < 0) 0 else next
            val result = normalized % configs.size
            val updated = if (normalized == Int.MAX_VALUE) 0 else normalized + 1
            if (local) localIndex = updated else cloudIndex = updated
            result
        }
        return configs[idx]
    }


    private fun requiredCapability(taskType: TaskType): Capability {
        return when (taskType) {
            TaskType.IMAGE_GEN -> Capability.IMAGE_GEN
            else -> Capability.TEXT
        }
    }

    private fun isLocalStyle(style: ApiStyle): Boolean {
        return style == ApiStyle.LOCAL_IMAGE || style == ApiStyle.LOCAL_TEXT || style == ApiStyle.LIQUID
    }
}

