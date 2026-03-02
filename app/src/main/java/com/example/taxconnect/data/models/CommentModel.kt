package com.example.taxconnect.data.models

import java.io.Serializable
import java.util.Date

data class CommentModel(
    var id: String? = null,
    var postId: String? = null,
    var userId: String? = null,
    var userName: String? = null,
    var userRole: String? = null,
    var content: String? = null,
    var timestamp: Date? = null
) : Serializable
