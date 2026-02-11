package com.shadowai.app.di

import android.content.Context
import com.google.gson.Gson
import com.shadowai.app.ai.*
import com.shadowai.app.db.FailureDao
import com.shadowai.app.db.FeedbackDao
import com.shadowai.app.db.LedgerDao
import com.shadowai.app.db.MemoryDao
import com.shadowai.app.db.MessageDao
import com.shadowai.app.db.ShadowDatabase
import com.shadowai.app.db.TaskDao
import com.shadowai.app.security.AccessControlManager
import com.shadowai.app.security.BiometricKeyManager
import com.shadowai.app.security.SecurityManager
import com.shadowai.core.LocalInferenceEngine
import com.shadowai.core.security.PiiMaskingProcessor
import com.shadowai.core.security.TeeKeyManager
import com.shadowai.pipelineplanner.PipelineExecutor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideLlamaNative(): LlamaNative = LlamaNative()

    @Provides
    @Singleton
    fun provideLocalInferenceManager(
        @ApplicationContext context: Context,
        piiMaskingProcessor: PiiMaskingProcessor
    ): LocalInferenceManager = LocalInferenceManager(context, piiMaskingProcessor)

    /**
     * Provides runtime-switchable LocalInferenceEngine.
     *
     * Default selection comes from BuildConfig, while runtime preference updates
     * are handled by SwitchableLocalInferenceEngine via UserPreferences.
     */
    @Provides
    @Singleton
    fun provideLocalInferenceEngine(
        engine: SwitchableLocalInferenceEngine
    ): LocalInferenceEngine = engine

    @Provides
    @Singleton
    fun provideModelDownloader(
        @ApplicationContext context: Context
    ): ModelDownloader = ModelDownloader.getInstance(context)

    // ModelMigrationManager: resolved automatically by Hilt via @Singleton @Inject constructor

    // LocalBrainManager is now @Inject constructor() and will be provided automatically
    // fun provideLocalBrainManager(...) is removed

    // FunctionExecutor: resolved automatically by Hilt via @Singleton @Inject constructor

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    // ProviderRepository is now resolved automatically by Hilt via @Inject constructor.
    // Its dependencies (ProviderCrudRepository, ProviderModelRepository, etc.) are also
    // @Singleton @Inject classes that Hilt resolves automatically.

    @Provides
    @Singleton
    fun provideSecurityManager(
        @ApplicationContext context: Context
    ): SecurityManager = SecurityManager(context)

    @Provides
    @Singleton
    fun provideAccessControlManager(
        securityManager: SecurityManager
    ): AccessControlManager = AccessControlManager(securityManager)

    @Provides
    @Singleton
    fun provideShadowDatabase(
        @ApplicationContext context: Context
    ): ShadowDatabase = ShadowDatabase.getDatabase(context)

    @Provides
    fun provideMessageDao(
        database: ShadowDatabase
    ): MessageDao = database.messageDao()

    @Provides
    fun provideMemoryDao(
        database: ShadowDatabase
    ): MemoryDao = database.memoryDao()

    @Provides
    fun provideLedgerDao(
        database: ShadowDatabase
    ): LedgerDao = database.ledgerDao()

    @Provides
    fun provideTaskDao(
        database: ShadowDatabase
    ): TaskDao = database.taskDao()

    @Provides
    fun provideFailureDao(
        database: ShadowDatabase
    ): FailureDao = database.failureDao()

    @Provides
    fun provideFeedbackDao(
        database: ShadowDatabase
    ): FeedbackDao = database.feedbackDao()

    // TemplateVerifier: resolved automatically by Hilt via @Singleton @Inject constructor

    @Provides
    @Singleton
    fun provideBiometricKeyManager(
        @ApplicationContext context: Context
    ): BiometricKeyManager = BiometricKeyManager(context)

    @Provides
    @Singleton
    fun provideTeeKeyManager(
        @ApplicationContext context: Context
    ): TeeKeyManager = TeeKeyManager(context)

    @Provides
    @Singleton
    fun providePipelineExecutor(): PipelineExecutor = PipelineExecutor()

    // PromptInjectionDefense: resolved automatically by Hilt via @Singleton @Inject constructor
    // ProviderSecretRepository: resolved automatically by Hilt via @Singleton @Inject constructor
    // NovitaImageGenerator: resolved automatically by Hilt via @Singleton @Inject constructor
    // PixaiImageGenerator: resolved automatically by Hilt via @Singleton @Inject constructor
    // DeviceActionExecutor: resolved automatically by Hilt via @Singleton @Inject constructor
}
