package com.histoury.app.data.model

data class Review(

    val id: String = "",

    val siteId: String = "",

    val userId: String = "",

    val userName: String = "",

    val rating: Double = 0.0,

    val arExperienceRating: Double = 0.0,

    val reviewText: String = "",

    val likes: Int = 0,

    /** Reports received (maintained by the reportReview Cloud Function). */
    val reportCount: Int = 0,

    /** "visit", "ar_prompt" or "site_details" — where it was written. */
    val source: String = "",

    val status: String = "",

    val visitId: String = "",

    val sceneId: String = "",

    val edited: Boolean = false,

    val createdAt: Any? = null,

    val updatedAt: Any? = null

)
