package com.proj.Musicality.util

private val dimensionRegex = Regex("""(?<=[=\-])w\d+""")
private val heightRegex = Regex("""(?<=[=\-])h\d+""")
private val sizeRegex = Regex("""(?<=[=\-])s\d+""")

fun upscaleThumbnail(url: String?, size: Int = 544): String? {
    if (url == null) return null
    return url
        .replace(dimensionRegex, "w$size")
        .replace(heightRegex, "h$size")
        .replace(sizeRegex, "s$size")
}
