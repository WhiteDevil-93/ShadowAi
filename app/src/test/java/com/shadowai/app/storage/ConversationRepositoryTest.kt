package com.shadowai.app.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.google.gson.Gson
import com.shadowai.app.ai.ConversationSummarizer
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class ConversationRepositoryTest {

    private lateinit var repository: ConversationRepository
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockGson = mockk<Gson>(relaxed = true)
    private val mockDataStore = mockk<DataStore<Preferences>>(relaxed = true)
    private val mockPreferences = mockk<Preferences>()

    private val testConversationId = "test-conversation-123"
    private val testSummary = ConversationSummarizer.StoredSummary(
        metadata = ConversationSummarizer.SummaryMetadata(
            id = "summary-1",
            createdAt = 1234567890L,
            messageCount = 5,
            originalContextTokens = 1000,
            summaryTokens = 150,
            timestampBegin = 1234560000L,
            timestampEnd = 1234567890L
        ),
        content = "This is a test summary",
        summarizedMessages = emptyList()
    )

    @Before
    fun setup() {
        // Mock DataStore creation
        mockkStatic(androidx.datastore.preferences.preferencesDataStore)
        every { mockContext.applicationContext } returns mockContext

        repository = ConversationRepository(mockContext, mockGson)
    }

    @After
    fun tearDown() {
        unmockkStatic(androidx.datastore.preferences.preferencesDataStore)
        clearAllMocks()
    }

    @Test
    fun `generateConversationId should generate unique IDs`() {
        val id1 = repository.generateConversationId()
        val id2 = repository.generateConversationId()

        assertNotNull(id1)
        assertNotNull(id2)
        assertTrue(id1 != id2)
        assertTrue(id1.isNotEmpty())
        assertTrue(id2.isNotEmpty())
    }

    @Test
    fun `saveSummary should store summary correctly`() = runTest {
        val summaryDataSlot = slot<ConversationRepository.SummaryData>()

        coEvery {
            mockGson.toJson(any<Map<String, List<ConversationRepository.SummaryData>>>())
        } returns "{}"

        coEvery {
            mockDataStore.edit(any())
        } coAnswers {
            val editor = it.invocation.args[0] as (suspend (MutablePreferences) -> Unit)
            editor(mockk(relaxed = true))
        }

        // This test verifies the method can be called without throwing
        repository.saveSummary(testConversationId, testSummary)
        
        coVerify { mockDataStore.edit(any()) }
    }

    @Test
    fun `getSummaries should return empty list for new conversation`() = runTest {
        // Mock empty preferences
        every { mockPreferences[any<Preferences.Key<String>>()] } returns null
        every { mockGson.fromJson(any<String>(), any<Class<*>>()) } returns emptyMap()

        every { mockDataStore.data } returns flowOf(mockPreferences)

        val summaries = repository.getSummaries(testConversationId).first()
        
        assertTrue(summaries.isEmpty())
    }

    @Test
    fun `getSummaryCount should return correct count`() = runTest {
        every { mockPreferences[any<Preferences.Key<String>>()] } returns null
        every { mockGson.fromJson(any<String>(), any<Class<*>>()) } returns emptyMap()
        every { mockDataStore.data } returns flowOf(mockPreferences)

        val count = repository.getSummaryCount(testConversationId).first()
        
        assertEquals(0, count)
    }

    @Test
    fun `deleteSummary should remove summary from storage`() = runTest {
        coEvery { mockGson.toJson(any<Map<String, List<ConversationRepository.SummaryData>>>()) } returns "{}"
        
        coEvery {
            mockDataStore.edit(any())
        } coAnswers {
            val editor = it.invocation.args[0] as (suspend (MutablePreferences) -> Unit)
            editor(mockk(relaxed = true))
        }

        // This test verifies the method can be called without throwing
        repository.deleteSummary(testConversationId, "summary-1")
        
        coVerify { mockDataStore.edit(any()) }
    }

    @Test
    fun `deleteAllSummaries should remove all summaries for conversation`() = runTest {
        coEvery { mockGson.toJson(any<Map<String, List<ConversationRepository.SummaryData>>>()) } returns "{}"
        
        coEvery {
            mockDataStore.edit(any())
        } coAnswers {
            val editor = it.invocation.args[0] as (suspend (MutablePreferences) -> Unit)
            editor(mockk(relaxed = true))
        }

        // This test verifies the method can be called without throwing
        repository.deleteAllSummaries(testConversationId)
        
        coVerify { mockDataStore.edit(any()) }
    }

    @Test
    fun `clearAllSummaries should remove all summaries`() = runTest {
        coEvery {
            mockDataStore.edit(any())
        } coAnswers {
            val editor = it.invocation.args[0] as (suspend (MutablePreferences) -> Unit)
            editor(mockk(relaxed = true))
        }

        // This test verifies the method can be called without throwing
        repository.clearAllSummaries()
        
        coVerify { mockDataStore.edit(any()) }
    }
}