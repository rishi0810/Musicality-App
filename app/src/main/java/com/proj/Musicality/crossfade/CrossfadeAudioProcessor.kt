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
 * Per-player audio processor: K-weighted loudness meter (BS.1770) → main biquad
 * (LPF / HPF / bypass) → mid/side stereo width → gain (smoothed) → static dB trim.
 *
 * All public setters are thread-safe via @Volatile primitives read once per [queueInput] call.
 * Coefficients are recomputed on the audio thread only when the parameter changes, so a single
 * frame pays for at most one biquad re-design.
 *
 * The processor activates only on 16-bit interleaved PCM; any other encoding makes it transparent
 * (returns NOT_SET from onConfigure), so it cannot corrupt float or passthrough streams.
 */
@UnstableApi
class CrossfadeAudioProcessor : BaseAudioProcessor() {

    enum class FilterMode { BYPASS, LOWPASS, HIGHPASS }

    private data class FilterRequest(val mode: FilterMode, val cutoffHz: Float, val q: Float)

    @Volatile private var targetGain: Float = 1f
    @Volatile private var trimLinearTarget: Float = 1f
    @Volatile private var trimRampMs: Float = 0f
    @Volatile private var targetStereoWidth: Float = 1f
    @Volatile private var filterRequest: FilterRequest =
        FilterRequest(FilterMode.BYPASS, 20_000f, BiquadCoefficients.DEFAULT_Q)

    private var currentGain: Float = 1f
    private var currentStereoWidth: Float = 1f
    private var currentTrimLinear: Float = 1f
    /** One-pole smoothing coefficient: gain reaches ~63 % of target in ~10 ms. */
    private var smoothingAlpha: Float = 0f
    /** Lazily computed from [trimRampMs]; updated when the requested ramp changes. */
    private var trimAlpha: Float = 1f
    private var trimAlphaRampMs: Float = -1f
    private var appliedFilter: FilterRequest = filterRequest
    private var currentCoeffs: BiquadCoefficients = BiquadCoefficients.BYPASS
    private var channelStates: Array<BiquadState> = emptyArray()
    private var sampleRate: Int = 0
    private var channelCount: Int = 0

    // ── Linear RMS meter (pre-filter tap, 200 ms sliding sum) ──
    private var rmsSum: Float = 0f
    private var rmsFramesAccumulated: Int = 0
    private var rmsWindowFrames: Int = DEFAULT_RMS_WINDOW_FRAMES
    @Volatile private var recentRmsLinear: Float = 0f

    // ── K-weighted loudness meter (BS.1770 K-weighting) ──
    // K-weighting = high-shelf (+4 dB @ ~1681 Hz) → RLB high-pass (38 Hz, Q=0.5).
    // Mean-square of the filtered signal is summed over a sliding window. The resulting
    // linear amplitude correlates with short-term LUFS to within a constant; we expose
    // both forms so the manager can pick the most convenient one.
    private var kShelfStates: Array<BiquadState> = emptyArray()
    private var kHpfStates: Array<BiquadState> = emptyArray()
    private var kShelfCoeffs: BiquadCoefficients = BiquadCoefficients.BYPASS
    private var kHpfCoeffs: BiquadCoefficients = BiquadCoefficients.BYPASS
    private var kWeightedSum: Float = 0f
    private var kWeightedFramesAccumulated: Int = 0
    private var kWeightedWindowFrames: Int = DEFAULT_K_WINDOW_FRAMES
    @Volatile private var recentKWeightedLinear: Float = 0f

    // ── Peak meter (raw input, 250 ms sliding window) ──
    // Tracks max(|x|) so the manager can decide whether a positive loudness trim would push
    // the incoming above 0 dBFS. Uses a rolling reservoir of per-segment maxes instead of a
    // single accumulator so the published peak doesn't keep dropping during quiet passages.
    private var peakReservoir: FloatArray = FloatArray(0)
    private var peakReservoirIndex: Int = 0
    private var peakSegmentMax: Float = 0f
    private var peakSegmentFrames: Int = 0
    private var peakSegmentFramesTarget: Int = DEFAULT_PEAK_SEGMENT_FRAMES
    @Volatile private var recentPeakLinear: Float = 0f

    fun setGainTarget(gain: Float) {
        targetGain = gain.coerceIn(0f, 2f)
    }

    /**
     * Static loudness trim. [rampMs] = 0 snaps immediately; > 0 smooths via a one-pole filter,
     * useful for drifting the trim back to unity after a loudness-matched crossfade so the new
     * track eventually plays at its native level without a perceptible step.
     */
    fun setTrimDb(db: Float, rampMs: Float = 0f) {
        trimLinearTarget = 10f.pow(db.coerceIn(-24f, 24f) / 20f)
        trimRampMs = rampMs.coerceAtLeast(0f)
    }

    fun setFilter(mode: FilterMode, cutoffHz: Float, q: Float = BiquadCoefficients.DEFAULT_Q) {
        filterRequest = FilterRequest(
            mode = mode,
            cutoffHz = cutoffHz.coerceIn(20f, 20_000f),
            q = q.coerceAtLeast(0.1f)
        )
    }

    /**
     * Stereo width via Mid/Side:
     *   M = (L + R) / 2, S = (L − R) / 2
     *   L' = M + width·S, R' = M − width·S
     * width = 1 leaves the field unchanged; width = 0 folds the side channel away
     * (mono); values > 1 widen but are clamped here to avoid out-of-phase amplification.
     * Mono streams skip the M/S path entirely.
     */
    fun setStereoWidth(width: Float) {
        targetStereoWidth = width.coerceIn(0f, 2f)
    }

    /** Return to unity gain, bypass filter, full stereo. The default "playing normally" state. */
    fun resetToIdle() {
        setGainTarget(1f)
        setTrimDb(0f)
        setStereoWidth(1f)
        setFilter(FilterMode.BYPASS, 20_000f)
    }

    /** Linear RMS of the raw input (unweighted), 200 ms window. 0..1. */
    fun getRmsLinear(): Float = recentRmsLinear

    /** Linear RMS of K-weighted input, ~300 ms window. Used for loudness-match decisions. */
    fun getKWeightedRmsLinear(): Float = recentKWeightedLinear

    /**
     * Short-term loudness in dBFS-K (≈ short-term LUFS minus the standard −0.691 offset).
     * Comparable between this processor and a sibling instance; the difference is the
     * trim that aligns the two tracks perceptually.
     */
    fun getKWeightedLoudnessDb(): Float =
        20f * log10(max(recentKWeightedLinear, 1e-8f))

    /** Force-clear the K-weighted accumulator so the next reading reflects only fresh audio. */
    fun resetLoudnessMeter() {
        kWeightedSum = 0f
        kWeightedFramesAccumulated = 0
        recentKWeightedLinear = 0f
        kShelfStates.forEach { it.reset() }
        kHpfStates.forEach { it.reset() }
        for (i in peakReservoir.indices) peakReservoir[i] = 0f
        peakSegmentMax = 0f
        peakSegmentFrames = 0
        peakReservoirIndex = 0
        recentPeakLinear = 0f
    }

    /**
     * Max |x| over the last ~250 ms of input, 0..1. Used to detect headroom before a positive
     * loudness trim would push the signal above 0 dBFS.
     */
    fun getRecentPeakLinear(): Float = recentPeakLinear

    // ── AudioProcessor lifecycle ──

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        channelStates = Array(channelCount) { BiquadState() }
        kShelfStates = Array(channelCount) { BiquadState() }
        kHpfStates = Array(channelCount) { BiquadState() }

        smoothingAlpha = (1.0 - exp(-1.0 / (0.010 * sampleRate))).toFloat()
        trimAlpha = computeAlpha(trimRampMs, sampleRate)
        trimAlphaRampMs = trimRampMs

        rmsWindowFrames = (sampleRate * 0.2f).toInt().coerceAtLeast(1024)
        // K-weighting window: shorter so the manager can read a fresh value within ~150 ms.
        kWeightedWindowFrames = (sampleRate * 0.15f).toInt().coerceAtLeast(1024)

        // Peak meter: 5 reservoir slots × ~50 ms = ~250 ms hold time.
        peakSegmentFramesTarget = (sampleRate * 0.05f).toInt().coerceAtLeast(256)
        peakReservoir = FloatArray(PEAK_RESERVOIR_SLOTS)
        peakReservoirIndex = 0
        peakSegmentMax = 0f
        peakSegmentFrames = 0
        recentPeakLinear = 0f

        kShelfCoeffs = BiquadCoefficients.highshelf(sampleRate, 1681.97f, 3.999f)
        kHpfCoeffs = BiquadCoefficients.highpass(sampleRate, 38.135f, 0.5003f)

        currentGain = targetGain
        currentStereoWidth = targetStereoWidth
        currentTrimLinear = trimLinearTarget
        currentCoeffs = resolveCoefficients(filterRequest)
        appliedFilter = filterRequest

        return inputAudioFormat
    }

    override fun onFlush() {
        channelStates.forEach { it.reset() }
        kShelfStates.forEach { it.reset() }
        kHpfStates.forEach { it.reset() }
        currentGain = targetGain
        currentStereoWidth = targetStereoWidth
        currentTrimLinear = trimLinearTarget
        rmsSum = 0f
        rmsFramesAccumulated = 0
        kWeightedSum = 0f
        kWeightedFramesAccumulated = 0
        for (i in peakReservoir.indices) peakReservoir[i] = 0f
        peakSegmentMax = 0f
        peakSegmentFrames = 0
        peakReservoirIndex = 0
        recentPeakLinear = 0f
    }

    override fun onReset() {
        channelStates = emptyArray()
        kShelfStates = emptyArray()
        kHpfStates = emptyArray()
        sampleRate = 0
        channelCount = 0
        recentRmsLinear = 0f
        recentKWeightedLinear = 0f
        peakReservoir = FloatArray(0)
        peakSegmentMax = 0f
        peakSegmentFrames = 0
        peakReservoirIndex = 0
        recentPeakLinear = 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesAvailable = inputBuffer.remaining()
        if (bytesAvailable == 0) return
        val channels = channelCount
        if (channels == 0) return

        val pending = filterRequest
        if (pending !== appliedFilter && pending != appliedFilter) {
            currentCoeffs = resolveCoefficients(pending)
            appliedFilter = pending
        }

        // Recompute trim smoothing coefficient if the requested ramp time changed.
        val pendingRamp = trimRampMs
        if (pendingRamp != trimAlphaRampMs) {
            trimAlpha = computeAlpha(pendingRamp, sampleRate)
            trimAlphaRampMs = pendingRamp
        }

        val frameBytes = 2 * channels
        val frameCount = bytesAvailable / frameBytes
        if (frameCount == 0) return

        val output = replaceOutputBuffer(frameCount * frameBytes)

        val coeffs = currentCoeffs
        val b0 = coeffs.b0; val b1 = coeffs.b1; val b2 = coeffs.b2
        val a1 = coeffs.a1; val a2 = coeffs.a2
        val bypass = coeffs.isBypass

        val kSh = kShelfCoeffs
        val sh_b0 = kSh.b0; val sh_b1 = kSh.b1; val sh_b2 = kSh.b2
        val sh_a1 = kSh.a1; val sh_a2 = kSh.a2
        val kHp = kHpfCoeffs
        val hp_b0 = kHp.b0; val hp_b1 = kHp.b1; val hp_b2 = kHp.b2
        val hp_a1 = kHp.a1; val hp_a2 = kHp.a2

        val alpha = smoothingAlpha
        val tAlpha = trimAlpha
        val trimTgt = trimLinearTarget
        var trim = currentTrimLinear
        var gain = currentGain
        var width = currentStereoWidth
        val targetWidth = targetStereoWidth

        var localRmsSum = rmsSum
        var rmsFrames = rmsFramesAccumulated
        val rmsWindow = rmsWindowFrames

        var kSum = kWeightedSum
        var kFrames = kWeightedFramesAccumulated
        val kWindow = kWeightedWindowFrames

        var peakSegMax = peakSegmentMax
        var peakSegFrames = peakSegmentFrames
        val peakSegTarget = peakSegmentFramesTarget
        var peakIdx = peakReservoirIndex
        val peakRes = peakReservoir
        val peakSlots = peakRes.size

        val basePos = inputBuffer.position()
        var readPos = basePos

        val invScale = 1f / 32768f
        val useStereoWidth = channels == 2

        // Scratch buffer for per-frame channel samples (avoids re-reading the input twice).
        val frameSamples = FloatArray(channels)

        var frame = 0
        while (frame < frameCount) {
            // Trim updates slowly (so post-swap drift back to unity is inaudible). The fast
            // gain smoother then tracks `targetGain * trim`, so gain still snaps at 10 ms.
            trim += (trimTgt - trim) * tAlpha
            val targetTotal = targetGain * trim
            gain += (targetTotal - gain) * alpha
            width += (targetWidth - width) * alpha

            // Read this frame's samples in order, apply K-weighting tap (raw input).
            var ch = 0
            while (ch < channels) {
                val raw = inputBuffer.getShort(readPos).toInt()
                readPos += 2
                val x = raw * invScale
                frameSamples[ch] = x

                localRmsSum += x * x
                val xAbs = if (x < 0f) -x else x
                if (xAbs > peakSegMax) peakSegMax = xAbs

                // K-weighting cascade on the raw signal — purely for metering.
                val shState = kShelfStates[ch]
                val shOut = sh_b0 * x + sh_b1 * shState.x1 + sh_b2 * shState.x2 -
                    sh_a1 * shState.y1 - sh_a2 * shState.y2
                shState.x2 = shState.x1; shState.x1 = x
                shState.y2 = shState.y1; shState.y1 = shOut

                val hpState = kHpfStates[ch]
                val hpOut = hp_b0 * shOut + hp_b1 * hpState.x1 + hp_b2 * hpState.x2 -
                    hp_a1 * hpState.y1 - hp_a2 * hpState.y2
                hpState.x2 = hpState.x1; hpState.x1 = shOut
                hpState.y2 = hpState.y1; hpState.y1 = hpOut

                kSum += hpOut * hpOut
                ch++
            }

            // Main filter on each channel.
            if (!bypass) {
                var c = 0
                while (c < channels) {
                    val x = frameSamples[c]
                    val st = channelStates[c]
                    val out = b0 * x + b1 * st.x1 + b2 * st.x2 - a1 * st.y1 - a2 * st.y2
                    st.x2 = st.x1; st.x1 = x
                    st.y2 = st.y1; st.y1 = out
                    frameSamples[c] = out
                    c++
                }
            }

            // Mid/Side stereo width (stereo only). Skip when width is already at unity to save work.
            if (useStereoWidth) {
                val widthDeviation = width - 1f
                if (widthDeviation > 1e-4f || widthDeviation < -1e-4f) {
                    val l = frameSamples[0]
                    val r = frameSamples[1]
                    val mid = 0.5f * (l + r)
                    val side = 0.5f * (l - r) * width
                    frameSamples[0] = mid + side
                    frameSamples[1] = mid - side
                }
            }

            // Apply gain, clamp, write out.
            var co = 0
            while (co < channels) {
                val scaled = frameSamples[co] * gain * 32768f
                val clamped = when {
                    scaled >  32767f ->  32767f
                    scaled < -32768f -> -32768f
                    else -> scaled
                }
                output.putShort(clamped.toInt().toShort())
                co++
            }

            rmsFrames++
            if (rmsFrames >= rmsWindow) {
                val avg = localRmsSum / (rmsWindow * channels)
                recentRmsLinear = if (avg > 0f) sqrt(avg) else 0f
                localRmsSum = 0f
                rmsFrames = 0
            }

            kFrames++
            if (kFrames >= kWindow) {
                val kAvg = kSum / (kWindow * channels)
                recentKWeightedLinear = if (kAvg > 0f) sqrt(kAvg) else 0f
                kSum = 0f
                kFrames = 0
            }

            peakSegFrames++
            if (peakSegFrames >= peakSegTarget && peakSlots > 0) {
                peakRes[peakIdx] = peakSegMax
                peakIdx = (peakIdx + 1) % peakSlots
                var maxSeen = 0f
                var s = 0
                while (s < peakSlots) {
                    val v = peakRes[s]
                    if (v > maxSeen) maxSeen = v
                    s++
                }
                recentPeakLinear = maxSeen
                peakSegMax = 0f
                peakSegFrames = 0
            }

            frame++
        }

        rmsSum = localRmsSum
        rmsFramesAccumulated = rmsFrames
        kWeightedSum = kSum
        kWeightedFramesAccumulated = kFrames
        peakSegmentMax = peakSegMax
        peakSegmentFrames = peakSegFrames
        peakReservoirIndex = peakIdx
        currentGain = gain
        currentStereoWidth = width
        currentTrimLinear = trim

        inputBuffer.position(readPos)
        output.flip()
    }

    /** Convert a one-pole time constant in milliseconds to a per-sample smoothing α. 0 → snap. */
    private fun computeAlpha(rampMs: Float, sr: Int): Float {
        if (rampMs <= 0f || sr <= 0) return 1f
        // τ-based: alpha = 1 - exp(-1 / (τ * sampleRate)), τ in seconds.
        return (1.0 - exp(-1.0 / (rampMs * 0.001 * sr))).toFloat()
    }

    private fun resolveCoefficients(request: FilterRequest): BiquadCoefficients =
        when (request.mode) {
            FilterMode.BYPASS -> BiquadCoefficients.BYPASS
            FilterMode.LOWPASS -> BiquadCoefficients.lowpass(sampleRate, request.cutoffHz, request.q)
            FilterMode.HIGHPASS -> BiquadCoefficients.highpass(sampleRate, request.cutoffHz, request.q)
        }

    companion object {
        private const val DEFAULT_RMS_WINDOW_FRAMES = 8820   // ≈ 200 ms at 44.1 kHz
        private const val DEFAULT_K_WINDOW_FRAMES   = 6615   // ≈ 150 ms at 44.1 kHz
        private const val DEFAULT_PEAK_SEGMENT_FRAMES = 2205 // ≈ 50 ms at 44.1 kHz
        private const val PEAK_RESERVOIR_SLOTS = 5           // 5 × 50 ms = 250 ms hold
    }
}
