package com.proj.Musicality.util

private const val DEFAULT_COMPACT_TITLE_MAX_CHARS = 58

private val trailingNoiseRegex = Regex(
    """\s*(\(|\[)?(official(\s+music)?\s+video|official\s+audio|official\s+version|official\s+mv|m/?v|full\s+(song|video)|lyrics?\s+video|lyrics?|lyric\s+video|visualizer|hd|hq|4k|uhd|remaster(ed)?|audio)(\)|\])?\s*$""",
    RegexOption.IGNORE_CASE
)

private val separatorNoiseRegex = Regex(
    """\s*[-–—|:]\s*(official|lyrics?|audio|video|m/?v|full\s+song|full\s+video).*$""",
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
    """\s*[\(\[]\s*(remix|edit|mix|version|ver\.?|deluxe|bonus\s+track|acoustic|live|demo|instrumental|extended|radio\s+edit|clean|explicit|sped\s+up|slowed(\s*\+?\s*reverb)?|reverb|lofi|lo-fi|from\s+"[^"]*"|from\s+[^)\]]*)\s*[\)\]]""",
    RegexOption.IGNORE_CASE
)

private val trailingDashTagRegex = Regex(
    """\s+[-–—]\s+(remix|acoustic|live|demo|instrumental|radio\s+edit|remaster(ed)?|from\s+.*)$""",
    RegexOption.IGNORE_CASE
)

private val hashtagRegex = Regex(
    """\s*#\S+""",
)

private val pipeSeparatedCreditsRegex = Regex(
    """\s*[-–—]\s+[^|]+(\|[^|]+)+\s*$""",
)

private val pipeShowNameRegex = Regex(
    """\s*\|\s+.+$"""
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
        .replace(hashtagRegex, "")
        .replace(pipeSeparatedCreditsRegex, "")
        .replace(pipeShowNameRegex, "")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
    if (clean.isBlank()) clean = fallback
    return clean
}

fun String.toSearchAwareTitle(searchQuery: String?): String {
    if (searchQuery.isNullOrBlank()) return toCleanSongTitle()
    val cleaned = toCleanSongTitle()
    val queryTrimmed = searchQuery.trim()
    val matchStart = cleaned.indexOf(queryTrimmed, ignoreCase = true)
    if (matchStart >= 0) {
        return cleaned.substring(matchStart, matchStart + queryTrimmed.length)
    }
    return cleaned
}

fun String.toCompactSongTitle(maxChars: Int = DEFAULT_COMPACT_TITLE_MAX_CHARS): String {
    val clean = toCleanSongTitle()
    if (clean.length <= maxChars) return clean

    val cutAt = clean.lastIndexOf(' ', maxChars).takeIf { it > 0 } ?: maxChars
    return clean.substring(0, cutAt).trimEnd() + "…"
}
