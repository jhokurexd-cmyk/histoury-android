# Histoury: MapLibre migration (pedestrian-first)

**Status as of 10 Oct 2026 (pre-deployment review):** code written and committed to your folders. The whole app was compiled with the **real** Kotlin 2.2.20 + Compose compiler against your project's actual Gradle dependencies and the real MapLibre 13.6.1 SDK: **0 errors**, for both the MapLibre and the Google renderer. **Not yet built with Gradle (`assembleDebug`) or tested on a device.** Nothing has been deployed, pushed, or paid for.

Histoury's maps now draw with **MapLibre Native** on **OpenStreetMap-based vector tiles**. The Google Maps renderer is kept behind a switch until MapLibre has been checked on real phones. That follows the brief: replace Google only after MapLibre matches every feature.

---

## 1. Before you build: Git checkpoint

**What the review found:** your last commit (`a4c3dba "added features"`, local only, not pushed) does **not** contain the working Google Maps app. 53 source files it needs (for example `NavigationMode.kt`, `GeofenceSync.kt`, `Tutorial.kt`, the `util/` folder) and the 5 AR sound files in `res/raw` were never committed, so checking out `main` today would not build. The working Google version existed only as uncommitted files.

**What I prepared:** a Git *bundle* file, `histoury-maplibre-branches.bundle`, in the project root. It holds two new commits on top of `a4c3dba`:

| Branch | Commit contents |
|---|---|
| `baseline/google-maps` | The working Google Maps app exactly as it was before the MapLibre work (the commute-era version that last compiled on your PC), including the 53 missing sources and the AR sounds. |
| `feature/maplibre-migration` | The baseline plus the MapLibre migration and this review's fixes. |

Neither commit contains `local.properties`, `.env` files, keystores, service-account keys, `build/`, `.kotlin/`, the `app/_backup_*` folders, `app/release/`, `src (2).zip`, `postman/`, `.postman/` or `Claude outputs/`. They change files only inside `app/`. Both were compile-checked before bundling.

Run these in the Android Studio **Terminal**, in the project folder:

```bash
git bundle verify histoury-maplibre-branches.bundle
git fetch histoury-maplibre-branches.bundle "baseline/google-maps:baseline/google-maps" "feature/maplibre-migration:feature/maplibre-migration"
git branch -v
```

That only **adds** two branches. It doesn't touch `main`, your files, or GitHub.

Your working folder already contains exactly the `feature/maplibre-migration` files, so to make that branch the current one **without rewriting any file**:

```bash
git symbolic-ref HEAD refs/heads/feature/maplibre-migration
git reset
git status
```

`git status` should list only untracked extras (the reports, `Claude outputs/`, etc.), no modified source files. Then delete the `.bundle` file.

To go back to the Google version for the defense: commit or stash anything new first, then `git switch baseline/google-maps`.

**Already in history before this work (not added by me):** `app/google-services.json` and `app/src/main/res/values/strings.xml` (which holds a Google Maps API key) are tracked, and `strings.xml` is in the commit that is on GitHub. That key is therefore public. In Google Cloud Console, restrict it to Android apps with your package name and SHA-1, and to the Maps SDK for Android only. Rotate it if you want to be safe.

---

## 2. Switching the map (no code change)

Add these to **`local.properties`** in the project root. Every one is optional.

```properties
# maplibre (default) or google
HISTOURY_MAP_RENDERER=maplibre

# Tile styles (defaults shown). OpenFreeMap: free, no key, commercial use allowed, no SLA.
HISTOURY_MAP_STYLE_LIGHT=https://tiles.openfreemap.org/styles/liberty
HISTOURY_MAP_STYLE_DARK=https://tiles.openfreemap.org/styles/dark

# Visible credit line on every map
HISTOURY_MAP_ATTRIBUTION=© OpenFreeMap © OpenMapTiles © OpenStreetMap contributors
```

After changing them, run **Build → Clean Project**, then run the app again.

| Setting | What the app does |
|---|---|
| `maplibre` | OSM map on every screen, OSM-based routes only. **Walk:** ORS, then Valhalla. **Drive:** ORS driving-car, then Valhalla auto. **Motorcycle:** Valhalla's motorcycle profile (needs a Valhalla endpoint, see section 6). **Commute:** the trains (LRT-1, LRT-2, MRT-3), buses and jeepneys mapped in OpenStreetMap, with walking in between; ride and wait times are estimates (see section 6). |
| `google` | The previous Google Maps app, exactly as before, including Commute and Motorcycle. |

---

## 3. What changed (file by file)

### New: Android (`app/src/main/java/com/histoury/app/`)

| File | Purpose |
|---|---|
| `map/MapConfig.kt` | Renderer switch and style URLs, read from BuildConfig. `FeatureFlags`: every mode is on for both maps (the "Coming Soon" path stays in the code for any future mode that can't be routed). `TravelModes`: which modes can be routed, which to show in a picker, and the "Coming Soon" text. It **never substitutes** one mode for another. |
| `ui/map/MapScene.kt` | Renderer-neutral description of what a map shows (lines, markers). It has no MapLibre types. |
| `ui/map/MarkerIcons.kt` | Draws pins, numbered stop pins and the navigation arrow at runtime, in theme colors. |
| `ui/map/HistouryMapState.kt` | Camera control (move, glide, fit bounds, follow frames), gesture events, drawing the scene as GeoJSON layers, the "you are here" dot, and marker hit-testing. |
| `ui/map/HistouryMapLibre.kt` | The **only** place a MapLibre `MapView` is created. Handles lifecycle (start/resume/pause/stop/destroy/low memory), light and dark styles, marker taps and callout, the my-location button, the load-error card with Retry, and the attribution. |
| `ui/map/ScreenMaps.kt` | Commute rides are drawn in their line's colour. MapLibre versions of the Directions preview, Map tab, Arrival, Itinerary overview and Place preview maps. |
| `ui/map/NavigationMapLibre.kt` | Live navigation: heading-up tilted camera, the gliding arrow, the route still ahead over the faint full route, follow / re-center / show-whole-route, and picture-in-picture. |

### Modified: Android

| File | Change |
|---|---|
| `app/build.gradle.kts` | `org.maplibre.gl:android-sdk-opengl:13.6.1`, `buildConfig = true`, the map settings above, and a `pickFirst` for a possible duplicate native C++ runtime. The OpenGL ES build is used instead of the default Vulkan one, which is less reliable on older Android 8–10 phones. |
| `MapScreen.kt`, `NavigationMode.kt`, `MapOverviewScreen.kt`, `ArrivalScreen.kt`, `ItineraryRouteOverview.kt`, `PlaceDetailsScreen.kt` | Each map is `if (MapConfig.useMapLibre) <MapLibre map> else <unchanged GoogleMap>`. The Google-only camera code is skipped on MapLibre. Travel-mode pickers show only modes that can actually be routed. |
| `DirectionsRepository.kt` | Sends `renderer` to `getRoute`. On MapLibre it **never** uses the legacy Google `getDirections` and **rejects** any Google-made route, so this stays safe even before the server is redeployed. Two new failure reasons give honest messages: "isn't set up on the routing server yet" (no provider for that mode) and "the routing server needs an update" (old `getRoute` answered with Google). |
| `MapViewModel.kt` | Keeps the requested mode as-is. (No mode is postponed any more; this is the safety net if one ever is.) For a postponed mode it fetches **no** route, clears any old route, and `startNavigation()` does nothing (this also stops an itinerary's auto-start). |
| `MapScreen.kt` | The saved postponed mode stays selected in the picker, marked "· Soon". A "Coming Soon" card replaces the route info, and the button reads "Coming Soon" and is disabled. On MapLibre each ride says what it is ("Jeepney · …", "Bus · …", "Train · …"), and the commute list says the times are estimates and not every route is mapped. |
| `ItineraryViewModel.kt` | A saved Motorcycle/Commute trip **opens as Motorcycle/Commute** and is saved back as it is. No route is fetched for it. Times stay as straight-line estimates, marked approximate. |
| `ItineraryRouteOverview.kt` | Same picker behaviour, plus a "Coming Soon" banner explaining that the times are rough estimates. |
| `ActiveItineraryTrip.kt` | Remembers the trip's travel mode, so every stop uses it. This fixes audit item **A4**: stops always opened in Walking. |

### Modified: Firebase Functions (`histoury_functions/functions/routing/`)

| File | Change |
|---|---|
| `orchestrator.js` | OSM-only chains: WALK = ORS → Valhalla; DRIVE = ORS driving-car → Valhalla auto; TWO_WHEELER = Valhalla motorcycle only (never a car route); TRANSIT refused. When every provider for a mode is switched off, the answer is "not set up" instead of "unavailable". Cached answers are kept apart per renderer, and `departureTime` is now part of the cache key. |
| `callable.js` | Reads `renderer` (missing = old app = Google behaviour, unchanged). Postponed modes return `failed-precondition` / `MODE_UNAVAILABLE`; a mode with no configured provider returns `failed-precondition` / `NOT_CONFIGURED`. New `ORS_BASE_URL` parameter and `VALHALLA_API_KEY` secret (see section 6). |
| `providers/osmtransit.js` + `transit/` | **Commute on MapLibre: trains, buses and jeepneys from OpenStreetMap.** `planner.js` finds the quickest walk → ride → (walk → ride …) → walk, riding buses and jeepneys only in their mapped direction; `osmtransit.js` fetches the walking parts from ORS and returns steps in the same shape as Google transit, so the app's commute list and map work unchanged. Jeepneys without mapped stops get boarding points every 300 m along their route (they stop almost anywhere) and are never given made-up stop names. No clock times or fares are ever invented, and every answer carries the "times are estimates, not every route is mapped" warning. When walking is quicker it returns the walking route and says so. Walking times to and from the nearby boarding points come from an ORS matrix (2 extra ORS calls per commute request), so a stop just across the Pasig isn't treated as "near". P2P buses can only be used terminal to terminal. `build-transit-data.js` makes `transit-data.json` from OpenStreetMap (section 6). |
| `providers/valhalla.js` | Motorcycle costing (avoids highways and tolls, because motorcycles under 400 cc are barred from expressways) with a "follow road signs" warning on every motorcycle route. Works with Stadia Maps' hosted Valhalla (`/route/v1`, key sent in a header, Stadia credit line). A missing key or `none` switches it off before any request. |
| `providers/ors.js` | Now calls HeiGIT's current endpoint, `https://api.heigit.org/openrouteservice/v2/...`. The old `api.openrouteservice.org` was deprecated on 28 Apr 2026, is limited to 10% of your quota since 27 Aug 2026, and shuts down 2–6 Nov 2026. Your existing key works on the new host, and the request format is unchanged. `ORS_BASE_URL` can point back to the old host if ever needed (until it shuts down); a bad value is refused before the key is sent anywhere. |
| `test/routing.test.js` | 5 MapLibre tests (walking never reaches Google, car falls back ORS → Valhalla, motorcycle/transit refused, no Google route from cache, walk chain minus Google) plus 4 endpoint tests and 7 motorcycle tests (Valhalla only, "not set up" when off, Google map unchanged, motorcycle costing and warning, Stadia URL and header, no key = off) and 14 commute tests in `test/transit.test.js` (classifying jeepneys, skipping broken/proposed routes, one-way buses, jeepney → train → bus planning, honest step details, never Google, speed on a 200-route network), using a made-up test network. **58/58 pass.** |
| `functions/package.json` and root `package.json` | `npm run deploy` no longer deploys every function. See section 6. |
| `README.md` | Routing table for MapLibre clients. |

Backups of every modified file:

- Android: `app/_backup_2026-10-10_maplibre/`
- Functions: `histoury_functions/_archive/backup_2026-10-10_maplibre/`
- Versions from before this review's fixes: `app/_backup_2026-10-10_review/` and `histoury_functions/_archive/backup_2026-10-10_review/`

---

## 4. Brief phases: where each one stands

| Phase | Status |
|---|---|
| 0 Audit and checkpoint | Done (this file and `MAPLIBRE_MIGRATION_AUDIT.md`). **You create the Git branch** (section 1). |
| 1 MapLibre map | Done in code: Intramuros start, gestures, camera animation, location dot, light/dark, coral branding, OSM paths, custom pins, loading / error / Retry, lifecycle, low memory. Device test pending. |
| 2 Exploration features | Done in code: site pins and tap → site card, Map-tab search moves the camera, itinerary pins and legs, arrival map, navigation controls, AR / Visit History untouched. **Geofence circles were never drawn on the Google map**, so there was nothing to migrate. Geofencing itself (Play Services) is unchanged. |
| 3 Entrance routing | Already built in the hybrid routing work: verified entrances, server-side selection by walking time, the "not verified" notice, and arrival judged at the entrance. **Fort Santiago's entrance is UNVERIFIED.** The candidate point for the Esplanade ticket-booth gate (14.5945288, 120.9712927) was read off OpenStreetMap in a browser. That is a lead, not evidence, and it has not been checked on site. Do not enter it as `verified`. If you add it in the admin, save it as `needs_review`; the app and server ignore anything that isn't `verified`, so routes keep ending at the site location with the "entrance not verified" notice. Mark it `verified` only after standing at the gate with GPS (several readings, accuracy ≤ 10 m) or getting confirmation from Intramuros Administration. |
| 4 Admin entrances | Already built (`EntranceManager`). Not changed. |
| 5 ORS walking | Already built (`getRoute`). Now the only walking source on MapLibre. |
| 6 Valhalla | Off until `VALHALLA_URL` is set. Used for the car fallback and, on MapLibre, for **motorcycle**. |
| 7 Driving and motorcycle | Drive: ORS driving-car on MapLibre, with drop-off → entrance walking leg support from the entrance work. Motorcycle: Valhalla's motorcycle profile, with an expressway warning. Both need `getRoute` redeployed; motorcycle also needs a Valhalla endpoint. |
| 8 Commute | **On MapLibre: the trains, buses and jeepneys mapped in OpenStreetMap.** The data built on 11 Oct 2026 (OSM as of 10 Oct 2026) has 3 train lines (LRT-1 Fernando Poe Jr.–Dr. Santos, 25 stations; LRT-2 Antipolo–Recto, 13; MRT-3 North Avenue–Taft Avenue, 13), 620 bus routes (41 of them P2P) and 83 jeepney routes; 205 routes were skipped as too broken to follow. Times are estimates. OSM is the only current, openly licensed source: the 2015 government GTFS feed is out of date and restricted, and FOI requests to DOTr (2022) and LTFRB (2023) for route data were denied. The Google build keeps Google transit. |
| 9 Itinerary | Preserved. The trip's mode now carries across stops. |
| 10 GPS / rerouting | Already in `MapViewModel`: accuracy check, 2 consecutive off-route fixes, 35 m walking / 55 m vehicle threshold, 10 s cooldown, stale route kept on failure. Unchanged. |
| 11 Functions | Done (above). **You redeploy only `getRoute`** (section 6). |
| 12 Offline | See section 8. Nothing changed. Offline content and AR downloads are untouched. |
| 13 Cost and performance | Request de-duplication, cooldowns, timeouts and the circuit breaker already exist. Tiles are free (OpenFreeMap). Lines and pins are redrawn only when they change, and the navigation arrow updates its own one-point source, so per-frame movement doesn't redraw the route. |
| 14 Testing | **Not done on a device.** Function tests: 37/37 pass. Android: the full app (195 Kotlin files) compiles with the real Kotlin 2.2.20 + Compose compiler plugin against the real dependency jars from your Gradle cache and the real MapLibre 13.6.1 classes, for both renderers, with 0 errors. JVM unit tests (`TravelModesTest`, `SmokeMotionTest`, `ExampleUnitTest`) pass. Not done: Gradle sync, resource merging, D8/R8 dexing, APK packaging, instrumented tests. Checklist in section 7. |
| 15 Cleanup | **Deliberately not started.** It removes the Google Maps SDK, and the brief says to do that only after MapLibre matches every feature on a device. Plan in section 9. |

---

## 5. Build: what to expect

1. **Sync Gradle.** The first sync downloads MapLibre from Maven Central (~10–20 MB of native libraries per phone architecture), plus its helper libraries (geojson, gestures, OkHttp, Timber).
2. Run the unit tests: **Gradle → app → Tasks → verification → testDebugUnitTest**, or `gradlew :app:testDebugUnitTest`. Expect `TravelModesTest`, `SmokeMotionTest` and `ExampleUnitTest` to pass.
3. If the build fails, send me the **first** error in the Build window.
4. Likely first-build issues, and the fix for each:
   - *"2 files found with path lib/…/libc++_shared.so"*: covered by `pickFirsts`. If another `.so` file is named, send it to me.
   - *"Unresolved reference: BuildConfig"*: needs `buildConfig = true`, which is already added. Run Sync, then Rebuild.
   - Duplicate classes from OkHttp or Gson: tell me the class name.

---

## 6. Deploy (you run it)

Not yet: you said not to deploy Functions for now. When you do:

**For commute on MapLibre (do this first):** build the transit data from OpenStreetMap. It needs internet and takes a minute or two:

```bash
cd histoury_functions/functions
npm run transit-data
```

It prints how many train, bus and jeepney routes it found, each train line with its first and last station, and how many routes it skipped as not running or too broken to follow. **Check those numbers** against what you know. The file it writes, `routing/transit/transit-data.json`, is deployed with `getRoute`. Re-run it now and then: OpenStreetMap volunteers keep adding routes. Without it, Commute says it isn't set up yet. The data is © OpenStreetMap contributors under the ODbL; the credit line is shown with every commute route.

**For motorcycle on MapLibre (optional):** you need a Valhalla endpoint. The simplest is Stadia Maps (hosted Valhalla). Its free plan needs no card and allows non-commercial academic use, so it fits a capstone; a public Play Store release needs a paid plan or a self-hosted Valhalla.

1. Create a Stadia Maps account and an API key (you do this; I can't create accounts).
2. In `functions/.env.histoury-64ea3` add the line `VALHALLA_URL=https://api.stadiamaps.com/route/v1`.
3. Run `firebase functions:secrets:set VALHALLA_API_KEY` and paste the key.

Without a Stadia key, run the same command and type `none`. Valhalla then stays off and the app says "Motorcycle directions aren't set up on the routing server yet". It never shows a car route instead.

```bash
cd histoury_functions/functions
npm test                      # 37 pass
firebase functions:list       # see what is deployed now
npm run deploy:routing        # = firebase deploy --only functions:getRoute
```

**Why this is safe:** `--only functions:getRoute` creates or updates `getRoute` and nothing else. It cannot delete or overwrite any other function. If the CLI asks for `ORS_BASE_URL`, accept the default (`https://api.heigit.org/openrouteservice`). The deploy fails if the `VALHALLA_API_KEY` secret doesn't exist, so set it first (a key or `none`).

**What was unsafe and is now fixed:** `npm run deploy` used to run `firebase deploy --only functions`. That redeploys **every** function from your local code, overwriting what is live. It also offers to **delete** deployed functions that aren't exported locally: `generateVoiceover` is deployed but no longer exported from `functions/index.js`, so it would be offered for deletion. `npm run deploy` now stops with a warning. A full deploy is still possible on purpose with `npm run deploy:all-functions`. Answer **No** if it ever asks to delete functions.

Until you redeploy, a MapLibre build gets **walking** routes (ORS was already first) but **no car or motorcycle routes**: the old server answers those from Google, which the app refuses to draw on MapLibre. The screen then says "The routing server needs an update" and shows straight-line estimates. The deployed `getRoute` also still calls the old ORS host, which stops working on 2–6 Nov 2026, so deploy `getRoute` before then.

---

## 7. Device test checklist (please run these and send me the results)

Mark each one **Pass / Fail / Not tested**, plus a screenshot if it fails. Nothing below has been tested yet.

**Map**

- [ ] Map tab opens on Intramuros. Pins show. Pinch, drag, rotate and tilt work.
- [ ] Light and dark theme: the map style switches (Settings → theme).
- [ ] The blue location dot and the round "my location" button work.
- [ ] Walkways inside Fort Santiago and along the Esplanade are drawn on the map.
- [ ] Credit line "© OpenFreeMap © OpenMapTiles © OpenStreetMap contributors" is visible. The "i" button opens the links.
- [ ] Airplane mode on first open: the "The map couldn't load" card shows, and Retry works when you're back online.

**Navigation**

- [ ] Fort Santiago → Manila Cathedral (walk).
- [ ] Manila Cathedral → San Agustin Church (walk).
- [ ] Fort Santiago (entrance **not** verified): the route ends at the site and the "entrance not verified" note shows.
- [ ] Drive mode gets a car route (**after redeploying getRoute**). Before redeploying, it says the routing server needs an update.
- [ ] Commute from somewhere near an LRT/MRT station (e.g. Quiapo or Cubao) to Fort Santiago: walk → train → walk, line colours on the map, station names and stop counts in the list, and the "times are estimates" note. A short trip inside Intramuros gives the walking route with "Walking is quicker…".
- [ ] Motorcycle gets a route with the expressway warning (**after redeploying getRoute with Stadia set up**). With `VALHALLA_API_KEY=none` it says motorcycle isn't set up yet, and never shows a car route.
- [ ] New directions: Walk, Drive, Motorcycle and Commute are all offered.
- [ ] Live navigation: the arrow glides, the map turns with your heading, panning shows "Re-center", "Show whole route" works.
- [ ] A missed turn reroutes after ~2 bad fixes, not after one.
- [ ] Picture-in-picture: the map keeps following.

**Itinerary**

- [ ] Build a 3-stop trip. Numbered pins and legs are drawn.
- [ ] Reorder and skip a stop. The map updates.
- [ ] Start the trip in Drive. Arrive at stop 1 and continue: stop 2 opens in **Drive**, not Walk.
- [ ] A Motorcycle trip routes each leg as motorcycle.
- [ ] An old saved Commute trip opens as Commute and routes its legs by train where that's quicker.

**Integration (should be unchanged)**

- [ ] Geofence arrival, AR launch, Visit History, offline site content, login.

**Fallback build**

- [ ] `HISTOURY_MAP_RENDERER=google` → the old app, exactly as before.

---

## 8. Offline maps (Phase 12)

What is offline today, unchanged:

- **Offline today:** historical content, images, narration, AR models (Offline Downloads), and itinerary metadata (Firestore cache).
- **Map tiles:** MapLibre keeps an automatic cache (~50 MB) of tiles you've viewed. Areas you've browsed usually still show offline, but this is **not guaranteed**.
- **Routing is never offline.** ORS / Valhalla run on servers. Offline tiles do **not** give offline directions. When offline, the app keeps the last route and says new directions are unavailable.

Possible next step, not built: a "Download Intramuros map" option. It would use MapLibre's `OfflineManager` to save the ~2 km² area at zoom 14–18 (a few MB). OpenFreeMap states no download limits, but check its terms again before building it. For a Play release, self-hosted tiles (section 10) make this simple.

---

## 9. Cleanup plan (Phase 15, only after section 7 passes)

1. Delete the `else GoogleMap(...)` branches and the Google-only camera code in the six screens.
2. Delete `NavigationMap` / `NavigationArrow` (Google) and `MapNightStyle.kt`.
3. Replace `com.google.android.gms.maps.model.LatLng` (used as a plain model class in ~10 files) with an app-owned `GeoPoint`.
4. Remove `maps-compose` and `play-services-maps` from Gradle, and the `com.google.android.geo.API_KEY` meta-data from the manifest. **Keep** `play-services-location` (GPS and geofencing) and ARCore.
5. Remove `HISTOURY_MAP_RENDERER` and the commute/motorcycle Google paths, or keep them for a future Google build.

---

## 10. Before a public Google Play release

| Item | Why | Action |
|---|---|---|
| **Tiles with an SLA** | OpenFreeMap is free and allows commercial use but has **no SLA**. | Choose one: self-host OpenFreeMap or a Protomaps PMTiles extract of Metro Manila on Cloud Storage / Firebase Hosting (cheap, also enables offline), or a paid plan from MapTiler/Stadia. **Their free plans are non-commercial.** It's a `local.properties` change. |
| **ORS terms** | The ORS terms don't explicitly address commercial apps. Free plan quotas are per day and per minute. | Email HeiGIT before monetising. Watch quota in the dashboard. Consider self-hosted ORS or Valhalla. |
| **Valhalla endpoint** | Needed for motorcycle. Stadia's free plan is non-commercial only. | For a public release, a paid Stadia plan or a self-hosted Valhalla. Never a public demo server. |
| **Attribution** | ODbL (OSM), OpenMapTiles CC-BY, ORS CC-BY. | Already on every map and route. Also list them on an "About / Licences" screen. |
| **Admin map tiles** | The admin's entrance map uses `tile.openstreetmap.org`. That's fine for light internal use with attribution, but it isn't production infrastructure. | Switch Leaflet to a provider (or MapLibre GL JS) before heavy use. |
| **Routes API billing** | Only matters if the Google build ships. | Set a daily quota in Cloud Console. |
| **Privacy policy / Data safety** | Location (foreground and background), camera, accounts. | Declare location shared with routing providers (coordinates only, no user IDs). |
| **Crash monitoring** | Not set up. | Firebase Crashlytics (free). |
| **Device testing** | — | Test at least one Android 8–9 phone and one recent phone, outdoors in Intramuros. |

---

## 11. Known limitations

- **Commute on the OSM map uses only the routes volunteers have mapped**, with estimated times. Many jeepney routes aren't mapped yet; UV Express, tricycles and ferries aren't included. No timetables, fares, service hours or disruptions are known; the app says so on every commute route. Jeepney boarding points are spots along the route, not official stops. The data is only as good as OpenStreetMap and as fresh as the last `npm run transit-data`. You can improve it by mapping missing routes in OpenStreetMap.
- **Motorcycle routes come from OSM access tags.** Valhalla avoids highways and tolls, but OSM may not tag every expressway or local restriction. The route always tells the rider to follow signs.
- **Fort Santiago's visitor entrance is unverified** until checked on site.
- **OSM data quality decides the walking routes.** Paths that OSM marks private or that are mapped wrong can produce wrong routes. Verify on site and fix in OSM if needed. Histoury never invents paths.
- **No traffic** in ORS car routes. ETAs are typical, not live.
- Tiles depend on OpenFreeMap being up, so the map can be slow or missing during an outage. Site content and AR are unaffected.
