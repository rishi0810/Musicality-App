package com.proj.Musicality.api

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

class RequestDeduplicator {
    private val inFlight = ConcurrentHashMap<String, Deferred<*>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> deduplicate(key: String, block: suspend () -> T): T {
        val existing = inFlight[key] as? Deferred<T>
        if (existing != null && existing.isActive) return existing.await()

        val deferred = coroutineScope {
            async {
                try {
                    block()
                } finally {
                    inFlight.remove(key)
                }
            }
        }
        inFlight[key] = deferred
        return deferred.await()
    }
}
