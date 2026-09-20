package com.histoury.app.data.model

/**
 * A non-heritage point of interest inside Intramuros — cafés, restaurants,
 * parks, hotels, souvenir shops, schools, banks — shown in Home's "Explore
 * Intramuros" section.
 *
 * Stored in the `places` collection, shared with the admin panel's Places
 * Management module. Mirrors the historical_sites slug convention:
 * [placeId] is the business slug, [documentId] the Firestore id.
 */
data class Place(
    val documentId: String = "",

    val placeId: String = "",

    val name: String = "",

    // Admin panel values: "food", "park", "hotel", "souvenir_shop",
    // "school", "bank". "cafe"/"restaurant" are also recognized for any
    // older entries created before Places Management unified them under
    // "food".
    val category: String = "",

    val location: String = "",

    val description: String = "",

    val latitude: Double? = null,

    val longitude: Double? = null,

    val featuredImage: String = "",

    // Extra photos beyond the cover — shown in PlaceDetailsScreen's gallery
    // strip. Written by the admin panel's "Gallery Images" uploader.
    val galleryImages: List<String> = emptyList(),

    // The admin panel's richer listing fields. All optional — blank/empty
    // just means that row is skipped when rendering PlaceDetailsScreen.
    val fullDescription: String = "",

    val address: String = "",

    val contactPhone: String = "",

    val contactWebsite: String = "",

    val contactFacebook: String = "",

    val contactEmail: String = "",

    val openingHours: String = "",

    // Free text, e.g. "₱75" or "Free" — blank means not set. Matches the
    // admin panel's "Entrance Fee (optional)" field name exactly.
    val entranceFee: String = "",

    val status: String = "published"
) {

    val categoryLabel: String
        get() = when (category.lowercase()) {
            "food", "cafe" -> "Café"
            "restaurant" -> "Restaurant"
            "park" -> "Park"
            "hotel" -> "Hotel"
            "souvenir_shop" -> "Souvenir Shop"
            "school" -> "School"
            "bank" -> "Bank"
            else -> "Place"
        }

    val categoryEmoji: String
        get() = when (category.lowercase()) {
            "food", "cafe" -> "\u2615"          // ☕
            "restaurant" -> "\uD83C\uDF7D\uFE0F" // 🍽️
            "park" -> "\uD83C\uDF33"    // 🌳
            "hotel" -> "\uD83C\uDFE8"   // 🏨
            "souvenir_shop" -> "\uD83D\uDECD\uFE0F" // 🛍️
            "school" -> "\uD83C\uDF93"  // 🎓
            "bank" -> "\uD83C\uDFE6"    // 🏦
            else -> "\uD83D\uDCCD"      // 📍
        }
}

/**
 * Adapts a Place into the HistoricalSite shape so the existing Map screen
 * (directions, travel modes, live navigation, arrival detection) works for
 * places without any changes — MapViewModel already builds its destination
 * from site coordinates alone since sites became coordinate-canonical.
 */
fun Place.toSiteShape(): HistoricalSite {
    return HistoricalSite(
        documentId = documentId,
        siteId = placeId.ifBlank { documentId },
        siteName = name,
        location = location,
        description = description,
        latitude = latitude,
        longitude = longitude,
        featuredImage = featuredImage,
        entranceFee = entranceFee,
        status = status
    )
}
