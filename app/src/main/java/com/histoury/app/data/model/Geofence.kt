package com.histoury.app.data.model

data class Geofence(

    val id: String = "",

    val siteId: String = "",

    val latitude: Double = 0.0,

    val longitude: Double = 0.0,

    val radius: Double = 0.0,

    val status: String = "",

    val createdAt: Any? = null,

    val updatedAt: Any? = null

)