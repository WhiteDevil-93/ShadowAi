package com.shadowai.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.shadowai.app.ui.theme.*

/**
 * Floating Chat Tabs - Implements UI Orchestrator governance
 * 
 * GOVERNANCE COMPLIANCE:
 * - Floats above chat content (NOT in TopAppBar)
 * - Auto-hides on scroll down
 * - Reappears on scroll up
 * - Uses FilterChip styling (Material 3)
 * - These are MODES, not destinations
 * 
 * FORBIDDEN:
 * - ❌ In TopAppBar
 * - ❌ In BottomNavigation
 * - ❌ As separate navigation destinations
 * 
 * @param selectedMode Currently selected chat mode
 * @param onModeSelected Callback when mode is selected
 * @param modifier Optional modifier
 */
@Composable
fun FloatingChatTabs(
    selectedMode: ChatMode,
    onModeSelected: (ChatMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.wrapContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = bg_3.copy(alpha = 0.85f), // Glassy dark background
        border = androidx.compose.foundation.BorderStroke(1.dp, stroke_subtle),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatMode.entries.forEach { mode ->
                val isSelected = selectedMode == mode
                
                FilterChip(
                    selected = isSelected,
                    onClick = { onModeSelected(mode) },
                    label = {
                        Text(
                            text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = 1.sp
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = text_secondary,
                        selectedContainerColor = emerald_core,
                        selectedLabelColor = Color.Black,
                        selectedLeadingIconColor = Color.Black
                    ),
                    border = null // Clean look
                )
            }
        }
    }
}
