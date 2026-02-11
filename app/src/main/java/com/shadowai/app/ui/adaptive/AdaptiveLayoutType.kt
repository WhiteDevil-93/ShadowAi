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

package com.shadowai.app.ui.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowWidthSizeClass

/**
 * Adaptive layout types for ShadowAi's responsive design system.
 *
 * Based on Material3 Adaptive Layouts and Androidify pattern.
 * Supports phones, tablets, and foldable devices with optimal UX for each.
 *
 * BREAKPOINTS (Material3 Standard):
 * - Compact: width < 600dp (phones)
 * - Medium: 600dp <= width < 840dp (small tablets, foldables)
 * - Expanded: width >= 840dp (large tablets, desktops)
 *
 * @see AdaptiveScaffold for implementation
 * @see <a href="https://developer.android.com/develop/ui/compose/layouts/adaptive">Material3 Adaptive</a>
 */
enum class AdaptiveLayoutType {
    /**
     * Compact layout for phones and small devices (< 600dp width).
     *
     * Characteristics:
     * - Single pane layout
     * - Modal navigation drawer
     * - Bottom navigation bar (optional)
     * - Full-screen detail views
     */
    COMPACT,

    /**
     * Medium layout for small tablets and foldables (600dp - 840dp width).
     *
     * Characteristics:
     * - Two-pane layout (list/detail or navigation/content)
     * - Permanent navigation rail (left side)
     * - Split view for master/detail patterns
     * - 30/70 or 50/50 split ratios
     */
    MEDIUM,

    /**
     * Expanded layout for large tablets and desktops (>= 840dp width).
     *
     * Characteristics:
     * - Three-pane layout possible
     * - Permanent navigation drawer or rail
     * - Optimized content spacing
     * - Multi-column chat view
     * - 20/60/20 or 25/50/25 split ratios
     */
    EXPANDED;

    companion object {
        /**
         * Determines the layout type from window size class.
         * Use this in Composables with currentWindowAdaptiveInfo().
         *
         * Example:
         * ```kotlin
         * val layoutType = AdaptiveLayoutType.fromWindowSizeClass(
         *     currentWindowAdaptiveInfo().windowSizeClass
         * )
         * ```
         *
         * @param windowSizeClass The Material3 window size class
         * @return The appropriate AdaptiveLayoutType
         */
        fun fromWindowSizeClass(windowSizeClass: androidx.window.core.layout.WindowSizeClass): AdaptiveLayoutType {
            return when (windowSizeClass.windowWidthSizeClass) {
                WindowWidthSizeClass.COMPACT -> COMPACT
                WindowWidthSizeClass.MEDIUM -> MEDIUM
                WindowWidthSizeClass.EXPANDED -> EXPANDED
                else -> COMPACT // Default fallback
            }
        }

        /**
         * Quick check for two-pane capable layouts (Medium or Expanded).
         * Useful for conditional logic in composables.
         *
         * @return true if layout supports side-by-side panes
         */
        fun AdaptiveLayoutType.supportsTwoPane(): Boolean = this != COMPACT

        /**
         * Quick check for three-pane capable layouts (Expanded only).
         *
         * @return true if layout supports three-pane layout
         */
        fun AdaptiveLayoutType.supportsThreePane(): Boolean = this == EXPANDED
    }
}
