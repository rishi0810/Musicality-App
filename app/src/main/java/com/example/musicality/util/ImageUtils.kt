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
    
    /**
     * Resizes a Google thumbnail URL to custom width and height
     * Used for Explore More album thumbnails (480x360)
     * Example input: https://lh3.googleusercontent.com/.../image=w544-h544-l90-rj
     * Example output: https://lh3.googleusercontent.com/.../image=w480-h360-l90-rj
     */
    fun resizeThumbnail(url: String, width: Int, height: Int): String {
        return url
            .replace(Regex("=w\\d+"), "=w$width")  // Match =w followed by digits
            .replace(Regex("-h\\d+"), "-h$height")  // Match -h followed by digits
    }
}
