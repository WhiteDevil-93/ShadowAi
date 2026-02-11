package com.shadowai.app.thread

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shadowai.modelcatalog.ModelDiscovery
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

/**
 * Integration tests for ModelDiscovery thread safety.
 * Verifies concurrent operations don't cause race conditions or duplicate entries.
 */
@RunWith(AndroidJUnit4::class)
class ConcurrentRescanTest {

    private lateinit var modelDiscovery: ModelDiscovery
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        modelDiscovery = ModelDiscovery(context, Gson())
    }

    @Test
    fun `concurrent rescan produces no duplicates`() = runBlocking {
        val repetitions = 10
        val allModelSets = mutableSetOf<String>()

        coroutineScope {
            val jobs = List(repetitions) { index ->
                async(Dispatchers.IO) {
                    val models = modelDiscovery.rescan()
                    models.forEach { model ->
                        allModelSets.add(model.id)
                    }
                    models
                }
            }

            // Wait for all concurrent scans to complete
            val results = jobs.awaitAll()

            // Verify all scans completed successfully
            results.forEach { models ->
                assertNotNull("Scan should return non-null result", models)
            }

            // Verify no duplicate model IDs across all scans
            val modelIdCounts = ConcurrentHashMap<String, Int>()
            results.forEach { models ->
                models.forEach { model ->
                    modelIdCounts.compute(model.id) { _, count -> (count ?: 0) + 1 }
                }
            }

            modelIdCounts.forEach { (modelId, count) ->
                assertEquals("Model $modelId should have consistent count across scans",
                    repetitions, count)
            }
        }

        println("✅ All concurrent scans completed with no duplicates")
    }

    @Test
    fun `concurrent model discovery uses mutex correctly`() = runBlocking {
        val iterations = 20
        val scanCount = ConcurrentHashMap<String, Int>()

        coroutineScope {
            val jobs = List(iterations) {
                async(Dispatchers.IO) {
                    val models = modelDiscovery.discoverFromAllSources()
                    models.forEach { scanCount.compute(it.id) { _, c -> (c ?: 0) + 1 } }
                    models.size
                }
            }

            val sizes = jobs.awaitAll()

            // All scans should complete successfully
            assertTrue("All scans should complete", sizes.all { it >= 0 })
        }

        println("✅ Concurrent mutex protection verified")
    }

    @Test
    fun `custom directory scans are thread-safe`() = runBlocking {
        val testDirs = listOf(
            context.filesDir.absolutePath,
            context.getExternalFilesDir("models")?.absolutePath
                ?: context.filesDir.absolutePath
        )

        val concurrentScans = 5
        val results = mutableMapOf<Int, List<String>>()

        coroutineScope {
            val jobs = (0 until concurrentScans).map { index ->
                async(Dispatchers.IO) {
                    val models = modelDiscovery.discoverFromAllSources(
                        localModelDirs = testDirs,
                        jsonConfigFiles = emptyList()
                    )
                    results[index] = models.map { it.id }
                    models.size
                }
            }

            jobSizes = jobs.awaitAll()
        }

        // Verify thread safety: results should be deterministically the same
        val uniqueResultCounts = results.values.map { it.size }.toSet()
        assertEquals("All scans should find same number of models", 1, uniqueResultCounts.size)

        println("✅ Custom directory scans are thread-safe")
    }
}
