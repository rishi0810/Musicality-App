package com.proj.Musicality.data.parser

import com.proj.Musicality.data.json.VideoFetchResponse
import com.proj.Musicality.data.model.VideoFetchData

object VideoParser {

    fun extractVideoFetchData(jsonString: String): VideoFetchData? {
        val response = runCatching {
            JsonParser.instance.decodeFromString<VideoFetchResponse>(jsonString)
        }.getOrNull() ?: return null

        val details = response.playerResponse?.videoDetails ?: return null
        val formats = response.playerResponse.streamingData?.adaptiveFormats ?: emptyList()

        val audioFormat = formats.find { it.itag == 251 }
            ?: formats.find { it.itag == 140 }

        val streamUrl = audioFormat?.url ?: return null
        val bestThumbnail = details.thumbnail?.thumbnails?.maxByOrNull { it.width }?.url

        return VideoFetchData(
            videoId = details.videoId,
            title = details.title,
            author = details.author,
            channelId = details.channelId,
            lengthSeconds = details.lengthSeconds.toLongOrNull() ?: 0L,
            viewCount = details.viewCount,
            thumbnailUrl = bestThumbnail,
            streamUrl = streamUrl,
            mimeType = audioFormat.mimeType ?: "",
            bitrate = audioFormat.bitrate ?: 0
        )
    }
}
