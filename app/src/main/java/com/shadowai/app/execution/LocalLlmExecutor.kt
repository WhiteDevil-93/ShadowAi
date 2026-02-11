package com.shadowai.app.execution

import javax.inject.Inject
import javax.inject.Singleton

/**
 * DEPRECATED: Legacy local LLM executor.
 *
 * Logic has been migrated to:
 * 1. [com.shadowai.provideradapters.LocalLlamaAdapter] for native inference.
 * 2. [com.shadowai.app.execution.HeuristicActionParser] for rule-based device control.
 *
 * Please delete this file once all references are removed.
 */
@Deprecated("Use ProviderAdapter architecture via AdapterBridge")
@Singleton
class LocalLlmExecutor @Inject constructor() {
    // Legacy placeholder
}
