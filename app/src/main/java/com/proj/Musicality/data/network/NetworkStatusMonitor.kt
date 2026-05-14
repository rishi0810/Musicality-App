package com.proj.Musicality.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class NetworkStatus {
    object Unknown : NetworkStatus()
    object Normal : NetworkStatus()
    object Slow : NetworkStatus()
    object None : NetworkStatus()
}

class NetworkStatusMonitor private constructor(context: Context) {

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _status = MutableStateFlow<NetworkStatus>(NetworkStatus.Unknown)
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var publishJob: Job? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            recompute()
        }

        override fun onLost(network: Network) {
            recompute()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            // Only react to caps changes on the network that's currently default. A handoff
            // target (e.g., a not-yet-validated WiFi while cellular is still default) emits
            // caps changes too, and classifying *those* would briefly publish None even when
            // the active network is healthy.
            if (network == cm.activeNetwork) {
                recompute(capabilities)
            }
        }
    }

    init {
        // Cold-start state is committed immediately so screens render correctly on first
        // composition. Runtime transitions go through the debounced publish() instead.
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        _status.value = classify(caps)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
    }

    private fun recompute(capsHint: NetworkCapabilities? = null) {
        val active = cm.activeNetwork
        val caps = capsHint ?: active?.let { cm.getNetworkCapabilities(it) }
        publish(classify(caps))
    }

    // Absorb the transient None that the OS can produce during a network handoff (e.g., the
    // brief window between cellular stepping down and WiFi becoming the validated default).
    // Without this, StateFlow conflation can either swallow a real None entirely (no toast on
    // WiFi-off) or expose the bogus one (spurious "no internet" then immediate "back online").
    private fun publish(newStatus: NetworkStatus) {
        publishJob?.cancel()
        val current = _status.value
        if (current == newStatus) return
        publishJob = scope.launch {
            delay(STABLE_WINDOW_MS)
            _status.value = newStatus
        }
    }

    private fun classify(caps: NetworkCapabilities?): NetworkStatus {
        if (caps == null) return NetworkStatus.None
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkStatus.None
        // VALIDATED filters out captive portals and dead-end networks.
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return NetworkStatus.None

        val congested = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)
        val downKbps = caps.linkDownstreamBandwidthKbps
        // System-reported bandwidth on Wifi is the link rate to the AP (often optimistic), so a
        // 0 reading or a huge reading both mean "trust the other signals." Treat only the
        // explicit-low-cellular case as Slow; congestion alone is also enough.
        val explicitlySlow = downKbps in 1..SLOW_BANDWIDTH_THRESHOLD_KBPS
        return if (congested || explicitlySlow) NetworkStatus.Slow else NetworkStatus.Normal
    }

    companion object {
        private const val SLOW_BANDWIDTH_THRESHOLD_KBPS = 500
        private const val STABLE_WINDOW_MS = 800L

        @Volatile
        private var INSTANCE: NetworkStatusMonitor? = null

        fun getInstance(context: Context): NetworkStatusMonitor =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkStatusMonitor(context.applicationContext).also { INSTANCE = it }
            }
    }
}
