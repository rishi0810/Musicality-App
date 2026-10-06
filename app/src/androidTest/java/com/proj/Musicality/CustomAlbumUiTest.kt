package com.proj.Musicality

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.proj.Musicality.data.local.LibraryCollectionType
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.PlaybackQueue
import com.proj.Musicality.data.model.QueueSource
import com.proj.Musicality.ui.components.AddToCustomAlbumSheet
import com.proj.Musicality.ui.components.CustomAlbumFormSheet
import com.proj.Musicality.ui.screen.LibraryCollectionScreen
import com.proj.Musicality.ui.screen.LibraryScreen
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomAlbumUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = LibraryRepository.getInstance(context)
    private val createdIds = mutableListOf<String>()
    private val track = MediaItem("custom-album-ui-song", "Test song", "Artist", "artist", null, null,
        null, "3:33", "MUSIC_VIDEO_TYPE_ATV")

    @After
    fun cleanUp() = runBlocking {
        createdIds.forEach { repository.deleteCustomAlbum(it) }
    }

    @Test
    fun formRequiresNameThenCreatesAnEmptyCollection() {
        var albumId by mutableStateOf<String?>(null)
        val name = "Workout ${UUID.randomUUID()}"
        compose.setContent {
            MaterialTheme {
                if (albumId == null) CustomAlbumFormSheet(repository, onDismiss = {}, onSaved = {
                    createdIds.add(it)
                    albumId = it
                }) else LibraryCollectionScreen(
                    LibraryCollectionType.LIKED, onTrackTap = {}, onPlayNext = {}, onAddToQueue = {},
                    onArtistTap = { _, _, _ -> }, collapsedMiniPlayerHeight = 0.dp, customAlbumId = albumId
                )
            }
        }
        compose.onNodeWithText("Create album").assertIsNotEnabled()
        compose.onNodeWithText("Your name").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput(name)
        compose.onNodeWithText("Create album").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { albumId != null }
        compose.onNodeWithText(name).assertExists()
        compose.onNodeWithText("No songs yet. Use Add in the player to add a song.").assertExists()
        compose.onNodeWithText("Play", substring = false).assertDoesNotExist()
    }

    @Test
    fun selectionCommitsOnlyOnAddAndCollectionBuildsItsOwnQueue() {
        val name = "Travel ${UUID.randomUUID()}"
        val album = runBlocking { repository.createCustomAlbum(name, null) }
        createdIds.add(album.id)
        var showSelector by mutableStateOf(true)
        var playedQueue: PlaybackQueue? = null
        compose.setContent {
            MaterialTheme {
                if (showSelector) AddToCustomAlbumSheet(track, repository, onDismiss = { showSelector = false })
                else LibraryCollectionScreen(
                    LibraryCollectionType.LIKED, onTrackTap = { playedQueue = it }, onPlayNext = {}, onAddToQueue = {},
                    onArtistTap = { _, _, _ -> }, collapsedMiniPlayerHeight = 0.dp, customAlbumId = album.id
                )
            }
        }
        compose.onNodeWithText("Add", substring = false).assertIsNotEnabled()
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.onNodeWithText("Add", substring = false).assertIsEnabled()
        assertFalse(runBlocking { repository.observeCustomAlbumMembership(track.videoId).first() }.contains(album.id))
        compose.onNodeWithText("Add", substring = false).performClick()
        compose.waitUntil(10_000) { !showSelector }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(track.title).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Play", substring = false).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(QueueSource.CUSTOM_ALBUM, playedQueue?.source)
            assertEquals(listOf(track), playedQueue?.items)
        }
        assertTrue(runBlocking { repository.observeCustomAlbumMembership(track.videoId).first() }.contains(album.id))
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Test
    fun libraryCreateTileOpensTheFormAndNewAlbumOpensById() {
        val album = runBlocking { repository.createCustomAlbum("Library ${UUID.randomUUID()}", null) }
        createdIds.add(album.id)
        var openedId: String? = null
        compose.setContent {
            MaterialTheme {
                AnimatedVisibility(visible = true) {
                    LibraryScreen(
                        onOpenCollection = {}, onOpenCustomAlbum = { openedId = it },
                        onOpenArtist = { _, _, _ -> }, onOpenPlaylist = { _, _, _, _ -> },
                        onOpenAlbum = { _, _, _, _, _ -> }, animatedVisibilityScope = this
                    )
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(album.name))
        compose.onNodeWithText(album.name).performClick()
        compose.runOnIdle { assertEquals(album.id, openedId) }
        compose.onNodeWithText("Create album").performClick()
        compose.onNodeWithText("New album").assertExists()
        compose.onNodeWithText("Album name").assertExists()
        compose.onNodeWithText("Choose artwork").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("New album").assertDoesNotExist()
    }

    @Test
    fun longPressSelectAllPortsWithoutDuplicatesThenDeletesOnlySourceMembership() {
        val source = runBlocking { repository.createCustomAlbum("Source ${UUID.randomUUID()}", null) }
        val target = runBlocking { repository.createCustomAlbum("Target ${UUID.randomUUID()}", null) }
        createdIds.addAll(listOf(source.id, target.id))
        val other = track.copy(videoId = "custom-album-ui-other", title = "Other test song")
        runBlocking {
            repository.addToCustomAlbum(source.id, listOf(track, other))
            repository.addToCustomAlbum(target.id, track)
        }
        var playbackStarted = false
        compose.setContent {
            MaterialTheme {
                LibraryCollectionScreen(
                    LibraryCollectionType.LIKED, onTrackTap = { playbackStarted = true },
                    onPlayNext = {}, onAddToQueue = {}, onArtistTap = { _, _, _ -> },
                    collapsedMiniPlayerHeight = 0.dp, customAlbumId = source.id
                )
            }
        }
        compose.onNodeWithText(track.title).performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("2 selected").assertExists()
        compose.onNodeWithText("Port to album").performClick()
        compose.onNodeWithText(target.name).performScrollTo().performClick()
        compose.onNodeWithText("Add", substring = false).performClick()
        compose.waitUntil(10_000) {
            repository.snapshot.value.customAlbums.first { it.id == target.id }.itemCount == 2 &&
                compose.onAllNodesWithText("Add to album").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(2, repository.snapshot.value.customAlbums.first { it.id == source.id }.itemCount)
        compose.onNodeWithText(track.title).performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("Delete", substring = false).performClick()
        compose.onNodeWithText("Delete 2 songs?").assertExists()
        compose.onAllNodesWithText("Delete", substring = false).onLast().performClick()
        compose.waitUntil(10_000) {
            repository.snapshot.value.customAlbums.first { it.id == source.id }.itemCount == 0
        }
        assertEquals(2, repository.snapshot.value.customAlbums.first { it.id == target.id }.itemCount)
        assertFalse(playbackStarted)
    }

    @Test
    fun likedSongSelectionDeletesLikesAndKeepsCustomAlbumMembership() {
        val album = runBlocking { repository.createCustomAlbum("Kept ${UUID.randomUUID()}", null) }
        createdIds.add(album.id)
        val likedTracks = listOf(
            track.copy(videoId = "bulk-liked-first", title = "First liked test song"),
            track.copy(videoId = "bulk-liked-second", title = "Second liked test song")
        )
        runBlocking {
            repository.addToCustomAlbum(album.id, likedTracks)
            likedTracks.forEach { repository.toggleLike(it) }
        }
        try {
            compose.setContent {
                MaterialTheme {
                    LibraryCollectionScreen(
                        LibraryCollectionType.LIKED, onTrackTap = {}, onPlayNext = {}, onAddToQueue = {},
                        onArtistTap = { _, _, _ -> }, collapsedMiniPlayerHeight = 0.dp
                    )
                }
            }
            compose.onNodeWithText(likedTracks.first().title).performScrollTo().performTouchInput { longClick() }
            compose.onNodeWithText("Select all").performClick()
            compose.onNodeWithText("2 selected").assertExists()
            compose.onNodeWithText("Delete", substring = false).performClick()
            compose.onAllNodesWithText("Delete", substring = false).onLast().performClick()
            compose.waitUntil(10_000) { repository.snapshot.value.likedSongs.isEmpty() }
            assertEquals(2, repository.snapshot.value.customAlbums.first { it.id == album.id }.itemCount)
        } finally {
            runBlocking { repository.removeFromCollection(LibraryCollectionType.LIKED, likedTracks) }
        }
    }

    @Test
    fun bulkAddRollsBackAllSongsWhenOneSongHasNoId() = runBlocking {
        val album = repository.createCustomAlbum("Atomic ${UUID.randomUUID()}", null)
        createdIds.add(album.id)
        try {
            repository.addToCustomAlbum(album.id, listOf(track, track.copy(videoId = "")))
            fail("A missing song ID must fail")
        } catch (_: IllegalArgumentException) {
            assertTrue(repository.observeCustomAlbumItems(album.id).first().isEmpty())
        }
    }

    @Test
    fun existingMembershipDisablesAddingTheSameSong() {
        val album = runBlocking { repository.createCustomAlbum("Existing ${UUID.randomUUID()}", null) }
        createdIds.add(album.id)
        runBlocking { repository.addToCustomAlbum(album.id, track) }
        compose.setContent { MaterialTheme { AddToCustomAlbumSheet(track, repository, onDismiss = {}) } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Already added", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add", substring = false).assertIsNotEnabled()
    }

    @Test
    fun artworkIsCopiedResizedAndRemovedOnEditAndDelete() = runBlocking {
        val source = File(context.cacheDir, "album-art-${UUID.randomUUID()}.png")
        try {
            val bitmap = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val album = repository.createCustomAlbum("Artwork test", Uri.fromFile(source))
            createdIds.add(album.id)
            val copied = File(requireNotNull(album.artworkPath))
            assertTrue(copied.exists())
            source.delete()
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(copied.absolutePath, bounds)
            assertEquals(1024, bounds.outWidth)
            assertEquals(512, bounds.outHeight)
            repository.editCustomAlbum(album.id, "Renamed", null, removeArtwork = true)
            assertFalse(copied.exists())
            assertNull(repository.snapshot.value.customAlbums.first { it.id == album.id }.artworkPath)
            repository.deleteCustomAlbum(album.id)
            assertFalse(repository.snapshot.value.customAlbums.any { it.id == album.id })
        } finally {
            source.delete()
        }
    }
}
