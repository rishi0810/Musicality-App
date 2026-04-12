package com.proj.Musicality

import com.proj.Musicality.util.shouldRestartCurrentTrack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTransportControlsTest {

    @Test
    fun atTrackStart_goesToPreviousTrack() {
        assertFalse(shouldRestartCurrentTrack(0L))
        assertFalse(shouldRestartCurrentTrack(1_000L))
    }

    @Test
    fun awayFromTrackStart_restartsCurrentTrack() {
        assertTrue(shouldRestartCurrentTrack(1_001L))
    }
}
