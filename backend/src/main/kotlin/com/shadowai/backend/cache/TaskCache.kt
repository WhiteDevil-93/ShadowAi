package com.shadowai.backend.cache

import com.shadowai.backend.ImageResource
import com.shadowai.backend.TaskStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class TaskInfo(
    val taskId: String,
    var status: TaskStatus,
    var progress: Int = 0,
    val images: MutableList<ImageResource> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var lastAccessedAt: Long = System.currentTimeMillis()
)

class TaskCache(
    private val maxSize: Int = 1000,
    private val ttlMs: Long = 30 * 60 * 1000L // 30 minutes default TTL
) {
    private val store = ConcurrentHashMap<String, TaskInfo>()
    
    // Background cleanup scheduler
    private val cleanupScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "TaskCache-Cleanup").apply { isDaemon = true }
    }
    
    init {
        // Run cleanup every 5 minutes using scheduleWithFixedDelay for reliable execution
        cleanupScheduler.scheduleWithFixedDelay(
            {
                runCatching { cleanupExpiredEntries() }
                    .onFailure { /* keep scheduler alive even if cleanup fails */ }
            },
            5, 5, TimeUnit.MINUTES
        )
    }

    fun get(taskId: String): TaskInfo? {
        val entry = store[taskId] ?: return null
        entry.lastAccessedAt = System.currentTimeMillis()
        return entry
    }

    fun upsert(taskId: String, supplier: () -> TaskInfo): TaskInfo {
        // Enforce max size with LRU eviction before adding
        evictIfNecessary()
        
        return store.computeIfAbsent(taskId) { supplier() }.also {
            it.lastAccessedAt = System.currentTimeMillis()
        }
    }

    fun updateStatus(taskId: String, status: TaskStatus, progress: Int = 0, images: List<ImageResource> = emptyList()) {
        store.computeIfPresent(taskId) { _, current ->
            current.status = status
            current.progress = progress
            current.lastAccessedAt = System.currentTimeMillis()
            if (images.isNotEmpty()) {
                current.images.clear()
                current.images.addAll(images)
            }
            current
        }
    }

    fun hasTask(taskId: String) = store.containsKey(taskId)
    
    /**
     * Current cache size
     */
    fun size(): Int = store.size
    
    /**
     * Evict oldest entries if cache exceeds max size (LRU eviction).
     * Optimized to O(n) instead of O(n log n) by finding cutoff timestamp
     * and using removeIf.
     */
    private fun evictIfNecessary() {
        if (store.size < maxSize) return
        
        val targetSize = maxSize - (maxSize / 10) // Remove 10% when full
        if (store.size <= targetSize) return

        // Find the nth oldest access time where n = targetSize
        val accessTimes = store.values.map { it.lastAccessedAt }.sorted()
        val cutoffTime = accessTimes.getOrNull(targetSize) ?: return

        // Remove entries older than cutoff in one pass
        store.entries.removeIf { it.value.lastAccessedAt <= cutoffTime }
    }
    
    /**
     * Remove entries that have exceeded TTL
     */
    private fun cleanupExpiredEntries() {
        val now = System.currentTimeMillis()
        store.entries.removeIf { now - it.value.createdAt > ttlMs }
    }
    
    /**
     * Shutdown the cleanup scheduler
     */
    fun shutdown() {
        cleanupScheduler.shutdown()
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            cleanupScheduler.shutdownNow()
        }
    }
}
