package com.shadowai.app

import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.view.Display
import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.core.ProviderId
import com.shadowai.app.providers.ProviderRepository
import com.shadowai.app.security.AccessControlManager
import com.shadowai.hotswapping.ProviderHotSwapManager
import com.shadowai.pipelineplanner.PipelineRegistry
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shadow AI Application class.
 */
@HiltAndroidApp
class ShadowApplication : Application() {
    @Inject lateinit var adminRepository: AdminRepository
    @Inject lateinit var providerRepository: ProviderRepository
    @Inject lateinit var accessControlManager: AccessControlManager
    @Inject lateinit var pipelineRegistry: PipelineRegistry
    @Inject lateinit var providerHotSwapManager: ProviderHotSwapManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        initStrictMode()

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
                // 1. Load dynamic provider configurations
                providerHotSwapManager.refresh()

                // 2. Initialize transformation graph
                pipelineRegistry.initialize()

                // 3. Ensure default providers are set up
                val providers = providerRepository.listProviders()
                if (providers.none { it.id == ProviderId.LIQUID }) {
                    providerRepository.saveProviders(emptyList())
                }

                if (adminRepository.getActiveProviderSync() == null) {
                    adminRepository.setActiveProvider(ProviderId.LIQUID)
                }
            } catch (e: Exception) {
                android.util.Log.e("ShadowApplication", "Initialization failed", e)
            }
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
}
