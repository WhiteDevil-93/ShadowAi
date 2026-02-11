package com.shadowai.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.shadowai.app.R
import com.shadowai.app.auth.User
import com.shadowai.core.ProviderId
import com.shadowai.app.ui.theme.*

/**
 * Navigation Drawer Content - ShadowAI Settings Sidebar
 *
 * DESIGN: Matches DarcyAI reference with user profile, account, and settings sections
 *
 * GOVERNANCE COMPLIANCE:
 * - OLED Black background
 * - Emerald accent for active items
 * - User profile header
 * - Comprehensive settings sections
 *
 * @param user Current authenticated user data
 * @param onNavigateToSettings Callback for settings navigation
 * @param onNavigateToProviderSelection Callback for provider selection flow
 * @param onNavigateToProviderConfig Callback for provider configuration
 * @param onSignOut Callback for user sign out
 */
@Composable
fun NavigationDrawerContent(
    user: User? = null,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToProviderSelection: () -> Unit,
    onNavigateToProviderConfig: (ProviderId) -> Unit,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg_0) // True OLED black
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {
        // User Profile Header
        UserProfileHeader(user = user)

        Spacer(modifier = Modifier.height(8.dp))

        Spacer(modifier = Modifier.height(24.dp))

        // My ShadowAI Section
        SectionHeader("My ShadowAI")

        DrawerNavigationItem(
            icon = Icons.Default.Cloud,
            label = "AI Providers",
            subtitle = "Switch runtime or API provider",
            hasChevron = true,
            onClick = onNavigateToProviderSelection
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Account Section
        SectionHeader("Account")

        if (user != null) {
            DrawerInfoItem(
                icon = Icons.Default.Email,
                label = "Email",
                value = user.email
            )

            // Only show phone if available
            user.displayName?.let {
                DrawerInfoItem(
                    icon = Icons.Default.Person,
                    label = "Display Name",
                    value = it
                )
            }
        } else {
            DrawerInfoItem(
                icon = Icons.Default.Email,
                label = "Email",
                value = "Not signed in"
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Configuration section
        SectionHeader("Configuration")

        DrawerNavigationItem(
            icon = Icons.Default.Settings,
            label = "Settings",
            hasChevron = true,
            onClick = onNavigateToSettings
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // Sign Out Button
        if (user != null) {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Sign Out", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // User Footer (duplicate profile for quick access)
        DrawerNavigationItem(
            icon = Icons.Default.Person,
            label = user?.displayName ?: "User",
            subtitle = user?.email ?: "Not signed in",
            hasChevron = true,
            onClick = onNavigateToProfile
        )
    }
}

@Composable
private fun UserProfileHeader(user: User?) {
    val context = LocalContext.current

    // Generate initials from user name or use default
    val displayName = user?.displayName ?: "User"
    val initials = displayName
        .split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifEmpty { "U" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Profile Avatar
        if (user?.photoUrl != null) {
            // Load profile image from URL
            Image(
                painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(context)
                        .data(user.photoUrl)
                        .crossfade(true)
                        .build()
                ),
                contentDescription = "Profile picture",
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )
        } else {
            // Show initials in colored circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(emerald_core.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = emerald_core
                )
            }
        }

        Column {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = text_primary
            )
            Text(
                text = user?.email ?: "Not signed in",
                style = MaterialTheme.typography.bodySmall,
                color = text_muted
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Medium
        ),
        color = text_muted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun DrawerNavigationItem(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    hasChevron: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = text_secondary,
                modifier = Modifier.size(24.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = text_primary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = text_muted
                    )
                }
            }
            
            if (hasChevron) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Navigate",
                    tint = text_muted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = text_secondary,
            modifier = Modifier.size(24.dp)
        )
        
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = text_primary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = text_muted
            )
        }
    }
}
