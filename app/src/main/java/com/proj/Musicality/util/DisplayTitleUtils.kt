package com.proj.Musicality.util

private const val DEFAULT_COMPACT_TITLE_MAX_CHARS = 58

private val trailingNoiseRegex = Regex(
    """\s*(\(|\[)?(official(\s+music)?\s+video|official\s+audio|lyrics?|lyric\s+video|visualizer|hd|hq|4k|remaster(ed)?|audio)(\)|\])?\s*$""",
    RegexOption.IGNORE_CASE
)

private val separatorNoiseRegex = Regex(
    """\s*[-|:]\s*(official|lyrics?|audio|video).*$""",
    RegexOption.IGNORE_CASE
)

/**
 * Compact UI-friendly title for list rows/cards/mini-player.
 * Keeps song screen untouched by applying this only where explicitly called.
 */
fun String.toCompactSongTitle(maxChars: Int = DEFAULT_COMPACT_TITLE_MAX_CHARS): String {
    val fallback = ifBlank { "Unknown song" }
    var compact = fallback
        .replace(Regex("""\s+"""), " ")
        .trim()
        .replace(trailingNoiseRegex, "")
        .replace(separatorNoiseRegex, "")
        .trim()

    if (compact.isBlank()) compact = fallback
    if (compact.length <= maxChars) return compact

    val cutAt = compact.lastIndexOf(' ', maxChars).takeIf { it > 0 } ?: maxChars
    return compact.substring(0, cutAt).trimEnd() + "…"
}

