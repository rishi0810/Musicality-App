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
x[n] → RMS meter tap → biquad (LPF / HPF / bypass) → * gain → clamp → y[n]
```

**Public parameter API (thread-safe, volatile-backed):**

- `setGainTarget(gain)` — target gain, smoothed toward per sample (≈10 ms)
- `setTrimDb(db)` — static loudness-match trim (±24 dB)
- `setFilter(mode, cutoffHz, q)` — LPF / HPF / bypass; coefficients are recomputed on the audio thread on change
- `resetToIdle()` — unity gain, bypass filter — the "playing normally" state
- `getRmsDb()` / `getRmsLinear()` — sliding-window loudness of the *raw input*, usable for match-gain decisions

**Why a biquad:** A second-order IIR section is the smallest filter that can give a flat passband, a monotonic rolloff, and a controllable slope. Two multiplies and two adds per sample per channel — cheap enough to run on any Android device at 44.1 kHz without measurable CPU cost. The cookbook formulas (Robert Bristow-Johnson) map directly from (sampleRate, cutoffHz, Q) to `(b0, b1, b2, a1, a2)`.

**Why Direct Form I:** Time-varying coefficients (swept cutoff) inject less transient energy when the state variables hold the *inputs* rather than intermediate sums. DF1 is the safest form when the filter re-designs while running.

---

### 3. `SimpleCrossfadeManager` (the strategy)

**What it is:** The crossfade strategy. Owns the timing, gain curves, and filter sweeps. Drives the two `CrossfadeAudioProcessor`s and calls `delegatingPlayer.swapActivePlayer()` when done.

**Lifecycle during a crossfade:**

```
monitor() detects remaining ≤ TRIGGER_LEAD_MS
       │
       ▼
prepareNext() called (downloads + builds CrossfadeNextTrack)
       │
       ▼
executeCrossfade():
  outgoing processor: gain=1, filter=BYPASS    (already in this state)
  incoming processor: gain=0, filter=HPF @ 180 Hz
  inactivePlayer.setMediaItem(nextTrack.mediaItem)
  inactivePlayer.prepare()
  inactivePlayer.play()                       ← silent: processor gain is 0
       │
       ▼
Wait until inactivePlayer.isPlaying
       │
       ▼
nextTrack.onCrossfadeStart()                  ← ViewModel updates UI
       │
       ▼
For each STEP_MS of FADE_DURATION_MS:
  t = progress in [0, 1]
  outgoing processor:
    gain    = cos(t·π/2)                      ← equal-power down
    filter  = LPF, cutoff = logSweep(20 kHz, 500 Hz, t)
  incoming processor:
    gain    = sin(t·π/2)                      ← equal-power up
    filter  = HPF, cutoff = logSweep(180 Hz, 20 Hz, min(t / 0.6, 1))
    once hpfProgress ≥ 1 → filter = BYPASS    (phase-clean signal)
       │
       ▼
delegatingPlayer.swapActivePlayer()
Both processors ← resetToIdle()
       │
       ▼
nextTrack.onCrossfadeComplete()               ← ViewModel re-wires, prefetches
```

**The logarithmic cutoff sweep** matters: a linear sweep from 20 kHz to 500 Hz spends 97% of its time above 600 Hz, so the *audible* filter effect only happens in the last 100 ms — perceptually it sounds like a sudden "dunk" at the end. Log-spaced sweeps move through each octave for the same fraction of the fade, which is how the ear perceives frequency.

**Why the incoming HPF** starts at 180 Hz and opens over only the first 60% of the fade: two bass lines in different keys sum to comb-filter artifacts at their fundamentals — a brief "phasing" sound during the overlap. A gentle HPF on the incoming keeps its bass out of the way while the outgoing is still audible; once the outgoing is nearly silent, the incoming's low end opens back up fully.

**Why no more `OUTGOING_FLOOR`:** The old pipeline held the outgoing at 10 % gain to the end of the fade so the swap wouldn't feel abrupt. With sample-accurate gain smoothing + spectral thinning via LPF, the outgoing can go to absolute zero and the final swap is inaudible — the perceived presence has already tapered off through loss of highs.

**Cancellation:** `cancelTransition()` cancels the coroutine job, resets both processors to idle, and stops the inactive player. The processors are always left in a clean state so the next crossfade starts predictably.

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
