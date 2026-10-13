package com.proj.Musicality

import com.proj.Musicality.data.model.hasMissingPlaybackMetadata
import com.proj.Musicality.data.model.withMissingMetadataFrom
import com.proj.Musicality.data.parser.NextParser
import org.junit.Assert.*
import org.junit.Test

class PlaybackMetadataTest {
    private fun song() = NextParser.extractUpNextQueue(
        requireNotNull(javaClass.getResource("/next-GacH6Fc57r8.json")).readText()
    )!!.items.single()

    @Test
    fun audioTrackIncludesArtistAndSquareArtwork() {
        val song = song()
        assertEquals("GacH6Fc57r8", song.videoId)
        assertEquals("delicate", song.title)
        assertEquals("almost monday", song.artistName)
        assertEquals("UCn5ErQ1909_LjCdhJhc5-8w", song.artistId)
        assertTrue(song.thumbnailUrl!!.endsWith("=w544-h544-l90-rj"))
        assertEquals("MUSIC_VIDEO_TYPE_ATV", song.musicVideoType)
        assertFalse(song.hasMissingPlaybackMetadata())
    }

    @Test
    fun recoversSavedPlaceholderMetadata() {
        val complete = song()
        val saved = complete.copy(artistName = "Unknown artist", artistId = null,
            thumbnailUrl = "", albumName = null, albumId = null, durationText = null)
        assertTrue(saved.hasMissingPlaybackMetadata())
        assertEquals(complete, saved.withMissingMetadataFrom(complete))
    }

    @Test
    fun keepsExistingDetailsAndRejectsAnotherVideo() {
        val complete = song()
        val saved = complete.copy(title = "Saved title", thumbnailUrl = "file:///local/art.jpg")
        assertEquals(saved, saved.withMissingMetadataFrom(complete))
        val incomplete = saved.copy(artistName = "", artistId = null)
        assertEquals(incomplete, incomplete.withMissingMetadataFrom(complete.copy(videoId = "other")))
        assertEquals(saved, saved.withMissingMetadataFrom(complete.copy(artistName = "", thumbnailUrl = null)))
    }
}
