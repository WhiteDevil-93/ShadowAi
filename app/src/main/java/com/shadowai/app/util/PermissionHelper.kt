package com.shadowai.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Helper class for handling runtime permission requests.
 *
 * Usage:
 * 1. Create instance in Activity onCreate (before super.onCreate)
 * 2. Call checkAndRequestPermission() when needed
 * 3. Handle result in the callback
 */
class PermissionHelper(private val activity: ComponentActivity) {

    /**
     * Permission groups for common use cases
     */
    object Permissions {
        val AUDIO = arrayOf(Manifest.permission.RECORD_AUDIO)

        val MEDIA_READ = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val NOTIFICATIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    }

    private var permissionCallback: ((Map<String, Boolean>) -> Unit)? = null
    private var singlePermissionCallback: ((Boolean) -> Unit)? = null

    private val multiplePermissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            permissionCallback?.invoke(results)
            permissionCallback = null
        }

    private val singlePermissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            singlePermissionCallback?.invoke(granted)
            singlePermissionCallback = null
        }

    /**
     * Check if a single permission is granted.
     */
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if all permissions in the array are granted.
     */
    fun arePermissionsGranted(permissions: Array<String>): Boolean {
        return permissions.all { isPermissionGranted(it) }
    }

    /**
     * Request a single permission with callback.
     */
    fun requestPermission(permission: String, onResult: (Boolean) -> Unit) {
        if (isPermissionGranted(permission)) {
            onResult(true)
            return
        }
        singlePermissionCallback = onResult
        singlePermissionLauncher.launch(permission)
    }

    /**
     * Request multiple permissions with callback.
     */
    fun requestPermissions(permissions: Array<String>, onResult: (Map<String, Boolean>) -> Unit) {
        val notGranted = permissions.filter { !isPermissionGranted(it) }
        if (notGranted.isEmpty()) {
            onResult(permissions.associateWith { true })
            return
        }
        permissionCallback = onResult
        multiplePermissionLauncher.launch(notGranted.toTypedArray())
    }

    /**
     * Check if user should see rationale for permission.
     */
    fun shouldShowRationale(permission: String): Boolean {
        return activity.shouldShowRequestPermissionRationale(permission)
    }

    /**
     * Request audio recording permission with callback.
     */
    fun requestAudioPermission(onResult: (Boolean) -> Unit) {
        requestPermission(Manifest.permission.RECORD_AUDIO, onResult)
    }

    /**
     * Request media read permissions with callback.
     */
    fun requestMediaPermissions(onResult: (allGranted: Boolean) -> Unit) {
        val permissions = Permissions.MEDIA_READ
        if (permissions.isEmpty()) {
            onResult(true)
            return
        }
        requestPermissions(permissions) { results ->
            onResult(results.values.all { it })
        }
    }

    /**
     * Request notification permission (Android 13+) with callback.
     */
    fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        val permissions = Permissions.NOTIFICATIONS
        if (permissions.isEmpty()) {
            onResult(true)
            return
        }
        requestPermission(permissions.first(), onResult)
    }

    companion object {
        /**
         * Check if a permission is granted (static helper).
         */
        fun isGranted(context: Context, permission: String): Boolean {
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Check if audio permission is granted.
         */
        fun hasAudioPermission(context: Context): Boolean {
            return isGranted(context, Manifest.permission.RECORD_AUDIO)
        }

        /**
         * Check if media read permissions are granted.
         */
        fun hasMediaPermissions(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isGranted(context, Manifest.permission.READ_MEDIA_IMAGES) &&
                isGranted(context, Manifest.permission.READ_MEDIA_VIDEO) &&
                isGranted(context, Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}
