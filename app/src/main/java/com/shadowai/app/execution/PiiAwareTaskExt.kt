package com.shadowai.app.execution

import android.util.Log
import com.shadowai.core.security.PiiMaskingProcessor
import com.shadowai.app.tasks.Task

/**
 * Extension function for DefaultTaskExecutor to mask PII in tasks before
 * sending to external/cloud providers.
 *
 * @param task The original task
 * @param sourceName The execution source name for logging
 * @return Task with PII masked if external provider, otherwise original task
 */
fun DefaultTaskExecutor.maskPiiInTask(task: Task, sourceName: String): Task {
    val maskedInput = piiMaskingProcessor.maskPii(task.input)

    if (piiMaskingProcessor.containsPii(task.input)) {
        Log.i("TaskExecutor", "PII masked before sending to $sourceName provider")
    }

    return if (maskedInput != task.input) {
        task.copy(input = maskedInput)
    } else {
        task
    }
}
