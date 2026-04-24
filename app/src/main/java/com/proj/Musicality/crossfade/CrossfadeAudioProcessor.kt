package com.proj.Musicality.crossfade

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * An ExoPlayer [BaseAudioProcessor] that applies, in order:
 *
 *  1. An RMS meter tap on the raw input (for loudness metering / match-gain decisions)
 *  2. A per-channel biquad IIR filter (low-pass OR high-pass OR bypass, time-varying cutoff)
 *  3. A target gain with per-sample smoothing (zipper-free amplitude ramps)
 *  4. A static dB trim (loudness-match compensation)
 *
 * All parameter setters are thread-safe: they write `@Volatile` primitives that the audio thread
 * reads at the start of every [queueInput] call. Coefficient updates are computed on the audio
 * thread the first time they're observed, so we do exactly one biquad re-design per change.
 *
 * The processor only activates on 16-bit interleaved PCM. Any other encoding makes it transparent
 * (returns `NOT_SET` from `onConfigure`), so it can't corrupt float or passthrough streams.
 */
@UnstableApi
class CrossfadeAudioProcessor : BaseAudioProcessor() {

    enum class FilterMode { BYPASS, LOWPASS, HIGHPASS }

    private data class FilterRequest(val mode: FilterMode, val cutoffHz: Float, val q: Float)

    // ── Parameters (written from any thread, read from audio thread) ──
    @Volatile private var targetGain: Float = 1f
    @Volatile private var trimLinear: Float = 1f
    @Volatile private var filterRequest: FilterRequest =
        FilterRequest(FilterMode.BYPASS, 20_000f, BiquadCoefficients.DEFAULT_Q)

    // ── Audio-thread-only state ──
    private var currentGain: Float = 1f
    /** One-pole smoothing coefficient: gain reaches ~63 % of target in ~10 ms. */
    private var smoothingAlpha: Float = 0f
    private var appliedFilter: FilterRequest = filterRequest
    private var currentCoeffs: BiquadCoefficients = BiquadCoefficients.BYPASS
    private var channelStates: Array<BiquadState> = emptyArray()
    private var sampleRate: Int = 0
    private var channelCount: Int = 0

    // ── RMS meter (sliding window, single running sum) ──
    private var rmsSum: Float = 0f
    private var rmsFramesAccumulated: Int = 0
    private var rmsWindowFrames: Int = DEFAULT_RMS_WINDOW_FRAMES
    @Volatile private var recentRmsLinear: Float = 0f

    // ── Public API (call from any thread) ──

    /** Target playback gain (0..1 typical; up to 2 allowed for headroom). Smoothed per sample. */
    fun setGainTarget(gain: Float) {
        targetGain = gain.coerceIn(0f, 2f)
    }

    /** Static gain trim in decibels — use for loudness match between outgoing and incoming. */
    fun setTrimDb(db: Float) {
        trimLinear = 10f.pow(db.coerceIn(-24f, 24f) / 20f)
    }

    /** Switch filter mode and cutoff. Coefficient recalculation happens on the audio thread. */
    fun setFilter(mode: FilterMode, cutoffHz: Float, q: Float = BiquadCoefficients.DEFAULT_Q) {
        filterRequest = FilterRequest(
            mode = mode,
            cutoffHz = cutoffHz.coerceIn(20f, 20_000f),
            q = q.coerceAtLeast(0.1f)
        )
    }

    /** Return to a transparent unity-gain pass-through — the state a "playing normally" track expects. */
    fun resetToIdle() {
        setGainTarget(1f)
        setTrimDb(0f)
        setFilter(FilterMode.BYPASS, 20_000f)
    }

    /** Current short-term RMS in dBFS (−∞..0). Returns -160 if no audio has been measured. */
    fun getRmsDb(): Float =
        20f * log10(max(recentRmsLinear, 1e-8f))

    /** Linear amplitude RMS of the most recent window (0..1). */
    fun getRmsLinear(): Float = recentRmsLinear

    // ── AudioProcessor lifecycle ──

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            // Silently fall back — processor becomes inactive and the sink bypasses it.
            return AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        channelStates = Array(channelCount) { BiquadState() }

        // Target ~10 ms time-constant for gain smoothing regardless of sample rate.
        smoothingAlpha = (1.0 - exp(-1.0 / (0.010 * sampleRate))).toFloat()

        // RMS window ≈ 200 ms.
        rmsWindowFrames = (sampleRate * 0.2f).toInt().coerceAtLeast(1024)

        currentGain = targetGain
        currentCoeffs = resolveCoefficients(filterRequest)
        appliedFilter = filterRequest

        return inputAudioFormat
    }

    override fun onFlush() {
        channelStates.forEach { it.reset() }
        currentGain = targetGain
        rmsSum = 0f
        rmsFramesAccumulated = 0
    }

    override fun onReset() {
        channelStates = emptyArray()
        sampleRate = 0
        channelCount = 0
        recentRmsLinear = 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesAvailable = inputBuffer.remaining()
        if (bytesAvailable == 0) return
        val channels = channelCount
        if (channels == 0) return

        // Pick up any requested filter change once per call.
        val pending = filterRequest
        if (pending !== appliedFilter && pending != appliedFilter) {
            currentCoeffs = resolveCoefficients(pending)
            appliedFilter = pending
        }

        val frameBytes = 2 * channels
        val frameCount = bytesAvailable / frameBytes
        if (frameCount == 0) return

        val output = replaceOutputBuffer(frameCount * frameBytes)

        val coeffs = currentCoeffs
        val b0 = coeffs.b0; val b1 = coeffs.b1; val b2 = coeffs.b2
        val a1 = coeffs.a1; val a2 = coeffs.a2
        val bypass = coeffs.isBypass

        val targetTotal = targetGain * trimLinear
        val alpha = smoothingAlpha
        var gain = currentGain

        var localRmsSum = rmsSum
        var rmsFrames = rmsFramesAccumulated
        val rmsWindow = rmsWindowFrames

        val basePos = inputBuffer.position()
        var readPos = basePos

        val invScale = 1f / 32768f

        var frame = 0
        while (frame < frameCount) {
            // Smooth gain toward target once per frame (uniform across channels).
            gain += (targetTotal - gain) * alpha

            var ch = 0
            while (ch < channels) {
                val raw = inputBuffer.getShort(readPos).toInt()
                readPos += 2
                val x = raw * invScale

                // Pre-filter, pre-gain RMS tap.
                localRmsSum += x * x

                val y: Float = if (bypass) {
                    x
                } else {
                    val st = channelStates[ch]
                    val out = b0 * x + b1 * st.x1 + b2 * st.x2 - a1 * st.y1 - a2 * st.y2
                    st.x2 = st.x1; st.x1 = x
                    st.y2 = st.y1; st.y1 = out
                    out
                }

                val scaled = (y * gain) * 32768f
                val clamped = when {
                    scaled >  32767f ->  32767f
                    scaled < -32768f -> -32768f
                    else -> scaled
                }
                output.putShort(clamped.toInt().toShort())
                ch++
            }

            rmsFrames++
            if (rmsFrames >= rmsWindow) {
                val avg = localRmsSum / (rmsWindow * channels)
                recentRmsLinear = if (avg > 0f) sqrt(avg) else 0f
                localRmsSum = 0f
                rmsFrames = 0
            }
            frame++
        }

        rmsSum = localRmsSum
        rmsFramesAccumulated = rmsFrames
        currentGain = gain

        inputBuffer.position(readPos)
        output.flip()
    }

    private fun resolveCoefficients(request: FilterRequest): BiquadCoefficients =
        when (request.mode) {
            FilterMode.BYPASS -> BiquadCoefficients.BYPASS
            FilterMode.LOWPASS -> BiquadCoefficients.lowpass(sampleRate, request.cutoffHz, request.q)
            FilterMode.HIGHPASS -> BiquadCoefficients.highpass(sampleRate, request.cutoffHz, request.q)
        }

    companion object {
        private const val DEFAULT_RMS_WINDOW_FRAMES = 8820 // ≈ 200 ms at 44.1 kHz fallback
    }
}
