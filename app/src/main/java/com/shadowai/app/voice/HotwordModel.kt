package com.shadowai.app.voice

/**
 * Data class representing a loaded on-device hotword model (e.g., a TFLite or custom binary).
 *
 * @param name The name of the wake word (e.g., "Hey Shadow").
 * @param modelPath The path to the model file on the device.
 * @param sensitivity The detection threshold (0.0 to 1.0).
 * @param sampleRate The required audio sample rate (e.g., 16000).
 * @param inputSize The expected input buffer size for the model.
 */
data class HotwordModel(
    val name: String,
    val modelPath: String,
    val sensitivity: Float,
    val sampleRate: Int,
    val inputSize: Int
) {
    companion object {
        val HEY_SHADOW = HotwordModel(
            name = "Hey Shadow",
            modelPath = "models/hey_shadow.tflite",
            sensitivity = 0.7f,
            sampleRate = 16000,
            inputSize = 16000 // 1 second of 16kHz audio
        )
    }
}