package com.shadowai.app.ai

import com.shadowai.app.providers.ActiveProviderConfig
import com.shadowai.app.providers.ApiStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DEPRECATED: Legacy Brain Manager.
 *
 * Its responsibilities (managing active provider configuration and API client instantiation)
 * have been moved to [com.shadowai.app.providers.AdapterBridge]
 * and [com.shadowai.provideradapters.ProviderAdapterFactory].
 *
 * This class now serves as a simplified stub to fulfill existing dependencies until they
 * are fully refactored.
 *
 * Please delete this file once all references are removed.
 */
@Deprecated("Use AdapterBridge and ProviderAdapterFactory for provider management")
@Singleton
class LocalBrainManager @Inject constructor() {

    // Dummy properties to satisfy existing injections for now
    val currentApiStyle: ApiStyle get() = ApiStyle.OPENAI_COMPAT
    val currentModel: String get() = "deprecated-model"

    fun getActiveConfig(): ActiveProviderConfig? = null
    fun applyConfig(config: ActiveProviderConfig) { /* no-op */ }
}
