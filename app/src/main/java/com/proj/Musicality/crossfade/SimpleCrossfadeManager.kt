package com.proj.Musicality.crossfade

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Data the ViewModel provides when the next track is ready for crossfade.
 */
data class CrossfadeNextTrack(
    val mediaItem: MediaItem,
    val onCrossfadeStart: suspend () -> Unit,
    val onCrossfadeComplete: suspend () -> Unit
)

/**
 * Dual-player crossfade with no handoff.
 *
 * Architecture:
 *   Track A plays on activePlayer
 *   Track B is preloaded on inactivePlayer
 *   At trigger: B starts, volumes ramp (equal-power), when done → swap active
 *   B continues from exactly where it was — no reload, no duplicated audio.
 *
 * Timeline:
 *   A ──────────────┐
 *                   ├── overlap (fade A down, fade B up)
 *   B ──────────────┘── continues playing
 */
class SimpleCrossfadeManager(private val context: Context) {

    companion object {
        private const val TAG = "SimpleCrossfade"
        private const val FADE_DURATION_MS = 6000L
        private const val TRIGGER_LEAD_MS = 6200L
        private const val STEP_MS = 40L
        private const val OUTGOING_FLOOR = 0.10f
    }

    private var enabled = false
    private var transitionJob: Job? = null
    private var lastTriggeredTrackId: String? = null
    private var transitionState: TransitionState? = null

    private var outgoingEq: Equalizer? = null
    private var incomingEq: Equalizer? = null
    private var outgoingEqSession: Int? = null
    private var incomingEqSession: Int? = null

    private data class TransitionState(
        val delegatingPlayer: CrossfadeDelegatingPlayer,
        val outgoing: ExoPlayer,
        val incoming: ExoPlayer,
        var uiSwitchedToIncoming: Boolean = false
    )

    fun setEnabled(enabled: Boolean, delegatingPlayer: CrossfadeDelegatingPlayer? = null) {
        this.enabled = enabled
        if (!enabled) {
            cancelTransition(
                delegatingPlayer = delegatingPlayer,
                commitToIncoming = true,
                reason = "disabled"
            )
        }
    }

    fun isEnabled(): Boolean = enabled
    fun isTransitioning(): Boolean = transitionJob != null

    /**
     * Called every ~300ms from position polling. Checks if it's time to trigger crossfade.
     */
    fun monitor(
        scope: CoroutineScope,
        trackId: String?,
        hasNext: Boolean,
        positionMs: Long,
        durationMs: Long,
        delegatingPlayer: CrossfadeDelegatingPlayer,
        prepareNext: suspend () -> CrossfadeNextTrack?
    ) {
        if (!enabled || !hasNext || trackId.isNullOrBlank()) return
        if (transitionJob != null || durationMs <= 0L) return
        if (trackId == lastTriggeredTrackId) return

        val remaining = durationMs - positionMs
        if (remaining > TRIGGER_LEAD_MS) return

        transitionJob = scope.launch(Dispatchers.Main) {
            runCatching {
                val nextTrack = prepareNext() ?: return@launch
                lastTriggeredTrackId = trackId
                executeCrossfade(delegatingPlayer, nextTrack)
            }.onFailure {
                if (it is CancellationException) {
                    Log.d(TAG, "Crossfade cancelled")
                } else {
                    Log.e(TAG, "Crossfade failed", it)
                }
            }
            transitionJob = null
        }
    }

    fun cancelTransition(
        delegatingPlayer: CrossfadeDelegatingPlayer? = null,
        commitToIncoming: Boolean = false,
        reason: String = "manual"
    ) {
        Log.d(TAG, "cancelTransition: reason=$reason, commitToIncoming=$commitToIncoming")
        transitionJob?.cancel()
        transitionJob = null
        val state = transitionState
        if (state != null) {
            abortTransition(state, commitToIncoming)
        } else if (delegatingPlayer != null) {
            ensureSingleActivePlayback(delegatingPlayer)
        }
        releaseEqs()
        transitionState = null
    }

    fun release() {
        cancelTransition(reason = "release")
    }

    fun resetTriggerForTrack(trackId: String?) {
        if (!trackId.isNullOrBlank() && lastTriggeredTrackId == trackId) {
            Log.d(TAG, "resetTriggerForTrack: trackId=$trackId")
            lastTriggeredTrackId = null
        }
    }

    fun onManualSeek(trackId: String?, positionMs: Long, durationMs: Long) {
        if (trackId.isNullOrBlank()) return
        if (trackId != lastTriggeredTrackId || durationMs <= 0L) return
        val retriggerBoundary = (durationMs - TRIGGER_LEAD_MS).coerceAtLeast(0L)
        if (positionMs < retriggerBoundary) {
            Log.d(TAG, "onManualSeek: re-arming crossfade for trackId=$trackId")
            lastTriggeredTrackId = null
        }
    }

    /**
     * The core crossfade: pure dual-player volume automation.
     *
     * 1. Preload B on inactive player, start at vol 0
     * 2. Ramp A down (equal-power cos), B up (equal-power sin) over FADE_DURATION_MS
     * 3. When done: swap active player, stop old player
     * 4. B continues from exactly where it was — no reload
     */
    private suspend fun executeCrossfade(
        delegatingPlayer: CrossfadeDelegatingPlayer,
        nextTrack: CrossfadeNextTrack
    ) {
        val outgoing = delegatingPlayer.activePlayer
        val incoming = delegatingPlayer.inactivePlayer
        val state = TransitionState(
            delegatingPlayer = delegatingPlayer,
            outgoing = outgoing,
            incoming = incoming
        )
        transitionState = state

        try {
            // Prepare and start incoming at volume 0
            incoming.volume = 0f
            incoming.setMediaItem(nextTrack.mediaItem)
            incoming.prepare()
            incoming.play()

            // Wait for incoming to actually start playing
            var waited = 0L
            while (!incoming.isPlaying && waited < 3000L) {
                delay(STEP_MS)
                waited += STEP_MS
            }

            if (!incoming.isPlaying) {
                Log.e(TAG, "Incoming player failed to start, aborting crossfade")
                incoming.stop()
                return
            }

            // Notify UI: switch to next song now
            nextTrack.onCrossfadeStart()
            state.uiSwitchedToIncoming = true

            // Equal-power crossfade ramp
            val steps = (FADE_DURATION_MS / STEP_MS).toInt().coerceAtLeast(1)
            repeat(steps) { index ->
                val progress = ((index + 1).toFloat() / steps.toFloat()).coerceIn(0f, 1f)

                // Equal-power curves
                val outGain = kotlin.math.cos(progress * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)
                val inGain = kotlin.math.sin(progress * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)

                outgoing.volume = lerp(1f, OUTGOING_FLOOR, 1f - outGain)
                incoming.volume = inGain

                applyEq(outgoing, progress, outgoing = true, primaryChain = true)
                applyEq(incoming, progress, outgoing = false, primaryChain = false)

                delay(STEP_MS)
            }

            // Fade complete: incoming is at full volume, outgoing is silent
            incoming.volume = 1f
            outgoing.volume = 0f

            // Swap: incoming becomes the active player, outgoing stops
            delegatingPlayer.swapActivePlayer()
            releaseEqs()

            Log.d(TAG, "Crossfade complete. New active player position: ${delegatingPlayer.activePlayer.currentPosition}ms")

            // Notify ViewModel: crossfade is done, update any remaining state
            nextTrack.onCrossfadeComplete()
        } catch (cancelled: CancellationException) {
            abortTransition(state, commitToIncoming = state.uiSwitchedToIncoming)
            throw cancelled
        } finally {
            releaseEqs()
            transitionState = null
        }
    }

    /**
     * Get the incoming player's position during a crossfade (for UI tracking).
     */
    fun getIncomingPosition(delegatingPlayer: CrossfadeDelegatingPlayer): Long {
        if (!isTransitioning()) return -1L
        return delegatingPlayer.inactivePlayer.currentPosition.takeIf { it >= 0 } ?: -1L
    }

    /**
     * Get the incoming player's duration during a crossfade.
     */
    fun getIncomingDuration(delegatingPlayer: CrossfadeDelegatingPlayer): Long {
        if (!isTransitioning()) return -1L
        val dur = delegatingPlayer.inactivePlayer.duration
        return if (dur > 0) dur else -1L
    }

    // ── EQ shaping (bass boost on outgoing, high cut for warm receding feel) ──

    private fun applyEq(
        player: ExoPlayer,
        progress: Float,
        outgoing: Boolean,
        primaryChain: Boolean
    ) {
        val eq = ensureEq(player, primaryChain) ?: return
        val blend = if (outgoing) progress else (1f - progress)
        val range = eq.bandLevelRange
        if (range == null || range.size < 2) return

        val min = range[0].toInt()
        val max = range[1].toInt()
        val bassBand = 0.toShort()
        val highBand = (eq.numberOfBands.toInt() - 1).coerceAtLeast(0).toShort()

        val bassBoost = (180f * blend).toInt().coerceIn(min, max).toShort()
        val highCut = (-500f * blend).toInt().coerceIn(min, max).toShort()

        runCatching {
            eq.setBandLevel(bassBand, bassBoost)
            eq.setBandLevel(highBand, highCut)
        }
    }

    private fun ensureEq(player: ExoPlayer, primaryChain: Boolean): Equalizer? {
        val sessionId = player.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return null

        if (primaryChain) {
            if (outgoingEq != null && outgoingEqSession == sessionId) return outgoingEq
        } else {
            if (incomingEq != null && incomingEqSession == sessionId) return incomingEq
        }

        val created = runCatching {
            Equalizer(0, sessionId).apply { enabled = true }
        }.getOrNull() ?: return null

        if (primaryChain) {
            outgoingEq?.release()
            outgoingEq = created
            outgoingEqSession = sessionId
        } else {
            incomingEq?.release()
            incomingEq = created
            incomingEqSession = sessionId
        }
        return created
    }

    private fun releaseEqs() {
        runCatching { outgoingEq?.release() }
        runCatching { incomingEq?.release() }
        outgoingEq = null
        incomingEq = null
        outgoingEqSession = null
        incomingEqSession = null
    }

    private fun abortTransition(
        state: TransitionState,
        commitToIncoming: Boolean
    ) {
        val delegatingPlayer = state.delegatingPlayer
        val outgoing = state.outgoing
        val incoming = state.incoming

        if (commitToIncoming && state.uiSwitchedToIncoming) {
            runCatching {
                incoming.volume = 1f
                outgoing.volume = 0f
                if (delegatingPlayer.activePlayer !== incoming) {
                    delegatingPlayer.swapActivePlayer()
                }
                if (!delegatingPlayer.activePlayer.isPlaying) {
                    delegatingPlayer.activePlayer.play()
                }
            }
            return
        }

        runCatching {
            incoming.pause()
            incoming.stop()
            incoming.clearMediaItems()
        }
        runCatching {
            outgoing.volume = 1f
        }
    }

    private fun ensureSingleActivePlayback(delegatingPlayer: CrossfadeDelegatingPlayer) {
        val active = delegatingPlayer.activePlayer
        val inactive = delegatingPlayer.inactivePlayer

        runCatching {
            inactive.pause()
            inactive.stop()
            inactive.clearMediaItems()
        }
        runCatching {
            active.volume = 1f
        }
    }

    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}
