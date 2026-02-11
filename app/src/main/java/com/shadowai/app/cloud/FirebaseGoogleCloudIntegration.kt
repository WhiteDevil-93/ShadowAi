package com.shadowai.app.cloud

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.shadowai.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseGoogleCloudIntegration @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val remoteConfig: FirebaseRemoteConfig
) {
    fun warmUp() {
        val firebaseReady = FirebaseApp.getApps(firebaseAuth.app.applicationContext).isNotEmpty()
        if (!firebaseReady) {
            Log.e("FirebaseGcpIntegration", "Firebase is not initialized. Check app/google-services.json")
            return
        }

        Log.i(
            "FirebaseGcpIntegration",
            "Firebase ready. projectId=${BuildConfig.GCP_PROJECT_ID.ifBlank { "unset" }}, functionsRegion=${BuildConfig.GCP_FUNCTIONS_REGION}, functionsClient=${functions.javaClass.simpleName}"
        )

        // Trigger lazy initialization and background fetch to ensure integrations are live.
        firestore.firestoreSettings
        remoteConfig.fetchAndActivate()
    }
}
