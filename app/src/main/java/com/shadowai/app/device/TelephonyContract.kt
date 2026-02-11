package com.shadowai.app.device

/**
 * Defines the capabilities for telephony interactions.
 * This is an inert interface for Phase 4.
 */
interface TelephonyContract {
    /**
     * Initiates a voice call to the specified number.
     * @param phoneNumber The target phone number.
     */
    fun initiateCall(phoneNumber: String)

    /**
     * Ends the current call if active.
     */
    fun endCall()
}
