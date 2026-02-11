package com.shadowai.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.app.BuildConfig
import com.shadowai.app.admin.GenerationSettings
import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.auth.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GenerationSettingsViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _settings = MutableStateFlow<GenerationSettings?>(null)
    val settings: StateFlow<GenerationSettings?> = _settings.asStateFlow()
    val useIsolatedInference: StateFlow<Boolean> = userPreferences.inferenceIsolationEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BuildConfig.USE_ISOLATED_INFERENCE_ENGINE
        )

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _settings.value = adminRepository.getGenerationSettings()
        }
    }

    fun saveSettings(newSettings: GenerationSettings) {
        viewModelScope.launch {
            adminRepository.saveGenerationSettings(newSettings)
            loadSettings()
        }
    }

    fun setUseIsolatedInference(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.saveInferenceIsolationEnabled(enabled)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerationSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: GenerationSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val useIsolatedInference by viewModel.useIsolatedInference.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generation Parameters") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        modifier = Modifier.systemBarsPadding()
    ) { padding ->
        if (settings == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            GenerationSettingsContent(
                settings = settings!!,
                useIsolatedInference = useIsolatedInference,
                onInferenceIsolationChanged = viewModel::setUseIsolatedInference,
                onSettingsChanged = { viewModel.saveSettings(it) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun GenerationSettingsContent(
    settings: GenerationSettings,
    useIsolatedInference: Boolean,
    onInferenceIsolationChanged: (Boolean) -> Unit,
    onSettingsChanged: (GenerationSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Temperature
        SliderSetting(
            label = "Temperature",
            description = "Creativity vs Determinism",
            value = settings.temperature.toFloat(),
            range = 0f..2f,
            onValueChange = { onSettingsChanged(settings.copy(temperature = it.toDouble())) }
        )

        // Top P
        SliderSetting(
            label = "Top P (Nucleus Sampling)",
            description = "Consider tokens with top_p probability mass",
            value = settings.topP.toFloat(),
            range = 0f..1f,
            onValueChange = { onSettingsChanged(settings.copy(topP = it.toDouble())) }
        )

        // Top K
        SliderSetting(
            label = "Top K",
            description = "Consider top_k most likely tokens",
            value = settings.topK.toFloat(),
            range = 1f..100f,
            steps = 99,
            valueFormat = "%.0f",
            onValueChange = { onSettingsChanged(settings.copy(topK = it.toInt())) }
        )

        // Max Tokens
        OutlinedTextField(
            value = settings.maxTokens.toString(),
            onValueChange = {
                val newValue = it.toIntOrNull()
                if (newValue != null) {
                    onSettingsChanged(settings.copy(maxTokens = newValue))
                }
            },
            label = { Text("Max Tokens") },
            modifier = Modifier.fillMaxWidth()
        )
        
        // Repetition Penalty
        SliderSetting(
            label = "Repetition Penalty",
            description = "Discourage repeated text",
            value = settings.repetitionPenalty.toFloat(),
            range = 1f..2f,
            onValueChange = { onSettingsChanged(settings.copy(repetitionPenalty = it.toDouble())) }
        )

        // Content Filter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Safety Filters", style = MaterialTheme.typography.titleMedium)
                Text("Enable content safety filtering", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = settings.contentFilterEnabled,
                onCheckedChange = { onSettingsChanged(settings.copy(contentFilterEnabled = it)) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Isolated Inference Process", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Run local inference in :inference process for crash/memory isolation",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = useIsolatedInference,
                onCheckedChange = onInferenceIsolationChanged
            )
        }
    }
}

@Composable
fun SliderSetting(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueFormat: String = "%.2f",
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(String.format(Locale.getDefault(), valueFormat, value), style = MaterialTheme.typography.bodyMedium)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}
