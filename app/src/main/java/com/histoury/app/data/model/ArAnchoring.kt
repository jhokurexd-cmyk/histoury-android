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
 * How much of the photo's physical HEIGHT the structure itself occupies,
 * clamped to a sane range.
 *
 * Firestore data that is missing, inverted (bottom above top) or otherwise
 * nonsensical falls back to the full frame (1f) rather than producing a
 * zero, negative, or wildly wrong scale factor — the same fail-safe the
 * admin panel's own editor applies when saving.
 */
val ArReferenceImage.structureHeightFraction: Float
    get() {
        val top = structureTopFraction.toFloat().coerceIn(0f, 1f)
        val bottom = structureBottomFraction.toFloat().coerceIn(0f, 1f)
        val fraction = bottom - top
        return if (fraction >= 0.01f) fraction else 1f
    }

enum class ArAnchorMode {
    IMAGE,
    GEOSPATIAL,
    BOTH;

    val usesImage: Boolean get() = this == IMAGE || this == BOTH

    val usesGeospatial: Boolean get() = this == GEOSPATIAL || this == BOTH

    companion object {
        /** Missing or unknown -> IMAGE, which is what every older site used. */
        fun from(raw: String?): ArAnchorMode = when (raw?.trim()?.lowercase()) {
            "geospatial" -> GEOSPATIAL
            "both" -> BOTH
            else -> IMAGE
        }
    }
}

/**
 * The site's anchoring mode.
 *
 * When the admin panel hasn't recorded one (sites saved before the choice
 * existed), a site that has a geospatial placement and no reference photo
 * is treated as GEOSPATIAL — it must deploy by location, not wait for a
 * scan of a photo it doesn't have. Everything else defaults to IMAGE.
 */
val HistoricalSite.anchorMode: ArAnchorMode
    get() = if (arAnchorMode.isBlank()) {
        if (hasGeospatialPlacement && referenceImages.isEmpty()) {
            ArAnchorMode.GEOSPATIAL
        } else {
            ArAnchorMode.IMAGE
        }
    } else {
        ArAnchorMode.from(arAnchorMode)
    }

/**
 * The reference photos to recognise, primary first.
 *
 * Newer sites list them in [HistoricalSite.arReferenceImages]. Older ones
 * only have the single-photo fields, which become a one-entry list here so
 * the AR screen has one code path.
 */
val HistoricalSite.referenceImages: List<ArReferenceImage>
    get() {
        val listed = arReferenceImages.filter { it.url.isNotBlank() }
        if (listed.isNotEmpty()) {
            return listed.mapIndexed { index, image ->
                if (image.id.isBlank()) image.copy(id = "reference_$index") else image
            }
        }
        if (arReferenceImageUrl.isBlank()) return emptyList()
        return listOf(
            ArReferenceImage(
                id = "reference_primary",
                label = "Primary photo",
                url = arReferenceImageUrl,
                headingDegrees = arHeadingDegrees,
                offsetRightMeters = arOffsetRightMeters,
                offsetUpMeters = arOffsetUpMeters,
                offsetForwardMeters = arOffsetForwardMeters
            )
        )
    }

val HistoricalSite.hasImagePlacement: Boolean
    get() = referenceImages.isNotEmpty()

/** Same test as the admin panel's hasGeospatialPlacement. */
val HistoricalSite.hasGeospatialPlacement: Boolean
    get() {
        val lat = arLatitude ?: return false
        val lng = arLongitude ?: return false
        return lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0 &&
            !(lat == 0.0 && lng == 0.0) &&
            arModelScale.isFinite() && arModelScale > 0.0
    }

/**
 * Image recognition is switched on AND has at least one photo. False for
 * geospatial scenes even if old photo fields linger on the document, so
 * those scenes never scan.
 */
val HistoricalSite.imageAnchoringAvailable: Boolean
    get() = anchorMode.usesImage && hasImagePlacement

/** Geospatial is switched on AND has a complete placement. */
val HistoricalSite.geospatialAnchoringAvailable: Boolean
    get() = anchorMode.usesGeospatial && hasGeospatialPlacement

/**
 * Whether the AR experience can actually start: enabled, has a model, and
 * at least one complete anchoring method for its mode.
 */
val HistoricalSite.isArReady: Boolean
    get() = arEnabled && arModelUrl.isNotBlank() &&
        (imageAnchoringAvailable || geospatialAnchoringAvailable)
