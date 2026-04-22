package com.proj.Musicality.lyrics.providers

import com.proj.Musicality.lyrics.LyricsProvider
import com.proj.Musicality.lyrics.TTMLParser
import com.proj.Musicality.lyrics.lyricsHttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
private data class LyricWord(
    val time: Long = 0,
    val duration: Long = 0,
    val text: String = "",
    val isBackground: Boolean = false,
)

@Serializable
private data class LyricLineJson(
    val time: Long = 0,
    val duration: Long = 0,
    val text: String = "",
    val syllabus: List<LyricWord>? = null,
)

@Serializable
private data class LyricsPlusResponse(
    val type: String? = null,
    val lyrics: List<LyricLineJson>? = null,
)

@Serializable
private data class BinimumLyricsApiResponse(
    val total: Int? = null,
    val source: String? = null,
    val results: List<BinimumLyricsResult> = emptyList(),
    val error: String? = null,
)

@Serializable
private data class BinimumLyricsResult(
    val id: String? = null,
    val track_name: String? = null,
    val artist_name: String? = null,
    val album_name: String? = null,
    val duration: Int? = null,
    val isrc: String? = null,
    val timing_type: String? = null,
    val lyricsUrl: String? = null,
)

private data class BinimumFetch(val lrc: String, val isWordSync: Boolean)

object LyricsPlusProvider : LyricsProvider {
    override val name: String = "LyricsPlus"

    private const val BINIMUM_API = "https://lyrics-api.binimum.org/"
    private const val ISRC_PATTERN = "^[A-Z]{2}[A-Z0-9]{3}\\d{2}\\d{5}$"
    private val ISRC_REGEX by lazy { Regex(ISRC_PATTERN) }

    private val baseUrls = listOf(
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.atomix.one/",
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus-seven.vercel.app",
    )

    @Volatile
    private var lastWorkingServer: String? = null

    private fun prioritized(): List<String> {
        val last = lastWorkingServer
        return if (last != null && last in baseUrls) listOf(last) + baseUrls.filter { it != last } else baseUrls
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        val binimum = fetchBinimum(id, title, artist, duration, album)
        if (binimum?.isWordSync == true) return@runCatching binimum.lrc

        val jsonResponse = fetchLyricsPlus(title, artist, duration, album)
        val jsonLrc = convertToLrc(jsonResponse)
        resolve(binimum, jsonResponse, jsonLrc)
            ?: throw IllegalStateException("No LyricsPlus match")
    }

    private fun resolve(
        binimum: BinimumFetch?,
        response: LyricsPlusResponse?,
        jsonLrc: String?,
    ): String? {
        if (binimum?.isWordSync == false) {
            val jsonIsWord = response?.type.equals("Word", ignoreCase = true)
            return if (jsonIsWord && !jsonLrc.isNullOrBlank()) jsonLrc else binimum.lrc
        }
        return jsonLrc
    }

    private suspend fun fetchLyricsPlus(
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): LyricsPlusResponse? {
        if (title.isBlank() || artist.isBlank()) return null
        for (base in prioritized()) {
            try {
                val response = lyricsHttpClient.get("$base/v2/lyrics/get") {
                    parameter("title", title)
                    parameter("artist", artist)
                    if (duration > 0) parameter("duration", duration)
                    if (!album.isNullOrBlank()) parameter("album", album)
                }
                if (response.status == HttpStatusCode.OK) {
                    val parsed = runCatching { response.body<LyricsPlusResponse>() }.getOrNull()
                    if (parsed != null && !parsed.lyrics.isNullOrEmpty()) {
                        lastWorkingServer = base
                        return parsed
                    }
                }
            } catch (_: Exception) { /* try next mirror */ }
        }
        return null
    }

    private suspend fun fetchBinimum(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): BinimumFetch? {
        val normalizedId = id.trim()
        val normalizedIsrc = normalizedId.uppercase()
        val canUseIsrc = normalizedIsrc.matches(ISRC_REGEX)
        val hasMetadata = title.isNotBlank() && artist.isNotBlank()
        if (!canUseIsrc && !hasMetadata) return null

        suspend fun byMeta() = runCatching {
            lyricsHttpClient.get(BINIMUM_API) {
                parameter("track", title)
                parameter("artist", artist)
                if (!album.isNullOrBlank()) parameter("album", album)
                if (duration > 0) parameter("duration", duration)
            }
        }.getOrNull()

        suspend fun byIsrc() = runCatching {
            lyricsHttpClient.get(BINIMUM_API) { parameter("isrc", normalizedIsrc) }
        }.getOrNull()

        val response = (if (canUseIsrc) byIsrc() ?: byMeta() else byMeta()) ?: return null
        if (!response.status.isSuccess()) return null

        val payload = runCatching { response.body<BinimumLyricsApiResponse>() }.getOrNull() ?: return null
        if (payload.results.isEmpty()) return null

        val selected = payload.results.firstOrNull { !it.lyricsUrl.isNullOrBlank() } ?: return null
        val ttml = runCatching { lyricsHttpClient.get(selected.lyricsUrl!!) }.getOrNull()?.let {
            if (it.status.isSuccess()) runCatching { it.body<String>() }.getOrNull() else null
        } ?: return null

        val parsed = runCatching { TTMLParser.parseTTML(ttml) }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: return null
        val lrc = runCatching { TTMLParser.toLRC(parsed).trim() }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null

        return BinimumFetch(
            lrc = lrc,
            isWordSync = selected.timing_type.equals("word", ignoreCase = true),
        )
    }

    /**
     * Convert LyricsPlus JSON (with word-level `syllabus`) into extended LRC.
     * Agents and background vocals are stripped — this port is single-voice English.
     */
    private fun convertToLrc(response: LyricsPlusResponse?): String? {
        val lyrics = response?.lyrics?.takeIf { it.isNotEmpty() } ?: return null
        val isWordSync = response.type.equals("Word", ignoreCase = true)
        val sb = StringBuilder(lyrics.size * 128)

        for (line in lyrics) {
            val mainWords = line.syllabus?.filter { !it.isBackground } ?: emptyList()
            val text = when {
                isWordSync && mainWords.isNotEmpty() -> mainWords.joinToString("") { it.text }.trim()
                else -> line.text.trim()
            }
            if (text.isBlank()) continue

            sb.append(formatLrcTime(line.time)).append(text).append('\n')

            if (isWordSync && mainWords.isNotEmpty()) {
                val valid = mainWords.filter { it.text.isNotBlank() }
                if (valid.isNotEmpty()) {
                    sb.append('<')
                    valid.forEachIndexed { i, w ->
                        val start = w.time / 1000.0
                        val end = (w.time + w.duration) / 1000.0
                        sb.append(w.text.trim()).append(':').append(start).append(':').append(end)
                        if (i < valid.lastIndex) sb.append('|')
                    }
                    sb.append(">\n")
                }
            }
        }
        return sb.toString().trimEnd().ifBlank { null }
    }

    private fun formatLrcTime(timeMs: Long): String {
        val m = timeMs / 60000
        val s = (timeMs % 60000) / 1000
        val c = (timeMs % 1000) / 10
        return buildString {
            append('[')
            if (m < 10) append('0')
            append(m).append(':')
            if (s < 10) append('0')
            append(s).append('.')
            if (c < 10) append('0')
            append(c).append(']')
        }
    }
}
