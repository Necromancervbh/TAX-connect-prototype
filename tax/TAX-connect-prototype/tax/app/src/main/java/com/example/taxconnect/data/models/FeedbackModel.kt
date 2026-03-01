package com.example.taxconnect.data.models

import java.io.Serializable

data class FeedbackModel(
    val id: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val message: String? = null,
    val rating: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceModel: String = android.os.Build.MODEL,
    val androidVersion: String = android.os.Build.VERSION.RELEASE
) : Serializable
