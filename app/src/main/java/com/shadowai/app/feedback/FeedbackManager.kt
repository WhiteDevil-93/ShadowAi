package com.shadowai.app.feedback

import android.content.Context
import com.shadowai.app.db.ShadowDatabase
import com.shadowai.app.db.FeedbackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Manager for user feedback and ratings on AI responses.
 */
class FeedbackManager(context: Context) {
    
    private val database = ShadowDatabase.getDatabase(context)
    private val feedbackDao = database.feedbackDao()
    
    data class Feedback(
        val id: String = UUID.randomUUID().toString(),
        val messageId: String,
        val rating: Int, // 1-5 stars
        val feedbackType: FeedbackType,
        val comment: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val wasHelpful: Boolean? = null
    )
    
    enum class FeedbackType {
        HELPFUL,
        NOT_HELPFUL,
        ACCURATE,
        INACCURATE,
        RELEVANT,
        IRRELEVANT,
        WELL_WRITTEN,
        CONFUSING,
        TOO_LONG,
        TOO_SHORT,
        OTHER
    }
    
    private val _recentFeedback = MutableStateFlow<List<Feedback>>(emptyList())
    val recentFeedback: Flow<List<Feedback>> = _recentFeedback.asStateFlow()
    
    private val _averageRating = MutableStateFlow(0.0)
    val averageRating: Flow<Double> = _averageRating.asStateFlow()
    
    /**
     * Submit feedback for a message.
     */
    suspend fun submitFeedback(feedback: Feedback) {
        val entity = FeedbackEntity(
            id = feedback.id,
            messageId = feedback.messageId,
            rating = feedback.rating,
            feedbackType = feedback.feedbackType.name,
            comment = feedback.comment,
            timestamp = feedback.timestamp,
            wasHelpful = feedback.wasHelpful
        )
        
        feedbackDao.insertFeedback(entity)
        refreshStats()
    }
    
    /**
     * Rate a message (1-5 stars).
     */
    suspend fun rateMessage(messageId: String, rating: Int, comment: String? = null) {
        require(rating in 1..5) { "Rating must be between 1 and 5" }
        
        submitFeedback(
            Feedback(
                messageId = messageId,
                rating = rating,
                feedbackType = if (rating >= 4) FeedbackType.HELPFUL else FeedbackType.NOT_HELPFUL,
                comment = comment
            )
        )
    }
    
    /**
     * Mark a message as helpful or not helpful.
     */
    suspend fun markHelpfulness(messageId: String, wasHelpful: Boolean) {
        val feedbackType = if (wasHelpful) FeedbackType.HELPFUL else FeedbackType.NOT_HELPFUL
        val rating = if (wasHelpful) 5 else 1
        
        submitFeedback(
            Feedback(
                messageId = messageId,
                rating = rating,
                feedbackType = feedbackType,
                wasHelpful = wasHelpful
            )
        )
    }
    
    /**
     * Get all feedback for a specific message.
     */
    suspend fun getFeedbackForMessage(messageId: String): List<Feedback> {
        return feedbackDao.getFeedbackForMessage(messageId).map { it.toFeedback() }
    }
    
    /**
     * Get all feedback of a specific type.
     */
    suspend fun getFeedbackByType(type: FeedbackType): List<Feedback> {
        return feedbackDao.getFeedbackByType(type.name).map { it.toFeedback() }
    }
    
    /**
     * Get the most recent feedback.
     */
    suspend fun getRecentFeedback(limit: Int = 10): List<Feedback> {
        return feedbackDao.getRecentFeedback(limit).map { it.toFeedback() }
    }
    
    /**
     * Calculate and return the average rating.
     */
    suspend fun getAverageRating(): Double {
        return feedbackDao.getAverageRating() ?: 0.0
    }
    
    /**
     * Get the distribution of ratings (how many 1-star, 2-star, etc.).
     */
    suspend fun getRatingDistribution(): Map<Int, Int> {
        return feedbackDao.getRatingDistribution().associate { it.rating to it.count }
    }
    
    /**
     * Get feedback statistics.
     */
    suspend fun getStats(): FeedbackStats {
        val avgRating = getAverageRating()
        val distribution = getRatingDistribution()
        val totalFeedback = distribution.values.sum()
        
        return FeedbackStats(
            totalFeedbackCount = totalFeedback,
            averageRating = avgRating,
            ratingDistribution = distribution,
            helpfulCount = feedbackDao.getFeedbackByType(FeedbackType.HELPFUL.name).size,
            notHelpfulCount = feedbackDao.getFeedbackByType(FeedbackType.NOT_HELPFUL.name).size
        )
    }
    
    /**
     * Clear all feedback data.
     */
    suspend fun clearAllFeedback() {
        feedbackDao.clearAllFeedback()
        refreshStats()
    }
    
    private suspend fun refreshStats() {
        _averageRating.value = getAverageRating()
        _recentFeedback.value = getRecentFeedback()
    }
    
    private fun FeedbackEntity.toFeedback(): Feedback {
        return Feedback(
            id = id,
            messageId = messageId,
            rating = rating,
            feedbackType = try {
                FeedbackType.valueOf(feedbackType)
            } catch (e: IllegalArgumentException) {
                FeedbackType.OTHER
            },
            comment = comment,
            timestamp = timestamp,
            wasHelpful = wasHelpful
        )
    }
    
    data class FeedbackStats(
        val totalFeedbackCount: Int,
        val averageRating: Double,
        val ratingDistribution: Map<Int, Int>,
        val helpfulCount: Int,
        val notHelpfulCount: Int
    ) {
        val helpfulPercent: Double
            get() = if (totalFeedbackCount > 0) {
                (helpfulCount.toDouble() / totalFeedbackCount * 100)
            } else 0.0
    }
}

/**
 * Quality evaluator for AI responses.
 */
class ResponseQualityEvaluator {
    
    data class QualityScore(
        val overall: Double, // 0-100
        val accuracy: Double, // 0-100
        val relevance: Double, // 0-100
        val clarity: Double, // 0-100
        val conciseness: Double // 0-100
    )
    
    /**
     * Evaluate the quality of a response based on user feedback patterns.
     */
    fun evaluateQuality(feedbackList: List<com.shadowai.app.feedback.FeedbackManager.Feedback>): QualityScore {
        if (feedbackList.isEmpty()) {
            return QualityScore(0.0, 0.0, 0.0, 0.0, 0.0)
        }
        
        val avgRating = feedbackList.map { it.rating }.average()
        val accuracy = feedbackList.count { it.feedbackType == com.shadowai.app.feedback.FeedbackManager.FeedbackType.ACCURATE || it.feedbackType == com.shadowai.app.feedback.FeedbackManager.FeedbackType.HELPFUL }
        val relevance = feedbackList.count { it.feedbackType == com.shadowai.app.feedback.FeedbackManager.FeedbackType.RELEVANT || it.feedbackType == com.shadowai.app.feedback.FeedbackManager.FeedbackType.HELPFUL }
        val clarity = feedbackList.count { it.feedbackType == com.shadowai.app.feedback.FeedbackManager.FeedbackType.WELL_WRITTEN || it.rating >= 4 }
        val conciseness = feedbackList.size - feedbackList.count { it.feedbackType == com.shadowai.app.feedback.FeedbackManager.FeedbackType.TOO_LONG }
        
        val total = feedbackList.size.toDouble()
        
        return QualityScore(
            overall = (avgRating / 5.0) * 100,
            accuracy = (accuracy / total) * 100,
            relevance = (relevance / total) * 100,
            clarity = (clarity / total) * 100,
            conciseness = (conciseness / total) * 100
        )
    }
    
    /**
     * Get suggestions for improvement based on feedback.
     */
    fun getImprovementSuggestions(feedbackList: List<com.shadowai.app.feedback.FeedbackManager.Feedback>): List<String> {
        val suggestions = mutableListOf<String>()
        
        val quality = evaluateQuality(feedbackList)
        
        if (quality.accuracy < 50) {
            suggestions.add("Consider providing more accurate information")
        }
        if (quality.relevance < 50) {
            suggestions.add("Try to be more relevant to the user's query")
        }
        if (quality.clarity < 50) {
            suggestions.add("Work on improving the clarity of responses")
        }
        if (quality.conciseness < 50) {
            suggestions.add("Try to be more concise in responses")
        }
        
        val notHelpful = feedbackList.count { it.feedbackType == com.shadowai.app.feedback.FeedbackManager.FeedbackType.NOT_HELPFUL }
        if (notHelpful > feedbackList.size / 2) {
            suggestions.add("Review the overall quality of responses")
        }
        
        return suggestions
    }
}
