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
package com.shadowai.app.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.shadowai.app.auth.User
import com.shadowai.app.ui.ChatViewModel
import com.shadowai.app.ui.chat.ChatScreen
import com.shadowai.app.ui.providers.ProviderConfigScreen
import com.shadowai.app.ui.providers.ProviderSelectionScreen
import com.shadowai.app.ui.workflows.ImageGenerationScreen
import com.shadowai.app.ui.settings.DiagnosticsScreen
import com.shadowai.app.ui.settings.HotSwapScreen
import com.shadowai.app.ui.settings.SettingsScreen
import com.shadowai.app.ui.settings.GenerationSettingsScreen
import com.shadowai.app.ui.settings.UsageCreditsScreen
import com.shadowai.app.ui.settings.AppearanceSettingsScreen

/**
 * Main Navigation Graph using Navigation 3
 *
 * MIGRATION: This file has been migrated from Navigation 2 to Navigation 3.
 *
 * KEY CHANGES:
 * - Uses NavDisplay instead of NavHost
 * - Uses entryProvider instead of composable blocks
 * - Routes are type-safe @Serializable data classes/objects
 * - Back stack is a SnapshotStateList<NavigationRoute>
 * - Deep links are handled via URI mapping (if needed, can be added)
 * 
 * Navigation 3 benefits:
 * - Type-safe navigation with compile-time checking
 * - No more string-based route matching
 * - Better state preservation with rememberSaveableStateHolder
 * - Assisted injection support for ViewModels (see ProviderConfigScreen example)
 * - Simplified API without NavType declarations
 *
 * GOVERNANCE COMPLIANCE:
 * - Proper back stack management via SnapshotStateList
 * - State preservation with rememberSaveableStateHolderNavEntryDecorator
 * - ViewModel state preservation with rememberViewModelStoreNavEntryDecorator
 * - Accessibility (screen reader announcements)
 * - Clear navigation hierarchy
 *
 * NAVIGATION STRUCTURE:
 * - Chat (home) - Normal mode with tabs and list
 * - Provider Config - Full screen configuration
 * - Provider Selection - Provider picker
 * - Image Generation - Focus mode workflow
 * - Settings - Full screen settings
 * - Generation Settings - AI generation configuration
 * - Usage Credits - Credit management
 * - Appearance - Theme settings
 * - Diagnostics - Debug and diagnostics
 * - HotSwap - Provider hot swapping
 *
 * @param backStack The navigation back stack as a SnapshotStateList
 * @param chatViewModel Shared chat view model
 * @param currentUser Current authenticated user
 * @param onSignOut Callback for user sign out
 * @param modifier Modifier for the NavDisplay
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ShadowAINavGraph(
    backStack: androidx.compose.runtime.snapshots.SnapshotStateList<NavigationRoute>,
    chatViewModel: ChatViewModel,
    currentUser: User? = null,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = {
            ContentTransform(
                fadeIn(tween(300)),
                fadeOut(tween(300)),
            )
        },
        popTransitionSpec = {
            ContentTransform(
                fadeIn(tween(300)),
                scaleOut(
                    targetScale = 0.7f,
                ),
            )
        },
        entryProvider = entryProvider {
            // Chat Screen - Home/Default
            entry<Chat> {
                ChatScreen(
                    viewModel = chatViewModel,
                    user = currentUser,
                    onNavigateToImageGeneration = {
                        // GOVERNANCE: Navigate to focus mode
                        backStack.removeAll { it is ImageGeneration }
                        backStack.add(ImageGeneration)
                    },
                    onNavigateToProviderSelection = {
                        backStack.removeAll { it is ProviderSelection }
                        backStack.add(ProviderSelection)
                    },
                    onNavigateToProviderConfig = { providerId ->
                        val route = ProviderConfig.createRoute(providerId)
                        backStack.removeAll { it is ProviderConfig }
                        backStack.add(route)
                    },
                    onNavigateToSettings = {
                        backStack.removeAll { it is Settings }
                        backStack.add(Settings)
                    },
                    onSignOut = onSignOut
                )
            }

            // Provider Configuration Screen
            entry<ProviderConfig> { configKey ->
                val providerId = configKey.toProviderId()

                // MIGRATION EXAMPLE: For assisted injection, use this pattern:
                // val configViewModel = hiltViewModel<ProviderConfigViewModel, ProviderConfigViewModel.Factory>(
                //     creationCallback = { factory ->
                //         factory.create(providerId = providerId)
                //     }
                // )

                ProviderConfigScreen(
                    providerId = providerId,
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    },
                    onSaveConfig = {
                        backStack.removeLastOrNull()
                    }
                )
            }

            // Provider Selection Screen
            entry<ProviderSelection> {
                ProviderSelectionScreen(
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    },
                    onNavigateToConfig = { providerId ->
                        val route = ProviderConfig.createRoute(providerId)
                        backStack.removeAll { it is ProviderConfig }
                        backStack.add(route)
                    }
                )
            }

            // Image Generation Screen - FOCUS MODE
            entry<ImageGeneration> {
                // GOVERNANCE: Focus mode - chat list and tabs hidden
                ImageGenerationScreen(
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    },
                    onGenerateImage = { params ->
                        chatViewModel.generateImage(params)
                        backStack.removeLastOrNull()
                    }
                )
            }

            // Settings Screen
            entry<Settings> {
                SettingsScreen(
                    onNavigateToCredits = {
                        backStack.removeAll { it is UsageCredits }
                        backStack.add(UsageCredits)
                    },
                    onNavigateToGeneration = {
                        backStack.removeAll { it is GenerationSettings }
                        backStack.add(GenerationSettings)
                    },
                    onNavigateToAppearance = {
                        backStack.removeAll { it is Appearance }
                        backStack.add(Appearance)
                    },
                    onNavigateToDiagnostics = {
                        backStack.removeAll { it is Diagnostics }
                        backStack.add(Diagnostics)
                    },
                    onNavigateToHotSwap = {
                        backStack.removeAll { it is HotSwap }
                        backStack.add(HotSwap)
                    }
                )
            }

            // Generation Settings Screen
            entry<GenerationSettings> {
                GenerationSettingsScreen(
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }

            // Usage Credits Screen
            entry<UsageCredits> {
                UsageCreditsScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            // Appearance Settings Screen
            entry<Appearance> {
                AppearanceSettingsScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            // Diagnostics Screen
            entry<Diagnostics> {
                DiagnosticsScreen(
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }

            // Provider Hot Swap Screen
            entry<HotSwap> {
                HotSwapScreen(
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }
        },
        modifier = modifier
    )
}
