package com.shadowai.app.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Thread-safe LRU Cache with automatic eviction and size limits.
 * 
 * @param maxSize Maximum number of entries in the cache
 * @param ttlMs Time-to-live in milliseconds (0 = no expiration)
 */
class LRUCache<K, V>(
    private val maxSize: Int = 100,
    private val ttlMs: Long = 0
) {
    private val mutex = Mutex()
    private val cacheScope = CoroutineScope(Dispatchers.IO + Job())
    private val cache = object : LinkedHashMap<K, CacheEntry<V>>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<K, CacheEntry<V>>): Boolean {
            return size > maxSize
        }
    }
    
    private val accessTimestamps = ConcurrentHashMap<K, Long>()
    private val scheduledCleanup: ScheduledExecutorService? = if (ttlMs > 0) {
        Executors.newSingleThreadScheduledExecutor().apply {
            scheduleWithFixedDelay(
                { runCleanup() },
                ttlMs / 2,
                ttlMs / 2,
                TimeUnit.MILLISECONDS
            )
        }
    } else null
    
    data class CacheEntry<T>(
        val value: T,
        val createdAt: Long = System.currentTimeMillis(),
        val lastAccessedAt: Long = System.currentTimeMillis(),
        val accessCount: Int = 0
    )
    
    /**
     * Get a value from the cache.
     */
    suspend fun get(key: K): V? = mutex.withLock {
        val entry = cache[key] ?: return@withLock null
        
        if (ttlMs > 0 && System.currentTimeMillis() - entry.createdAt > ttlMs) {
            cache.remove(key)
            accessTimestamps.remove(key)
            return@withLock null
        }
        
        // Update access metadata
        val updatedEntry = entry.copy(
            lastAccessedAt = System.currentTimeMillis(),
            accessCount = entry.accessCount + 1
        )
        cache[key] = updatedEntry
        accessTimestamps[key] = System.currentTimeMillis()
        
        updatedEntry.value
    }
    
    /**
     * Put a value in the cache.
     */
    suspend fun put(key: K, value: V): V? = mutex.withLock {
        val existingEntry = cache[key]
        val entry = CacheEntry(
            value = value,
            createdAt = existingEntry?.createdAt ?: System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            accessCount = existingEntry?.accessCount ?: 0
        )
        cache[key] = entry
        accessTimestamps[key] = System.currentTimeMillis()
        existingEntry?.value
    }
    
    /**
     * Remove a value from the cache.
     */
    suspend fun remove(key: K): V? = mutex.withLock {
        val entry = cache.remove(key)
        accessTimestamps.remove(key)
        entry?.value
    }
    
    /**
     * Check if key exists in cache.
     */
    suspend fun contains(key: K): Boolean = mutex.withLock {
        if (ttlMs > 0) {
            val entry = cache[key] ?: return@withLock false
            if (System.currentTimeMillis() - entry.createdAt > ttlMs) {
                cache.remove(key)
                accessTimestamps.remove(key)
                return@withLock false
            }
        }
        cache.containsKey(key)
    }
    
    /**
     * Get the current size of the cache.
     */
    suspend fun size(): Int = mutex.withLock {
        cache.size
    }
    
    /**
     * Clear all entries from the cache.
     */
    suspend fun clear() = mutex.withLock {
        cache.clear()
        accessTimestamps.clear()
    }
    
    /**
     * Get cache statistics.
     */
    suspend fun getStats(): CacheStats = mutex.withLock {
        val totalAccess = cache.values.sumOf { it.accessCount }
        CacheStats(
            size = cache.size,
            maxSize = maxSize,
            ttlMs = ttlMs,
            totalAccessCount = totalAccess,
            hitRate = if (totalAccess > 0) {
                cache.values.count { it.accessCount > 1 }.toDouble() / totalAccess
            } else 0.0
        )
    }
    
    private fun runCleanup() {
        if (ttlMs <= 0) return
        
        cacheScope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val expiredKeys = cache.entries.filter {
                    now - it.value.createdAt > ttlMs
                }.map { it.key }
                
                expiredKeys.forEach { key ->
                    cache.remove(key)
                    accessTimestamps.remove(key)
                }
            }
        }
    }
    
    /**
     * Shutdown the cleanup scheduler.
     */
    fun shutdown() {
        scheduledCleanup?.shutdown()
        cacheScope.cancel()
    }
    
    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val ttlMs: Long,
        val totalAccessCount: Int,
        val hitRate: Double
    )
}

/**
 * Multi-tier cache implementation that combines memory and disk caching.
 */
class MultiTierCache<K, V>(
    private val memoryCache: LRUCache<K, V>,
    private val diskCache: DiskCache<V>
) {
    private val mutex = Mutex()
    
    /**
     * Get value from cache, checking memory first then disk.
     */
    suspend fun get(key: K): V? {
        // Try memory first
        val memoryValue = memoryCache.get(key)
        if (memoryValue != null) {
            return memoryValue
        }
        
        // Try disk
        val diskValue = diskCache.get(key.toString())
        if (diskValue != null) {
            // Promote to memory
            memoryCache.put(key, diskValue)
            return diskValue
        }
        
        return null
    }
    
    /**
     * Put value in both memory and disk tiers.
     */
    suspend fun put(key: K, value: V) {
        memoryCache.put(key, value)
        diskCache.put(key.toString(), value)
    }
    
    /**
     * Remove from both tiers.
     */
    suspend fun remove(key: K) {
        memoryCache.remove(key)
        diskCache.remove(key.toString())
    }
    
    /**
     * Clear all tiers.
     */
    suspend fun clear() {
        memoryCache.clear()
        diskCache.clear()
    }
}
