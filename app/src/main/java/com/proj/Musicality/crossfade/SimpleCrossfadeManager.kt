package com.proj.Musicality.crossfade

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
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
 * Dual-player crossfade with sample-accurate DSP shaping, loudness matching, peak-aware
 * clip protection, silence-trimming, stereo width modulation, and pause-aware progress.
 * Designed to make any two tracks blend seamlessly — regardless of mastering level, key,
 * density, or leading/trailing silence — without server-side metadata.
 *
 * Pipeline per transition:
 *
 *     0. Pre-trigger sampling (continuously during the last ~12 s of the outgoing)
 *         · Each [monitor] tick samples outgoing K-weighted RMS into a 30-element history.
 *         · The history feeds two decisions: trailing-silence detection (early trigger) and
 *           the longer-window outgoing loudness reference for the trim calculation.
 *
 *     1. Trigger
 *         · Normal: remaining ≤ TRIGGER_LEAD_MS (~6.7 s).
 *         · Early: remaining ≤ MAX_EARLY_TRIGGER_LEAD_MS (~14 s) AND the outgoing has gone
 *           sustained quiet for ≥ OUTGOING_QUIET_DURATION_MS — reclaims fade time wasted on
 *           reverb tails / outro silence.
 *
 *     2. Pre-roll (up to MAX_PRE_ROLL_MS while the incoming is still silent)
 *         · Incoming plays at gain = 0 (silent) so its K-weighted RMS can settle.
 *         · Settle window allows the K-weighting biquads to fill.
 *         · While loudness < LEADING_SILENCE_FLOOR, keep waiting up to a cap — this absorbs
 *           leading silence on the incoming and aligns the fade with the actual musical start.
 *         · Once audio is detected (or the cap is hit), take PRE_ROLL_SAMPLES readings,
 *           compute matched trim from the longer outgoing window + the pre-roll samples,
 *           guard the trim against peak clipping, and apply.
 *
 *     3. Fade
 *         · Outgoing: equal-power gain ramp + log LPF sweep 20 kHz → 150 Hz + stereo narrow 1 → 0.
 *         · Incoming: equal-power gain ramp + log HPF sweep 220 → 30 Hz (over first 85 %)
 *                     + stereo open 0.55 → 1.
 *         · Tukey-shaped progress (15 % taper) eases the very first and last moments.
 *         · Progress is driven by accumulated *active* time — if the user pauses, the fade
 *           pauses too; on resume the gain picks up exactly where it left off.
 *
 *     4. Swap + drift
 *         · delegatingPlayer.swapActivePlayer() flips the active player atomically.
 *         · The (now active) processor's trim drifts back to 0 dB over TRIM_DECAY_MS so the
 *           new track ends up at its native level without an audible step.
 */
class SimpleCrossfadeManager(@Suppress("UNUSED_PARAMETER") private val context: Context) {

    companion object {
        private const val TAG = "SimpleCrossfade"

        // Overall timing.
        private const val FADE_DURATION_MS = 6_000L
        private const val PRE_ROLL_MS = 500L
        private const val TRIGGER_LEAD_MS = FADE_DURATION_MS + PRE_ROLL_MS + 200L  // 6700
        private const val STEP_MS = 20L
        private const val PRE_ROLL_SETTLE_MS = 200L
        private const val PRE_ROLL_SAMPLES = 3
        private const val PRE_ROLL_SAMPLE_INTERVAL_MS = 100L

        // Leading-silence handling. Extend the pre-roll until the incoming K-weighted RMS
        // crosses LEADING_SILENCE_FLOOR, but cap at MAX_PRE_ROLL_MS so a fully-silent file
        // can't stall the fade. The cap is sized so the fade still fits inside TRIGGER_LEAD_MS:
        // MAX_PRE_ROLL_MS + FADE_DURATION_MS ≤ TRIGGER_LEAD_MS − safety.
        private const val MAX_PRE_ROLL_MS = 2_500L
        private const val LEADING_SILENCE_FLOOR = 0.005f  // ≈ −46 dBFS K-weighted

        // Trailing-silence early-trigger. We track outgoing K-weighted RMS in a sliding history
        // during the last MAX_EARLY_TRIGGER_LEAD_MS of the song. If it stays below
        // OUTGOING_QUIET_THRESHOLD for OUTGOING_QUIET_DURATION_MS, we fire the crossfade
        // earlier — turning a "fade-into-silence" tail into reclaimed transition time.
        private const val MAX_EARLY_TRIGGER_LEAD_MS = 14_000L
        private const val OUTGOING_QUIET_THRESHOLD = 0.01f   // ≈ −40 dBFS K-weighted
        private const val OUTGOING_QUIET_DURATION_MS = 800L
        // Only consider history collected while we were actually near the end (avoid being
        // tripped by quiet bridges in the middle of a song).
        private const val LOUDNESS_HISTORY_MAX = 40           // 40 × 300 ms polling ≈ 12 s

        // Peak headroom: cap matched trim so trim·peak ≤ this, leaving a small safety margin
        // below 0 dBFS. 0.94 ≈ −0.5 dBFS — generous enough to absorb intra-sample peaks while
        // staying audibly transparent.
        private const val PEAK_HEADROOM = 0.94f

        // Filter sweep endpoints — corrected from previous (500 / 20 Hz) values.
        // LPF down to 150 Hz strips everything but sub-bass from the outgoing.
        // HPF stays active until 85 % of the fade — by then the outgoing is at ≈ −13 dB so
        // its bass no longer competes. The HPF endpoint at 30 Hz is essentially bypass in the
        // audible range (−3 dB at 30 Hz, −0.3 dB at 100 Hz), so the final BYPASS flip below
        // changes the spectrum negligibly and avoids any audible "bass bloom."
        private const val OUTGOING_LPF_START_HZ = 20_000f
        private const val OUTGOING_LPF_END_HZ = 150f
        private const val INCOMING_HPF_START_HZ = 220f
        private const val INCOMING_HPF_END_HZ = 30f
        private const val INCOMING_HPF_OPEN_FRACTION = 0.85f

        // Stereo-width envelope: outgoing narrows to mono, incoming opens from narrowed.
        private const val OUTGOING_WIDTH_START = 1.0f
        private const val OUTGOING_WIDTH_END = 0.0f
        private const val INCOMING_WIDTH_START = 0.55f
        private const val INCOMING_WIDTH_END = 1.0f
        private const val INCOMING_WIDTH_OPEN_FRACTION = 0.8f

        // Loudness matching. We clamp the trim to ±this to avoid wildly amplifying very
        // quiet intros (e.g. a piano lead-in) or attenuating a very loud incoming.
        private const val LOUDNESS_TRIM_MAX_DB = 6.0f
        // Below this short-term K-weighted reading the measurement is treated as silence and
        // discarded — so a quiet intro doesn't trigger a +6 dB boost.
        private const val LOUDNESS_FLOOR_LINEAR = 1e-4f
        // After the swap, the matched trim drifts back to 0 dB so the new track ends up at
        // its native level. At τ = 5 s the worst-case 6 dB trim returns at ~1.2 dB/s — slow
        // enough to fall below the casual-listener detection threshold for gradual loudness
        // change, while still settling to within ~0.3 dB of native after ~15 s.
        private const val TRIM_DECAY_MS = 5_000f
        // During the fade we want the trim to be effectively instant; 80 ms keeps it audibly
        // snappy while avoiding any zipper artifacts.
        private const val TRIM_SET_MS = 80f

        // Tukey-window taper applied to the [0, 1] progress so the gain ramp eases in/out.
        private const val TUKEY_TAPER = 0.15f
    }

    private var enabled = false
    private var transitionJob: Job? = null
    private var lastTriggeredTrackId: String? = null
    private var transitionState: TransitionState? = null

    /**
     * Rolling history of (elapsedRealtimeMs, K-weighted RMS linear) sampled by [monitor] in
     * the last ~12 s of the outgoing. Used for trailing-silence detection and as the longer
     * outgoing reference for loudness matching.
     */
    private val loudnessHistory: ArrayDeque<LoudnessSample> = ArrayDeque(LOUDNESS_HISTORY_MAX)
    private var loudnessHistoryTrackId: String? = null

    private data class LoudnessSample(val realtimeMs: Long, val kWeightedLinear: Float)

    private data class TransitionState(
        val delegatingPlayer: CrossfadeDelegatingPlayer,
        val outgoing: ExoPlayer,
        val incoming: ExoPlayer,
        val outgoingProc: CrossfadeAudioProcessor,
        val incomingProc: CrossfadeAudioProcessor,
        var uiSwitchedToIncoming: Boolean = false,
        var appliedTrimDb: Float = 0f
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

    fun monitor(
        scope: CoroutineScope,
        trackId: String?,
        hasNext: Boolean,
        positionMs: Long,
        durationMs: Long,
        delegatingPlayer: CrossfadeDelegatingPlayer,
        repeatMode: Int = Player.REPEAT_MODE_OFF,
        prepareNext: suspend () -> CrossfadeNextTrack?
    ) {
        if (!enabled || !hasNext || trackId.isNullOrBlank()) return
        if (transitionJob != null || durationMs <= 0L) return
        if (trackId == lastTriggeredTrackId) return
        if (repeatMode == Player.REPEAT_MODE_ONE) return

        val remaining = durationMs - positionMs

        // Sample outgoing K-weighted RMS while we're in the danger zone — even if we don't
        // trigger this tick. The history feeds the trim calculation and the silence check.
        sampleOutgoingLoudness(trackId, remaining, delegatingPlayer)

        val normalTrigger = remaining <= TRIGGER_LEAD_MS
        val earlyTrigger = !normalTrigger &&
            remaining <= MAX_EARLY_TRIGGER_LEAD_MS &&
            isOutgoingSustainedQuiet()
        if (!normalTrigger && !earlyTrigger) return
        if (earlyTrigger) {
            Log.d(TAG, "Early trigger: outgoing sustained quiet, remaining=${remaining}ms")
        }

        val job = scope.launch(Dispatchers.Main) {
            try {
                val nextTrack = prepareNext()
                if (nextTrack != null) {
                    lastTriggeredTrackId = trackId
                    executeCrossfade(delegatingPlayer, nextTrack)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Crossfade cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Crossfade failed", e)
            }
        }
        transitionJob = job
        job.invokeOnCompletion { if (transitionJob === job) transitionJob = null }
    }

    /**
     * Track outgoing loudness during the last ~12 s of a track. We only sample inside this
     * window so a quiet bridge mid-song can't be mistaken for an outro. The history is keyed
     * to the track id so it resets cleanly on track change.
     */
    private fun sampleOutgoingLoudness(
        trackId: String,
        remainingMs: Long,
        delegatingPlayer: CrossfadeDelegatingPlayer
    ) {
        if (remainingMs > MAX_EARLY_TRIGGER_LEAD_MS) {
            if (loudnessHistoryTrackId != trackId) {
                loudnessHistory.clear()
                loudnessHistoryTrackId = trackId
            }
            return
        }
        if (loudnessHistoryTrackId != trackId) {
            loudnessHistory.clear()
            loudnessHistoryTrackId = trackId
        }
        val processor = delegatingPlayer.processorFor(delegatingPlayer.activePlayer)
        loudnessHistory.addLast(
            LoudnessSample(SystemClock.elapsedRealtime(), processor.getKWeightedRmsLinear())
        )
        while (loudnessHistory.size > LOUDNESS_HISTORY_MAX) loudnessHistory.removeFirst()
    }

    private fun isOutgoingSustainedQuiet(): Boolean {
        if (loudnessHistory.size < 3) return false
        val cutoff = SystemClock.elapsedRealtime() - OUTGOING_QUIET_DURATION_MS
        // Find the oldest sample still within the quiet-duration window.
        val recent = loudnessHistory.filter { it.realtimeMs >= cutoff }
        if (recent.size < 3) return false
        return recent.all { it.kWeightedLinear < OUTGOING_QUIET_THRESHOLD }
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
        loudnessHistory.clear()
        loudnessHistoryTrackId = null
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
     * The core crossfade:
     *   1. Prime processors → preload + silent-start incoming → measure both tracks' loudness.
     *   2. Apply matched trim to incoming.
     *   3. Run the gain / filter / stereo-width envelope, driven by wall-clock progress.
     *   4. Swap active player and drift trim back to 0 dB on the new active track.
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
            // ── Prime processors ──
            // Outgoing stays at unity / bypass / full stereo (its current "playing" state).
            outgoingProc.setGainTarget(1f)
            outgoingProc.setFilter(CrossfadeAudioProcessor.FilterMode.BYPASS, OUTGOING_LPF_START_HZ)
            outgoingProc.setStereoWidth(OUTGOING_WIDTH_START)

            // Incoming armed at silence + HPF + slightly narrowed.
            incomingProc.setGainTarget(0f)
            incomingProc.setTrimDb(0f)
            incomingProc.setStereoWidth(INCOMING_WIDTH_START)
            incomingProc.setFilter(CrossfadeAudioProcessor.FilterMode.HIGHPASS, INCOMING_HPF_START_HZ)
            incomingProc.resetLoudnessMeter()

            outgoing.volume = 1f
            incoming.volume = 1f

            incoming.setMediaItem(nextTrack.mediaItem)
            incoming.prepare()
            incoming.play()

            // Wait for incoming to actually start producing audio.
            var waited = 0L
            while (!incoming.isPlaying && waited < 3_000L) {
                delay(STEP_MS)
                waited += STEP_MS
            }
            if (!incoming.isPlaying) {
                Log.e(TAG, "Incoming player failed to start, aborting crossfade")
                incoming.stop()
                return
            }

            // ── Pre-roll: settle, optionally absorb leading silence, then sample ──
            // The K-weighting biquads need ~150 ms to fill, so we settle first. Then, if the
            // incoming starts with quiet (silent intro or long fade-in), keep waiting (still
            // silent — gain is 0) until we either detect audio or hit MAX_PRE_ROLL_MS. This
            // shifts the fade so it aligns with the actual musical start of the new track.
            delay(PRE_ROLL_SETTLE_MS)

            var preRollWaitedMs = PRE_ROLL_SETTLE_MS
            while (
                preRollWaitedMs < MAX_PRE_ROLL_MS &&
                incomingProc.getKWeightedRmsLinear() < LEADING_SILENCE_FLOOR
            ) {
                delay(STEP_MS)
                preRollWaitedMs += STEP_MS
            }
            if (preRollWaitedMs > PRE_ROLL_SETTLE_MS + 50L) {
                Log.d(TAG, "Pre-roll extended to ${preRollWaitedMs}ms (leading silence on incoming)")
            }

            val outgoingSamples = mutableListOf<Float>()
            val incomingSamples = mutableListOf<Float>()
            repeat(PRE_ROLL_SAMPLES) {
                outgoingSamples += outgoingProc.getKWeightedRmsLinear()
                incomingSamples += incomingProc.getKWeightedRmsLinear()
                delay(PRE_ROLL_SAMPLE_INTERVAL_MS)
            }

            val incomingPeak = incomingProc.getRecentPeakLinear()
            val matchedTrimDb = computeLoudnessTrim(
                preRollOutgoing = outgoingSamples,
                preRollIncoming = incomingSamples,
                historicalOutgoing = loudnessHistory.map { it.kWeightedLinear },
                incomingPeak = incomingPeak
            )
            if (matchedTrimDb != 0f) {
                incomingProc.setTrimDb(matchedTrimDb, rampMs = TRIM_SET_MS)
                state.appliedTrimDb = matchedTrimDb
                Log.d(TAG, "Loudness trim applied to incoming: ${"%.2f".format(matchedTrimDb)} dB")
            }

            // ── UI switches to next song ──
            nextTrack.onCrossfadeStart()
            state.uiSwitchedToIncoming = true

            // ── Envelope driven by accumulated *active* playback time ──
            // We progress the fade only while the user wants playback. If the user pauses
            // mid-fade, wall-clock time keeps advancing but the audio doesn't — the
            // accumulator freezes, the incoming is paused to stay in lock-step, and on resume
            // both sides pick up exactly where they were. This makes mid-fade pause behave
            // like a normal pause anywhere else in the track.
            //
            // We key off [Player.getPlayWhenReady] (user intent) rather than [isPlaying]
            // (transport state). The outgoing reaching STATE_ENDED mid-fade is expected by
            // design — TRIGGER_LEAD_MS only barely covers pre-roll + fade, and any leading
            // silence on the incoming consumes more of that budget — so isPlaying would flip
            // false on natural end-of-stream and incorrectly freeze the fade forever.
            val fadeDuration = FADE_DURATION_MS.toFloat()
            val halfPi = (PI / 2.0).toFloat()
            var accumulatedActiveMs = 0L
            var lastTickRealtime = SystemClock.elapsedRealtime()
            var pausedDueToOutgoing = false

            while (true) {
                val now = SystemClock.elapsedRealtime()
                val userWantsPlayback = outgoing.playWhenReady

                if (userWantsPlayback) {
                    if (pausedDueToOutgoing) {
                        // Resume incoming and reset the tick reference — we don't credit the
                        // paused interval against the fade.
                        runCatching { incoming.play() }
                        pausedDueToOutgoing = false
                        lastTickRealtime = now
                    } else {
                        accumulatedActiveMs += (now - lastTickRealtime)
                        lastTickRealtime = now
                    }
                } else {
                    if (!pausedDueToOutgoing) {
                        runCatching { incoming.pause() }
                        pausedDueToOutgoing = true
                    }
                    delay(STEP_MS)
                    continue
                }

                val rawProgress = (accumulatedActiveMs.toFloat() / fadeDuration).coerceIn(0f, 1f)
                val progress = tukey(rawProgress, TUKEY_TAPER)

                val outGain = cos(progress * halfPi).coerceIn(0f, 1f)
                val inGain = sin(progress * halfPi).coerceIn(0f, 1f)

                val outCutoff = logSweep(OUTGOING_LPF_START_HZ, OUTGOING_LPF_END_HZ, progress)

                val hpfProgress = (progress / INCOMING_HPF_OPEN_FRACTION).coerceIn(0f, 1f)
                val inCutoff = logSweep(INCOMING_HPF_START_HZ, INCOMING_HPF_END_HZ, hpfProgress)

                val widthProgress =
                    (progress / INCOMING_WIDTH_OPEN_FRACTION).coerceIn(0f, 1f)
                val outWidth = OUTGOING_WIDTH_START +
                    (OUTGOING_WIDTH_END - OUTGOING_WIDTH_START) * progress
                val inWidth = INCOMING_WIDTH_START +
                    (INCOMING_WIDTH_END - INCOMING_WIDTH_START) * widthProgress

                outgoingProc.setGainTarget(outGain)
                outgoingProc.setStereoWidth(outWidth)
                outgoingProc.setFilter(
                    CrossfadeAudioProcessor.FilterMode.LOWPASS,
                    outCutoff
                )

                incomingProc.setGainTarget(inGain)
                incomingProc.setStereoWidth(inWidth)
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

                if (rawProgress >= 1f) break
                delay(STEP_MS)
            }

            // Pin endpoints exactly so the swap finds them in a known state.
            outgoingProc.setGainTarget(0f)
            outgoingProc.setStereoWidth(OUTGOING_WIDTH_END)
            incomingProc.setGainTarget(1f)
            incomingProc.setStereoWidth(INCOMING_WIDTH_END)
            incomingProc.setFilter(CrossfadeAudioProcessor.FilterMode.BYPASS, INCOMING_HPF_END_HZ)

            // ── Swap and clean up ──
            delegatingPlayer.swapActivePlayer()

            // Former outgoing returns to idle for next reuse.
            outgoingProc.resetToIdle()

            // The new active (former incoming) keeps its trim; we drift it back to 0 dB
            // smoothly so the track ultimately plays at native level. If a new transition
            // starts during the decay, we cancel and overwrite.
            startTrimDecay(incomingProc, state.appliedTrimDb)

            Log.d(
                TAG,
                "Crossfade complete. New active position: ${delegatingPlayer.activePlayer.currentPosition}ms"
            )

            nextTrack.onCrossfadeComplete()
        } catch (cancelled: CancellationException) {
            abortTransition(state, commitToIncoming = state.uiSwitchedToIncoming)
            throw cancelled
        } finally {
            transitionState = null
        }
    }

    fun getIncomingPosition(delegatingPlayer: CrossfadeDelegatingPlayer): Long {
        if (!isTransitioning()) return -1L
        return delegatingPlayer.inactivePlayer.currentPosition.takeIf { it >= 0 } ?: -1L
    }

    fun getIncomingDuration(delegatingPlayer: CrossfadeDelegatingPlayer): Long {
        if (!isTransitioning()) return -1L
        val dur = delegatingPlayer.inactivePlayer.duration
        return if (dur > 0) dur else -1L
    }

    /**
     * Exponential interpolation; progress in [0,1] is linear in log-frequency. The ear
     * perceives an octave step regardless of whether it's 100→200 Hz or 5k→10k, so a
     * straight `start + (end - start) * t` cutoff sweep sounds non-linear; log is.
     */
    private fun logSweep(start: Float, end: Float, t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        val lnStart = ln(start.toDouble())
        val lnEnd = ln(end.toDouble())
        return exp(lnStart + (lnEnd - lnStart) * clamped).toFloat()
    }

    /**
     * Tukey (tapered-cosine) window mapping [0, 1] → [0, 1] with a raised-cosine soft start
     * and end. With [taper] = 0.15 the first and last 15 % of the fade ease in/out, while
     * the middle 70 % is straight linear — perceptually the curve has no hard edges.
     */
    private fun tukey(t: Float, taper: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        if (taper <= 0f) return t

        val edge = taper.coerceIn(0f, 0.5f)
        return when {
            t < edge -> {
                val phase = (t / edge) * PI.toFloat()
                val raised = 0.5f * (1f - cos(phase))
                raised * edge
            }
            t > 1f - edge -> {
                val phase = ((t - (1f - edge)) / edge) * PI.toFloat()
                val raised = 0.5f * (1f + sin(phase - PI.toFloat() / 2f))
                (1f - edge) + raised * edge
            }
            else -> t
        }
    }

    /**
     * Compute the loudness trim that aligns incoming's perceived level with outgoing's.
     *
     * Three signals are blended:
     *   - [preRollOutgoing]: outgoing K-weighted RMS sampled while the incoming was silent.
     *     Reflects the very-recent loudness of the outgoing.
     *   - [historicalOutgoing]: 12 s of outgoing samples collected during [monitor]. Reflects
     *     the longer-window loudness — important for tracks with dynamics or quiet outros.
     *   - [preRollIncoming]: incoming K-weighted RMS sampled during pre-roll. The reference
     *     for the new track.
     *
     * The historical samples are weighted higher than the pre-roll ones when both are
     * available (a 12 s average is more representative than a 300 ms slice). Quiet readings
     * below [LOUDNESS_FLOOR_LINEAR] are discarded so a silent intro / outro can't dominate.
     *
     * The result is then clamped twice:
     *   - To ±[LOUDNESS_TRIM_MAX_DB] so a near-silent reference can't trigger extreme trims.
     *   - To whatever positive trim keeps [incomingPeak] × trim ≤ [PEAK_HEADROOM], which
     *     prevents a +trim from clipping the incoming.
     */
    private fun computeLoudnessTrim(
        preRollOutgoing: List<Float>,
        preRollIncoming: List<Float>,
        historicalOutgoing: List<Float>,
        incomingPeak: Float
    ): Float {
        val validPreOut = preRollOutgoing.filter { it > LOUDNESS_FLOOR_LINEAR }
        val validHistOut = historicalOutgoing.filter { it > LOUDNESS_FLOOR_LINEAR }
        val validIn = preRollIncoming.filter { it > LOUDNESS_FLOOR_LINEAR }
        if (validIn.isEmpty()) return 0f

        // Outgoing reference: 70 % historical, 30 % pre-roll, when both exist.
        val preOutAvg = validPreOut.takeIf { it.isNotEmpty() }?.average()
        val histOutAvg = validHistOut.takeIf { it.isNotEmpty() }?.average()
        val outAvg: Double = when {
            preOutAvg != null && histOutAvg != null -> 0.7 * histOutAvg + 0.3 * preOutAvg
            preOutAvg != null -> preOutAvg
            histOutAvg != null -> histOutAvg
            else -> return 0f
        }
        val inAvg = validIn.average()
        if (outAvg <= 0.0 || inAvg <= 0.0) return 0f

        val ratio = (outAvg / inAvg).toFloat()
        val rawDb = 20f * log10(max(ratio, 1e-6f))
        var clampedDb = rawDb.coerceIn(-LOUDNESS_TRIM_MAX_DB, LOUDNESS_TRIM_MAX_DB)

        // Peak headroom: if a positive trim would push the incoming above the headroom
        // ceiling, reduce it. Negative trims always have headroom.
        if (clampedDb > 0f && incomingPeak > 0f) {
            val maxTrimLinear = PEAK_HEADROOM / incomingPeak
            if (maxTrimLinear > 0f) {
                val maxTrimDb = 20f * log10(maxTrimLinear)
                if (maxTrimDb < clampedDb) {
                    Log.d(
                        TAG,
                        "Peak headroom clamps trim: ${"%.2f".format(clampedDb)} → " +
                            "${"%.2f".format(maxTrimDb)} dB (peak=${"%.3f".format(incomingPeak)})"
                    )
                    clampedDb = maxOf(0f, maxTrimDb)
                }
            }
        }
        return clampedDb
    }

    /**
     * After the swap, slide the matched trim back toward 0 dB on the new active processor.
     * Uses the processor's own per-sample smoothing — manager just sets the target and the
     * audio thread does the work. A subsequent crossfade overwrites the target.
     */
    private fun startTrimDecay(processor: CrossfadeAudioProcessor, fromDb: Float) {
        if (fromDb == 0f) {
            processor.setTrimDb(0f)
            return
        }
        processor.setTrimDb(0f, rampMs = TRIM_DECAY_MS)
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
                incomingProc.setGainTarget(1f)
                incomingProc.setStereoWidth(INCOMING_WIDTH_END)
                incomingProc.setFilter(
                    CrossfadeAudioProcessor.FilterMode.BYPASS,
                    INCOMING_HPF_END_HZ
                )
                outgoingProc.setGainTarget(0f)
                if (delegatingPlayer.activePlayer !== incoming) {
                    delegatingPlayer.swapActivePlayer()
                }
                outgoingProc.resetToIdle()
                if (!delegatingPlayer.activePlayer.isPlaying) {
                    delegatingPlayer.activePlayer.play()
                }
                // Even on an aborted-but-committed transition, smooth the trim back to 0 so
                // the new track doesn't suddenly jump if we partially applied a match.
                startTrimDecay(incomingProc, state.appliedTrimDb)
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
