package com.proj.Musicality.automotive

import com.proj.Musicality.data.local.ListeningEventDbRecord
import com.proj.Musicality.data.local.SongPlayCountDbRecord
import com.proj.Musicality.data.model.MediaItem as AppMediaItem

internal fun ListeningEventDbRecord.toAppMediaItem(): AppMediaItem = AppMediaItem(
    videoId = videoId,
    title = title,
    artistName = artistName,
    artistId = artistId,
    albumName = null,
    albumId = null,
    thumbnailUrl = thumbnailUrl,
    durationText = null,
    musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
)

internal fun SongPlayCountDbRecord.toAppMediaItem(): AppMediaItem = AppMediaItem(
    videoId = videoId,
    title = title,
    artistName = artistName,
    artistId = artistId,
    albumName = null,
    albumId = null,
    thumbnailUrl = thumbnailUrl,
    durationText = null,
    musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
)
