package com.shadowai.app.tasks

import androidx.annotation.Keep

/**
 * Uniquely identifies a task within the system.
 * Validates that the ID is non-empty and within acceptable length.
 */
@Keep
@JvmInline
value class TaskIdentifier(val id: String) {
    init {
        require(id.isNotBlank()) { "TaskIdentifier.id cannot be blank" }
        require(id.length <= 128) { "TaskIdentifier.id exceeds maximum length of 128 characters" }
        require(id.matches(Regex("^[a-zA-Z0-9_-]+$"))) { "TaskIdentifier.id contains invalid characters" }
    }
}

/**
 * Categorizes the nature of a task.
 */
@Keep
enum class TaskType {
    /** A conversational turn or query. */
    CONVERSATION,

    /** Voice-driven telephony intent (calls, dialer). */
    TELEPHONY,

    /** SMS or messaging interactions. */
    MESSAGING,

    /** Media-related commands (play/pause/volume). */
    MEDIA_CONTROL,

    /** Navigation and system-level interactions. */
    SYSTEM_INTERACTION,

    /** A command to control a device function. */
    DEVICE_CONTROL,

    /** A background analysis or summary task. */
    ANALYSIS,

    /** A system administration or maintenance task. */
    SYSTEM,

    /** Image generation task. */
    IMAGE_GEN,

    /** Creative writing task. */
    WRITING,

    /** Vocal/Voice synthesis task. */
    VOCAL,

    /** Text generation task. */
    TEXT_GEN,

    /** Code generation task. */
    CODE_GEN,

    /** Search task. */
    SEARCH,

    /** Custom task. */
    CUSTOM,

    /** Video generation task (Phase 4). */
    VIDEO_GEN,

    /** Audio generation task (Phase 4). */
    AUDIO_GEN
}

/**
 * Represents the lifecycle state of a task.
 * This is a strict state machine: QUEUED -> RUNNING -> COMPLETED | FAILED
 */
@Keep
sealed class TaskState {
    /** The task has been accepted but execution has not started. */
    data object Queued : TaskState()

    /** The task is currently executing. */
    data object Running : TaskState()

    /** The task successfully finished. */
    data class Completed(
        val output: String,
        val timestamp: Long
    ) : TaskState()

    /** The task failed to complete. */
    data class Failed(
        val reason: String,
        val timestamp: Long,
        val isRecoverable: Boolean
    ) : TaskState()
}

/**
 * Phase 1.3: Self-Correction Budgeting
 * Constraints enforced on the execution of a task.
 * Validates that all budget values are within acceptable ranges.
 */
data class TaskBudget(
    val maxRetries: Int = 3,
    val timeoutMs: Long = 30000,
    val maxTokens: Int = 4096,
    val allowStreaming: Boolean = false
) {
    init {
        require(maxRetries >= 0) { "maxRetries cannot be negative" }
        require(timeoutMs > 0) { "timeoutMs must be positive" }
        require(maxTokens > 0) { "maxTokens must be positive" }
        require(maxRetries <= 10) { "maxRetries exceeds maximum of 10" }
        require(timeoutMs <= 600000) { "timeoutMs exceeds maximum of 10 minutes" }
        require(maxTokens <= 128000) { "maxTokens exceeds maximum of 128K" }
    }
}

/**
 * Represents a structured unit of work for the assistant.
 *
 * @property id The unique identifier for this task.
 * @property type The functional category of the task.
 * @property input The primary input content (e.g., user prompt or command payload).
 * @property jsonSchema Optional schema definition for structured outputs.
 * @property currentState The current lifecycle state of the task.
 * @property budget The constraints assigned to this task.
 */
data class Task(
    val id: TaskIdentifier,
    val type: TaskType,
    val input: String,
    val jsonSchema: String? = null,
    val currentState: TaskState = TaskState.Queued,
    val budget: TaskBudget = TaskBudget()
) {
    init {
        require(input.length <= 65536) { "Task input exceeds maximum length of 64KB" }
        jsonSchema?.let {
            require(it.length <= 16384) { "jsonSchema exceeds maximum length of 16KB" }
        }
    }
}
