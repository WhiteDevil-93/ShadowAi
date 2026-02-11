package com.shadowai.artifactsystem.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Placeholder DI module for artifact-system.
 *
 * The artifact system functionality is currently provided by core-contracts (Artifact class)
 * and will be expanded in Phase 5 (Pipeline Planner & Artifacts).
 *
 * This module is intentionally minimal to avoid build conflicts while maintaining
 * the module structure for future implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
object ArtifactSystemModule {
    // Placeholder for future artifact system bindings
    // Phase 5 will implement:
    // - ArtifactCache
    // - ArtifactStore
    // - ArtifactValidator
    // - ArtifactConverter
}
