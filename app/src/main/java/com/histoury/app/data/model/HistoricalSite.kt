package com.histoury.app.data.model

data class HistoricalSite(

    val documentId: String = "",

    val siteId: String = "",

    val siteName: String = "",

    val location: String = "",

    // Canonical site coordinates (added alongside the geofence's own
    // center). Nullable: older documents may not have them yet.
    val latitude: Double? = null,

    val longitude: Double? = null,

    val description: String = "",

    val featuredImage: String = "",

    val averageRating: Double = 0.0,

    val reviewCount: Int = 0,

    val visitCount: Int = 0,

    val tagIds: List<String> = emptyList(),

    // Free text, e.g. "₱75" or "Free" — blank means not set. Matches the
    // admin panel's "Entrance Fee (optional)" field name exactly.
    val entranceFee: String = "",

    val status: String = "",

    // ----- AR experience -----

    // Master switch set from the admin panel. False (the default) keeps the
    // Site Details button in its locked "Coming Soon" state, which is how
    // every site other than Fort Santiago behaves.
    val arEnabled: Boolean = false,

    // Firebase Storage download URL of the .glb model.
    val arModelUrl: String = "",

    // Real-world length in metres of the model's longest side, as set in the
    // admin panel. Required for geospatial placement (there is no photo to
    // measure against). With image recognition it is optional: when set it
    // overrides the camera's own size estimate. 0 means "not set" — the
    // admin panel deletes the field rather than writing a default.
    //
    // Double, not Float: Firestore stores every decimal written by the
    // JavaScript admin panel as a Double, and a Float here makes decoding
    // fail on some Firestore SDK versions.
    val arModelScale: Double = 0.0,

    // Optional mobile smoke, configured separately from the ruins GLB.
    val arSmoke: ArSmokeSettings = ArSmokeSettings(),

    // Which anchoring method(s) the admin panel set up for this site:
    // "image", "geospatial" or "both". Blank on sites saved before the
    // choice existed — those were all image recognition. See ArAnchoring.kt.
    val arAnchorMode: String = "",

    // ----- Geospatial anchoring -----

    // Where the model stands, independent of [latitude]/[longitude]: the
    // geofence centre is a good place to trigger arrival from, but rarely
    // the exact spot a structure should occupy.
    val arLatitude: Double? = null,

    val arLongitude: Double? = null,

    // Compass bearing the model's front faces, 0 = north, clockwise.
    // Separate from [arHeadingDegrees], which is relative to a photo.
    val arGeoHeadingDegrees: Double = 0.0,

    // Lifts or sinks the model relative to the terrain at the pin. Terrain
    // anchors resolve their own altitude, so this only corrects for models
    // whose origin isn't at their base.
    val arAltitudeOffsetMeters: Double = 0.0,

    // ----- Augmented-image anchoring -----
    //
    // Geospatial anchoring positions the model from latitude/longitude,
    // which needs a clear view of the sky and of surrounding facades. Inside
    // Intramuros — narrow streets, crowds, repetitive stonework — that fix
    // often never converges, so the model lands off-target and drifts.
    //
    // Augmented images invert the problem. Instead of asking "where on Earth
    // is the phone", ARCore recognizes the photographed structure itself and
    // reports where it is relative to the camera. Alignment then holds
    // regardless of GPS, because it is measured against the thing the model
    // is meant to replace.
    //
    // Both methods can be configured at once ([arAnchorMode] = "both"); the
    // AR screen then places the model from whichever locks on first, and
    // prefers the photo when it gets both.

    // Every reference photo, each with its own placement. ARCore treats each
    // as a separate flat target and reports where THAT photo is, so the
    // offsets and rotation are per photo. Empty on sites saved before
    // multiple photos existed — those use the single-photo fields below.
    val arReferenceImages: List<ArReferenceImage> = emptyList(),

    // The PRIMARY reference photo — also the first entry of
    // [arReferenceImages] on newer sites. A flat-on, evenly lit shot of the
    // structure, with no people in frame.
    val arReferenceImageUrl: String = "",

    // Yaw relative to the primary photo, in degrees. Not a compass bearing.
    val arHeadingDegrees: Double = 0.0,

    // No longer written by the admin panel (ARCore now estimates the size);
    // kept so older documents still decode.
    val arImageWidthMeters: Double = 0.0,

    // Where the model sits relative to the centre of the photographed
    // structure, in metres, from the visitor's point of view when facing it.
    // All three default to zero, which places the model's origin exactly at
    // the centre of the reference image.

    // Positive moves the model to the visitor's right.
    val arOffsetRightMeters: Double = 0.0,

    // Positive raises the model. A gate photographed head-on has its centre
    // partway up the structure, so this is usually negative — the model's
    // base needs to drop to ground level.
    val arOffsetUpMeters: Double = 0.0,

    // Positive moves the model toward the visitor, negative pushes it back
    // behind the real facade. Useful when the historical structure stood
    // slightly forward of or behind what stands there now.
    val arOffsetForwardMeters: Double = 0.0
)
