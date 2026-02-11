package com.shadowai.core.security

/**
 * Exception thrown when a prompt injection attack is detected.
 */
class PromptInjectionException(message: String) : SecurityException(message)
