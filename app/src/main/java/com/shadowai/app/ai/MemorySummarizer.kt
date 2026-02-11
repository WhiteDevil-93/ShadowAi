package com.shadowai.app.ai

import android.util.Log
import com.shadowai.core.Artifact
import com.shadowai.core.Modality
import com.shadowai.core.Transform
import com.shadowai.app.providers.AdapterBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemorySummarizer @Inject constructor(
    private val memoryManager: MemoryManager,
    private val promptManager: PromptManager,
    private val adapterBridge: AdapterBridge,
    private val brainManager: LocalBrainManager,
    @com.shadowai.app.di.ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MemorySummarizer"
    }
    
    fun summarizeAndStore(content: String, source: String = "conversation") {
        if (content.isBlank()) return

        scope.launch(Dispatchers.IO) {
             try {
                 val messages = promptManager.buildSummarizationMessages(content)
                 
                 // Get active provider config
                 val config = brainManager.getActiveConfig() ?: run {
                     Log.w(TAG, "No active brain config for summarization")
                     return@launch
                 }
                 
                 // Format prompt
                 val prompt = messages.joinToString("\n") { 
                    "${it.role.toString().uppercase()}: ${it.content}" 
                 }
                 val inputArtifact = Artifact.Text.create(prompt)
                 
                 // Execute summarization
                 val result = adapterBridge.execute(
                     appConfig = config,
                     transform = Transform.TextToText(),
                     input = inputArtifact
                 )
                 
                 result.onSuccess { output ->
                     if (output is String && output.isNotBlank()) {
                         Log.d(TAG, "Generated summary ($source): ${output.take(50)}...")
                         memoryManager.saveSummary(output)
                     }
                 }.onFailure { e ->
                     Log.e(TAG, "Failed to generate summary", e)
                 }
             } catch (e: Exception) {
                 Log.e(TAG, "Background summarization failed", e)
             }
        }
    }
}
