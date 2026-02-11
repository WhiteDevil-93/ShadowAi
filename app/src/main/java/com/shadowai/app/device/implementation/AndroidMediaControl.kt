package com.shadowai.app.device.implementation

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.shadowai.app.device.MediaControlContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMediaControl @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaControlContract {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun togglePlayback() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        audioManager.dispatchMediaKeyEvent(event)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        audioManager.dispatchMediaKeyEvent(eventUp)
    }

    override fun nextTrack() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
        audioManager.dispatchMediaKeyEvent(event)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)
        audioManager.dispatchMediaKeyEvent(eventUp)
    }

    override fun adjustVolume(level: Int) {
        // We treat the integer as an absolute volume level for deterministic control.
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, AudioManager.FLAG_SHOW_UI)
    }
}
