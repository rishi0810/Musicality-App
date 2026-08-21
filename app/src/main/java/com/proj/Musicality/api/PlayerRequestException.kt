package com.proj.Musicality.api

import java.io.IOException

class PlayerRequestException(
    val videoId: String,
    val statusCode: Int,
) : IOException("Player request failed with HTTP $statusCode for '$videoId'")
