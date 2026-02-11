package com.shadowai.pipelineplanner.di

import com.shadowai.pipelineplanner.PipelineGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PipelinePlannerModule {

    @Provides
    @Singleton
    fun providePipelineGraph(): PipelineGraph = PipelineGraph()
}
