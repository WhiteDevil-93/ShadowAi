package com.shadowai.app.cache

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Disk-based cache for persisting data across app restarts.
 * 
 * @param context Application context
 * @param cacheDirName Name of the cache directory
 * @param maxSizeBytes Maximum cache size in bytes
 * @param gson Gson instance for serialization
 */
class DiskCache<V>(
    private val context: Context,
    private val cacheDirName: String = "disk_cache",
    private val maxSizeBytes: Long = 50 * 1024 * 1024, // 50MB
    private val gson: Gson = Gson()
) {
    private val cacheDir: File by lazy {
        File(context.cacheDir, cacheDirName).apply {
            if (!exists()) mkdirs()
        }
    }
    
    private val metadata = ConcurrentHashMap<String, CacheMetadata>()
    private val mutex = kotlinx.coroutines.sync.Mutex()
    
    data class CacheMetadata(
        val key: String,
        val size: Long,
        val createdAt: Long,
        val lastAccessedAt: Long,
        val accessCount: Int
    )
    
    /**
     * Get a value from disk cache.
     */
    suspend fun get(key: String): V? = withContext(Dispatchers.IO) {
        val file = getFile(key)
        if (!file.exists()) return@withContext null
        
        try {
            val json = file.readText()
            @Suppress("UNCHECKED_CAST")
            val cached = gson.fromJson(json, CachedValue::class.java) as? CachedValue<V>
                ?: return@withContext null
            
            // Update access metadata
            updateMetadata(key) { it.copy(
                lastAccessedAt = System.currentTimeMillis(),
                accessCount = it.accessCount + 1
            )}
            
            cached.value
        } catch (e: Exception) {
            // Corrupted cache entry
            file.delete()
            metadata.remove(key)
            null
        }
    }
    
    /**
     * Put a value in disk cache.
     */
    suspend fun put(key: String, value: V) = withContext(Dispatchers.IO) {
        mutex.lock()
        try {
            val file = getFile(key)
            val cached = CachedValue(value, System.currentTimeMillis())
            val json = gson.toJson(cached)
            file.writeText(json)
            
            // Create metadata
            val metadata = CacheMetadata(
                key = key,
                size = file.length(),
                createdAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis(),
                accessCount = 1
            )
            this@DiskCache.metadata[key] = metadata
            
            // Enforce size limit
            enforceSizeLimit()
        } catch (e: Exception) {
            // Handle write errors
        } finally {
            mutex.unlock()
        }
    }
    
    /**
     * Remove a value from disk cache.
     */
    suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        val file = getFile(key)
        if (file.exists()) {
            file.delete()
        }
        metadata.remove(key)
    }
    
    /**
     * Check if key exists in cache.
     */
    suspend fun contains(key: String): Boolean = withContext(Dispatchers.IO) {
        getFile(key).exists()
    }
    
    /**
     * Get current cache size in bytes.
     */
    suspend fun size(): Long = withContext(Dispatchers.IO) {
        metadata.values.sumOf { it.size }
    }
    
    /**
     * Clear all entries from disk cache.
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.lock()
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            metadata.clear()
        } finally {
            mutex.unlock()
        }
    }
    
    /**
     * Get cache statistics.
     */
    suspend fun getStats(): DiskCacheStats = withContext(Dispatchers.IO) {
        mutex.lock()
        try {
            val totalSize = metadata.values.sumOf { it.size }
            val totalEntries = metadata.size
            val totalAccess = metadata.values.sumOf { it.accessCount }
            
            DiskCacheStats(
                entryCount = totalEntries,
                totalSizeBytes = totalSize,
                maxSizeBytes = maxSizeBytes,
                usagePercent = if (maxSizeBytes > 0) {
                    (totalSize.toDouble() / maxSizeBytes * 100)
                } else 0.0,
                totalAccessCount = totalAccess
            )
        } finally {
            mutex.unlock()
        }
    }
    
    private fun getFile(key: String): File {
        // Sanitize key to be a valid filename
        val sanitizedKey = key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(cacheDir, "${sanitizedKey}.cache")
    }
    
    private fun updateMetadata(key: String, update: (CacheMetadata) -> CacheMetadata) {
        metadata[key]?.let { old ->
            metadata[key] = update(old)
        }
    }
    
    private suspend fun enforceSizeLimit() {
        if (maxSizeBytes <= 0) return
        
        var currentSize = size()
        if (currentSize <= maxSizeBytes) return
        
        // Remove least recently used entries
        val entriesToRemove = metadata.values
            .sortedBy { it.lastAccessedAt }
            .takeWhile { currentSize > maxSizeBytes * 0.8 } // Target 80% of max
        
        entriesToRemove.forEach { entry ->
            remove(entry.key)
            currentSize -= entry.size
        }
    }
    
    private data class CachedValue<T>(
        val value: T,
        val cachedAt: Long
    )
    
    data class DiskCacheStats(
        val entryCount: Int,
        val totalSizeBytes: Long,
        val maxSizeBytes: Long,
        val usagePercent: Double,
        val totalAccessCount: Int
    )
}
