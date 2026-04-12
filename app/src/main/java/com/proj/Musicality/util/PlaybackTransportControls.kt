package com.proj.Musicality.util

private const val TRACK_START_WINDOW_MS = 1_000L

fun shouldRestartCurrentTrack(positionMs: Long): Boolean = positionMs > TRACK_START_WINDOW_MS
