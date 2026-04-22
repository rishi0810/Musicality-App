package com.proj.Musicality.lyrics

import com.proj.Musicality.data.model.LyricLine
import com.proj.Musicality.data.model.WordTimestamp
import java.util.Locale

private val LRC_TIMESTAMP_HINT = Regex("""\[\d{1,2}:\d{2}""")
private val LINE_TIMESTAMP = Regex("""\[(\d{1,2}):(\d{2})\.(\d{2,3})\]""")
private val AGENT_TAG = Regex("""\{agent:[^}]+\}""")
private val BG_TAG = Regex("""\{bg\}""")

// Returns true when the raw text carries any `[mm:ss.xx]` tag (BOM + leading blanks tolerated).
fun lyricsTextLooksSynced(lyrics: String?): Boolean {
    if (lyrics.isNullOrBlank()) return false
    val t = lyrics.trim().removePrefix("﻿").trimStart()
    if (t.startsWith('[')) return true
    return LRC_TIMESTAMP_HINT.containsMatchIn(t.take(4096))
}

fun cleanTitleForSearch(title: String): String =
    title.replace(Regex("\\s*[(\\[].*?[)\\]]"), "").trim()

/**
 * Strip "Lyrics by ...", "Synced by ...", "[foo synced by bar]", etc.
 * Works on raw LRC *and* on text that still carries `{agent}`/`{bg}` tags.
 */
fun filterLyricsCreditLines(lyrics: String): String =
    lyrics.lines().filter { line ->
        var text = line.trim()
        var changed = true
        while (changed) {
            val before = text.length
            text = text
                .replaceFirst(LINE_TIMESTAMP, "")
                .replaceFirst(AGENT_TAG, "")
                .replaceFirst(BG_TAG, "")
                .trim()
            changed = text.length < before
        }
        val lower = text.lowercase(Locale.US)
        val isCredit = lower.startsWith("synced by") ||
            lower.startsWith("lyrics by") ||
            lower.startsWith("music by") ||
            lower.startsWith("arranged by") ||
            (lower.startsWith("[") && lower.endsWith("]") && lower.length < 40 && lower.contains("synced by"))
        !isCredit
    }.joinToString("\n")

/**
 * Parses Metrolist's extended LRC into simple lines.
 *
 *   [mm:ss.xx]{agent:v1}text
 *   <word:startSec:endSec|word:...>   ← optional word block (karaoke timing)
 *
 * We drop agent/bg tags and collapse duplicate timestamps; multi-agent rendering is
 * out of scope here.
 */
fun parseLrc(raw: String): List<LyricLine> {
    val normalized = raw.replace('\r', '\n').trim().removePrefix("﻿")
    if (normalized.isBlank()) return emptyList()

    val srcLines = normalized.lines()
    val out = mutableListOf<LyricLine>()
    var i = 0
    while (i < srcLines.size) {
        val line = srcLines[i].trim()
        i++
        if (line.isEmpty()) continue

        val stamps = LINE_TIMESTAMP.findAll(line).toList()
        if (stamps.isEmpty()) continue
        val afterStamp = line.substring(stamps.last().range.last + 1)

        val text = afterStamp
            .replace(AGENT_TAG, "")
            .replace(BG_TAG, "")
            .trim()

        // Collect optional following <word:startSec:endSec|...> block.
        var words: List<WordTimestamp>? = null
        if (i < srcLines.size) {
            val peek = srcLines[i].trim()
            if (peek.startsWith("<") && peek.endsWith(">")) {
                words = parseWordBlock(peek)
                i++
            }
        }

        if (text.isBlank() && words.isNullOrEmpty()) continue

        val displayText = if (text.isNotBlank()) text else words!!.joinToString(" ") { it.text }

        for (stamp in stamps) {
            val min = stamp.groupValues[1].toLong()
            val sec = stamp.groupValues[2].toLong()
            val frac = stamp.groupValues[3]
            val fracMs = when (frac.length) {
                2 -> frac.toLong() * 10
                3 -> frac.toLong()
                else -> 0L
            }
            val startMs = (min * 60 + sec) * 1000L + fracMs
            val rebased = words?.let { ws ->
                // If the line has multiple timestamps (same words repeating), rebase word timings
                // to the first timestamp; subsequent stamps share the same list unshifted since
                // the word times are absolute from the original line.
                if (stamps.size == 1) ws else ws.map { w ->
                    w.copy(startMs = startMs + (w.startMs - (ws.first().startMs)), endMs = startMs + (w.endMs - ws.first().startMs))
                }
            }
            out.add(LyricLine(timeMs = startMs, text = displayText, words = rebased))
        }
    }
    out.sortBy { it.timeMs }
    return out
}

/** Parse `<word:startSec:endSec|word:startSec:endSec|...>`. Times in seconds (decimal). */
private fun parseWordBlock(block: String): List<WordTimestamp>? {
    val body = block.trim().removePrefix("<").removeSuffix(">").trim()
    if (body.isEmpty()) return null
    val out = mutableListOf<WordTimestamp>()
    for (segment in body.split('|')) {
        val seg = segment.trim()
        if (seg.isEmpty()) continue
        val endColon = seg.lastIndexOf(':')
        if (endColon <= 0) continue
        val startColon = seg.lastIndexOf(':', endColon - 1)
        if (startColon <= 0) continue
        val text = seg.substring(0, startColon)
        val start = seg.substring(startColon + 1, endColon).toDoubleOrNull() ?: continue
        val end = seg.substring(endColon + 1).toDoubleOrNull() ?: continue
        val startMs = (start * 1000).toLong()
        val endMs = (end * 1000).toLong()
        out.add(WordTimestamp(text, startMs, endMs))
    }
    return out.takeIf { it.isNotEmpty() }
}

/** Fallback for plain (non-synced) lyrics — sequential fake timestamps, no seeking. */
fun parsePlainLyrics(raw: String): List<LyricLine> =
    raw.lines()
        .filter { it.isNotBlank() }
        .mapIndexed { i, text -> LyricLine(i.toLong(), text.trim()) }
