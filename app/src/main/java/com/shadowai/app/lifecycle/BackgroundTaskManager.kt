package com.shadowai.app.lifecycle

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized manager for background task lifecycle and cleanup.
 *
 * Ensures all background tasks (coroutines, WorkManager jobs, timers) are properly
 * cleaned up when no longer needed, preventing memory leaks and resource waste.
 *
 * Features:
 * - Coroutine scope tracking and cancellation
 * - WorkManager job tracking
 * - Automatic cleanup on app termination
 * - Resource usage monitoring
 *
 * Usage:
 * ```
 * // Register a background scope
 * val scope = backgroundTaskManager.createScope("my-feature")
 * scope.launch { /* work */ }
 *
 * // Later, cleanup when done
 * backgroundTaskManager.cancelScope("my-feature")
 *
 * // Or cleanup everything
 * backgroundTaskManager.cancelAll()
 * ```
 */
@Singleton
class BackgroundTaskManager @Inject constructor(
    private val context: Context
) {

    private companion object {
        private const val TAG = "BackgroundTaskMgr"
    }

    // Track all managed coroutine scopes
    private val managedScopes = ConcurrentHashMap<String, ManagedScope>()

    // Track WorkManager unique work names
    private val workManagerTags = ConcurrentHashMap<String, WorkManagerTask>()

    private val workManager by lazy { WorkManager.getInstance(context) }

    /**
     * Managed coroutine scope with metadata
     */
    data class ManagedScope(
        val id: String,
        val scope: CoroutineScope,
        val createdAt: Long = System.currentTimeMillis(),
        var lastActivity: Long = System.currentTimeMillis()
    ) {
        var jobCount: Int = 0
    }

    /**
     * WorkManager task tracking
     */
    data class WorkManagerTask(
        val id: String,
        val workName: String,
        val tags: Set<String>,
        val createdAt: Long = System.currentTimeMillis()
    )

    /**
     * Create a managed coroutine scope
     *
     * @param id Unique identifier for this scope
     * @param dispatcher Coroutine dispatcher (default: Dispatchers.Default)
     * @return Managed CoroutineScope
     */
    fun createScope(
        id: String,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): CoroutineScope {
        if (managedScopes.containsKey(id)) {
            Log.w(TAG, "Scope '$id' already exists, returning existing scope")
            return managedScopes[id]!!.scope
        }

        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val managedScope = ManagedScope(id, scope)

        managedScopes[id] = managedScope
        Log.d(TAG, "Created managed scope: $id")

        return scope
    }

    /**
     * Get an existing managed scope
     *
     * @param id Scope identifier
     * @return CoroutineScope or null if not found
     */
    fun getScope(id: String): CoroutineScope? {
        return managedScopes[id]?.also {
            it.lastActivity = System.currentTimeMillis()
        }?.scope
    }

    /**
     * Register a coroutine job with a managed scope
     *
     * @param scopeId Scope identifier
     * @param job The job to track
     */
    fun registerJob(scopeId: String, job: Job) {
        managedScopes[scopeId]?.let { managedScope ->
            managedScope.jobCount++
            managedScope.lastActivity = System.currentTimeMillis()

            job.invokeOnCompletion {
                managedScope.jobCount--
                Log.d(TAG, "Job completed in scope '$scopeId', remaining: ${managedScope.jobCount}")
            }
        }
    }

    /**
     * Cancel a managed scope and all its jobs
     *
     * @param id Scope identifier
     * @return true if scope was found and cancelled
     */
    fun cancelScope(id: String): Boolean {
        val managedScope = managedScopes.remove(id) ?: return false

        managedScope.scope.cancel("Scope '$id' manually cancelled")
        Log.i(TAG, "Cancelled scope: $id (had ${managedScope.jobCount} active jobs)")

        return true
    }

    /**
     * Register a WorkManager task for tracking
     *
     * @param id Unique identifier
     * @param workName Unique work name used with WorkManager
     * @param tags Tags associated with the work
     */
    fun registerWorkManagerTask(
        id: String,
        workName: String,
        tags: Set<String> = emptySet()
    ) {
        val task = WorkManagerTask(id, workName, tags)
        workManagerTags[id] = task
        Log.d(TAG, "Registered WorkManager task: $id (work name: $workName)")
    }

    /**
     * Cancel a WorkManager task
     *
     * @param id Task identifier
     * @param cancelByTag If true, also cancels all work with matching tags
     * @return true if task was found and cancelled
     */
    fun cancelWorkManagerTask(id: String, cancelByTag: Boolean = false): Boolean {
        val task = workManagerTags.remove(id) ?: return false

        try {
            // Cancel by unique work name
            workManager.cancelUniqueWork(task.workName)
            Log.i(TAG, "Cancelled WorkManager task: $id")

            // Optionally cancel by tags
            if (cancelByTag && task.tags.isNotEmpty()) {
                task.tags.forEach { tag ->
                    workManager.cancelAllWorkByTag(tag)
                    Log.d(TAG, "Cancelled all work with tag: $tag")
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel WorkManager task: $id", e)
            return false
        }
    }

    /**
     * Cancel all managed scopes
     */
    fun cancelAllScopes() {
        val count = managedScopes.size
        managedScopes.forEach { (id, managedScope) ->
            managedScope.scope.cancel("Cancelling all scopes")
            Log.d(TAG, "Cancelled scope: $id")
        }
        managedScopes.clear()
        Log.i(TAG, "Cancelled all $count managed scopes")
    }

    /**
     * Cancel all WorkManager tasks
     */
    fun cancelAllWorkManagerTasks() {
        val count = workManagerTags.size
        workManagerTags.forEach { (id, _) ->
            cancelWorkManagerTask(id, cancelByTag = false)
        }
        Log.i(TAG, "Cancelled all $count WorkManager tasks")
    }

    /**
     * Cancel everything - all scopes and WorkManager tasks
     */
    fun cancelAll() {
        Log.i(TAG, "Cancelling all background tasks")
        cancelAllScopes()
        cancelAllWorkManagerTasks()
    }

    /**
     * Get statistics about managed resources
     */
    fun getStats(): BackgroundTaskStats {
        val activeScopeCount = managedScopes.size
        val totalJobCount = managedScopes.values.sumOf { it.jobCount }
        val workManagerTaskCount = workManagerTags.size

        val oldestScope = managedScopes.values.minByOrNull { it.createdAt }
        val idleScopes = managedScopes.values.count {
            System.currentTimeMillis() - it.lastActivity > 5 * 60 * 1000 // 5 minutes
        }

        return BackgroundTaskStats(
            activeScopeCount = activeScopeCount,
            totalJobCount = totalJobCount,
            workManagerTaskCount = workManagerTaskCount,
            oldestScopeAge = oldestScope?.let {
                System.currentTimeMillis() - it.createdAt
            },
            idleScopeCount = idleScopes
        )
    }

    /**
     * Cleanup idle scopes (no activity for 5+ minutes)
     *
     * @return Number of scopes cleaned up
     */
    fun cleanupIdleScopes(idleThresholdMs: Long = 5 * 60 * 1000): Int {
        val now = System.currentTimeMillis()
        var cleaned = 0

        val idleScopes = managedScopes.filter { (_, scope) ->
            scope.jobCount == 0 && (now - scope.lastActivity) > idleThresholdMs
        }

        idleScopes.forEach { (id, _) ->
            if (cancelScope(id)) {
                cleaned++
                Log.d(TAG, "Cleaned up idle scope: $id")
            }
        }

        if (cleaned > 0) {
            Log.i(TAG, "Cleaned up $cleaned idle scopes")
        }

        return cleaned
    }

    /**
     * Log current state for debugging
     */
    fun logState() {
        val stats = getStats()
        Log.d(TAG, """
            Background Task Manager State:
            - Active Scopes: ${stats.activeScopeCount}
            - Total Jobs: ${stats.totalJobCount}
            - WorkManager Tasks: ${stats.workManagerTaskCount}
            - Idle Scopes: ${stats.idleScopeCount}
            - Oldest Scope Age: ${stats.oldestScopeAge?.let { "${it / 1000}s" } ?: "N/A"}

            Scopes:
            ${managedScopes.values.joinToString("\n") {
                "  - ${it.id}: ${it.jobCount} jobs, idle ${(System.currentTimeMillis() - it.lastActivity) / 1000}s"
            }}
        """.trimIndent())
    }

    /**
     * Statistics about background tasks
     */
    data class BackgroundTaskStats(
        val activeScopeCount: Int,
        val totalJobCount: Int,
        val workManagerTaskCount: Int,
        val oldestScopeAge: Long?,
        val idleScopeCount: Int
    )

    /**
     * Cleanup all resources - call on app termination
     */
    fun shutdown() {
        Log.i(TAG, "Shutting down BackgroundTaskManager")
        logState()
        cancelAll()
    }
}

/**
 * Extension function to launch a job in a managed scope
 */
fun BackgroundTaskManager.launchInManagedScope(
    scopeId: String,
    block: suspend CoroutineScope.() -> Unit
): Job? {
    val scope = getScope(scopeId) ?: return null
    val job = scope.launch(block = block)
    registerJob(scopeId, job)
    return job
}
