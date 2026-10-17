# Histoury: Hybrid Routing on Google Maps — Implementation Report

Prepared 10 Oct 2026. It covers phases 0–14 of the hybrid-routing brief and supersedes `MAPLIBRE_MIGRATION_AUDIT.md` (MapLibre is **not** being adopted).

**Status in one line:** the code is written for all three codebases and nothing is deployed. The Functions code passes 28 automated tests. The Android and admin changes still need your build and on-device testing.

---

## 1. What was changed

### Android (`AndroidStudioProjects/Histoury/app`)

| File | Change |
|---|---|
| `build.gradle.kts` | `maps-compose` 4.3.3 → **6.12.2**, `play-services-maps` 18.2.0 → **19.2.0** (Phase 1) |
| `ui/components/MapNightStyle.kt` | New `rememberThemedMapColorScheme()`. Interactive maps no longer get the JSON night style; dark mode uses Google's native dark scheme. Lite-mode maps keep the JSON style. |
| `ui/screens/map/MapScreen.kt` | Native colour scheme; green **verified-entrance** marker; "Route ends at: …" line, unverified-entrance note and route credit line under the route |
| `ui/screens/map/NavigationMode.kt` | Native colour scheme; entrance marker; route credit line above the bottom bar |
| `ui/screens/map/MapOverviewScreen.kt`, `ui/screens/arrival/ArrivalScreen.kt` | Native colour scheme |
| `ui/screens/explore/ItineraryRouteOverview.kt` | Native colour scheme; route credit lines under the trip summary |
| `ui/screens/placedetails/PlaceDetailsScreen.kt` | Marks its lite-mode map (`liteMode = true`) |
| `data/repository/DirectionsRepository.kt` | Calls **`getRoute`**. Falls back automatically to `getDirections` while `getRoute` isn't deployed. Parses provider, attribution, warnings, entrance destination, final walk and step travel mode. New optional `destinationSiteId` and `avoidHighways` parameters. |
| `data/routing/RoutePath.kt`, `RoutePathRepository.kt` | Itinerary legs pass each stop's site id, so legs end at verified entrances; legs carry attribution and an entrance flag |
| `data/viewmodel/ItineraryViewModel.kt` | Passes stop site ids to the route path |
| `data/viewmodel/MapViewModel.kt` | Entrance-aware destination and arrival; reroute **debounce** (2 consecutive off-route fixes); **keeps the last valid route** when a refresh or reroute fails; route refresh every 5 min instead of every 20 s |

### Firebase Functions (`histoury_functions`)

| File | Change |
|---|---|
| `functions/routing/` (new) | `callable.js` (`getRoute`), `orchestrator.js`, `entrances.js`, `providers/{ors,valhalla,google}.js`, `polyline.js`, `http.js`, `errors.js`, `test/routing.test.js`, `README.md` |
| `functions/index.js` | One line added: `exports.getRoute = …`. `getDirections` and `getWalkingDirections` are untouched. |
| `functions/package.json` | Added `test` and `deploy:routing` scripts. Dependencies unchanged. |
| `firestore.rules` | `siteFieldsOk()` also checks `entrancesOk()`: `entrances`, if present, must be a list of at most 20 items. Admin-only writes are unchanged. |

### Admin panel (`histoury-admin/src`)

| File | Change |
|---|---|
| `components/EntranceManager.jsx` (new) | Visitor Entrance Management with its own Leaflet map. You can pick or drag points; set name, access type, travel modes, primary, active/closed, verification status and method; record who verified and when; add notes; preview a walking route (via `getRoute`); open a satellite check. |
| `utils/entrances.js` (new) | Field constants, load/save mapping, polyline decoder |
| `pages/EditSite.jsx` | Loads and saves `entrances` with the site; blocks saving on invalid entrances. The site pin, geofence sync and all other fields behave as before. |

**Rollback copies** of every file changed above were written next to the projects (see section 13).

---

## 2. Phase 0 audit: additional findings

- **Project root not accessed.** The access prompt timed out, so `settings.gradle.kts`, `gradle/libs.versions.toml` and the Android `.git` weren't inspected. The dependencies changed here are declared directly in `app/build.gradle.kts`, so the root wasn't needed for these edits.
- **Git checkpoint:** I can't run git on your PC. Commit everything **before** building these changes; the rollback copies are only a fallback.
- **`firebase functions:list` not run.** This session has no shell on your PC. Run it yourself.
- ⚠️ **`functions/voiceover.js` defines `generateVoiceover`, but `index.js` never exports it.** If it's deployed, a full `firebase deploy --only functions` would offer to **delete** it. Only deploy by name (`functions:getRoute`).
- **Automated tests:** the Android app has none (no `src/test`). The admin has one (`smokeSettings.test.js`). Functions now has 28 routing tests.
- **Secrets hygiene:**
  - The functions `.gitignore` covers `.env` and service-account keys. I didn't open `.env` or `ServiceAccountKey.json`.
  - The admin `firebase.js` contains the Firebase **web** API key. That's normal (it's public by design) but must be restricted in Google Cloud.
- **Already working, so not rebuilt:**
  - The itinerary builder: ordering, drag reorder, skip/revisit, out-of-order "Optimize", save/resume.
  - Off-route detection with a GPS-accuracy gate.
  - Stale-response protection (job cancellation).
  - Voice guidance.
  - The geofences.
  - Offline Firestore persistence and the offline downloads (see `OFFLINE_ARCHITECTURE.md`).

---

## 3. Phase 1: pedestrian paths missing in the app

**Cause (verified from the code and Google's release notes):**

1. **Old SDK.** The app pinned **Maps SDK 18.2.0**. Google's current base map is only delivered to **Maps SDK ≥ 19**: the same palette and data as the Google Maps app, and from **19.1.0** "road-level details" (sidewalks, crosswalks, lane markings) at **zoom ≥ 17** in supported cities. Upgrading to 19.2.0 is the legitimate fix.
2. **Dark mode flattened paths.** The JSON night style recoloured *every* `road` feature the same dark grey on a near-identical background. Footpaths and walkways became invisible or looked like roads. Interactive maps now use Google's native `DARK` colour scheme, which is designed to keep that detail.
3. **Light mode never had a style**, so in light mode only cause 1 applied.

**What this can't do:**

- A style change can't add data Google doesn't have. Paths that exist only in OpenStreetMap still won't be drawn by the Google base map.
- When an ORS walking route uses such a path, the **route line** is drawn along it on the Google map. That is our own overlay; nothing is drawn as if it were Google map data.
- Whether a specific Intramuros footpath appears after the upgrade can only be confirmed on a device, at the same coordinates and zoom ≥ 17 (test T2).

**Navigation zooms** were checked: walking follow-mode is 18.5 and the itinerary frame 17, so the detail is visible during navigation. The overview's 15 is intentionally too far out.

---

## 4. Phase 2: routing architecture

```
App (DirectionsRepository) ──getRoute──► RoutingOrchestrator ──► ORS / Valhalla / Google adapters
        │ fallback while undeployed                │ circuit breaker, 60 s cache, de-dup
        └──getDirections (unchanged) ─────────────► Google Routes (as before)
```

**Normalised response:** provider, mode, distance, duration, **precision-5 polyline**, steps (maneuver at the step start, the convention the app's `RouteStepTracker` already uses), maneuver coordinates, travel mode per step, transit details, warnings, attribution, entrance destination, fallback trail.

**Coordinate order:**

- ORS requests use `[lng, lat]`; everything else in the code uses `{lat, lng}` objects.
- Valhalla's precision-6 shape is re-encoded to precision 5.
- Both are unit-tested, including a lat/lng-swap check.

**Mixing providers:** segments from different providers are never stitched into one leg. Each leg comes from one provider, and the drop-off → entrance final walk is a separate leg.

---

## 5. Phases 3–5: providers

| | Behaviour |
|---|---|
| **ORS walking** | `foot-walking`, `preference: recommended`, snap radius **75 m**. An entrance more than 75 m from the walkable network is rejected and the next provider tried, rather than silently moved through a wall. Arrive steps are dropped (the app announces arrival itself). |
| **ORS car fallback** | `driving-car`, `avoid_features: highways` when asked. No traffic, and the app never claims otherwise (`trafficAware: false`). |
| **Valhalla** | Off unless `VALHALLA_URL` is an `https://` endpoint. Never uses a public demo server. Only tried after ORS fails. Never chosen for being shorter. |
| **Google DRIVE** | Traffic-aware, optional `avoidHighways`, optional alternatives. |
| **Google TWO_WHEELER** | No fallback (a car route is never passed off as a motorcycle route). Adds a safety/access warning. Where Google doesn't support it, you get a clear "no route". |
| **Google TRANSIT** | Walking and transit steps are kept apart. Line, vehicle, stops and times come **only** from Google's answer; missing values stay `null`. No jeepney routes or fares are invented. Multi-stop itineraries are one journey per leg (how the app already routes). |

**Android coverage today:** the commute UI is **not** built. `TravelMode` still has Walk/Car/Motorcycle; the server is ready for `TRANSIT`. Adding a fourth enum value touches several exhaustive `when` blocks and needs a transit step list in the UI, so it's a separate step. The avoid-highways toggle is likewise supported end-to-end in the data layer but has no UI switch yet.

---

## 6. Phase 6: verified entrances

**Data:** `historical_sites/{doc}.entrances[]` holds:

- `id`, `name`, `latitude`, `longitude`
- `allowedTravelModes`
- `accessType`: pedestrian / vehicle_dropoff / parking / pedestrian_and_vehicle
- `isPrimary`, `isActive`
- `verificationStatus`: verified / unverified / needs_review
- `verificationMethod`, `lastVerifiedAt`, `verifiedBy`, `notes`

The site's `latitude/longitude` and its geofence are untouched.

**Selection (server):**

- Only active **and** verified entrances are used.
- With one entrance, that one is used.
- With several, walking times are compared with **one ORS matrix call**. The primary entrance wins unless another saves ≥ 90 s and is ≥ 15 % faster.
- If timings aren't available, the **primary** entrance is used, never "nearest as the crow flies".
- Car/motorcycle trips go to a verified drop-off or parking point, plus a walking leg to the entrance.
- With no verified entrance, the route ends at the site coordinates and the app shows "The exact visitor entrance … hasn't been verified yet".

**App:**

- The green marker and "Route ends at: Main Gate" appear only for verified entrances.
- During in-app navigation, arrival is judged at the entrance. Before, it was judged at the geofence centre, which for Fort Santiago is inside the walls.
- Background geofence arrivals are unchanged and still mean "near the site", not "at the entrance".

**Admin:** see section 1. Moving a verified point automatically sets it back to "needs review". Saving is blocked when:

- a name is missing,
- a point is unplaced or more than 800 m from the pin,
- no travel mode is chosen,
- "verified" has no method,
- there are two primary visitor entrances.

**Data still needed:** no entrance coordinates exist yet, and none were invented. Fort Santiago and the other sites need someone to place and verify them.

---

## 7. Phases 7–8: itineraries and rerouting

- **Itineraries:**
  - Stop order, completion, reorder, skip, revisit, out-of-order "Optimize", mode switching and resume are unchanged (they were already in place).
  - Legs now end at verified entrances.
  - Only legs not already cached are fetched, with the cache keyed by origin, destination, mode and site.
  - The provider is invisible to the tourist.
- **Rerouting:**
  - A reroute needs an accuracy-gated fix (existing) **and now 2 consecutive** off-route fixes beyond 35 m walking / 55 m vehicle.
  - It has a 10 s cool-down (existing) and uses the latest-job-wins cancellation (existing).
  - **New:** a failed refresh or reroute keeps the last valid route instead of replacing it with a straight-line estimate.
  - **New:** periodic refresh every 5 min (it was 20 s).
  - The server's 60 s cache and in-flight de-duplication absorb repeats.
- **Limitation:** itinerary leg *origins* are still the previous stop's coordinates, not its entrance. Legs are fetched in parallel, so chaining would serialise them.

---

## 8. Phase 9: Functions

`getRoute`:

- validates coordinates, mode and options;
- requires a signed-in, active, non-suspended account;
- uses the existing per-account `directions` rate limit (150 / 10 min);
- has per-provider timeouts (8 s ORS and Valhalla, 10 s Google) and a circuit breaker (3 failures → skip for 60 s);
- returns structured errors;
- logs safe metrics only (mode, provider, latency, fallback trail; **no coordinates, no user ids**).

Keys stay in Secret Manager.

---

## 9. Phase 10: offline

Unchanged and still correct (see `OFFLINE_ARCHITECTURE.md`).

- Route calculation needs a connection. There is **no offline routing engine**, and the app doesn't claim one.
- With no signal, the last valid route now stays on screen and itinerary progress is preserved (Firestore offline writes).
- Google route data is cached for 60 s only, within the 30-day lat/lng limit in the Routes API terms. Nothing from Google is stored permanently.

---

## 10. Phase 11: monthly cost estimate (USD, list prices, before volume discounts)

**Assumptions per tourist per day:**

- Travel mix: 70 % walking, 15 % car, 5 % motorcycle, 10 % commute.
- A 4-stop itinerary: 3 preview legs, plus 4 navigations each with 1 start, 1 reroute and periodic refreshes.
- Calls per tourist: walking ≈ 15 route calls + 2 matrix calls; car/motorcycle ≈ 13; commute ≈ 10.

**Google Routes prices** (Google's pricing page, Oct 2026):

| SKU | Price per 1,000 | Free per month | Used by |
|---|---|---|---|
| Essentials | $5 | 10,000 | Commute, walking fallback |
| Pro | $10 | 5,000 | Traffic-aware driving |
| Enterprise | $15 | 1,000 | **Two-wheeler routing** |

| DAU | ORS walking calls/day | Google car | Google motorcycle | Google transit | **Google total / month** | Functions calls / month |
|---|---|---|---|---|---|---|
| 100 | ~1,190 | ~$8 | ~$14 | $0 | **~$23** | ~42 k |
| 500 | ~5,950 | ~$242 | ~$131 | ~$25 | **~$399** | ~212 k |
| 1,000 | ~11,900 | ~$535 | ~$278 | ~$100 | **~$912** | ~423 k |
| 5,000 | ~59,500 | ~$2,875 | ~$1,448 | ~$700 | **~$5,022** | ~2.1 M |

With the old 20-second refresh (about 45 refreshes per 15-minute drive), car + motorcycle alone would have cost about 15× these figures: roughly $64,000/month at 5,000 DAU before volume discounts.

- **ORS:** the free Standard key's documented default is **2,000 directions/day**. That covers ~100 DAU. Beyond that you need a higher HeiGIT plan or agreement, or a **self-hosted ORS** (typically one 8–16 GB VM, roughly $40–120/month for a Philippines graph). Commercial-use terms for the hosted API could not be confirmed (the ToS page did not load); confirm with HeiGIT before public launch.
- **Firebase:**
  - Functions invocations are inside the free 2 M/month except at about 5,000 DAU (≈ $0.05).
  - Each call also does one Firestore rate-limit transaction (1 read + 1 write). At 5,000 DAU that's ≈ $5–6/month.
- **Maps SDK map loads:** Google's pricing page lists the Maps SDK (no map ID) as free and unlimited. The SKU page describes map-load billing. Confirm in the Cloud console billing report.
- **Biggest lever:** motorcycle routing is billed as Enterprise. Dropping `TRAFFIC_AWARE` for driving would move car trips to Essentials ($5) at the cost of traffic-aware ETAs.
- **Set budget alerts** in Google Cloud (e.g. 50 / 90 / 100 % of a monthly budget) and an ORS usage check before launch.

---

## 11. Phase 12: security checklist

| Item | Status |
|---|---|
| Routing keys server-side only (`ROUTES_API_KEY`, `ORS_API_KEY` secrets) | ✅ by design |
| Auth plus active-account check on routing calls | ✅ (`requireActiveAccount`) |
| Per-account rate limit | ✅ (shared `directions` limit) |
| Input validation (coordinates, mode, options, site id length) | ✅ unit-tested |
| Entrances writable by admins only | ✅ rules unchanged for writers, size limit added |
| Safe logging (no coordinates or uids in routing metrics) | ✅ |
| Android Maps key restricted to package + SHA-1 and Maps SDK only | ⬜ check in Cloud console |
| Routes key restricted to Routes API only | ⬜ check in Cloud console |
| App Check | ⬜ still `enforceAppCheck: false` (unchanged) |
| Full-directory deploy risk (`generateVoiceover`) | ⚠️ deploy by name only |

---

## 12. Phase 13: test report

| # | Test | Result |
|---|---|---|
| — | Functions unit tests (polyline, ORS / Valhalla / Google adapters incl. timeout, quota, no-route, transit; orchestrator fallbacks, no motorcycle → car, no transit fallback, de-dup, circuit breaker; entrance selection) | **Passed (28/28)** |
| — | `index.js` loads with all 29 exports incl. `getRoute` and the unchanged `getDirections` | **Passed** |
| — | Admin files compile (esbuild JSX parse); entrance helpers round-trip | **Passed** |
| — | Android sources parse (ktlint) | **Passed**: syntax only, **not compiled** |
| 1 | Google Maps screens load after the SDK upgrade | Not tested: needs your build |
| 2 | Pedestrian paths visible where supported (zoom ≥ 17, light and dark) | Requires physical testing |
| 3–4 | Fort Santiago → Manila Cathedral, Cathedral → San Agustin on foot | Requires physical testing (after deploy + ORS key) |
| 5 | Routing to verified entrances | Not tested: no entrances entered yet |
| 6–9 | Multi-stop, reorder, skip, change mode | Not tested: needs your build |
| 10–12 | Car, avoid highways, motorcycle | Server-side unit-tested; app not tested. Avoid highways has no UI switch yet |
| 13 | Commute with walking | Server-side unit-tested; **no app UI yet** |
| 14–15 | Missed turn / GPS drift | Requires physical testing |
| 16–19 | ORS timeout, Valhalla failure, Google failure, quota | **Passed** (unit, simulated) |
| 20 | Connectivity loss keeps last route | Not tested: needs a device |
| 21–22 | Firestore offline, app restart mid-itinerary | Requires physical testing (see offline tests sent earlier) |
| 23–24 | Geofence arrival, AR after arrival | Requires physical testing |
| 25 | Device performance | Requires physical testing |

---

## 13. What you need to do, in order

1. **Commit** the current state in Android Studio (and the admin repo), then pull in these files. Rollback copies are at:
   - `Histoury\app\_backup_2026-10-10_hybrid_routing\`
   - `histoury_functions\_archive\backup_2026-10-10_hybrid_routing\`
   - `histoury-admin\src\Claude outputs\backup-hybrid-routing\`
2. **Build the app.** Gradle downloads maps-compose 6.12.2. Send me any compile errors.
3. **Phase 1 check:** open the same Intramuros footpath in the Google Maps app and in Histoury at zoom ≥ 17, in light and dark mode.
4. **ORS key** from account.heigit.org, then `firebase functions:secrets:set ORS_API_KEY`.
5. **Deploy only the new function and rules** (with your approval):
   - `cd functions && npm test`
   - `firebase deploy --only functions:getRoute`
   - `firebase deploy --only firestore:rules`
6. **Run `firebase functions:list`** and compare the output with `index.js`, especially `generateVoiceover`.
7. **Enter and verify entrances** in the admin, starting with Fort Santiago. Use on-site or imagery verification, and choose the method in the form.
8. **Before public launch:**
   - confirm the HeiGIT commercial terms and quota (or self-host ORS);
   - get written confirmation of the Google Maps Platform position on drawing non-Google route lines on a Google map (section 14);
   - set up budget alerts and key restrictions;
   - configure Valhalla only with an approved endpoint.

---

## 14. Licensing and terms

- **Google Routes on a Google map:** allowed. Routes API content must not be used with a non-Google map (Service Specific Terms §19.2). It may be cached as lat/lng for ≤ 30 days (§19.3); this backend keeps 60 s.
- **ORS or Valhalla routes drawn on a Google map:**
  - The general Maps Platform terms (§3.2.3(e)) forbid using Google Maps Core Services "with or near a non-Google map" and linking a Google map to "non-Google Maps Content".
  - They don't explicitly address drawing a route line from another provider as an overlay. That is a common pattern, and here the overlay is the app's own polyline, not a second map.
  - Because the wording is ambiguous, **get legal or Google confirmation before commercial launch**. If it's not allowed, set `ROUTING_WALK_CHAIN=google`: walking then uses Google with no app update.
- **OpenStreetMap / ODbL:** OSM-based routes show "© OpenStreetMap contributors" plus the provider credit wherever the route is shown: the directions sheet, navigation and the itinerary overview.
- **HeiGIT ORS:**
  - Keys must not be used client-side, which is satisfied.
  - The default quota is 2,000 directions/day per the FAQ.
  - Commercial use of the hosted API is unconfirmed (see section 10).
- **Valhalla:** the software is MIT-licensed; hosted services have their own terms. The integration stays disabled until you choose an approved endpoint.
- **Admin map tiles:** the admin's Leaflet maps use `tile.openstreetmap.org`. OSMF's tile policy allows light, attributed use, which a few admins editing sites is. Don't reuse those tiles in anything public-facing.

---

## 15. Known limitations

1. The Android changes are uncompiled. Expect to fix small compile issues after the dependency bump.
2. There is no commute UI and no avoid-highways switch in the app yet. The server supports both.
3. No entrances exist yet. Until they are verified, routes still end at site coordinates, now with an honest notice.
4. Itinerary legs start from the previous stop's coordinates, not its exit.
5. The admin route preview needs `getRoute` deployed and an admin account with a `users/{uid}` profile.
6. ORS production capacity and terms are unconfirmed beyond about 100 DAU.
