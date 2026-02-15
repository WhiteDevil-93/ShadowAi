/*
 * Copyright 2025 ShadowAi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:OptIn(ExperimentalSerializationApi::class)

package com.shadowai.app.ui.navigation

import com.shadowai.core.ProviderId
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Navigation routes for ShadowAi using Navigation 3
 * 
 * These routes are type-safe and use Kotlin Serialization for
 * automatic serialization/deserialization when navigating.
 * 
 * MIGRATION: Navigation 3 with @Serializable routes
 * - Replaces string-based routes with type-safe data classes
 * - Enables compile-time safety for navigation arguments
 * - Supports assisted injection for ViewModels
 */
sealed interface NavigationRoute

/**
 * Serializer for URI strings (stored as String for serialization)
 */
object UriSerializer : KSerializer<android.net.Uri> {
    override val descriptor = PrimitiveSerialDescriptor("Uri", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: android.net.Uri) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): android.net.Uri = android.net.Uri.parse(decoder.decodeString())
}

/**
 * Wrapper for URI that supports serialization
 */
@Serializable
data class SerializableUri(
    @Serializable(with = UriSerializer::class)
    val uri: android.net.Uri
)

/**
 * Chat screen - Home/default destination
 * Normal mode with floating tabs and chat list
 * 
 * @param initialText Optional text pre-filled from Intent Share Sheet
 * @param imageUris Optional image URIs from Intent Share Sheet
 */
@Serializable
data class Chat(
    val initialText: String? = null,
    val imageUris: List<SerializableUri> = emptyList()
) : NavigationRoute {
    companion object {
        fun create(initialText: String? = null, imageUris: List<android.net.Uri> = emptyList()): Chat {
            return Chat(
                initialText = initialText,
                imageUris = imageUris.map { SerializableUri(it) }
            )
        }
        
        // Default instance for Navigation 3 back stack initialization
        val Default = Chat()
    }
}

/**
 * Provider configuration screen
 * Full screen with provider-specific config
 *
 * @param providerId Provider to configure (stored as String for serialization)
 */
@Serializable
data class ProviderConfig(val providerId: String) : NavigationRoute {
    companion object {
        fun createRoute(providerId: ProviderId): ProviderConfig {
            return ProviderConfig(providerId.name.lowercase())
        }
    }
    
    fun toProviderId(): ProviderId {
        return ProviderId.parseOrNull(providerId) ?: ProviderId.OPENAI
    }
}

/**
 * Provider selection screen
 * Allows choosing active provider and toggling availability
 */
@Serializable
data object ProviderSelection : NavigationRoute

/**
 * Image generation screen - FOCUS MODE
 * Full screen dedication, chat list and tabs hidden
 */
@Serializable
data object ImageGeneration : NavigationRoute

/**
 * Settings screen
 * Full screen settings and preferences
 */
@Serializable
data object Settings : NavigationRoute

/**
 * Generation settings screen
 */
@Serializable
data object GenerationSettings : NavigationRoute

/**
 * Usage Credits screen
 */
@Serializable
data object UsageCredits : NavigationRoute

/**
 * Appearance settings screen
 */
@Serializable
data object Appearance : NavigationRoute

/**
 * Diagnostics screen
 */
@Serializable
data object Diagnostics : NavigationRoute

/**
 * Provider hot swap screen
 */
@Serializable
data object HotSwap : NavigationRoute

/**
 * Security settings screen
 */
@Serializable
data object SecuritySettings : NavigationRoute

/**
 * Voice settings screen
 */
@Serializable
data object VoiceSettings : NavigationRoute

/**
 * Chat history screen with biometric protection
 */
@Serializable
data object ChatHistory : NavigationRoute

/**
 * Model picker screen with quantization display
 * @param providerId Provider for model selection
 */
@Serializable
data class ModelPicker(val providerId: String) : NavigationRoute {
    companion object {
        fun createRoute(providerId: ProviderId): ModelPicker {
            return ModelPicker(providerId.name.lowercase())
        }
    }

    fun toProviderId(): ProviderId {
        return ProviderId.parseOrNull(providerId) ?: ProviderId.LIQUID
    }
}

/**
 * Voice settings route (alias for consistency)
 */
@Serializable
data object VoiceSettingsRoute : NavigationRoute
