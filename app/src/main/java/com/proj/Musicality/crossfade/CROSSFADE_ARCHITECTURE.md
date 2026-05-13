# Crossfade Architecture

## Overview

The crossfade system is built on three layers: player wrapping, DSP processors, and a strategy manager.
Adding a new crossfade type means implementing a new manager — the player and processor layers stay unchanged.

```
MediaSession
     │
ForwardingPlayer (skip intercepts, PlaybackService)
     │
CrossfadeDelegatingPlayer          ← stable skeleton
     │
  ┌──┴──┐
  A     B                          ← two ExoPlayers (always both alive)
  │     │
  ▼     ▼                          ← each has a CrossfadeAudioProcessor in its
CAP-A   CAP-B                         audio pipeline (gain + biquad filter + RMS meter)
```

### Why a custom AudioProcessor

Earlier versions used `android.media.audiofx.Equalizer` + `ExoPlayer.setVolume` to shape the transition. That approach has three problems that the sample-accurate AudioProcessor fixes:

| Platform Equalizer + `setVolume`                                              | `CrossfadeAudioProcessor`                                       |
| ----------------------------------------------------------------------------- | --------------------------------------------------------------- |
| 5-band fixed EQ — coarse control, no true LPF/HPF, phase weirdness at edges   | Arbitrary biquad filters — per-sample RBJ cookbook coefficients |
| Bound to `audioSessionId`; released during `swapActivePlayer`, brief bypass   | Lives in the audio sink — stable for the player's lifetime      |
| `ExoPlayer.setVolume` is coarse (applied at the track level, not per-sample)  | Gain smoothed per-sample (one-pole, ≈10 ms time constant)       |
| 40 ms-quantized parameter updates visible as zipper artifacts on some devices | Internal smoothing → no zipper, even with aggressive sweeps     |

---

## Components

### 1. `CrossfadeDelegatingPlayer`

**What it is:** A `ForwardingPlayer` wrapper that holds two ExoPlayers (`playerA`, `playerB`) and routes all `Player` interface calls to whichever is currently `activePlayer`.

**What it does:**

- Maintains `activePlayer` and `inactivePlayer` properties
- Forwards all `Player` interface calls (seek, play, pause, position, etc.) to `activePlayer`
- Propagates all player events to `MediaSession` listeners via an internal bridge
- On `swapActivePlayer()`: removes the bridge from old active, stops it, flips `activePlayer`, reinstalls bridge, and notifies listeners of the new state

**What it never does:**

- Never knows anything about crossfade strategy or timing
- Never touches volume directly
- Never loads media

**Key invariant:** The `MediaSession` is bound to this player once and never rewired. Swapping the active player is invisible to the system — notifications, Bluetooth, and Android Auto remain stable.

---

### 2. `CrossfadeAudioProcessor` (the DSP)

**What it is:** A Media3 `BaseAudioProcessor` subclass that sits in each ExoPlayer's audio sink. One instance per player.

**What it does, per sample:**

```
x[n] → linear-RMS tap → K-weighting cascade tap → main biquad (LPF / HPF / bypass)
     → mid/side stereo width → * gain → * trim → clamp → y[n]
```

The K-weighting tap is BS.1770-style (high-shelf at 1681.97 Hz +4 dB → high-pass at
38.135 Hz, Q ≈ 0.5). Its mean-square is summed over a 150 ms sliding window and exposed
as a linear value; the ratio of two processors' readings is the perceptual loudness
delta between two tracks — the same metric Spotify and Apple Music use to normalize.

**Public parameter API (thread-safe, volatile-backed):**

- `setGainTarget(gain)` — target gain, smoothed toward per sample (≈10 ms)
- `setTrimDb(db, rampMs)` — static loudness-match trim (±24 dB). `rampMs = 0` snaps;
  `> 0` enables a per-sample one-pole smoother (used to drift the matched trim back to
  unity over ~5 s after the swap, so the new track ends up at native level)
- `setFilter(mode, cutoffHz, q)` — LPF / HPF / bypass; coefficients are recomputed on
  the audio thread the next frame after a change
- `setStereoWidth(width)` — mid/side scaling. 1 = unchanged; 0 = mono fold; up to 2.
  Mono streams skip the M/S path entirely
- `resetToIdle()` — unity gain, no trim, bypass filter, full stereo — the "playing
  normally" state
- `getRmsLinear()` — unweighted 200 ms sliding RMS of the raw input
- `getKWeightedRmsLinear()` / `getKWeightedLoudnessDb()` — K-weighted (BS.1770) loudness
  of the raw input over the last ~150 ms; used by the manager for cross-track matching
- `resetLoudnessMeter()` — clear the K-weighted accumulator and biquad state, so the
  next reading reflects only fresh audio (called when arming an incoming player)

**Why a biquad:** A second-order IIR section is the smallest filter that can give a flat passband, a monotonic rolloff, and a controllable slope. Two multiplies and two adds per sample per channel — cheap enough to run on any Android device at 44.1 kHz without measurable CPU cost. The cookbook formulas (Robert Bristow-Johnson) map directly from (sampleRate, cutoffHz, Q) to `(b0, b1, b2, a1, a2)`.

**Why Direct Form I:** Time-varying coefficients (swept cutoff) inject less transient energy when the state variables hold the *inputs* rather than intermediate sums. DF1 is the safest form when the filter re-designs while running.

---

### 3. `SimpleCrossfadeManager` (the strategy)

**What it is:** The crossfade strategy. Owns the timing, gain curves, and filter sweeps. Drives the two `CrossfadeAudioProcessor`s and calls `delegatingPlayer.swapActivePlayer()` when done.

**Lifecycle during a crossfade:**

```
monitor() detects remaining ≤ TRIGGER_LEAD_MS  (= FADE_DURATION_MS + PRE_ROLL_MS + 200)
       │
       ▼
prepareNext() called (downloads + builds CrossfadeNextTrack)
       │
       ▼
executeCrossfade():
  ── prime ──
  outgoing processor: gain=1, filter=BYPASS, width=1
  incoming processor: gain=0, trim=0, filter=HPF @ 220 Hz, width=0.55,
                       loudness meter cleared
  inactivePlayer.setMediaItem(nextTrack.mediaItem); prepare(); play()
       │
       ▼
  Wait until inactivePlayer.isPlaying
       │
       ▼
  ── pre-roll (500 ms): silent loudness sampling ──
  delay 200 ms (let K-weighting biquads settle and meter window fill)
  3 × { sample outgoing.kWeightedRmsLinear, incoming.kWeightedRmsLinear; delay 100 ms }
  trimDb = 20·log10(avg(out) / avg(in)), clamped ±LOUDNESS_TRIM_MAX_DB (=6)
  incoming.setTrimDb(trimDb, ramp=80 ms)      ← matched-loudness lock
       │
       ▼
  nextTrack.onCrossfadeStart()                ← ViewModel updates UI
       │
       ▼
  ── time-driven envelope (6 s, 20 ms ticks) ──
  start = SystemClock.elapsedRealtime()
  loop until raw progress ≥ 1:
    rawT  = (now − start) / FADE_DURATION_MS
    t     = tukey(rawT, 0.15)                 ← raised-cosine ease in/out
    outgoing:
      gain   = cos(t·π/2)                     ← equal-power down
      filter = LPF, cutoff = logSweep(20 kHz → 150 Hz, t)
      width  = 1 → 0  (linear in t)           ← mono-fold to "recede"
    incoming:
      gain   = sin(t·π/2)                     ← equal-power up
      filter = HPF, cutoff = logSweep(220 → 30 Hz, min(t/0.85, 1))
      once hpfProgress ≥ 1 → filter = BYPASS  (cutoff already inaudible)
      width  = 0.55 → 1  (linear in t/0.8)   ← opens out of mono
       │
       ▼
  delegatingPlayer.swapActivePlayer()
  outgoing → resetToIdle()
  incoming → setTrimDb(0, ramp=5 s)          ← drift back to native level
       │
       ▼
  nextTrack.onCrossfadeComplete()             ← ViewModel re-wires, prefetches
```

**Time-driven progress (not iteration count)**: the loop computes `rawT` from
`SystemClock.elapsedRealtime()` each tick, so a coroutine tick that fires late doesn't
push later ticks late too — the next tick computes the correct progress and the curve
catches up. With the old `index/steps` formulation, jitter accumulated and the fade
could end audibly off-cadence on a busy device.

**Tukey progress shaping**: a raised-cosine softens the first and last 15 % of the
fade. The cosine gain curve has zero slope where it meets the eased endpoints — so the
gain change "eases in" (no sudden drop on the outgoing) and "eases out" (no sudden
plateau on the incoming). The middle 70 % is straight linear, preserving the equal-
power behavior where it matters most.

**The logarithmic cutoff sweep** matters: a linear sweep from 20 kHz to 150 Hz spends
> 97 % of its time above 1 kHz, so the audible filter effect only happens in the last
~100 ms — perceptually a sudden "dunk" at the end. Log-spaced sweeps move through each
octave for the same fraction of the fade, which is how the ear perceives frequency.

**Why the incoming HPF stays active until 85 % of the fade**: with the outgoing LPF
sweeping to 150 Hz, the outgoing's bass is still around −7 dB at the 70 % mark. Holding
the incoming HPF until 85 % keeps the two tracks' bass lines from summing into comb-
filter "phasing" artifacts. The HPF endpoint at 30 Hz is essentially BYPASS in the
audible range, so the final flip to BYPASS is acoustically silent.

**Stereo-width modulation** is a depth cue: narrowing the outgoing toward mono makes it
recede from the front-stage; opening the incoming from 0.55 to full stereo makes it
"unfold" into the field. Cost: four multiplies and two adds per stereo frame. Mono
streams skip the M/S stage entirely.

**Loudness matching** is the biggest perceptual win. Adjacent tracks at different
master levels (e.g. a −9 LUFS pop song into a −18 LUFS jazz ballad) jump audibly even
with a perfect equal-power curve. Sampling K-weighted RMS on both players during the
500 ms pre-roll and applying a clamped ±6 dB trim to the incoming aligns the perceived
loudness across the entire fade. After the swap, that trim drifts back to 0 dB over
~5 s (τ = 5 s, one-pole) so the new track ends up at native level without an audible
step — the rate (≈ 1.2 dB/s worst case) is below the casual-listener detection
threshold for slow gain change.

**Why no more `OUTGOING_FLOOR`**: the old pipeline held the outgoing at 10 % gain to
the end of the fade so the swap wouldn't feel abrupt. With sample-accurate gain
smoothing + spectral thinning via LPF + width narrowing, the outgoing can go to zero
and the final swap is inaudible — the perceived presence has already tapered off
through loss of highs and stereo spread.

**Cancellation**: `cancelTransition()` cancels the fade coroutine and any in-flight
trim-decay, then either commits to the incoming (preserving the matched trim and
starting the decay) or reverts (kills incoming, returns outgoing to idle). The
processors are always left in a clean state so the next crossfade starts predictably.

---

### 3. `CrossfadeNextTrack` (the data contract)

```kotlin
data class CrossfadeNextTrack(
    val mediaItem: MediaItem,
    val onCrossfadeStart: suspend () -> Unit,
    val onCrossfadeComplete: suspend () -> Unit
)
```

`PlaybackViewModel.prepareCrossfadeNextTrack()` fills this in:

| Callback              | What the ViewModel does                                                                       |
| --------------------- | --------------------------------------------------------------------------------------------- |
| `onCrossfadeStart`    | Updates UI to next song, resets position to 0, fetches lyrics, records history                |
| `onCrossfadeComplete` | Re-calls `setupPlayerListener()` on new active player, unpins old audio file, prefetches next |

---

## Adding a New Crossfade Type

### Step 1 — Create a new manager

```kotlin
class MyNewCrossfadeManager(private val context: Context) {

    private var transitionJob: Job? = null
    private var lastTriggeredTrackId: String? = null

    fun setEnabled(enabled: Boolean) { /* ... */ }
    fun isTransitioning(): Boolean = transitionJob != null

    fun monitor(
        scope: CoroutineScope,
        trackId: String?,
        hasNext: Boolean,
        positionMs: Long,
        durationMs: Long,
        delegatingPlayer: CrossfadeDelegatingPlayer,
        prepareNext: suspend () -> CrossfadeNextTrack?
    ) {
        // Same guard pattern as SimpleCrossfadeManager
        if (transitionJob != null || trackId == lastTriggeredTrackId) return
        val remaining = durationMs - positionMs
        if (remaining > TRIGGER_LEAD_MS) return

        lastTriggeredTrackId = trackId
        transitionJob = scope.launch(Dispatchers.Main) {
            val next = prepareNext() ?: return@launch
            executeMyTransition(delegatingPlayer, next)
            transitionJob = null
        }
    }

    private suspend fun executeMyTransition(
        delegatingPlayer: CrossfadeDelegatingPlayer,
        next: CrossfadeNextTrack
    ) {
        val outgoing = delegatingPlayer.activePlayer
        val incoming = delegatingPlayer.inactivePlayer
        val outProc = delegatingPlayer.processorFor(outgoing)
        val inProc = delegatingPlayer.processorFor(incoming)

        // 1. Arm the processors for the crossfade start state
        inProc.setGainTarget(0f)
        inProc.setFilter(CrossfadeAudioProcessor.FilterMode.HIGHPASS, 180f)

        // 2. Load + start incoming (it's silent — processor gain is 0)
        incoming.setMediaItem(next.mediaItem)
        incoming.prepare()
        incoming.play()

        // wait for incoming.isPlaying ...

        // 3. Tell ViewModel to switch UI
        next.onCrossfadeStart()

        // 4. Your gain + filter automation here — call outProc / inProc setters per step
        // ...

        // 5. Swap — this is always the same call regardless of strategy
        delegatingPlayer.swapActivePlayer()
        delegatingPlayer.resetProcessors()

        // 6. Tell ViewModel to rewire and prefetch
        next.onCrossfadeComplete()
    }

    fun cancelTransition() {
        transitionJob?.cancel()
        transitionJob = null
    }

    fun release() = cancelTransition()
}
```

**Rules:**

- Always use `delegatingPlayer.activePlayer` and `delegatingPlayer.inactivePlayer`
- Always call `delegatingPlayer.swapActivePlayer()` to complete a transition
- Always call `next.onCrossfadeStart()` before the fade and `next.onCrossfadeComplete()` after the swap
- Never call `stop()` or `release()` on either player directly

### Step 2 — Wire into `PlaybackViewModel`

Replace `SimpleCrossfadeManager` with your new manager. The rest of the ViewModel (`prepareCrossfadeNextTrack`, polling loop, `startPositionPolling`) stays identical:

```kotlin
// In PlaybackViewModel:
private val crossfadeManager = MyNewCrossfadeManager(application.applicationContext)
```

The `monitor()` call in `startPositionPolling()` and all callbacks are already generic:

```kotlin
crossfadeManager.monitor(
    scope = viewModelScope,
    trackId = currentTrackId,
    hasNext = hasNext,
    positionMs = pos,
    durationMs = duration,
    delegatingPlayer = dp
) {
    prepareCrossfadeNextTrack()   // unchanged
}
```

### Step 3 — `CrossfadeDelegatingPlayer` stays untouched

The delegating player is strategy-agnostic. You never modify it when adding a new crossfade type.

---

## Rules Summary

| Rule                                                                   | Reason                                                    |
| ---------------------------------------------------------------------- | --------------------------------------------------------- |
| Only `swapActivePlayer()` changes which player is active               | Keeps MediaSession stable                                 |
| `onCrossfadeStart` must fire before audio is audible on B              | UI reflects current audio source                          |
| `onCrossfadeComplete` must fire after swap                             | Listener rewired to new active player                     |
| Never hold a direct reference to `playerA` or `playerB` in the manager | Use `delegatingPlayer.activePlayer` — it may have swapped |
| Always call `cancelTransition()` before skip/prev/skipToIndex          | Prevents two fades running simultaneously                 |

---

## File Map

```
crossfade/
  Biquad.kt                      ← RBJ-cookbook coefficients + per-channel state
  CrossfadeAudioProcessor.kt     ← Media3 AudioProcessor with biquad + gain + RMS meter
  CrossfadeRenderersFactory.kt   ← wires the processor into each ExoPlayer's audio sink
  CrossfadeDelegatingPlayer.kt   ← dual-player skeleton + processor lookup
  SimpleCrossfadeManager.kt      ← current strategy (equal-power + log filter sweep)
  CROSSFADE_ARCHITECTURE.md      ← this file

PlaybackService.kt               ← calls CrossfadeDelegatingPlayer.create(context)
viewmodel/PlaybackViewModel.kt   ← owns the manager, prepares tracks, updates UI
```
