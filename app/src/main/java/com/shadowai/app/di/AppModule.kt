package com.shadowai.app.di

import android.content.Context
import com.google.gson.Gson
import com.shadowai.app.ai.*
import com.shadowai.app.auth.UserPreferences
import com.shadowai.app.network.CertificatePinningInterceptor
import com.shadowai.app.db.FailureDao
import com.shadowai.app.db.FeedbackDao
import com.shadowai.app.db.LedgerDao
import com.shadowai.app.db.MemoryDao
import com.shadowai.app.db.MessageDao
import com.shadowai.app.db.ModelPathDao
import com.shadowai.app.db.ShadowDatabase
import com.shadowai.app.db.TaskDao
import com.shadowai.app.security.AccessControlManager
import com.shadowai.app.security.BiometricKeyManager
import com.shadowai.app.security.CertificatePinningConfig
import com.shadowai.app.security.CertificateErrorHandler
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

    /**
     * CIRCULAR DEPENDENCY FIX: ILlamaEngine interface binding
     */
    @Provides
    @Singleton
    fun provideILlamaEngine(llamaNative: LlamaNative): ILlamaEngine = llamaNative

    @Provides
    @Singleton
    fun provideLocalInferenceManager(
        @ApplicationContext context: Context,
        piiMaskingProcessor: PiiMaskingProcessor,
        llamaEngine: ILlamaEngine,
        userPreferences: UserPreferences
    ): LocalInferenceManager = LocalInferenceManager(context, piiMaskingProcessor, llamaEngine, userPreferences)

    /**
     * Provides runtime-switchable LocalInferenceEngine.
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

    @Provides
    @Singleton
    fun provideOkHttpClient(
        certificateErrorHandler: CertificateErrorHandler
    ): OkHttpClient {
        val pinningInterceptor = CertificatePinningInterceptor(
            onError = { exception ->
                certificateErrorHandler.handleCertificatePinningError(exception)
            }
        )

        return OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .certificatePinner(CertificatePinningConfig.createPinner())
            .addInterceptor(pinningInterceptor)
            .build()
    }

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
    fun provideCertificateErrorHandler(
        @ApplicationContext context: Context
    ): CertificateErrorHandler = CertificateErrorHandler(context)

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

    @Provides
    fun provideModelPathDao(
        database: ShadowDatabase
    ): ModelPathDao = database.modelPathDao()

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

    @Provides
    @Singleton
    fun provideConversationSummarizer(
        llamaNative: LlamaNative,
        tokenCounter: TokenCounter
    ): ConversationSummarizer = ConversationSummarizer(llamaNative, tokenCounter)
}
