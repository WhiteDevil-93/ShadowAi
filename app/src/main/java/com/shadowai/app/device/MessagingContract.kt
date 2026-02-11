package com.shadowai.app.device

/**
 * Defines the capabilities for messaging interactions (SMS/MMS).
 * This is an inert interface for Phase 4.
 */
interface MessagingContract {
    /**
     * Sends a text message to the specified recipient.
     * @param phoneNumber The recipient's phone number.
     * @param message The content of the message.
     */
    fun sendSms(phoneNumber: String, message: String)

    /**
     * Retrieves recent messages from the system.
     */
    fun getRecentMessages(): List<String>
}
