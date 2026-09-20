package com.histoury.app.data.model

data class VisitHistory(

    val id: String = "",

    val userId: String = "",

    val siteId: String = "",

    val sceneId: String = "",

    val visitDate: Any? = null,

    val duration: Long = 0L,

    val arActivated: Boolean = false,

    val photosCaptured: Int = 0,

    val reviewEligible: Boolean = false,

    val reviewSubmitted: Boolean = false,

    val status: String = ""

)
