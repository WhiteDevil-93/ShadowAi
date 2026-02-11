package com.shadowai.app.providers
 
 import kotlinx.coroutines.sync.Mutex
 import kotlinx.coroutines.sync.withLock

/**
 * Plugin interface for AI providers.
 * Implement this interface to add custom providers to the system.
 */
interface ProviderPlugin {
    /**
     * Unique identifier for this plugin.
     */
    val pluginId: String
    
    /**
     * Display name for the plugin.
     */
    val displayName: String
    
    /**
     * Priority of this plugin (higher = checked first).
     */
    val priority: Int
    
    /**
     * Whether this plugin is currently enabled.
     */
    var isEnabled: Boolean
    
    /**
     * Create a provider instance from this plugin.
     */
    fun createProvider(): Provider?
    
    /**
     * Check if the provider is available/healthy.
     */
    suspend fun checkHealth(): Boolean
    
    /**
     * Get the API style for this provider.
     */
    fun getApiStyle(): ApiStyle
}

/**
 * Plugin registry for managing provider plugins.
 */
class ProviderPluginRegistry {
    private val plugins = mutableListOf<ProviderPlugin>()
    private val mutex = Mutex()
    
    /**
     * Register a new plugin.
     */
    suspend fun register(plugin: ProviderPlugin) {
        mutex.withLock {
            plugins.add(plugin)
            plugins.sortByDescending { it.priority }
        }
    }
    
    /**
     * Unregister a plugin.
     */
    suspend fun unregister(pluginId: String) {
        mutex.withLock {
            plugins.removeAll { it.pluginId == pluginId }
        }
    }
    
    /**
     * Get all enabled plugins.
     */
    suspend fun getEnabledPlugins(): List<ProviderPlugin> {
        return mutex.withLock {
            plugins.filter { it.isEnabled }
        }
    }
    
    /**
     * Get all registered plugins.
     */
    suspend fun getAllPlugins(): List<ProviderPlugin> {
        return mutex.withLock {
            plugins.toList()
        }
    }
    
    /**
     * Get plugins by API style.
     */
    suspend fun getPluginsByStyle(apiStyle: ApiStyle): List<ProviderPlugin> {
        return mutex.withLock {
            plugins.filter { it.isEnabled && it.getApiStyle() == apiStyle }
        }
    }
    
    /**
     * Check health of all enabled plugins.
     */
    suspend fun checkAllHealth(): Map<String, Boolean> {
        return getEnabledPlugins().associate { plugin ->
            plugin.pluginId to plugin.checkHealth()
        }
    }
}

/**
 * Extension function to register plugins easily.
 */
suspend fun ProviderPluginRegistry.registerPlugin(
    id: String,
    name: String,
    priority: Int = 0,
    apiStyle: ApiStyle,
    provider: Provider
): ProviderPlugin {
    val plugin = object : ProviderPlugin {
        override val pluginId: String = id
        override val displayName: String = name
        override val priority: Int = priority
        override var isEnabled: Boolean = true
        
        override fun createProvider(): Provider? = provider
        override suspend fun checkHealth(): Boolean = true
        override fun getApiStyle(): ApiStyle = apiStyle
    }
    register(plugin)
    return plugin
}
