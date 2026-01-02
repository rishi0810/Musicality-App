# Song Thumbnail Extraction Logic

## API Response JSON Path Mapping

| Context                  | JSON Path                                                                                 | Index Used |
| ------------------------ | ----------------------------------------------------------------------------------------- | ---------- |
| **Player (Now Playing)** | `videoDetails.thumbnail.thumbnails[]`                                                     | `last()`   |
| **Playlist Songs**       | `musicResponsiveListItemRenderer.thumbnail.musicThumbnailRenderer.thumbnail.thumbnails[]` | `first()`  |
| **Album Songs**          | Uses album-level thumbnail, not per-song                                                  | -          |
| **Queue Songs**          | `playlistPanelVideoRenderer.thumbnail.thumbnails[]`                                       | `last()`   |
| **Artist Top Songs**     | `musicResponsiveListItemRenderer.thumbnail.musicThumbnailRenderer.thumbnail.thumbnails[]` | `last()`   |

## URL Transformation Utility

**File:** `util/ImageUtils.kt`

### `upscaleThumbnail(url, targetSize = 544)`

Replaces width/height params in Google image URLs.

```
Input:  https://lh3.googleusercontent.com/.../=w120-h120-l90-rj
Output: https://lh3.googleusercontent.com/.../=w544-h544-l90-rj
```

**Regex:** `=w\d+` → `=w{targetSize}`, `-h\d+` → `-h{targetSize}`

### `resizeThumbnail(url, width, height)`

Same logic, custom dimensions. Used for carousels (e.g., 480x360).

## Usage in UI

- `AlbumScreen` / `ArtistScreen` song items: `ImageUtils.upscaleThumbnail(thumbnail, 226)`
- Carousel items (albums/playlists): `ImageUtils.resizeThumbnail(thumbnail, 360, 360)`
