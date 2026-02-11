package com.shadowai.app.ui.providers

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.shadowai.app.R
import androidx.lifecycle.viewModelScope
import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.functions.PixAiSettings
import com.shadowai.app.ui.settings.SliderSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PixAiConfigViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _settings = MutableStateFlow<PixAiSettings?>(null)
    val settings: StateFlow<PixAiSettings?> = _settings.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _settings.value = adminRepository.getPixAiSettings()
        }
    }

    fun saveSettings(newSettings: PixAiSettings) {
        viewModelScope.launch {
            adminRepository.savePixAiSettings(newSettings)
            loadSettings()
        }
    }
}

@Composable
fun PixAiConfig(
    modifier: Modifier = Modifier,
    viewModel: PixAiConfigViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    if (settings != null) {
        val current = settings!!
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.subtitle_pixai), style = MaterialTheme.typography.titleMedium)
            
            // Dimensions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = current.width.toString(),
                    onValueChange = { 
                        it.toIntOrNull()?.let { v -> viewModel.saveSettings(current.copy(width = v)) }
                    },
                    label = { Text(stringResource(R.string.label_width)) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = current.height.toString(),
                    onValueChange = { 
                        it.toIntOrNull()?.let { v -> viewModel.saveSettings(current.copy(height = v)) }
                    },
                    label = { Text(stringResource(R.string.label_height)) },
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Steps
            SliderSetting(
                label = stringResource(R.string.admin_hint_sd_sampling_steps),
                description = stringResource(R.string.admin_hint_sd_sampling_steps),
                value = current.samplingSteps.toFloat(),
                range = 10f..50f,
                steps = 40,
                valueFormat = "%.0f",
                onValueChange = { viewModel.saveSettings(current.copy(samplingSteps = it.toInt())) }
            )
            
            // CFG Scale
            SliderSetting(
                label = stringResource(R.string.label_cfg_scale),
                description = stringResource(R.string.admin_hint_sd_cfg_scale),
                value = current.cfgScale.toFloat(),
                range = 1f..20f,
                onValueChange = { viewModel.saveSettings(current.copy(cfgScale = it.toDouble())) }
            )
        }
    }
}
