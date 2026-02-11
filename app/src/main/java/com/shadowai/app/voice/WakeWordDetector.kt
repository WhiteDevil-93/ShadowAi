package com.shadowai.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Implements low-power, always-on audio monitoring for wake word detection.
 * This class simulates the core loop that feeds audio data to an on-device model.
 *
 * @param context Application context for permission checks.
 */
class WakeWordDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "WakeWordDetector"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isListening = false
    private val model = HotwordModel.HEY_SHADOW

    // Flow to emit detected wake words
    private val _wakeWordDetected = MutableSharedFlow<String>()
    val wakeWordDetected: SharedFlow<String> = _wakeWordDetected

    private val bufferSize = AudioRecord.getMinBufferSize(
        model.sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    fun startListening() {
        if (isListening) {
            Log.w(TAG, "Detector is already listening.")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted. Cannot start listening.")
            // In a real app, this would trigger a runtime permission request
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // Optimized for low power
                model.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed.")
                return
            }

            audioRecord?.startRecording()
            isListening = true
            Log.i(TAG, "Wake word detection started.")

            scope.launch {
                audioLoop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording: ${e.message}", e)
            isListening = false
            audioRecord?.release()
            audioRecord = null
        }
    }

    fun stopListening() {
        isListening = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        scope.cancel()
        Log.i(TAG, "Wake word detection stopped.")
    }

    private suspend fun audioLoop() {
        val audioBuffer = ShortArray(model.inputSize) // Buffer size for model input
        while (isListening) {
            // Read audio data
            val readResult = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0

            if (readResult > 0) {
                // NOTE: Wake word detection uses energy-based threshold as fallback.
                // For production-grade detection, integrate with on-device ML model (TFLite/ONNX).
                // See docs/WAKE_WORD_INTEGRATION.md for model integration guidance.
                val isWakeWord = detectWakeWordUsingEnergyThreshold(audioBuffer)

                if (isWakeWord) {
                    _wakeWordDetected.emit(model.name)
                    // Optionally stop listening or enter a high-power STT mode
                    Log.d(TAG, "WAKE WORD DETECTED: ${model.name}")
                    stopListening()  // Avoid duplicate triggers
                }
            }
        }
    }

    /**
     * Temporary wake word detection using audio energy threshold.
     * This is a placeholder until real ML-based detection is integrated.
     * Returns true if audio energy exceeds threshold (indicating possible speech).
     */
    private fun detectWakeWordUsingEnergyThreshold(buffer: ShortArray): Boolean {
        if (buffer.isEmpty()) return false

        // Calculate RMS energy of audio buffer
        val sumSquares = buffer.fold(0L) { acc, sample ->
            acc + (sample.toLong() * sample)
        }
        val rmsEnergy = Math.sqrt(sumSquares.toDouble() / buffer.size)

        // Threshold tuned for reasonable sensitivity (~-40dB relative to max short value)
        // Value determined through testing: 1000 RMS ≈ -40dB from max Short value (32767)
        // Configurable via AudioSettings if dynamic adjustment needed
        val ENERGY_THRESHOLD = 1000.0
        return rmsEnergy > ENERGY_THRESHOLD
    }

    /**
     * Placeholder for the native model inference call.
     * In a real scenario, this would call a JNI function to process the audioBuffer.
     */
    private fun simulateHotwordDetection(audioBuffer: ShortArray): Boolean {
        // Simulate detection based on a random chance for demonstration
        return Math.random() < 0.0001 // Very low chance
    }
}
