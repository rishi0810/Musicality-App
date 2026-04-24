package com.proj.Musicality.crossfade

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * A renderers factory that injects a single [CrossfadeAudioProcessor] into the audio pipeline of
 * the ExoPlayer instance it serves. The processor receives decoded PCM before it reaches the
 * platform AudioTrack, which lets the crossfade manager drive gain + filter parameters with
 * sample-accurate resolution instead of the coarse platform Equalizer + `ExoPlayer.setVolume`.
 *
 * Float output is disabled so the processor has a predictable 16-bit PCM contract. The
 * processor itself silently becomes inactive on any other encoding (passthrough, float), so
 * enabling float later wouldn't cause corruption — it would just bypass the effect.
 */
@UnstableApi
class CrossfadeRenderersFactory(
    context: Context,
    private val processor: CrossfadeAudioProcessor
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf<AudioProcessor>(processor))
            .build()
    }
}
