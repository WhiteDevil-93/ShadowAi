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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer

/**
 * Union type to hold any NavigationRoute subtype.
 * This allows serialization without polymorphic discovery issues.
 */
@Serializable
private class NavigationRouteWrapper(
    val type: String = "",
    val chat: Chat? = null,
    val providerConfig: ProviderConfig? = null,
    val providerSelection: ProviderSelection? = null,
    val imageGeneration: ImageGeneration? = null,
    val settings: Settings? = null,
    val generationSettings: GenerationSettings? = null,
    val usageCredits: UsageCredits? = null,
    val appearance: Appearance? = null,
    val diagnostics: Diagnostics? = null,
    val hotSwap: HotSwap? = null
) {
    fun toRoute(): NavigationRoute = when (type) {
        "Chat" -> chat!!
        "ProviderConfig" -> providerConfig!!
        "ProviderSelection" -> providerSelection!!
        "ImageGeneration" -> imageGeneration!!
        "Settings" -> settings!!
        "GenerationSettings" -> generationSettings!!
        "UsageCredits" -> usageCredits!!
        "Appearance" -> appearance!!
        "Diagnostics" -> diagnostics!!
        "HotSwap" -> hotSwap!!
        else -> throw SerializationException("Unknown type: $type")
    }
    
    companion object {
        fun fromRoute(route: NavigationRoute): NavigationRouteWrapper = when (route) {
            is Chat -> NavigationRouteWrapper(type = "Chat", chat = route)
            is ProviderConfig -> NavigationRouteWrapper(type = "ProviderConfig", providerConfig = route)
            is ProviderSelection -> NavigationRouteWrapper(type = "ProviderSelection", providerSelection = route)
            is ImageGeneration -> NavigationRouteWrapper(type = "ImageGeneration", imageGeneration = route)
            is Settings -> NavigationRouteWrapper(type = "Settings", settings = route)
            is GenerationSettings -> NavigationRouteWrapper(type = "GenerationSettings", generationSettings = route)
            is UsageCredits -> NavigationRouteWrapper(type = "UsageCredits", usageCredits = route)
            is Appearance -> NavigationRouteWrapper(type = "Appearance", appearance = route)
            is Diagnostics -> NavigationRouteWrapper(type = "Diagnostics", diagnostics = route)
            is HotSwap -> NavigationRouteWrapper(type = "HotSwap", hotSwap = route)
        }
    }
}

private val listWrapperSerializer = ListSerializer(serializer<NavigationRouteWrapper>())

/**
 * Creates a remembered mutable state list of NavigationRoutes that persists across config changes.
 */
@Composable
fun rememberNavigationStateListOf(vararg elements: NavigationRoute): SnapshotStateList<NavigationRoute> {
    return rememberSaveable(
        saver = navigationRouteSnapshotStateListSaver()
    ) {
        elements.toList().toMutableStateList()
    }
}

/**
 * Backward compatibility alias.
 */
@Suppress("UNCHECKED_CAST")
@Composable
inline fun <reified T : NavigationRoute> rememberMutableStateListOf(vararg elements: T): SnapshotStateList<T> {
    return rememberNavigationStateListOf(*elements) as SnapshotStateList<T>
}

private fun navigationRouteSnapshotStateListSaver(): Saver<SnapshotStateList<NavigationRoute>, SavedState> {
    return Saver(
        save = { stateList ->
            val wrappers = stateList.map { NavigationRouteWrapper.fromRoute(it) }
            encodeToSavedState(listWrapperSerializer, wrappers)
        },
        restore = { savedState ->
            val wrappers = decodeFromSavedState(listWrapperSerializer, savedState)
            wrappers.map { it.toRoute() }.toMutableStateList()
        }
    )
}
