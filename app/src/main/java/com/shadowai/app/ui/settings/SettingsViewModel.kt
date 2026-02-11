package com.shadowai.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.app.auth.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    val themeOption: StateFlow<String> = userPreferences.themeOption
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "SYSTEM"
        )

    val fontScale: StateFlow<Float> = userPreferences.fontScale
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 1.0f
        )

    fun setTheme(theme: ThemeOption) {
        viewModelScope.launch {
            userPreferences.saveThemeOption(theme.name)
        }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            userPreferences.saveFontScale(scale)
        }
    }
}
