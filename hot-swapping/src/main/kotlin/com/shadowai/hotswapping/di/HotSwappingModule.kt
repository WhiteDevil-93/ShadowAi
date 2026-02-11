package com.shadowai.hotswapping.di

import android.content.Context
import com.google.gson.Gson
import com.shadowai.hotswapping.*
import com.shadowai.provideradapters.ProviderAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.yaml.snakeyaml.Yaml
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HotSwappingModule {

    @Provides
    @Singleton
    fun provideYaml(): Yaml = Yaml()

    @Provides
    @Singleton
    fun provideProviderConfigLoader(gson: Gson, yaml: Yaml): ProviderConfigLoader =
        ProviderConfigLoader(gson, yaml)

    @Provides
    @Singleton
    fun provideProviderConfigValidator(): ProviderConfigValidator = ProviderConfigValidator()

    @Provides
    @Singleton
    fun provideProviderConfigMigrator(): ProviderConfigMigrator = ProviderConfigMigrator()

    @Provides
    @Singleton
    fun provideProviderRegistry(adapterFactory: ProviderAdapterFactory): ProviderRegistry =
        ProviderRegistry(adapterFactory)

    @Provides
    @Singleton
    fun provideProviderBackupManager(
        @ApplicationContext context: Context,
        loader: ProviderConfigLoader
    ): ProviderBackupManager = ProviderBackupManager(context.filesDir, loader)

    @Provides
    @Singleton
    fun provideProviderHotSwapManager(
        @ApplicationContext context: Context,
        loader: ProviderConfigLoader,
        validator: ProviderConfigValidator,
        migrator: ProviderConfigMigrator,
        registry: ProviderRegistry,
        backupManager: ProviderBackupManager
    ): ProviderHotSwapManager = ProviderHotSwapManager(
        context,
        loader,
        validator,
        migrator,
        registry,
        backupManager
    )
}
