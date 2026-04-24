package com.proj.Musicality.crossfade

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Data the ViewModel provides when the next track is ready for crossfade.
 */
data class CrossfadeNextTrack(
    val mediaItem: MediaItem,
    val onCrossfadeStart: suspend () -> Unit,
    val onCrossfadeComplete: suspend () -> Unit
)

/**
 * Dual-player crossfade with DSP shaping, no handoff.
 *
 * Architecture:
 *   Track A plays on activePlayer (processor A: gain=1, bypass filter)
 *   Track B preloads on inactivePlayer (processor B primed: gain=0, HPF @ ~180 Hz)
 *   Trigger:
 *     outgoing: gain ramps down (equal-power cos), LPF cutoff sweeps 20 kHz → ~500 Hz (log)
 *     incoming: gain ramps up   (equal-power sin), HPF cutoff sweeps ~180 Hz → 20 Hz (log)
 *   Swap: incoming becomes active, both processors reset to idle (unity, bypass)
 *
 *   Timeline:
 *     A ──────────────┐
 *                     ├── overlap (fade A down + LPF sweep; fade B up + HPF sweep)
 *     B ──────────────┘── continues playing
 *
 *   Why this shape, musically:
 *     - Equal-power cos/sin keeps the perceived loudness of the overlap constant, avoiding the
 *       dip you get with a linear cross (which sums to 0.707 at the midpoint and sounds like a
 *       volume drop).
 *     - Low-passing the outgoing while it fades simulates how far sounds lose their highs to air
 *       absorption — "fading into the distance" feels natural, whereas a flat-spectrum fade
 *       feels like someone reached for the volume knob.
 *     - A gentle high-pass on the incoming during the first ~half of the overlap prevents the
 *       two tracks' low frequencies from summing incoherently. Two bass lines at different keys
 *       will phase-cancel at their fundamentals (comb filtering), giving a muddy "phasing"
 *       sound for a few hundred ms. The HPF keeps the incoming's bass out of the way until the
 *       outgoing is already mostly gone.
 */
class SimpleCrossfadeManager(@Suppress("UNUSED_PARAMETER") private val context: Context) {

    companion object {
        private const val TAG = "SimpleCrossfade"
        private const val FADE_DURATION_MS = 6000L
        private const val TRIGGER_LEAD_MS = 6200L
        private const val STEP_MS = 40L

        // DSP sweep endpoints.
        private const val OUTGOING_LPF_START_HZ = 20_000f
        private const val OUTGOING_LPF_END_HZ = 500f
        private const val INCOMING_HPF_START_HZ = 180f
        private const val INCOMING_HPF_END_HZ = 20f

        /**
         * Fraction of the fade over which the incoming HPF opens back up. After this point the
         * incoming is full-spectrum; the rest of the fade is pure gain ramp. Shorter value =
         * quicker return of incoming bass; longer = more frequency separation during overlap.
         */
        private const val INCOMING_HPF_OPEN_FRACTION = 0.6f
    }

    private var enabled = false
    private var transitionJob: Job? = null
    private var lastTriggeredTrackId: String? = null
    private var transitionState: TransitionState? = null

    private data class TransitionState(
        val delegatingPlayer: CrossfadeDelegatingPlayer,
        val outgoing: ExoPlayer,
        val incoming: ExoPlayer,
        val outgoingProc: CrossfadeAudioProcessor,
        val incomingProc: CrossfadeAudioProcessor,
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
     * The core crossfade: equal-power gain ramps paired with complementary biquad sweeps.
     *
     * 1. Prime both processors: outgoing stays at (gain=1, bypass); incoming is armed at
     *    (gain=0, HPF @ 180 Hz).
     * 2. Preload B on inactive player; start playback (still silent since processor gain=0).
     * 3. Step every STEP_MS: update both processors' target gain and filter cutoff.
     * 4. When ramp completes: swap active player, stop the old one, reset both processors.
     */
    private suspend fun executeCrossfade(
        delegatingPlayer: CrossfadeDelegatingPlayer,
        nextTrack: CrossfadeNextTrack
    ) {
        val outgoing = delegatingPlayer.activePlayer
        val incoming = delegatingPlayer.inactivePlayer
        val outgoingProc = delegatingPlayer.processorFor(outgoing)
        val incomingProc = delegatingPlayer.processorFor(incoming)

        val state = TransitionState(
            delegatingPlayer = delegatingPlayer,
            outgoing = outgoing,
            incoming = incoming,
            outgoingProc = outgoingProc,
            incomingProc = incomingProc
        )
        transitionState = state

        try {
            // Prime processors to their crossfade-start states.
            // Outgoing is already in its "playing normally" state; be explicit anyway.
            outgoingProc.setGainTarget(1f)
            outgoingProc.setFilter(CrossfadeAudioProcessor.FilterMode.BYPASS, OUTGOING_LPF_START_HZ)

            incomingProc.setGainTarget(0f)
            incomingProc.setFilter(CrossfadeAudioProcessor.FilterMode.HIGHPASS, INCOMING_HPF_START_HZ)

            // Keep ExoPlayer.volume at 1 — all gain is now in the processor.
            outgoing.volume = 1f
            incoming.volume = 1f

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

            // Equal-power crossfade ramp with complementary filter sweep.
            val steps = (FADE_DURATION_MS / STEP_MS).toInt().coerceAtLeast(1)
            val halfPi = (Math.PI / 2.0).toFloat()

            repeat(steps) { index ->
                val progress = ((index + 1).toFloat() / steps.toFloat()).coerceIn(0f, 1f)

                // Equal-power curves (sin² + cos² = 1 → constant summed power)
                val outGain = cos(progress * halfPi).coerceIn(0f, 1f)
                val inGain = sin(progress * halfPi).coerceIn(0f, 1f)

                // Logarithmic cutoff sweeps — frequency is perceptually logarithmic
                val outCutoff = logSweep(OUTGOING_LPF_START_HZ, OUTGOING_LPF_END_HZ, progress)
                val hpfProgress =
                    (progress / INCOMING_HPF_OPEN_FRACTION).coerceIn(0f, 1f)
                val inCutoff = logSweep(INCOMING_HPF_START_HZ, INCOMING_HPF_END_HZ, hpfProgress)

                outgoingProc.setGainTarget(outGain)
                outgoingProc.setFilter(
                    CrossfadeAudioProcessor.FilterMode.LOWPASS,
                    outCutoff
                )

                incomingProc.setGainTarget(inGain)
                // Once the HPF has opened all the way, flip to BYPASS for phase-clean signal.
                if (hpfProgress >= 1f) {
                    incomingProc.setFilter(
                        CrossfadeAudioProcessor.FilterMode.BYPASS,
                        INCOMING_HPF_END_HZ
                    )
                } else {
                    incomingProc.setFilter(
                        CrossfadeAudioProcessor.FilterMode.HIGHPASS,
                        inCutoff
                    )
                }

                delay(STEP_MS)
            }

            // Fade complete: pin incoming at full spectrum + unity, outgoing at silence.
            incomingProc.resetToIdle()
            outgoingProc.setGainTarget(0f)

            // Swap: incoming becomes the active player, outgoing stops
            delegatingPlayer.swapActivePlayer()

            // Former outgoing is now idle — leave it ready for next turn
            outgoingProc.resetToIdle()

            Log.d(TAG, "Crossfade complete. New active position: ${delegatingPlayer.activePlayer.currentPosition}ms")

            nextTrack.onCrossfadeComplete()
        } catch (cancelled: CancellationException) {
            abortTransition(state, commitToIncoming = state.uiSwitchedToIncoming)
            throw cancelled
        } finally {
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

    /**
     * Exponential interpolation from `start` to `end` — progress in [0, 1] moves the returned
     * value linearly in log-frequency space. This is how the ear perceives pitch / cutoff.
     */
    private fun logSweep(start: Float, end: Float, t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        val lnStart = ln(start.toDouble())
        val lnEnd = ln(end.toDouble())
        return exp(lnStart + (lnEnd - lnStart) * clamped).toFloat()
    }

    private fun abortTransition(
        state: TransitionState,
        commitToIncoming: Boolean
    ) {
        val delegatingPlayer = state.delegatingPlayer
        val outgoing = state.outgoing
        val incoming = state.incoming
        val outgoingProc = state.outgoingProc
        val incomingProc = state.incomingProc

        if (commitToIncoming && state.uiSwitchedToIncoming) {
            runCatching {
                incomingProc.resetToIdle()
                outgoingProc.setGainTarget(0f)
                if (delegatingPlayer.activePlayer !== incoming) {
                    delegatingPlayer.swapActivePlayer()
                }
                outgoingProc.resetToIdle()
                if (!delegatingPlayer.activePlayer.isPlaying) {
                    delegatingPlayer.activePlayer.play()
                }
            }
            return
        }

        // Revert: kill incoming, bring outgoing back to unity.
        runCatching {
            incoming.pause()
            incoming.stop()
            incoming.clearMediaItems()
            incomingProc.resetToIdle()
        }
        runCatching {
            outgoingProc.resetToIdle()
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
            delegatingPlayer.processorFor(inactive).resetToIdle()
        }
        runCatching {
            delegatingPlayer.processorFor(active).resetToIdle()
            active.volume = 1f
        }
    }
}
