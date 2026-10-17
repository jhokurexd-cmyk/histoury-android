# Histoury: MapLibre / OSM / Hybrid Routing Migration: Phase 0 Audit

Audited 10 Oct 2026 from the local working copies on the developer PC. No code has been changed for this migration yet.

---

## 1. What was inspected

| Repo / folder | Access | Notes |
|---|---|---|
| Android `app/` (build.gradle.kts, src/main, manifest) | ✅ full | No `src/test` or `src/androidTest` folder: **there are no routing unit tests** |
| Android project root (`settings.gradle.kts`, `gradle/libs.versions.toml`, `.git`) | ❌ names only | Root not connected. `.git` exists, so a branch can be made from Android Studio. |
| Firebase Functions (`histoury_functions`) | ✅ (`.env` / service-account files deliberately **not** read) | `getDirections` source is present |
| Admin panel `histoury-admin/src` | ✅ src only | No `package.json` visible (outside `src`) |

---

## 2. Current map & routing stack

| Concern | Today |
|---|---|
| Map SDK | `com.google.maps.android:maps-compose:4.3.3` + `play-services-maps:18.2.0` (Compose, no XML maps) |
| API key | `AndroidManifest.xml` → `com.google.android.geo.API_KEY` = `@string/google_maps_api_key` |
| Location | `play-services-location:21.2.0` (Fused), `GeofencingClient` |
| Routing | Firebase callable **`getDirections`** (asia-southeast1), which calls Google **Routes API** `computeRoutes`. Modes: `WALK`, `DRIVE`, `TWO_WHEELER`. Returns an encoded polyline plus a flat step list. Legacy `getWalkingDirections` is kept. |
| Server protections | `ROUTES_API_KEY` secret, `requireActiveAccount`, rate limit, in-memory `routeCache` / `routesInFlight` |
| Places / search | **No Google Places SDK** in the app. The admin uses **Photon (komoot)** for search, with an optional Google JS loader. |
| Transit / commute | **Not implemented anywhere** (new feature) |
| Offline maps | None. The offline system covers site content, images, AR GLBs and audio only. |
| Admin maps | **Leaflet** with `tile.openstreetmap.org` tiles in `GeofenceMap.jsx` |

---

## 3. File-by-file inventory

### 3.1 Files that render a Google map (Compose `GoogleMap { … }`)

| File | Lines | Google Maps APIs used | What it shows |
|---|---|---|---|
| `ui/screens/map/MapScreen.kt` | 1321 | GoogleMap, Marker ×2, Polyline ×2, CameraPositionState, CameraUpdateFactory, LatLngBounds, MapProperties, MapUiSettings | Directions to one site, route preview, location-off UI, tutorial targets |
| `ui/screens/map/NavigationMode.kt` | 789 | GoogleMap, Marker ×3, Polyline ×4 (dashed walked part), BitmapDescriptorFactory | Live turn-by-turn, follow mode, bearing, **picture-in-picture** mini-map |
| `ui/screens/map/MapOverviewScreen.kt` | 669 | GoogleMap, Marker, BitmapDescriptorFactory, camera | All sites on a map, site cards |
| `ui/screens/explore/ItineraryRouteOverview.kt` | 2190 | GoogleMap, Marker, Polyline ×5 (dashed legs), LatLngBounds | Multi-stop itinerary route and legs |
| `ui/screens/arrival/ArrivalScreen.kt` | 1112 | GoogleMap, Marker ×3, custom icons, bounds | Arrival panel with a small map |
| `ui/screens/placedetails/PlaceDetailsScreen.kt` | 811 | GoogleMap, Marker | Static mini-map of a place |
| `ui/components/MapNightStyle.kt` | — | MapStyleOptions, MapProperties | Dark-theme JSON style for Google |

### 3.2 Files that use only `LatLng` (the model class, no rendering)

`data/repository/DirectionsRepository.kt`, `data/routing/RoutePath.kt`, `data/routing/RoutePathRepository.kt`, `data/viewmodel/MapViewModel.kt`, `data/viewmodel/ItineraryViewModel.kt`, `data/viewmodel/MapOverviewViewModel.kt`, `util/UserDistance.kt`

These need either MapLibre's `LatLng` or, better, a small app-owned `GeoPoint` type, so business logic stops depending on any map SDK.

### 3.3 Routing / navigation logic (keep, adapt)

| File | Role | Migration impact |
|---|---|---|
| `DirectionsRepository.kt` | Callable client, retry and back-off, polyline decode, `TravelMode` enum (WALKING/DRIVING/MOTORCYCLE), `RouteStep` | **Modify**: becomes the client of a provider-neutral endpoint; add a `provider` field and transit metadata |
| `RoutePathRepository.kt`, `RoutePath.kt` | Per-leg multi-stop paths, straight-line fallback, leg cache | **Modify** types only |
| `MapViewModel.kt` (34 KB) | Single-destination routing, ETA, travel-mode switching, reroute | **Modify**: destination becomes the *entrance*, not the site |
| `ItineraryViewModel.kt` (55 KB) | Itinerary routes, legs | **Modify** (same) |
| `util/RouteStepTracker.kt` | Step progress against a polyline | Keep (pure math once `LatLng` is abstracted) |
| `util/VoiceGuidanceManager.kt`, `BearingSmoother.kt`, `NavigationPipState.kt` | Voice, heading, PiP | Keep |
| `data/itinerary/ItineraryPlanner.kt` | Nearest-neighbour + 2-opt ordering | Keep. Could later use routed travel times. |
| `location/NavigationForegroundService.kt` | Navigation while backgrounded | Keep |
| `location/Geofence*.kt`, `data/repository/GeofenceRepository.kt`, `data/model/Geofence.kt` | Arrival geofences (site-centred) | **Keep unchanged.** Requirement: preserve the site geofence. |

### 3.4 Data models (Firestore)

- `historical_sites` → `HistoricalSite`: `latitude`, `longitude` (site location), `arLatitude/arLongitude` (AR placement), plus the geofence in its own collection. **There is no entrance concept today.** `getDirections` routes to the site coordinates, which explains the Fort Santiago endpoint problem.
- `places` → `Place`: `latitude`, `longitude` only.
- `itineraries.stops[]` → `ItineraryStop`: one `latitude/longitude` per stop (copied from the site).

### 3.5 Firebase Functions (local source)

| File | Relevant |
|---|---|
| `functions/index.js` | `getDirections`, `getWalkingDirections`, `computeRoute()`, `extractSteps()`, route cache, `TRAVEL_MODES` |
| `rateLimit.js`, `accountGuard.js` | Reused by any new routing callables |
| `siteLookup.js`, `siteLifecycle.js` | Site helpers (may validate entrance writes later) |
| `firestore.rules` | `historical_sites` is read-public, written by admins only (`isAdmin() && siteFieldsOk()`). **`siteFieldsOk()` must allow the new entrance field(s).** |

**Deployed vs local:** the Android app calls 12 callables, and all 12 exist in local source. Deployed functions that aren't in local source can only be confirmed with `firebase functions:list`, which you need to run.

### 3.6 Admin panel

- `components/GeofenceMap.jsx`: Leaflet with **OSM Foundation public tiles** (`tile.openstreetmap.org`). This is the "no OSMF tiles in production" problem the brief mentions, and it applies to the admin today.
- `pages/AddSite.jsx`, `EditSite.jsx`, `Geofences.jsx`, `components/LocationSearch.jsx`, `utils/locationSearch.js` (Photon): the natural home for an **Entrances** editor.
- ⚠️ `histoury-admin/src` also contains a stray **copy of an old Android app** (`src/main/java/...`, `src/test`, `src/androidTest`). It isn't used by the React build. It's worth deleting later to avoid confusion (you'd need to delete it yourself).

---

## 4. Planned changes (for review, not done yet)

**New (Android)**

- `map/` package: `HistouryMap` composable wrapping MapLibre `MapView` (lifecycle-aware via `AndroidView` + `DisposableEffect`), `MapStyleConfig` (style URL from `local.properties` / BuildConfig, light and dark), and marker, polyline, circle and camera helpers that mirror what the screens use today.
- `GeoPoint` model and a polyline codec.
- `data/routing/provider/`: `RoutingProvider`, `RouteResult` (normalised), `RouteRepository`. The client stays thin; providers run server-side.
- `data/model/SiteEntrance.kt` and an entrance selector.
- `transit/`: an isolated Google Maps screen for Commute only (Phase 5+).

**New (Functions)**

- `routing/orchestrator.js`, `routing/providers/{ors,valhalla,google}.js`, `routing/normalize.js`.
- A new callable `getRoute` (provider-neutral). **`getDirections` stays deployed and unchanged** so older app builds keep working.

**Modified**

- The 7 map-rendering files (screens move from `GoogleMap {}` to `HistouryMap {}`, one screen at a time).
- The 7 `LatLng`-only files.
- `build.gradle.kts`, `AndroidManifest.xml` (Google key kept for the isolated transit screen), `firestore.rules`.
- Admin `EditSite` / `AddSite` (entrances) and `GeofenceMap` tiles.

**Preserved untouched:** auth, OTP, reviews, favorites, AR (SceneView / ARCore), offline downloads, voiceover, gallery, tutorials, notifications, geofences, PiP behaviour.

---

## 5. What could break

1. **PiP mini-map and follow mode** (`NavigationMode`). MapLibre's camera tracking API differs, so this needs device testing.
2. **Custom marker icons**. `BitmapDescriptorFactory` is replaced by MapLibre symbol layers or images. Tap-to-select must be re-implemented.
3. **Dashed polylines**. MapLibre uses `line-dasharray`, which is supported but styled differently.
4. **Tutorial spotlight targets** placed on map overlays (`tutorialTarget`). Layout changes can shift them.
5. **APK size**. MapLibre adds native `.so` files on top of SceneView/Filament. Expect several MB per ABI.
6. **Route geometry vs basemap**. ORS/Valhalla routes come from OSM, so they match an OSM basemap better than Google did. That is one of the goals.
7. **Firestore rules**. Adding `entrances` without updating `siteFieldsOk()` makes admin saves fail.
8. **Old app builds** keep calling `getDirections`, so it must not be removed.

---

## 6. Credentials and accounts needed

| Service | Needed for | Where it goes | Notes (verify on provider site) |
|---|---|---|---|
| Vector tile provider (e.g. **MapTiler** or **Stadia Maps**) | MapLibre basemap (app and admin) | `local.properties` → BuildConfig, never committed | MapTiler Free: 5k map sessions/month, *non-commercial / prototyping only*. Flex: $30/month for 25k. Stadia's free tier is for development, evaluation and non-commercial use **including academic**. A capstone demo fits the free tiers. A public release would not. |
| **OpenRouteService** (HeiGIT) API key | Walking and car routing | Functions secret `ORS_API_KEY` | Free "Standard" plan. Check daily and per-minute quotas in the HeiGIT dashboard. Max 50 waypoints, 6,000 km. |
| **Valhalla** host | Fallback walking/car, motorcycle | Functions secret | Options: Stadia Maps hosted Valhalla (same key as tiles if you choose Stadia), or self-hosted. The FOSSGIS public demo server is **not** for production. |
| Google Routes API key | Commute/transit only (and optional traffic-aware driving) | Existing `ROUTES_API_KEY` | Already set. |
| Google Maps SDK key | The isolated transit map only | Existing manifest key | Already set. |

**Compliance notes**

- Google Routes geometry must not be drawn on MapLibre. Transit gets its own Google-map screen.
- The current server keeps Google route responses in an in-memory cache. Confirm that this short-lived caching is allowed under the current Google Maps Platform Service Specific Terms before keeping it for Google results. ORS/Valhalla results can be cached under their own licences (ODbL attribution required).
- OSM attribution ("© OpenStreetMap contributors" plus the tile provider's credit) must be visible on every MapLibre map.

---

## 7. Missing from the checkout / blockers before Phase 1

1. **Source-control checkpoint**: I can't run git on your PC. In Android Studio: *Git → New Branch → `feature/maplibre-migration`*, then commit everything. Do the same for the admin repo, and zip `histoury_functions` (it isn't in git).
2. **Project-root access** (`settings.gradle.kts`, `gradle/libs.versions.toml`) so I can confirm repositories and plugin versions.
3. **Tile provider choice and key** (MapTiler or Stadia). You put the key in `local.properties` yourself.
4. **ORS key** set as a Functions secret by you: `firebase functions:secrets:set ORS_API_KEY`.
5. **Verified entrance coordinates** for Fort Santiago and every other site. These can't be fabricated. They must be surveyed or checked on-site / on imagery by an admin.
6. **The rest of the brief.** The message was cut off mid-Phase 5 ("Keep transit data distinguishable from pedestrian…").
7. `firebase functions:list` output, to compare deployed functions with local source.

## 8. Proposed order

Phase 1 (MapLibre on **one** low-risk screen first: `PlaceDetailsScreen` mini-map) → `MapOverviewScreen` → `MapScreen` → `ItineraryRouteOverview` → `ArrivalScreen` → `NavigationMode` (PiP last). The Google map code stays in place behind a flag until each screen passes on a device. Entrance model and admin (Phases 3–4) can run in parallel. The routing backend (Phase 5) is added as a new callable alongside the existing `getDirections`.
