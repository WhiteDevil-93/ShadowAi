package com.shadowai.pipelineplanner

import com.shadowai.core.ProviderExecutor
import com.shadowai.core.Transform

/**
 * Represents a complete transformation plan with ordered transforms.
 */
data class PipelinePlan(
    val transforms: List<Transform>,
    val totalSteps: Int,
    val isDirect: Boolean,
    val executor: ProviderExecutor? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Describes the transformation plan as a sequence.
     */
    fun getDescription(): String {
        val prefix = if (isDirect) "Direct" else "Multi-step"
        val transforms = transforms.joinToString(" -> ") { it::class.simpleName ?: "Transform" }
        return "$prefix pipeline ($totalSteps steps): $transforms"
    }

    /**
     * Estimates the cost of executing this plan.
     */
    fun estimateCost(): Float {
        val baseCost = totalSteps.toFloat()
        val complexityMultiplier = if (isDirect) 1.0f else 1.5f * totalSteps
        return baseCost * complexityMultiplier
    }

    /**
     * Estimates the execution time in milliseconds.
     */
    fun estimateExecutionTime(): Long {
        val baseTime = 500L // Base time per step
        val stepTime = baseTime * totalSteps
        return if (isDirect) stepTime else (stepTime * 1.3).toLong() // Multi-step penalty
    }

    /**
     * Checks if the plan is feasible given constraints.
     */
    fun isFeasible(
        maxSteps: Int = 5,
        requiresInternet: Boolean = false
    ): Boolean {
        return totalSteps <= maxSteps && (requiresInternet || !metadata.containsKey("requiresNetwork"))
    }

    companion object {
        /**
         * Creates an empty plan (identity transformation).
         */
        fun empty(): PipelinePlan = PipelinePlan(
            transforms = emptyList(),
            totalSteps = 0,
            isDirect = true
        )

        /**
         * Creates a single-step plan.
         */
        fun single(
            transform: Transform,
            executor: ProviderExecutor? = null
        ): PipelinePlan = PipelinePlan(
            transforms = listOf(transform),
            totalSteps = 1,
            isDirect = true,
            executor = executor
        )

        /**
         * Creates a multi-step plan.
         */
        fun multi(
            transforms: List<Transform>,
            executor: ProviderExecutor? = null
        ): PipelinePlan = PipelinePlan(
            transforms = transforms,
            totalSteps = transforms.size,
            isDirect = false,
            executor = executor
        )
    }
}

/**
 * Represents the result of a transformation.
 */
data class TransformResult(
    val transform: Transform,
    val input: Any,
    val output: Any,
    val latency: Long,
    val success: Boolean
) {
    val throughputMs: Double
        get() = if (latency > 0) 1000.0 / latency else 0.0

    override fun toString(): String = """
        |Transform Result:
        | - Transform: ${transform::class.simpleName}
        | - Latency: ${latency}ms
        | - Throughput: ${String.format("%.2f", throughputMs)} ops/sec
        | - Success: $success
    """.trimMargin()
}

/**
 * Represents a collection of pipeline plans with ranking.
 */
data class PipelinePlanRanking(
    val plans: List<PipelinePlan>,
    val criteria: RankingCriteria = RankingCriteria()
) {
    /**
     * Gets the best plan according to criteria.
     */
    fun getBestPlan(): PipelinePlan? {
        if (plans.isEmpty()) return null

        return plans.minByOrNull { plan ->
            val costScore = plan.estimateCost() * criteria.costWeight
            val stepScore = plan.totalSteps.toFloat() * criteria.stepsWeight
            val directScore = if (plan.isDirect) 0f else criteria.complexityPenalty
            costScore + stepScore + directScore
        }
    }

    /**
     * Gets all feasible plans.
     */
    fun getFeasiblePlans(): List<PipelinePlan> {
        return plans.filter { it.isFeasible(criteria.maxSteps) }
    }

    /**
     * Ranks plans by multiple criteria.
     */
    fun rankPlans(): List<Pair<PipelinePlan, Float>> {
        return plans.map { plan ->
            val score = calculateScore(plan)
            plan to score
        }.sortedByDescending { it.second }
    }

    private fun calculateScore(plan: PipelinePlan): Float {
        val costScore = (1.0f / plan.estimateCost()) * criteria.costWeight
        val directScore = if (plan.isDirect) criteria.directBonus else 0f
        val feasibilityScore = if (plan.isFeasible(criteria.maxSteps)) criteria.feasibilityBonus else -criteria.feasibilityPenalty
        return costScore + directScore + feasibilityScore
    }

    override fun toString(): String = """
        |Pipeline Plans (${plans.size}):
        | - Best: ${getBestPlan()?.getDescription()}
        | - Feasible: ${getFeasiblePlans().size}
        | - Criteria: $criteria
    """.trimMargin()
}

/**
 * Criteria for ranking pipeline plans.
 */
data class RankingCriteria(
    val costWeight: Float = 10f,
    val stepsWeight: Float = 5f,
    val directBonus: Float = 20f,
    val complexityPenalty: Float = 15f,
    val feasibilityBonus: Float = 10f,
    val feasibilityPenalty: Float = 50f,
    val maxSteps: Int = 5
) {
    override fun toString(): String = """
        |Ranking Criteria:
        | - Cost Weight: $costWeight
        | - Steps Weight: $stepsWeight
        | - Direct Bonus: $directBonus
        | - Max Steps: $maxSteps
    """.trimMargin()
}
