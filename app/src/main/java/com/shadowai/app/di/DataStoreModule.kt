package com.shadowai.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val SECURE_PREFS_NAME = "secure_prefs"
private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(name = SECURE_PREFS_NAME)

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Singleton
    @Provides
    fun providePreferencesDataStore(@ApplicationContext appContext: Context): DataStore<Preferences> {
        return appContext.secureDataStore
    }

    // Removed redundant provideContext — Hilt provides @ApplicationContext automatically
}
