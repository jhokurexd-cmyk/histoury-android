# Histoury: Hybrid Database & Offline Setup (Phase 1)

How the Android app stores data, what works without signal, and how changes made offline get back to the server. Written October 2026 from an audit of the app's source.

## 1. Audit summary (what already existed)

| Area | Where | What it does |
|---|---|---|
| Firestore offline cache | `HistouryApp.configureFirestoreCache()` | Persistent cache with **unlimited** size, so cached site records and `site_content` are never evicted. |
| Text/image warm-up | `OfflineContentCache.warmUp()` (MainActivity start) | Every 6 h when online: reads all published sites, places and site content into the Firestore cache and prefetches images into Coil. |
| Image cache | `HistouryApp.newImageLoader()` | Coil disk cache in `filesDir/offline_images` (250 MB), ignores short cache headers so cached images work offline. |
| AR / audio downloads | `OfflineSiteManager`, `OfflineStore` | GLB models, AR reference photos and narration MP3s saved in `filesDir/offline/…`, one JSON manifest per site, `.part` files with HTTP Range resume, validation of GLB files, update detection by URL + `arModelVersion`. |
| Download queue | `OfflineSiteManager.persist()/restore()` | Queue kept in SharedPreferences `histoury_offline_queue`, restored at app start; tasks wait for Wi‑Fi/connection instead of failing. |
| Download settings | `DownloadPreferences`, `AutoDownloader`, `TripPrep` | Wi‑Fi‑only, automatic updates, auto‑download on Wi‑Fi, "download for this trip". |
| Itinerary writes | `ItineraryRepository.awaitOrQueued()` | Create/save/delete/pause/resume don't hang offline; Firestore queues them. |
| Visit history | `VisitHistoryRepository` | Own pending-visit queue, flushed when back online. |
| Firestore rules | `historical_sites`, `site_content` | `allow read: if true`, so restoring downloads before login works. |

### Gaps found (and fixed in this phase)

1. **Stop progress was lost offline.** Marking a stop Arrived/Completed/Skipped and "Optimize Route" used Firestore *transactions*, which need the server. With no signal they failed and the progress was dropped.
2. **Favorites didn't work offline.** The heart called a Cloud Function; without signal the tap was undone.
3. **Taken-down sites stayed on the phone.** A downloaded site that was later unpublished, deleted or had AR switched off kept its files, but Offline Downloads only lists published AR sites, so the user could never see or delete them.
4. **An unpublished site could still be downloaded** from a restored queue, because the lookup by id ignores `status`.
5. **Update checks only ran once per launch**, not when the phone came back online or after signing in.

## 2. Who owns what (cloud vs local)

| Data | Source of truth | Local copy | Written offline? |
|---|---|---|---|
| Historical sites, places, tags | Firestore (admin panel) | Firestore cache | No (read-only in app) |
| Site content (overview, timeline, stories, sources) | Firestore | Firestore cache | No |
| AR models, reference photos, narration audio | Firebase Storage | `filesDir/offline/*` + manifest | No (downloaded only) |
| Images | Firebase Storage | Coil disk cache | No |
| Itineraries & stop progress | Firestore `itineraries` | Firestore cache | **Yes**: Firestore offline queue |
| Favorites | Firestore `users/{uid}.favoriteSiteIds` (via `setFavoriteSite`) | Listener cache + pending taps in SharedPreferences `histoury_pending_favorites` | **Yes**: sent when back online |
| Visit history | Firestore `visit_history` | Pending queue in VisitHistoryRepository | **Yes** |
| Reviews | Firestore via Cloud Function | none | No (needs connection, by design: moderation runs on the server) |
| Gallery photos | Device storage (`LocalGalleryStore`) | Device only | Yes (local only) |
| Settings, tutorials, download queue | SharedPreferences | n/a | Yes (local only) |

Rule of thumb: **content flows down** from the admin panel and is cached. **User actions flow up** and are queued when offline.

## 3. Room: evaluated, not adopted

**Decision: no Room database for now.**

- Firestore's persistent cache already *is* a local structured database (SQLite underneath). It supports queries offline, and the app's reads already fall back to it (`Source.CACHE` paths in the repositories).
- Itineraries, favorites and visits are small and per-user. Firestore's offline write queue plus the two small pending queues cover them.
- Downloaded files need real paths on disk (ARCore and MediaPlayer), so they live in `filesDir` with JSON manifests. A database wouldn't remove the files.
- Adding Room would create a **second** source of truth that must be kept in sync with Firestore. That means more code paths that can disagree, which is more risk than benefit at capstone scale.

**When Room would become worth it:** if the app needed complex offline queries Firestore can't do (full-text search, joins), or the cache grew past what Firestore handles comfortably.

**Schema / migration plan (only if Room is adopted later):**
1. Entities `SiteEntity`, `SiteContentEntity`, `DownloadEntity` (replacing JSON manifests), `PendingWriteEntity`.
2. Repositories read Room first and refresh from Firestore listeners (single source of truth = Room for the UI).
3. Migration v1: on first launch, import existing manifests from `filesDir/offline/manifests` into `DownloadEntity`; keep the JSON files until the import succeeds.
4. Use Room `Migration` objects for each later schema change. Never use `fallbackToDestructiveMigration` for download data.

## 4. Sync & conflict handling

| Write | Online | Offline | Conflict rule |
|---|---|---|---|
| Stop status (arrived/exploring/completed/skipped) | Transaction (read-check-write) | Same change computed from the cached itinerary, sent as a normal update and queued by Firestore | A completed or skipped stop never changes again (`canTransition`). The last write wins for the stop list once the queued update lands. |
| Optimize route (reorder) | Transaction | Same reorder on the cached copy, queued | The last write wins. |
| Create/save/delete/pause/resume itinerary | Plain write, 6 s wait | Queued by Firestore (`awaitOrQueued`) | The last write wins. |
| Favorite | `setFavoriteSite` callable | Kept as `siteId → true/false` and sent when online | Explicit state, not a toggle. The newest tap on the phone wins, and a newer tap cancels an unsent older one. If the server refuses the tap (site removed, list full), it is dropped and the heart shows the server's list. |
| Visit | Transaction | Pending visit queue | Handled in VisitHistoryRepository. |

A transaction that gets no answer within 8 s (`TRANSACTION_TIMEOUT_MS`) is treated as offline. Weak signal inside the walls often means a connection that never answers.

**Known limit:** if the same account edits the same itinerary on two phones while one is offline, the phone that syncs last overwrites the stop list. This is acceptable for a single-visitor trip planner.

## 5. Downloads across restarts and logins

- **Restart:** queued, waiting and paused downloads are restored at app start (`OfflineSiteManager.restore`). Partially downloaded files resume from where they stopped.
- **Login/logout:** downloads are content, not personal data, so they stay on the phone across logout and a different account. After a sign-in, `OfflineSiteManager.refreshNow()` checks them against the server straight away.
- **Coming back online:** `OfflineSiteManager.startMaintenance()` runs whenever the phone regains connection (at most every 10 min). It removes taken-down sites, then checks for updates.

## 6. Stale, deleted and unpublished content

- **Updated content:** a new model or narration upload gets a new Storage URL and a higher `arModelVersion`. The app shows "Update available" or updates automatically, depending on the setting. The old files are deleted only after the new ones are verified.
- **Unpublished, deleted, or AR switched off:** `removeRetiredSites()` asks the **server** (never the cache) for the published list. Downloaded or queued sites not on it (or without AR) are removed, and the user gets a toast. It does nothing if the server can't be reached or returns zero published sites, so a mistake can't wipe every download.
- A queued download for a site that has since been unpublished is refused ("no longer available").
- **Text content:** the Firestore cache refreshes whenever online and on the 6-hourly warm-up. Documents removed from a published query drop out of the cached query results after the next online read.

## 7. Manual test checklist (offline → online)

See the test steps sent with this change. The results go into Phase 1 "Test offline-to-online transition and cross-device sync".
