package com.shadowai.app.ui.audit

/**
 * Registry of user-visible actions used for audit and reachability checks.
 */
data class UserFacingAction(
    val id: String,
    val screen: String,
    val entryPoint: String,
    val expectedBehavior: String
)

object UserFacingActionRegistry {
    val actions: List<UserFacingAction> = listOf(
        UserFacingAction(
            id = "drawer_open_settings",
            screen = "ChatScreen",
            entryPoint = "NavigationDrawerContent > Settings",
            expectedBehavior = "Navigates to Settings screen"
        ),
        UserFacingAction(
            id = "drawer_open_profile",
            screen = "ChatScreen",
            entryPoint = "NavigationDrawerContent > User footer",
            expectedBehavior = "Routes to account/settings entry point"
        ),
        UserFacingAction(
            id = "provider_get_key",
            screen = "ProviderSelectionScreen",
            entryPoint = "ProviderSettingsCard > Get {provider} Key",
            expectedBehavior = "Opens provider key portal URL"
        ),
        UserFacingAction(
            id = "chat_mode_write",
            screen = "ChatScreen",
            entryPoint = "FloatingChatTabs > WRITE",
            expectedBehavior = "Switches write mode and applies write prompt formatting"
        ),
        UserFacingAction(
            id = "chat_mode_call",
            screen = "ChatScreen",
            entryPoint = "FloatingChatTabs > CALL",
            expectedBehavior = "Switches call mode and applies call prompt formatting"
        ),
        UserFacingAction(
            id = "chat_mode_image",
            screen = "ChatScreen",
            entryPoint = "FloatingChatTabs > IMAGE",
            expectedBehavior = "Navigates to ImageGenerationScreen"
        )
    )
}
