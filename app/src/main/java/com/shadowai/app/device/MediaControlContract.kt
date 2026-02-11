package com.shadowai.app.device

/**
 * Defines the capabilities for media control interactions.
 * This is an inert interface for Phase 4.
 */
interface MediaControlContract {
    /**
     * Plays or pauses the active media session.
     */
    fun togglePlayback()

    /**
     * Skips to the next track.
     */
    fun nextTrack()

    /**
     * Adjusts the volume.
     * @param level The target volume level or delta.
     */
    fun adjustVolume(level: Int)
}
