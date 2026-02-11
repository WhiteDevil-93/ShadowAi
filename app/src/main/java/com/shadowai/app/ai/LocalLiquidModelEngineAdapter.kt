package com.shadowai.app.ai

import com.shadowai.provideradapters.LocalModelEngine
import com.shadowai.provideradapters.LocalModelInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter that exposes LocalLiquidEngine through provider-adapters' LocalModelEngine contract.
 */
@Singleton
class LocalLiquidModelEngineAdapter @Inject constructor(
    private val localLiquidEngine: LocalLiquidEngine
) : LocalModelEngine {

    override fun scanForModels(): List<LocalModelInfo> {
        return localLiquidEngine.scanForModels().map { model ->
            LocalModelInfo(
                path = model.path,
                format = model.format,
                size = model.size
            )
        }
    }

    override fun setCustomModelDirectory(path: String?) {
        localLiquidEngine.setCustomModelDirectory(path)
    }
}

