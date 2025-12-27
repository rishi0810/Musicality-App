package com.example.musicality.util

/**
 * Utility functions for image URL manipulation
 */
object ImageUtils {
    /**
     * Upscales a Google thumbnail URL from w120-h120 to w544-h544
     * Example input: https://lh3.googleusercontent.com/.../image=w120-h120-l90-rj
     * Example output: https://lh3.googleusercontent.com/.../image=w544-h544-l90-rj
     */
    fun upscaleThumbnail(url: String, targetSize: Int = 544): String {
        // Use word boundaries and more specific patterns to avoid matching domain parts like "lh3"
        val result = url
            .replace(Regex("=w\\d+"), "=w$targetSize")  // Match =w followed by digits
            .replace(Regex("-h\\d+"), "-h$targetSize")  // Match -h followed by digits
        
        android.util.Log.d("ImageUtils", "Input URL: $url")
        android.util.Log.d("ImageUtils", "Output URL: $result")
        
        return result
    }
}
