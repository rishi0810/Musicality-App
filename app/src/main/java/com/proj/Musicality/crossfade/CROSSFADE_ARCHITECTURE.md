# Crossfade Architecture

## Overview

The crossfade system is built on three components.
Adding a new crossfade type means implementing a new manager — everything else stays unchanged.

```
MediaSession
     │
ForwardingPlayer (skip intercepts, PlaybackService)
     │
CrossfadeDelegatingPlayer          ← never changes
     │
  ┌──┴──┐
  A     B                          ← two ExoPlayers (always both alive)
```

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

### 2. `SimpleCrossfadeManager` (the strategy)

**What it is:** The crossfade strategy. Owns the timing, volume curves, and EQ shaping. Calls `delegatingPlayer.swapActivePlayer()` when done.

**Lifecycle during a crossfade:**

```
monitor() detects remaining ≤ TRIGGER_LEAD_MS
       │
       ▼
prepareNext() called (downloads + builds CrossfadeNextTrack)
       │
       ▼
executeCrossfade():
  inactivePlayer.setMediaItem(nextTrack.mediaItem)
  inactivePlayer.prepare()
  inactivePlayer.play() at volume 0
       │
       ▼
Wait until inactivePlayer.isPlaying
       │
       ▼
nextTrack.onCrossfadeStart()       ← ViewModel updates UI
       │
       ▼
Ramp volumes (equal-power curves) over FADE_DURATION_MS
  activePlayer:   cos(t·π/2) → OUTGOING_FLOOR
  inactivePlayer: sin(t·π/2) → 1.0
       │
       ▼
delegatingPlayer.swapActivePlayer()
       │
       ▼
nextTrack.onCrossfadeComplete()    ← ViewModel re-wires listener, prefetches
```

**Cancellation:** `cancelTransition()` cancels the coroutine job. The inactive player is left stopped (it doesn't need cleanup — it becomes `inactivePlayer` again and will be reused next time).

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

        // 1. Load + start incoming
        incoming.volume = 0f
        incoming.setMediaItem(next.mediaItem)
        incoming.prepare()
        incoming.play()

        // wait for incoming.isPlaying ...

        // 2. Tell ViewModel to switch UI
        next.onCrossfadeStart()

        // 3. Your volume/DSP logic here
        // ...

        // 4. Swap — this is always the same call regardless of strategy
        delegatingPlayer.swapActivePlayer()

        // 5. Tell ViewModel to rewire and prefetch
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
  CrossfadeDelegatingPlayer.kt   ← stable, never changes
  SimpleCrossfadeManager.kt      ← current strategy (equal-power dual-player)
  CROSSFADE_ARCHITECTURE.md      ← this file

PlaybackService.kt               ← creates CrossfadeDelegatingPlayer, wraps it in ForwardingPlayer
viewmodel/PlaybackViewModel.kt   ← owns the manager, prepares tracks, updates UI
```
