package com.proj.Musicality.data.model

import androidx.compose.runtime.Immutable
import com.proj.Musicality.data.parser.primaryArtistName

@Immutable
data class MediaItem(
    val videoId: String,
    val title: String,
    val artistName: String,
    val artistId: String?,
    val albumName: String?,
    val albumId: String?,
    val thumbnailUrl: String?,
    val durationText: String?,
    val musicVideoType: String?
)

fun MediaItem.hasMissingPlaybackMetadata(): Boolean =
    artistName.isBlank() || artistName.equals("Unknown Artist", ignoreCase = true) ||
        artistId.isNullOrBlank() || thumbnailUrl.isNullOrBlank()

fun MediaItem.withMissingMetadataFrom(details: MediaItem): MediaItem {
    if (videoId != details.videoId) return this
    return copy(
        title = title.ifBlank { details.title },
        artistName = if (artistName.isBlank() || artistName.equals("Unknown Artist", ignoreCase = true)) {
            details.artistName
        } else artistName,
        artistId = artistId?.takeIf { it.isNotBlank() } ?: details.artistId,
        albumName = albumName?.takeIf { it.isNotBlank() } ?: details.albumName,
        albumId = albumId?.takeIf { it.isNotBlank() } ?: details.albumId,
        thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() } ?: details.thumbnailUrl,
        durationText = durationText?.takeIf { it.isNotBlank() } ?: details.durationText,
        musicVideoType = musicVideoType?.takeIf { it.isNotBlank() } ?: details.musicVideoType
    )
}

fun SongResult.toMediaItem() = MediaItem(
    videoId = videoId, title = title, artistName = artist.primaryArtistName(),
    artistId = artistId, albumName = album, albumId = albumId,
    thumbnailUrl = thumb, durationText = duration, musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
)

fun VideoResult.toMediaItem() = MediaItem(
    videoId = videoId, title = title, artistName = artist.primaryArtistName(),
    artistId = artistId, albumName = null, albumId = null,
    thumbnailUrl = thumb, durationText = duration, musicVideoType = "MUSIC_VIDEO_TYPE_OMV"
)

fun Track.toMediaItem(album: AlbumPage) = MediaItem(
    videoId = videoId ?: "", title = title,
    artistName = artistName ?: album.artist.name,
    artistId = artistId ?: album.artist.id,
    albumName = album.title, albumId = null,
    thumbnailUrl = album.thumbnail, durationText = duration, musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
)

fun PlaylistTrack.toMediaItem() = MediaItem(
    videoId = videoId, title = title,
    artistName = (artists.firstOrNull { !it.id.isNullOrBlank() } ?: artists.firstOrNull())
        ?.name
        ?.primaryArtistName()
        .orEmpty(),
    artistId = (artists.firstOrNull { !it.id.isNullOrBlank() } ?: artists.firstOrNull())
        ?.id
        ?.takeIf { it.isNotBlank() },
    albumName = albumOrDate, albumId = null,
    thumbnailUrl = thumbnailUrl, durationText = duration,
    musicVideoType = musicVideoType
)

fun ArtistSong.toMediaItem(artistName: String, artistId: String?) = MediaItem(
    videoId = videoId, title = title, artistName = artistName.primaryArtistName(),
    artistId = artistId, albumName = album, albumId = null,
    thumbnailUrl = image, durationText = null, musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
)
