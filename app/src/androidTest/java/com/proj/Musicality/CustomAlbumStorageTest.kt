package com.proj.Musicality

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.proj.Musicality.data.local.CustomAlbum
import com.proj.Musicality.data.local.SQLiteDatabaseHelper
import com.proj.Musicality.data.local.SongDbRecord
import com.proj.Musicality.data.model.MediaItem
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomAlbumStorageTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private lateinit var helper: SQLiteDatabaseHelper

    private val track = MediaItem("track", "Song", "Artist", "artist", "Source album", "source-album",
        "https://example.com/art.jpg", "3:33", "MUSIC_VIDEO_TYPE_OMV")

    @Before
    fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(base.cacheDir, "album-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File = File(directory, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, errorHandler)
        }
        helper = SQLiteDatabaseHelper(context)
    }

    @After
    fun tearDown() {
        helper.close()
        directory.deleteRecursively()
    }

    @Test
    fun migrationFromVersion4PreservesExistingSongs() {
        helper.upsertSong(SongDbRecord("liked", "Existing", "Artist", "artist", null, null,
            null, null, null, "/audio/existing.m4a", true, true, 1L))
        helper.writableDatabase.apply {
            execSQL("DROP TABLE custom_album_items")
            execSQL("DROP TABLE custom_albums")
            version = 4
        }
        helper.close()
        helper = SQLiteDatabaseHelper(context)
        assertEquals(5, helper.readableDatabase.version)
        assertEquals("liked", helper.getLikedSongs().single().videoId)
        assertTrue(helper.getSong("liked")!!.isDownloaded)
        assertEquals("/audio/existing.m4a", helper.getSong("liked")!!.filePath)
        assertTrue(helper.getCustomAlbums().isEmpty())
        helper.insertCustomAlbum(CustomAlbum("workout", "Workout", null, 2L, 2L))
        assertTrue(helper.addCustomAlbumItem("workout", track, 3L))
        assertEquals(track, helper.getCustomAlbumItems("workout").single())
    }

    @Test
    fun membershipIsUniqueOrderedPersistentAndIndependent() {
        helper.insertCustomAlbum(CustomAlbum("workout", "Workout", null, 1L, 1L))
        helper.insertCustomAlbum(CustomAlbum("travel", "Travel", null, 2L, 2L))
        assertTrue(helper.addCustomAlbumItem("workout", track, 3L))
        assertFalse(helper.addCustomAlbumItem("workout", track, 4L))
        val second = track.copy(videoId = "second", musicVideoType = "MUSIC_VIDEO_TYPE_ATV")
        assertTrue(helper.addCustomAlbumItem("workout", second, 5L))
        assertTrue(helper.addCustomAlbumItem("travel", track, 6L))
        assertNull(helper.getSong(track.videoId))
        assertNull(helper.getVideo(track.videoId))
        helper.close()
        helper = SQLiteDatabaseHelper(context)
        assertEquals(listOf(track, second), helper.getCustomAlbumItems("workout"))
        assertEquals(setOf("workout", "travel"), helper.getCustomAlbumIdsForTrack(track.videoId))
        assertEquals(2, helper.getCustomAlbums().first { it.id == "workout" }.itemCount)
        helper.removeCustomAlbumItem("workout", track.videoId, 7L)
        assertEquals(listOf(second), helper.getCustomAlbumItems("workout"))
        assertEquals(listOf(track), helper.getCustomAlbumItems("travel"))
        helper.updateCustomAlbum("travel", "Road trip", "/cover.jpg", 8L)
        assertEquals("Road trip", helper.getCustomAlbums().first { it.id == "travel" }.name)
        helper.deleteCustomAlbum("travel")
        assertTrue(helper.getCustomAlbumItems("travel").isEmpty())
        assertTrue(helper.getCustomAlbumIdsForTrack(track.videoId).isEmpty())
        assertEquals(listOf(second), helper.getCustomAlbumItems("workout"))
    }

    @Test
    fun addingToDeletedAlbumFailsWithoutOrphanItems() {
        helper.insertCustomAlbum(CustomAlbum("deleted", "Deleted", null, 1L, 1L))
        helper.deleteCustomAlbum("deleted")
        try {
            helper.addCustomAlbumItem("deleted", track, 2L)
            fail("Adding to a missing album must fail")
        } catch (_: IllegalStateException) {
            assertTrue(helper.getCustomAlbumItems("deleted").isEmpty())
        }
    }
}
