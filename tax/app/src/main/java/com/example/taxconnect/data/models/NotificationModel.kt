package com.example.taxconnect.data.models

data class NotificationModel(
    val id: String? = null,
    val userId: String? = null,
    val type: String? = null, // message, call, request, booking
    val title: String? = null,
    val body: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val data: Map<String, String>? = null,
    var read: Boolean = false
)
