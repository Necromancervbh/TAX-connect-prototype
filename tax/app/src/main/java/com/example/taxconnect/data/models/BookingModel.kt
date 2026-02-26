package com.example.taxconnect.data.models

import java.io.Serializable

data class BookingModel(
    var id: String? = null,
    var caId: String? = null,
    var userId: String? = null,
    var userName: String? = null, // User's name for CA to see
    var caName: String? = null, // CA's name for User to see
    var appointmentTimestamp: Long = 0,
    var status: String? = null, // PENDING, CONFIRMED, COMPLETED, CANCELLED
    var createdAt: Long = System.currentTimeMillis(),
    var message: String? = null
) : Serializable
