package com.shadowai.hotswapping

import com.shadowai.core.ProviderId
import com.shadowai.provideradapters.ProviderAdapter
import com.shadowai.provideradapters.ProviderAdapterFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps active provider adapters in sync with configs.
 */
class ProviderRegistry(
    private val adapterFactory: ProviderAdapterFactory
) {
    private val adapters = ConcurrentHashMap<ProviderId, ProviderAdapter>()

    /**
     * Registers or updates an adapter for the given config.
     */
    fun register(config: ProviderConfig): ProviderAdapter {
        val adapter = adapterFactory.getAdapter(config.toAdapterConfig())
        adapters[config.providerId] = adapter
        return adapter
    }

    /**
     * Unregisters a provider adapter.
     */
    fun unregister(providerId: ProviderId) {
        adapters.remove(providerId)
        adapterFactory.removeAdapter(providerId)
    }

    /**
     * Syncs registry with enabled providers.
     */
    fun sync(enabledProviders: List<ProviderConfig>) {
        val enabledIds = enabledProviders.map { it.providerId }.toSet()
        enabledProviders.forEach { register(it) }

        adapters.keys
            .filter { it !in enabledIds }
            .forEach { unregister(it) }
    }

    /**
     * Returns currently registered adapters.
     */
    fun listAdapters(): List<ProviderAdapter> = adapters.values.toList()
}
