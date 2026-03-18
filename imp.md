# Implementation Prompt: Media Library & Local Storage System

## **1. Feature Overview**

Implement a comprehensive **Library System** that manages local media (Songs, Videos, Artists, Albums, Playlists) using **SQLite** for persistence and **Material3 Expressive UI** for the frontend. The UI must strictly follow the provided masonry-style wireframe.

## **2. Data & Persistence (SQLite)**

Create an `SQLiteDatabase` helper class to manage the local storage.

* **Schema Requirements:** Store metadata for `Songs`, `Videos`, `Artists`, `Albums`, and `Playlists`.
* **Blob/File Storage:** Include logic to save raw audio files and image thumbnails locally.
* **State Tracking:** * `isLiked`: Boolean (triggered by Like CTA).
* `isDownloaded`: Boolean (triggered by Download CTA).
* `dateAdded`: Long (timestamp for sorting).



## **3. Networking & Download Logic**

Implement the download mechanism for Songs and Videos:

* **Endpoint:** Use the existing song-fetching `curl` logic.
* **Implementation:** When the Download CTA is triggered, append the query parameter `&range=0-` to the request URL to fetch the full file.
* **Storage:** Stream the response data into the local SQLite/File storage and update the `isDownloaded` flag.

## **4. UI Layout (Material3 Expressive)**

### **Navigation & Header**

* **Top Tabs:** Use **Material3 Expressive Tabs**.
* **Primary Tabs:** "You" and "Saved".
* **Sub-Segments:** Under "Saved", implement a **Segmented Button** (Artist, Playlist, Album).


* **Sorting:** Implement a "Date Added" toggle (Newest vs. Oldest) as shown in the top-right of the wireframe.

### **The "You" Section (Horizontal Scroll)**

* Implement two specific rows using `LazyRow`:
1. **Liked Songs:** Card with a **Heart Icon**.
2. **Downloads:** Card with a **Download Icon**.


* **Navigation:** Both cards route to a modified **Album Page**.
* *Modification:* Remove the "Add" CTA; the header should only contain "Play" and "Shuffle" buttons in a single row.



### **The Masonry Grid (Visual Reference #1-#7)**

Implement a **Masonry/Staggered Grid** for the media items:

* **Card #1:** Large vertical feature card (Primary focus).
* **Cards #2 & #3:** Smaller square cards stacked vertically to the right of Card #1.
* **Cards #4 - #7:** Uniform grid tiles following the staggered pattern below.
* **Typography:** Section headings must be **Bold** and **Left-Aligned**.

## **5. Interaction Logic**

* **State Management:** Any item marked "Liked" must instantly appear in the "Liked" LazyRow.
* **Filtering:** The Segmented Buttons (Artist/Playlist/Album) must filter the Masonry Grid content dynamically without full page reloads.
* **Sorting:** Re-order the grid based on the `dateAdded` timestamp when the sort toggle is clicked.
