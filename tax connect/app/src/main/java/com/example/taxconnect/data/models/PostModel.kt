package com.example.taxconnect.data.models

import java.io.Serializable
import java.util.Date

data class PostModel(
    var id: String? = null,
    var userId: String? = null,
    var userName: String? = null,
    var userRole: String? = null, // "Client" or "CA"
    var content: String? = null,
    var timestamp: Date? = null,
    var likeCount: Int = 0,
    var commentCount: Int = 0,
    var tags: List<String>? = null,
    var likedBy: MutableList<String> = java.util.ArrayList()
) : Serializable
