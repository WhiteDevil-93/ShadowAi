package com.shadowai.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module providing AI agent dependencies.
 *
 * NOTE: ShadowAgent and SupervisorAgent use @Inject constructors and are
 * automatically provided by Hilt. AgenticLoop is created manually by
 * SupervisorAgent per-task, not as a singleton.
 *
 * This module now serves as a placeholder for any additional agent-related
 * bindings that can't use constructor injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentModule {
    // ShadowAgent, SupervisorAgent, and AgenticLoop use @Inject constructors
    // and are automatically managed by Hilt's dependency injection.
    // No manual @Provides methods needed for these classes.
}
