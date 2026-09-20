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

    val status: String = "",

    val visitId: String = "",

    val sceneId: String = "",

    val edited: Boolean = false,

    val createdAt: Any? = null,

    val updatedAt: Any? = null

)
