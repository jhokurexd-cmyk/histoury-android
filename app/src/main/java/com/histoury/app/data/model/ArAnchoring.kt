package com.histoury.app.data.model

/**
 * AR anchoring as configured in the admin panel.
 *
 * The admin panel (utils/arAnchoring.js) writes these fields onto each
 * historical_sites document. This file is the Android side of that same
 * contract, so the "is this site's AR ready?" answer is identical in the
 * panel's list, the Site Details button and the AR screen.
 */

/** One reference photo and where the model sits relative to it. */
data class ArReferenceImage(

    // Stable id from the admin panel. Used as the image's name in the
    // AugmentedImageDatabase, so a recognised image maps back to its entry.
    val id: String = "",

    // Admin-facing label ("Front gate", "Facing north-east").
    val label: String = "",

    val url: String = "",

    // Yaw relative to this photo, degrees. Not a compass bearing.
    val headingDegrees: Double = 0.0,

    val offsetRightMeters: Double = 0.0,

    val offsetUpMeters: Double = 0.0,

    val offsetForwardMeters: Double = 0.0,

    // Where the historical STRUCTURE sits inside this photo, as fractions of
    // the full image (0 = the image's own top/left edge, 1 = its bottom/
    // right edge). Set in the admin panel by dragging a box over just the
    // structure — not the sky, ground, trees or margin around it.
    //
    // ARCore only ever measures the physical size of the WHOLE photo it
    // recognises; it has no idea the building only fills the middle 60% of
    // the frame. Scaling the model off the full photo size would make it as
    // tall as the photo's background too. These fractions are what let the
    // AR screen convert "ARCore says the photo is 3m tall" into "the
    // structure inside it is 2.1m tall" before sizing the model.
    //
    // Default is the full frame (0..1), so a photo saved before this box
    // existed keeps behaving the way it always did rather than collapsing to
    // a zero-height structure.
    val structureTopFraction: Double = 0.0,

    val structureBottomFraction: Double = 1.0,

    val structureLeftFraction: Double = 0.0,

    val structureRightFraction: Double = 1.0
)

/**
 * Whether the AR experience can actually start for this site.
 *
 * Two things, and only two: the admin switched AR on, and there is a model
 * to place. That is the whole contract now.
 *
 * It used to also demand a complete anchoring method — a reference photo or
 * a geospatial placement — because the app decided where the structure went
 * and could not do that without one. Placement is manual now: the visitor
 * aims at a floor and taps. There is nothing left for the app to be missing.
 *
 * Leaving the old requirement in place was a real bug, not a tidy-up: the
 * admin panel deletes the reference-photo and coordinate fields when it
 * saves a scene, so every newly configured site satisfied `arEnabled` and
 * `arModelUrl` and then failed this check — Site Details showed "AR
 * experience coming soon" and ArModelCache silently skipped the download,
 * for a site that was completely set up.
 *
 * WHERE the visitor may run it is a separate question, answered by the
 * site's geofence inside the AR screen itself (see ArExperienceViewModel.
 * verifyGeofence). This property is only about whether there is anything to
 * show.
 */
val HistoricalSite.isArReady: Boolean
    get() = arEnabled && arModelUrl.isNotBlank()
