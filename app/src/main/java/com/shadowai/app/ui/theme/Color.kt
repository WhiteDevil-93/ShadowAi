package com.shadowai.app.ui.theme

import androidx.compose.ui.graphics.Color

// SHADOWAI Color Tokens (Section B of UI Spec)

// Core Neutrals
val bg_0 = Color(0xFF000000)        // true OLED
val bg_1 = Color(0xFF07090C)        // base canvas
val bg_2 = Color(0xFF0B0E12)        // panel
val bg_3 = Color(0xFF10151B)        // raised

// Text
val text_primary = Color(0xFFE6E8EB)
val text_secondary = Color(0xFFA8B0BA)
val text_muted = Color(0xFF6B7480)
val text_disabled = Color(0xFF49525C)

// Borders / Dividers
val stroke_subtle = Color(0xFF1B222B)
val stroke_normal = Color(0xFF26303B)
val divider = Color(0xFF141A21)

// Emerald Signal (ACCENT)
val emerald_core = Color(0xFF00E676)   // primary "armed/active"
val emerald_dim = Color(0xFF00B85C)    // hover/pressed/secondary active
val emerald_dark = Color(0xFF03110A)   // brand deep emerald
val emerald_glow = Color(0x1A33FF99)     // 10% opacity emerald glow for subtle effects

// Danger / Destructive
val danger = Color(0xFFFF3B30)
val warning = Color(0xFFFF9F0A)

// Violet / Experimental (secondary only)
val violet = Color(0xFF7C4DFF)
val violet_dim = Color(0xFF5E35B1)

// SHADOWAI Dark Theme (overriding Material 3 defaults)
val md_theme_dark_primary = emerald_core
val md_theme_dark_onPrimary = Color(0xFF000000)
val md_theme_dark_primaryContainer = emerald_dark
val md_theme_dark_onPrimaryContainer = emerald_core
val md_theme_dark_secondary = violet_dim
val md_theme_dark_onSecondary = Color(0xFFFFFFFF)
val md_theme_dark_secondaryContainer = Color(0xFF1A1030)
val md_theme_dark_onSecondaryContainer = violet
val md_theme_dark_tertiary = emerald_dim
val md_theme_dark_onTertiary = Color(0xFF000000)
val md_theme_dark_tertiaryContainer = Color(0xFF0A1A12)
val md_theme_dark_onTertiaryContainer = emerald_core
val md_theme_dark_error = danger
val md_theme_dark_errorContainer = Color(0xFF1A0F0E)
val md_theme_dark_onError = Color(0xFFFFFFFF)
val md_theme_dark_onErrorContainer = danger
val md_theme_dark_background = bg_1
val md_theme_dark_onBackground = text_primary
val md_theme_dark_surface = bg_1
val md_theme_dark_onSurface = text_primary
val md_theme_dark_surfaceVariant = bg_2
val md_theme_dark_onSurfaceVariant = text_secondary
val md_theme_dark_outline = stroke_normal
val md_theme_dark_inverseOnSurface = text_primary
val md_theme_dark_inverseSurface = bg_3
val md_theme_dark_inversePrimary = emerald_dim
val md_theme_dark_surfaceTint = emerald_core
val md_theme_dark_outlineVariant = stroke_subtle
val md_theme_dark_scrim = Color(0xFF000000)

// Light Theme - DISABLED per UI Spec (Rule 1: No Material default colours)
val md_theme_light_primary = Color(0xFF6750A4)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFEADDFF)
val md_theme_light_onPrimaryContainer = Color(0xFF21005D)
val md_theme_light_secondary = Color(0xFF625B71)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFE8DEF8)
val md_theme_light_onSecondaryContainer = Color(0xFF1D192B)
val md_theme_light_tertiary = Color(0xFF7D5260)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFFFD8E4)
val md_theme_light_onTertiaryContainer = Color(0xFF31111D)
val md_theme_light_error = Color(0xFFB3261E)
val md_theme_light_errorContainer = Color(0xFFF9DEDC)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410E0B)
val md_theme_light_background = Color(0xFFFFFBFE)
val md_theme_light_onBackground = Color(0xFF1C1B1F)
val md_theme_light_surface = Color(0xFFFFFBFE)
val md_theme_light_onSurface = Color(0xFF1C1B1F)
val md_theme_light_surfaceVariant = Color(0xFFE7E0EC)
val md_theme_light_onSurfaceVariant = Color(0xFF49454F)
val md_theme_light_outline = Color(0xFF79747E)
val md_theme_light_inverseOnSurface = Color(0xFFF4EFF4)
val md_theme_light_inverseSurface = Color(0xFF313033)
val md_theme_light_inversePrimary = Color(0xFFD0BCFF)
val md_theme_light_surfaceTint = Color(0xFF6750A4)
val md_theme_light_outlineVariant = Color(0xFFCAC4D0)
val md_theme_light_scrim = Color(0xFF000000)
