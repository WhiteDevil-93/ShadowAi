package com.shadowai.app.di

import com.shadowai.app.device.AccessibilityContract
import com.shadowai.app.device.MediaControlContract
import com.shadowai.app.device.MessagingContract
import com.shadowai.app.device.SystemInteractionContract
import com.shadowai.app.device.TelephonyContract
import com.shadowai.app.device.implementation.AndroidAccessibility
import com.shadowai.app.device.implementation.AndroidMediaControl
import com.shadowai.app.device.implementation.AndroidMessaging
import com.shadowai.app.device.implementation.AndroidSystemInteraction
import com.shadowai.app.device.implementation.AndroidTelephony
import com.shadowai.app.execution.DefaultTaskExecutor
import com.shadowai.app.execution.TaskExecutor
import com.shadowai.app.routing.PriorityRoutingHub
import com.shadowai.app.routing.RoutingEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {

    @Binds
    @Singleton
    abstract fun bindTaskExecutor(impl: DefaultTaskExecutor): TaskExecutor

    @Binds
    @Singleton
    abstract fun bindRoutingEngine(impl: PriorityRoutingHub): RoutingEngine

    @Binds
    @Singleton
    abstract fun bindTelephonyContract(impl: AndroidTelephony): TelephonyContract

    @Binds
    @Singleton
    abstract fun bindMessagingContract(impl: AndroidMessaging): MessagingContract

    @Binds
    @Singleton
    abstract fun bindMediaControlContract(impl: AndroidMediaControl): MediaControlContract

    @Binds
    @Singleton
    abstract fun bindSystemInteractionContract(impl: AndroidSystemInteraction): SystemInteractionContract

    @Binds
    @Singleton
    abstract fun bindAccessibilityContract(impl: AndroidAccessibility): AccessibilityContract
}
