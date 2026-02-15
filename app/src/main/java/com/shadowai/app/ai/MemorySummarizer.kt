package com.shadowai.app.ai

import android.util.Log
import com.shadowai.app.providers.AdapterBridge
import com.shadowai.app.providers.ProviderSelector
import com.shadowai.app.tasks.TaskType
import com.shadowai.core.Artifact
import com.shadowai.core.Transform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces compact summaries for long-running agentic loops and stores them in memory.
 *
 * This is intentionally scoped to background context compression and does not expose
 * conversation UI summarization APIs (those are provided by ConversationSummarizer).
 */
@Singleton
class MemorySummarizer @Inject constructor(
    private val memoryManager: MemoryManager,
    private val promptManager: PromptManager,
    private val adapterBridge: AdapterBridge,
    private val providerSelector: ProviderSelector,
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
                val config = providerSelector.nextForTask(TaskType.CONVERSATION) ?: run {
                    Log.w(TAG, "No active provider config for summarization")
                    return@launch
                }

                val prompt = messages.joinToString("\n") {
                    "${it.role.toString().uppercase()}: ${it.content}"
                }

                val result = adapterBridge.execute(
                    appConfig = config,
                    transform = Transform.TextToText(),
                    input = Artifact.Text.create(prompt)
                )

                result.onSuccess { output ->
                    val outputText = output.toDisplayText()
                    if (outputText.isNotBlank()) {
                        Log.d(TAG, "Generated summary ($source): ${outputText.take(80)}")
                        memoryManager.saveSummary(outputText)
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Failed to generate summary", error)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Summarization cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Background summarization failed", e)
            }
        }
    }
}

private fun Any.toDisplayText(): String = when (this) {
    is Artifact.Text -> content
    is Artifact.Image -> uri.toString()
    is Artifact.Audio -> uri.toString()
    is Artifact.Video -> uri.toString()
    is Artifact.Binary -> "Binary data (${data.size} bytes)"
    is Artifact.Json -> jsonString
    is Artifact.Error -> "Error: $message"
    is Artifact.Empty -> ""
    else -> toString()
}
