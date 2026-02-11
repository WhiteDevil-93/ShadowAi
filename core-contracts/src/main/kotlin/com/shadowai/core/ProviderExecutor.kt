package com.shadowai.core

/**
 * Interface for executing AI transformations through a provider.
 * Implementations handle the actual execution of transforms using specific providers
 * (local models, remote APIs, etc.) while presenting a unified interface.
 *
 * Refactored for Phase 5: Unified Artifact System integration.
 */
interface ProviderExecutor {
    /**
     * The provider identifier for this executor.
     */
    val providerId: ProviderId

    /**
     * Checks if the provider is available and ready to execute.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Determines if this executor can perform the specified transform.
     */
    suspend fun canExecute(transform: Transform): Boolean

    /**
     * Executes a transform with the given input and parameters.
     *
     * @param transform The transformation to perform
     * @param input The input artifact
     * @param parameters Additional execution parameters
     * @return Result containing the output artifact or an error
     */
    suspend fun execute(
        transform: Transform,
        input: Artifact,
        parameters: Map<String, Any> = emptyMap()
    ): Result<Artifact>

    /**
     * Returns the priority for this executor when multiple executors can handle a transform.
     * Higher values indicate higher priority.
     */
    fun getPriority(transform: Transform): Int = 0
}
