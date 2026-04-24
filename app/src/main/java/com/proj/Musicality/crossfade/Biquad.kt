package com.proj.Musicality.crossfade

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * IIR biquad filter coefficients — Robert Bristow-Johnson's audio-EQ cookbook, normalized so a0 == 1.
 *
 * Transfer function:
 *     H(z) = (b0 + b1·z⁻¹ + b2·z⁻²) / (1 + a1·z⁻¹ + a2·z⁻²)
 *
 * Difference equation (Direct Form I, run on each channel):
 *     y[n] = b0·x[n] + b1·x[n-1] + b2·x[n-2] − a1·y[n-1] − a2·y[n-2]
 *
 * Direct Form I is used because it handles time-varying coefficients more gracefully than
 * canonical (DF2) forms — the input delay-line is never back-substituted, which avoids the
 * small transient energy that DF2 can emit when coefficients change mid-stream.
 */
internal data class BiquadCoefficients(
    val b0: Float,
    val b1: Float,
    val b2: Float,
    val a1: Float,
    val a2: Float
) {
    val isBypass: Boolean
        get() = b0 == 1f && b1 == 0f && b2 == 0f && a1 == 0f && a2 == 0f

    companion object {
        /** Pure pass-through. y[n] = x[n]. */
        val BYPASS = BiquadCoefficients(1f, 0f, 0f, 0f, 0f)

        /** Butterworth-flat second-order low-pass (−12 dB/oct past cutoff). */
        fun lowpass(sampleRate: Int, cutoffHz: Float, q: Float = DEFAULT_Q): BiquadCoefficients {
            val fc = cutoffHz.coerceIn(10f, sampleRate * 0.49f)
            val w0 = 2.0 * PI * fc / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q)
            val a0 = 1.0 + alpha
            val b0 = ((1.0 - cosW0) / 2.0) / a0
            val b1 = (1.0 - cosW0) / a0
            val b2 = b0
            val a1 = (-2.0 * cosW0) / a0
            val a2 = (1.0 - alpha) / a0
            return BiquadCoefficients(
                b0.toFloat(), b1.toFloat(), b2.toFloat(), a1.toFloat(), a2.toFloat()
            )
        }

        /** Butterworth-flat second-order high-pass (−12 dB/oct below cutoff). */
        fun highpass(sampleRate: Int, cutoffHz: Float, q: Float = DEFAULT_Q): BiquadCoefficients {
            val fc = cutoffHz.coerceIn(10f, sampleRate * 0.49f)
            val w0 = 2.0 * PI * fc / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q)
            val a0 = 1.0 + alpha
            val b0 = ((1.0 + cosW0) / 2.0) / a0
            val b1 = -(1.0 + cosW0) / a0
            val b2 = b0
            val a1 = (-2.0 * cosW0) / a0
            val a2 = (1.0 - alpha) / a0
            return BiquadCoefficients(
                b0.toFloat(), b1.toFloat(), b2.toFloat(), a1.toFloat(), a2.toFloat()
            )
        }

        /** 1/√2 ≈ 0.7071 — Butterworth response, no resonant peak. */
        const val DEFAULT_Q: Float = 0.7071068f
    }
}

/** Per-channel two-sample delay line for a biquad running Direct Form I. */
internal class BiquadState {
    var x1: Float = 0f
    var x2: Float = 0f
    var y1: Float = 0f
    var y2: Float = 0f

    fun reset() {
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }
}
