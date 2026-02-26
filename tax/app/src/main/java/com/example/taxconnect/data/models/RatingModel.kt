package com.example.taxconnect.data.models

import java.io.Serializable

data class RatingModel(
    var userId: String? = null,
    var userName: String? = null,
    var caId: String? = null,
    var rating: Float = 0f,
    var review: String? = null,
    var timestamp: Long = 0
) : Serializable
