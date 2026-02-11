package com.shadowai.app.security

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal access control bootstrap used at app startup.
 */
@Singleton
class AccessControlManager @Inject constructor(
    private val securityManager: SecurityManager
) {
    fun initialize() {
        securityManager.generateHardwareBackedKey("shadowai_access_control_master")
    }
}

