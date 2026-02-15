package com.shadowai.app.db

import androidx.room.*
import com.shadowai.app.ui.ChatMessage

@Entity(
    tableName = "messages",
    indices = [Index(value = ["timestamp"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val text: String,
    val isUser: Boolean,
    val isThinking: Boolean,
    val timestamp: Long,
    val modelName: String? = null,
    val executionSource: String? = null,
    val error: String? = null,
    val imageUri: String? = null
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ChatMessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessagesOnce(): List<ChatMessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessages(limit: Int, offset: Int): List<ChatMessageEntity>

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(): ChatMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)

    @Query("DELETE FROM messages WHERE timestamp >= :timestamp")
    suspend fun deleteMessagesFrom(timestamp: Long)

    @Query("DELETE FROM messages")
    suspend fun clearHistory()

    @Query("DELETE FROM messages")
    fun deleteAllMessages()
}

@Entity(
    tableName = "memory",
    indices = [
        Index(value = ["key"]),
        Index(value = ["layer"]),
        Index(value = ["lastUpdated"])
    ]
)
data class MemoryEntity(
    @PrimaryKey val key: String,
    val value: String,
    val layer: String = "WORKING", // WORKING, EPISODIC, SYMBOLIC
    val confidence: Float = 1.0f,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory")
    suspend fun getFullMemory(): List<MemoryEntity>

    @Query("SELECT * FROM memory WHERE `key` = :key LIMIT 1")
    suspend fun getMemory(key: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMemory(memory: MemoryEntity)

    @Query("DELETE FROM memory WHERE `key` = :key")
    suspend fun forgetMemory(key: String)

    @Query("SELECT * FROM memory WHERE layer = :layer")
    suspend fun getMemoryByLayer(layer: String): List<MemoryEntity>

    @Query("DELETE FROM memory WHERE confidence < :threshold")
    suspend fun deleteLowConfidence(threshold: Float)

    @Query("DELETE FROM memory WHERE lastUpdated < :timestamp")
    suspend fun deleteExpiredMemories(timestamp: Long)

    @Query("DELETE FROM memory")
    suspend fun clearMemory()
}

@Entity(
    tableName = "ledger",
    indices = [Index(value = ["timestamp"])]
)
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stepId: String,
    val timestamp: Long,
    val actionHash: String,
    val resultHash: String,
    val verificationStatus: String
)

@Dao
interface LedgerDao {
    @Insert
    suspend fun log(entry: LedgerEntry)

    @Query("SELECT * FROM ledger ORDER BY timestamp DESC")
    suspend fun getLog(): List<LedgerEntry>

    @Query("DELETE FROM ledger WHERE timestamp < :timestamp")
    suspend fun deleteOldEntries(timestamp: Long)

    @Query("DELETE FROM ledger")
    suspend fun clearLedger()
}

/**
 * Phase 2.2: Persistent Authority
 * Stores current and historical task states including multi-step plans.
 */
@Entity(
    tableName = "tasks",
    indices = [Index(value = ["status"])]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val type: String,
    val input: String,
    val planJson: String? = null,
    val status: String,
    val lastUpdated: Long
)

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTask(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTask(id: String): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY lastUpdated DESC")
    suspend fun getAllTasks(): List<TaskEntity>
}

/**
 * Phase 6.1: Engineering for Failure
 * Dead-Letter Queue (DLQ) for recording unrecoverable agent errors.
 */
@Entity(
    tableName = "failure_dlq",
    indices = [Index(value = ["timestamp"])]
)
data class AgentFailureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskInput: String,
    val errorCategory: String,
    val errorMessage: String,
    val rawOutput: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface FailureDao {
    @Insert
    suspend fun logFailure(failure: AgentFailureEntity)

    @Query("SELECT * FROM failure_dlq ORDER BY timestamp DESC")
    suspend fun getFailures(): List<AgentFailureEntity>

    @Query("DELETE FROM failure_dlq WHERE timestamp < :timestamp")
    suspend fun deleteOldFailures(timestamp: Long)

    @Query("DELETE FROM failure_dlq")
    suspend fun clearFailures()

    @Query("SELECT COUNT(*) FROM failure_dlq")
    suspend fun getFailureCount(): Int
}

@Entity(
    tableName = "feedback",
    indices = [Index(value = ["messageId"])]
)
data class FeedbackEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val rating: Int,
    val feedbackType: String,
    val comment: String?,
    val timestamp: Long,
    val wasHelpful: Boolean?
)

@Dao
interface FeedbackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: FeedbackEntity)

    @Query("SELECT * FROM feedback WHERE messageId = :messageId")
    suspend fun getFeedbackForMessage(messageId: String): List<FeedbackEntity>

    @Query("SELECT * FROM feedback WHERE feedbackType = :type")
    suspend fun getFeedbackByType(type: String): List<FeedbackEntity>

    @Query("SELECT * FROM feedback ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentFeedback(limit: Int): List<FeedbackEntity>

    @Query("SELECT AVG(rating) FROM feedback")
    suspend fun getAverageRating(): Double?

    @Query("SELECT rating, COUNT(*) as count FROM feedback GROUP BY rating")
    suspend fun getRatingDistribution(): List<RatingCount>

    @Query("DELETE FROM feedback")
    suspend fun clearAllFeedback()
}

data class RatingCount(
    val rating: Int,
    val count: Int
)



