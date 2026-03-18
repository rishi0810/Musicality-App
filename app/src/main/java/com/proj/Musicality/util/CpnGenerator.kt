package com.proj.Musicality.util

object CpnGenerator {
    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun generate(): String {
        return buildString(16) {
            repeat(16) {
                append(CHARS.random())
            }
        }
    }
}
