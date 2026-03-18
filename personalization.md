# Home Feed Personalization Algorithm

## Overview

Musicality generates personalized sections that are prepended to the API-sourced home feed. The system uses three tiers based on listening history depth, with a **hero "Continue Playing" card** always appearing first.

Sections are generated in `PersonalizedHomeFeedGenerator.generateSections()` and merged by `HomeViewModel.mergeWithRecommendations()`.

## Tier Thresholds

| Tier | Distinct Songs | Trigger |
|------|---------------|---------|
| None | 0 | No personalized sections — API feed only |
| Cold | 1 | First song played; minimal data |
| Warm | 2–9 | Building a listening profile |
| Mature | 10+ | Rich history; full personalization |

## Section Catalog

### Cold Tier (1 song)

| Section | Layout | Data Source |
|---------|--------|-------------|
| Continue Playing | `HERO_CARD` | `recentUnique[0]` (local DB) |
| Similar to {song.title} | `STACKED_SONGS` | `/next` endpoint for seed song → 8 songs |
| More like {artistName} | `STACKED_SONGS` | Artist browse → 3 similar artists → top 4 songs each → 12 songs |

**API calls:** Wave 1 — 2 parallel (`/next` radio + artist browse). Wave 2 — up to 3 parallel (similar artist details).

### Warm Tier (2–9 songs)

| Section | Layout | Data Source |
|---------|--------|-------------|
| Continue Playing | `HERO_CARD` | Most recent unique song (local DB) |
| Keep Listening | `STACKED_SONGS` | `/next` for each unique song (up to 5), mixed & shuffled → 16 songs |
| Similar to {mostRecent.title} | `STACKED_SONGS` | `/next` for most recent → 8 songs |
| More like {latestArtist} | `STACKED_SONGS` | Latest artist browse → top 8 songs |
| Fans of {artist} also listen to... | `DEFAULT` | Artist's similar artists → their top songs → 6 songs |

**API calls:** Wave 1 — up to 7 parallel (1 similar radio + up to 5 keep-listening radios + 1 artist browse). Wave 2 — 2 parallel (fans-also artist lookups).

### Mature Tier (10+ songs)

| Section | Layout | Data Source |
|---------|--------|-------------|
| Continue Playing | `HERO_WITH_TOP_PICKS` | Hero = most recent. Top picks = top 3 by play count (excluding hero) |
| Keep Listening | `STACKED_SONGS` | Top 3 songs from DB + their `/next` results, interleaved → 20 songs |
| Similar to {topSong1.title} | `STACKED_SONGS` | `/next` for random top song → 8 songs |
| Similar to {topSong2.title} | `STACKED_SONGS` | `/next` for different random top song → 8 songs |
| More from {topArtist} | `ALBUM_CAROUSEL` | Top artist browse → albums shuffled → 8 album cards |
| Your Top Artists | `DEFAULT` | Top 5 artists as artist cards (cached artist details for thumbnails) |
| Rediscover | `STACKED_SONGS` | Songs played >3 days ago, not in top 5 recent → seed 1 via `/next` → 8 songs |
| Time Capsule | `STACKED_SONGS` | Top songs by play count whose `lastPlayedAt` > 24h ago → 8 songs |

**API calls:** Wave 1 — ~8 parallel (3 keep-listening + 2 similar + 1 top artist + 1 rediscover + deferred top artists). Wave 2 — up to 5 for top artist thumbnails (mostly cache hits from "More from" fetch).

## Layout Hints

| Layout | Description |
|--------|-------------|
| `DEFAULT` | UI decides via title pattern matching (API sections and some personalized) |
| `HERO_CARD` | Full-width 200dp banner with background image, gradient scrim, song info overlay |
| `HERO_WITH_TOP_PICKS` | Hero card + row of 3 compact 56dp song cards below |
| `ALBUM_CAROUSEL` | SectionHeader + LazyRow of 170dp `ContentCard` album items |
| `STACKED_SONGS` | Items chunked into groups of 4, displayed in stacked columns within a LazyRow |

## Resilience Strategy

- **Every network fetch** is wrapped in `runCatching { ... }.getOrDefault(emptyList())` (or `.getOrNull()`)
- If a fetch fails, the resulting section is simply **skipped** — other sections still appear
- The hero card (`Continue Playing`) uses **local DB data only** and never fails if listening history exists
- `/next` endpoint failures trigger one automatic retry with a refreshed visitor ID (`executeNextWithRecovery`)
- Artist detail fetches are **cached in `AppCache.browse`**, so repeated lookups for the same artist are instant
- Section ordering is **deterministic** (no tail shuffling) — Continue Playing always first

## Data Flow

```
ListeningHistoryRepository.getSnapshot()
    ↓
PersonalizedHomeFeedGenerator.generateSections()
    ↓ (coroutineScope with parallel async fetches)
List<HomeSection> with layoutHint set
    ↓
HomeViewModel.mergeWithRecommendations()
    ↓ (prepends to API HomeFeed)
HomeScreen renders via HomeSectionShelf
    ↓ (dispatches on section.layoutHint)
Hero cards / stacked songs / album carousels / default shelves
```
