package com.proj.Musicality.util

private val widthParamRegex = Regex("""(^|-)w\d+(?=-|$)""")
private val heightParamRegex = Regex("""(^|-)h\d+(?=-|$)""")
private val sizeParamRegex = Regex("""(^|-)s\d+(?=-|$)""")

fun upscaleThumbnail(url: String?, size: Int = 544): String? {
    if (url == null) return null
    val paramsStart = url.indexOf('=')
    if (paramsStart == -1) return url

    val imageId = url.substring(0, paramsStart)
    val params = url.substring(paramsStart + 1)
        .replace(widthParamRegex) { "${it.groupValues[1]}w$size" }
        .replace(heightParamRegex) { "${it.groupValues[1]}h$size" }
        .replace(sizeParamRegex) { "${it.groupValues[1]}s$size" }

    return "$imageId=$params"
}

fun isGoogleusercontentUrl(url: String?): Boolean =
    url?.contains("googleusercontent.com", ignoreCase = true) == true

fun albumArtUrlOrNull(url: String?, size: Int = 544): String? {
    if (!isGoogleusercontentUrl(url)) return null
    val base = url?.substringBefore("=") ?: return null
    return "$base=w$size-h$size-l90-rj"
}
