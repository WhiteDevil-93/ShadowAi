package com.shadowai.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Screen for customizing the application's appearance.
 * Fulfills the "Light theme option" and "Font size adjustment" requirements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeOption by viewModel.themeOption.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()

    val onThemeChange: (ThemeOption) -> Unit = { theme ->
        viewModel.setTheme(theme)
    }
    val onFontSizeChange: (Float) -> Unit = { scale ->
        viewModel.setFontScale(scale)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            ThemeSetting(
                currentTheme = try { ThemeOption.valueOf(themeOption) } catch (e: Exception) { ThemeOption.SYSTEM },
                onThemeChange = onThemeChange
            )
            HorizontalDivider()
            FontSizeSetting(
                currentScale = fontScale,
                onFontSizeChange = onFontSizeChange
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ThemeSetting(
    currentTheme: ThemeOption,
    onThemeChange: (ThemeOption) -> Unit
) {

    SettingGroup(title = "Theme") {
        ThemeOption.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onThemeChange(option)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentTheme == option,
                    onClick = {
                        onThemeChange(option)
                    }
                )
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun FontSizeSetting(
    currentScale: Float,
    onFontSizeChange: (Float) -> Unit
) {

    SettingGroup(title = "Font Size") {
        Text(
            text = "Adjust the text size across the application.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Slider(
            value = currentScale,
            onValueChange = {
                onFontSizeChange(it)
            },
            valueRange = 0.8f..1.2f,
            steps = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Small (0.8x)", style = MaterialTheme.typography.labelSmall)
            Text("Default (1.0x)", style = MaterialTheme.typography.labelSmall)
            Text("Large (1.2x)", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}


enum class ThemeOption(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}
