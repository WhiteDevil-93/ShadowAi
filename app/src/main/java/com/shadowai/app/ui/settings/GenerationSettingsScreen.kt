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
import com.shadowai.app.settings.SettingsBounds
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
    companion object {
        private const val MIN_TEMPERATURE = 0f
        private const val MAX_TEMPERATURE = 2f
        private const val MIN_REPETITION_PENALTY = 1f
        private const val MAX_REPETITION_PENALTY = 2f
        private const val MIN_TOP_K = 1
        private const val MAX_TOP_K = 100
        private const val MIN_MAX_TOKENS = 1
        private const val MAX_MAX_TOKENS = 8192
    }

    private val _settings = MutableStateFlow<GenerationSettings?>(null)
    val settings: StateFlow<GenerationSettings?> = _settings.asStateFlow()
    val useIsolatedInference: StateFlow<Boolean> = userPreferences.inferenceIsolationEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BuildConfig.USE_ISOLATED_INFERENCE_ENGINE
        )
    val useNnapiDelegation: StateFlow<Boolean> = userPreferences.nnapiDelegationEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    val useMemoryMapping: StateFlow<Boolean> = userPreferences.memoryMappingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )
    val autoSummarizationEnabled: StateFlow<Boolean> = userPreferences.autoSummarizationEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
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
            adminRepository.saveGenerationSettings(sanitizeSettings(newSettings))
            loadSettings()
        }
    }

    fun setUseIsolatedInference(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.saveInferenceIsolationEnabled(enabled)
        }
    }

    fun setUseNnapiDelegation(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.saveNnapiDelegationEnabled(enabled)
        }
    }

    fun setUseMemoryMapping(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.saveMemoryMappingEnabled(enabled)
        }
    }

    fun setAutoSummarization(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.saveAutoSummarizationEnabled(enabled)
        }
    }

    // M-13: Settings bounds validation
    fun updateTopP(value: Float) {
        viewModelScope.launch {
            val validated = SettingsBounds.validateNormalized(
                value = value,
                default = 0.9f,
                settingName = "Top P"
            )
            val current = settings.value ?: return@launch
            saveSettings(current.copy(topP = validated.toDouble()))
        }
    }

    fun updateTemperature(value: Float) {
        viewModelScope.launch {
            val current = settings.value ?: return@launch
            val validated = value.coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE)
            saveSettings(current.copy(temperature = validated.toDouble()))
        }
    }

    fun updateRepetitionPenalty(value: Float) {
        viewModelScope.launch {
            val current = settings.value ?: return@launch
            val validated = value.coerceIn(MIN_REPETITION_PENALTY, MAX_REPETITION_PENALTY)
            saveSettings(current.copy(repetitionPenalty = validated.toDouble()))
        }
    }

    private fun sanitizeSettings(settings: GenerationSettings): GenerationSettings {
        val validatedTopP = SettingsBounds.validateNormalized(
            value = settings.topP.toFloat(),
            default = 0.9f,
            settingName = "Top P"
        ).toDouble()

        return settings.copy(
            temperature = settings.temperature.toFloat().coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE).toDouble(),
            topP = validatedTopP,
            topK = settings.topK.coerceIn(MIN_TOP_K, MAX_TOP_K),
            maxTokens = settings.maxTokens.coerceIn(MIN_MAX_TOKENS, MAX_MAX_TOKENS),
            repetitionPenalty = settings.repetitionPenalty.toFloat().coerceIn(
                MIN_REPETITION_PENALTY,
                MAX_REPETITION_PENALTY
            ).toDouble()
        )
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
    val useNnapiDelegation by viewModel.useNnapiDelegation.collectAsState()
    val useMemoryMapping by viewModel.useMemoryMapping.collectAsState()
    val autoSummarizationEnabled by viewModel.autoSummarizationEnabled.collectAsState()

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
                useNnapiDelegation = useNnapiDelegation,
                useMemoryMapping = useMemoryMapping,
                autoSummarizationEnabled = autoSummarizationEnabled,
                onInferenceIsolationChanged = viewModel::setUseIsolatedInference,
                onNnapiDelegationChanged = viewModel::setUseNnapiDelegation,
                onMemoryMappingChanged = viewModel::setUseMemoryMapping,
                onAutoSummarizationChanged = viewModel::setAutoSummarization,
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
    useNnapiDelegation: Boolean,
    useMemoryMapping: Boolean,
    autoSummarizationEnabled: Boolean,
    onInferenceIsolationChanged: (Boolean) -> Unit,
    onNnapiDelegationChanged: (Boolean) -> Unit,
    onMemoryMappingChanged: (Boolean) -> Unit,
    onAutoSummarizationChanged: (Boolean) -> Unit,
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
        // === Performance Optimization Settings ===
        Text(
            "Performance Optimization",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        // NNAPI Delegation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("NNAPI Acceleration", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Use NPU for faster inference (Pixel, Samsung, etc.)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = useNnapiDelegation,
                onCheckedChange = onNnapiDelegationChanged
            )
        }

        // Memory Mapping
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Memory-Mapped Models", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Allow running models larger than RAM (recommended)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = useMemoryMapping,
                onCheckedChange = onMemoryMappingChanged
            )
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        // === Generation Parameters ===
        Text(
            "Generation Parameters",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        // Temperature
        SliderSetting(
            label = "Temperature",
            description = "Creativity vs Determinism",
            value = settings.temperature.toFloat(),
            range = 0f..2f,
            onValueChange = {
                val validated = it.coerceIn(0f, 2f)
                onSettingsChanged(settings.copy(temperature = validated.toDouble()))
            }
        )

        // Top P
        SliderSetting(
            label = "Top P (Nucleus Sampling)",
            description = "Consider tokens with top_p probability mass",
            value = settings.topP.toFloat(),
            range = 0f..1f,
            onValueChange = {
                val validated = com.shadowai.app.settings.SettingsBounds.validateNormalized(
                    value = it,
                    default = 0.9f,
                    settingName = "Top P"
                )
                onSettingsChanged(settings.copy(topP = validated.toDouble()))
            }
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
                    val validated = newValue.coerceIn(1, 8192)
                    onSettingsChanged(settings.copy(maxTokens = validated))
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
            onValueChange = {
                val validated = it.coerceIn(1f, 2f)
                onSettingsChanged(settings.copy(repetitionPenalty = validated.toDouble()))
            }
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

        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        // === Advanced Settings ===
        Text(
            "Advanced Settings",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        // Auto-Summarization
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-Summarization", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Automatically summarize long conversations at 70% context",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = autoSummarizationEnabled,
                onCheckedChange = onAutoSummarizationChanged
            )
        }

        // Isolated Inference
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
