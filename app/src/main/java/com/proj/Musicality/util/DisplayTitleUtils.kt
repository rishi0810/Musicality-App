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

private val featRegex = Regex(
    """\s*[\(\[]\s*(feat\.?|ft\.?|featuring|with|prod\.?\s+by|prod\.?)\s+[^)\]]*[\)\]]""",
    RegexOption.IGNORE_CASE
)

private val inlineFeatRegex = Regex(
    """\s+(feat\.?|ft\.?|featuring)\s+.+$""",
    RegexOption.IGNORE_CASE
)

private val parenthesizedTagRegex = Regex(
    """\s*[\(\[]\s*(remix|edit|mix|version|ver\.?|deluxe|bonus\s+track|acoustic|live|demo|instrumental|extended|radio\s+edit|clean|explicit|sped\s+up|slowed(\s*\+?\s*reverb)?|reverb|lofi|lo-fi)\s*[\)\]]""",
    RegexOption.IGNORE_CASE
)

private val trailingDashTagRegex = Regex(
    """\s+[-–—]\s+(remix|acoustic|live|demo|instrumental|radio\s+edit|remaster(ed)?).*$""",
    RegexOption.IGNORE_CASE
)

fun String.toCleanSongTitle(): String {
    val fallback = ifBlank { "Unknown song" }
    var clean = fallback
        .replace(Regex("""\s+"""), " ")
        .trim()
        .replace(trailingNoiseRegex, "")
        .replace(separatorNoiseRegex, "")
        .replace(featRegex, "")
        .replace(parenthesizedTagRegex, "")
        .replace(trailingDashTagRegex, "")
        .replace(inlineFeatRegex, "")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
    if (clean.isBlank()) clean = fallback
    return clean
}

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

