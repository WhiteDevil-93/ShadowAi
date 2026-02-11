package com.shadowai.app.di

import com.shadowai.app.admin.AdminContract
import com.shadowai.app.admin.implementation.AdminRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AdminModule {

    @Binds
    @Singleton
    abstract fun bindAdminContract(
        adminRepository: AdminRepository
    ): AdminContract
}
