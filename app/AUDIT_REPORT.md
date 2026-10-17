# Histoury — Full App Audit

Scope: Android app, Cloud Functions + Firestore/Storage rules, admin panel.
Focus: bugs & crashes, performance & cost, code quality, security.
Nothing has been changed yet. Pick the IDs you want fixed.

**Verified** = I opened the code and confirmed the failure path myself. Unmarked items come from the per-area review passes. They are plausible, but they get re-checked before any fix.

Size: **S** = under an hour, **M** = a few hours, **L** = a day or more.

---

## 1. Fix before the defense
These are things a panel could actually see during a demo, or that break core features.

| ID | Area | Problem | Why it matters | Size |
|---|---|---|---|---|
| **A1** ✔ verified | Android | Geofences are wiped and re-added every time Home is opened (throttled to every 30 s), with no initial trigger. | Suppose a visitor is already inside a site's fence and opens Home. The fence is re-registered while they stand inside it, so Android never sees them "cross in" and the arrival (DWELL) never fires. The fix is to re-register only when the fence list actually changed. | S |
| **A2** ✔ verified | Android | The visit "lock" document id is `uid_siteId`. Older sites whose `siteId` contains "/" make that an invalid path. | For those sites the visit isn't recorded, and the failed visit sits in the offline queue and blocks every visit queued after it. The fix is to clean the id the same way `contentDocIdFor` does. | S |
| **A3** ✔ verified | Android | AR camera permission: the visitor opens Settings from the dialog, comes back without allowing Camera, and gets an endless spinner. | Nothing re-shows the dialog, so the screen looks frozen. The fix is to show "Camera is off" with Retry / Settings / Close. | S |
| **A4** ✔ verified | Android | Itinerary → "next stop" always opens the map in Walking mode, even when the trip was planned by Car or Commute. | The trip's chosen mode is lost between stops. The fix is to pass the mode in the map route. | S |
| **A5** ✔ verified | Android | Commute ride lines use Google's line color, and many Manila lines come back white. | Some ride lines are drawn white and are hard or impossible to see on the map (your "why is it white" report). The fix is to fall back to coral when the color is too light, and to add an outline. | S |
| **A6** | Android | The navigation tutorial can cover the screen during live navigation, and the Arrival chime plays again when you go back. | Both look like glitches during a demo. | S |
| **B1** ✔ verified | Functions | `submitSiteReview` accepts a site's **document id** as the `siteId` and saves the review under it. The rating recount then counts only reviews stored under that id. | One review sent with the doc id resets the site's public rating to that single review (count = 1). It also lets one person leave two reviews for the same site. The fix is to always store the canonical `siteId`. | S |
| **W1** ✔ verified | Admin | Edit Site Content: if loading the content fails (network or permissions), the form shows up empty with no error, and **Save** overwrites the real overview, timeline, stories and narration with blanks. | Real content can be lost silently. The fix is to show an error and disable Save when loading failed. | S |
| **W2** ✔ verified | Admin | The "Add/Remove demo reviews" buttons are live in the production Ratings page. | One click puts fake reviews into the public ratings that real app users see. This is useful for testing rankings, but it should be hidden outside dev or behind a confirm-twice switch. | S |

---

## 2. Fix before a public release
None of these are likely to come up in a demo, but they matter for security and running cost.

### Security
| ID | Area | Problem | Size |
|---|---|---|---|
| **B2** | Functions | A visitor whose review comment was hidden can delete it and post again to get a fresh, visible review. | S |
| **B3** | Functions | OTP email can be requested without signing in, so someone could use it to send email (rate limit is per IP and easy to dodge with IPv6). The number of allowed code guesses is also generous. | M |
| **B4** | Rules | Profile name fields and feedback documents aren't validated in the rules (length/type), and feedback can be marked "anonymous" to skip checks. | S |
| **B5** | Storage | The `profile-images/` path and the admin upload paths have no size or file-type limits, so they could be used as free file hosting. | S |
| **A7** | Android | `allowBackup` is on with default rules, so offline caches and sign-in preferences go into Google backups. | S |
| **A8** | Android | The launcher activity is exported and accepts an `arrival_site_id` extra, so another app could open a fake "arrival" (and a visit). | S |
| **W3** | Admin | Map tooltips (entrance names, geofence names) are rendered as HTML, so a crafted name could run script in another admin's browser. | S |
| **W4** | Admin | Typing new entrance coordinates doesn't reset the entrance to "unverified" (dragging the marker does). | S |
| **A9** | Android | R8/minify is off for release builds. The code is easy to decompile, and the APK is larger than it needs to be. | M (needs keep rules + testing) |

### Bugs (lower visibility)
| ID | Area | Problem | Size |
|---|---|---|---|
| **A10** | Android | A profile photo over 5 MB is rejected by the storage rules with no clear message. The app should shrink it first. | S |
| **A11** | Android | A large AR model (GLB) is read fully into memory, which can crash low-RAM phones. | M |
| **A12** | Android | Home asks for permissions again in a chain, and the Home tour can overlap dialogs. | S |
| **W5** | Admin | The Maps JS script can load twice, which breaks Places search. | S |
| **W6** | Admin | Voiceover file names can collide after stories are reordered (one narration can overwrite another). | S |
| **W7** | Admin | Re-linking an AR scene to another site leaves the old one active. | S |
| **W8** | Admin | Files are uploaded before the Firestore write, so a failed save leaves orphan files (deleting a site leaves them too). | M |

### Performance & cost
| ID | Area | Problem | Size |
|---|---|---|---|
| **B6** | Functions | Car routes always request `TRAFFIC_AWARE`, which is billed at the higher Routes "Pro" rate, and there's no daily cap. **Do this one yourself in Cloud Console:** set a daily quota on the Routes API. In code, the fix is traffic-aware only for the first route, not for the 5-minute refreshes. | S |
| **B7** | Functions | The rating recount reads **every** review of the site on each new review, and two reviews at once can race. A running total in a transaction would avoid both. | M |
| **A13** | Android | The compass heading recomposes the whole map screen many times a second, which costs battery. | S |
| **W9** | Admin | Dashboard, Feedbacks and Ratings read whole collections, so cost grows with every review. | M |

---

## 3. Cleanup / low priority
Code quality only; none of these break anything you'd demo.

- Android: whole-list `toObject` fails if one document is malformed. Debug screen (`StreetscapeDebug`) reachable in release. Storage URLs logged. Placeholder support phone/email. "min walk" copy shown for every mode. Small touch targets. Lists without keys. `String.hashCode` used for file names. Email-exists live check (lets anyone test which emails have accounts).
- Functions: dev-only message in `otp.js`. Unbounded `getMyReviewInteractions`. Google HTTP 400 reported as "no route". The `departureTime` isn't part of the route cache key. Firestore TTL policy not configured for OTP/rate-limit docs. Dead code in `userAdmin`. Old comments. Demo seeding uses production collections.
- Admin: about 4,100 lines of unused code. Object URLs not released. Large bundle (no code splitting). Duplicate-`siteId` race on create. Archived sites can be re-enabled by editing. `profileImage` field-name mismatch on avatars.

## Checked and dismissed
- **"Functions still on Node 20"**: a false positive. My audit copy was stale; your real `firebase.json` and `package.json` are on Node 24 (you deployed with it).
- **App Check warnings in the logs**: harmless while enforcement is off.

---

## Suggested picks
- **Before the defense:** A1, A2, A3, A4, A5, B1, W1 (all small). Add W2 if you won't use demo reviews in the presentation.
- **Quick security wins, same session:** B4, B5, W3, W4, A8.
- **You do in Cloud Console:** B6's daily quota on the Routes API.
