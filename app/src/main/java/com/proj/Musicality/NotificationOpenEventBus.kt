package com.proj.Musicality

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

object NotificationOpenEventBus {
    private val openPlayerChannel = Channel<Unit>(capacity = Channel.BUFFERED)
    val openPlayerEvents: Flow<Unit> = openPlayerChannel.receiveAsFlow()

    fun emitOpenPlayer() {
        openPlayerChannel.trySend(Unit)
    }
}
