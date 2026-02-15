package com.shadowai.app

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.StrictMode
import android.view.Display
import android.util.Log
import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.ai.DeviceCapabilities
import com.shadowai.app.accessibility.VoicePreferences
import com.shadowai.app.accessibility.VoiceRecognitionManager
import com.shadowai.app.auth.UserPreferences
import com.shadowai.core.ProviderId
import com.shadowai.app.security.AccessControlManager
import com.shadowai.app.security.AutoLockManager
import com.shadowai.provideradapters.ProviderCrudRepository
import com.shadowai.hotswapping.ProviderHotSwapManager
import com.shadowai.pipelineplanner.PipelineRegistry
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shadow AI Application class.
 */
@HiltAndroidApp
class ShadowApplication : Application() {

    @Inject lateinit var adminRepository: AdminRepository
    @Inject lateinit var crudRepository: ProviderCrudRepository
    @Inject lateinit var accessControlManager: AccessControlManager
    @Inject lateinit var pipelineRegistry: PipelineRegistry
    @Inject lateinit var providerHotSwapManager: ProviderHotSwapManager
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var deviceCapabilities: DeviceCapabilities
    @Inject lateinit var autoLockManager: AutoLockManager
    @Inject lateinit var voiceRecognitionManager: VoiceRecognitionManager
    @Inject lateinit var voicePreferences: VoicePreferences

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        initStrictMode()

        configureVoiceCallbacks()

        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                voiceRecognitionManager.initializeWithPreferences(voicePreferences)
                maybeStartHotwordDetection()
            }.onFailure { error ->
                Log.w("ShadowApplication", "Voice settings initialization failed", error)
            }
        }

        if (Build.VERSION.SDK_INT >= 35) {
            val display = getSystemService(Display::class.java)
            if (display?.hasArrSupport() == true) {
                // Adaptive refresh rate support detected
            }
        }

        accessControlManager.initialize()

        // Initialize pipeline planner and providers on IO dispatcher
        applicationScope.launch(Dispatchers.IO) {
            try {
                // 1. Run user preferences migration for existing users
                migrateUserPreferences()

                // 2. Load dynamic provider configurations
                providerHotSwapManager.refresh()

                // 3. Initialize transformation graph
                pipelineRegistry.initialize()

                // 4. Ensure default providers are set up
                val providers = crudRepository.getAllProviders()
                if (providers.none { it.id == ProviderId.LIQUID }) {
                    // No LIQUID provider configured - providers initialized empty
                }

                if (adminRepository.getActiveProviderSync() == null) {
                    adminRepository.setActiveProvider(ProviderId.LIQUID)
                }
            } catch (e: Exception) {
                android.util.Log.e("ShadowApplication", "Initialization failed", e)
            }
        }
    }

    /**
     * Called when the app moves to foreground
     */
    fun onAppInForeground() {
        android.util.Log.d("ShadowApplication", "App moved to foreground")
        configureVoiceCallbacks()
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                voiceRecognitionManager.initializeWithPreferences(voicePreferences)
                maybeStartHotwordDetection()
            }.onFailure { error ->
                Log.w("ShadowApplication", "Failed to restart hotword detection", error)
            }
        }
    }

    /**
     * Called when the app moves to background
     */
    fun onAppInBackground() {
        android.util.Log.d("ShadowApplication", "App moved to background")
        // Stop hotword detection to save battery
        voiceRecognitionManager.stopHotwordDetector()
    }

    /**
     * Migrate user preferences for existing users.
     *
     * - Initialize new preference keys with smart defaults
     * - Check device NNAPI support and set appropriate default
     * - Ensure all keys exist to avoid nulls later
     */
    private suspend fun migrateUserPreferences() {
        try {
            android.util.Log.i("ShadowApplication", "Starting user preferences migration...")

            // Check NNAPI support and set smart default for existing users
            val nnapiDefault = deviceCapabilities.supportsNnapi()
            
            // Get current values (will use defaults if not set)
            val currentNnapi = userPreferences.nnapiDelegationEnabled.first()
            val currentIsolation = userPreferences.inferenceIsolationEnabled.first()
            val currentMemoryMapping = userPreferences.memoryMappingEnabled.first()
            val currentAutoSummarize = userPreferences.autoSummarizationEnabled.first()

            // If the key doesn't exist yet (first run after migration), set smart defaults
            // The flow's default values handle this, but we explicitly set for consistency
            if (nnapiDefault && !currentNnapi) {
                // Device supports NNAPI but it's not enabled - enable it
                userPreferences.saveNnapiDelegationEnabled(true)
                android.util.Log.i("ShadowApplication", "Enabled NNAPI delegation for device with NPU support")
            }

            // Ensure other keys have sensible defaults
            userPreferences.saveInferenceIsolationEnabled(currentIsolation)
            userPreferences.saveMemoryMappingEnabled(currentMemoryMapping)
            userPreferences.saveAutoSummarizationEnabled(currentAutoSummarize)

            android.util.Log.i("ShadowApplication", "User preferences migration complete")
            android.util.Log.d("ShadowApplication", "NNAPI enabled: $nnapiDefault, Isolation: $currentIsolation, MemoryMapping: $currentMemoryMapping")

        } catch (e: Exception) {
            android.util.Log.e("ShadowApplication", "User preferences migration failed", e)
        }
    }

    private fun initStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )

            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )

            android.util.Log.d("ShadowApplication", "StrictMode enabled in DEBUG build")
        }
    }

    /**
     * Clean up resources on app termination
     */
    override fun onTerminate() {
        super.onTerminate()
        autoLockManager.shutdown()
        voiceRecognitionManager.destroy()
    }

    companion object {
        const val ACTION_HOTWORD_DETECTED = "com.shadowai.app.action.HOTWORD_DETECTED"
    }

    private fun configureVoiceCallbacks() {
        voiceRecognitionManager.setCallbacks(
            onHotwordDetected = {
                android.util.Log.d("ShadowApplication", "Hotword detected!")
                val intent = Intent(ACTION_HOTWORD_DETECTED).apply {
                    setPackage(packageName)
                }
                sendBroadcast(intent)
            },
            onError = { error ->
                android.util.Log.w("ShadowApplication", "Voice recognition error: $error")
            }
        )
    }

    private fun maybeStartHotwordDetection() {
        if (!voiceRecognitionManager.hasPermissions()) {
            return
        }
        if (!voiceRecognitionManager.isHotwordEnabled.value) {
            return
        }
        voiceRecognitionManager.startHotwordDetector()
    }
}
