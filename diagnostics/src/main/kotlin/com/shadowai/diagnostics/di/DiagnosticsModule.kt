package com.shadowai.diagnostics.di

import com.shadowai.diagnostics.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency Injection module for the Diagnostics System (Phase 6).
 */
@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsModule {

    @Provides
    @Singleton
    fun provideErrorContextStore(): ErrorContextStore = ErrorContextStore()

    @Provides
    @Singleton
    fun provideDiagnosticsLogger(): DiagnosticsLogger = LogcatDiagnosticsLogger()

    @Provides
    @Singleton
    fun provideErrorAnalytics(): ErrorAnalytics = ErrorAnalytics()

    @Provides
    @Singleton
    fun provideErrorCollector(
        logger: DiagnosticsLogger,
        analytics: ErrorAnalytics,
        contextStore: ErrorContextStore
    ): ErrorCollector = ErrorCollector(logger, analytics, contextStore)
}
