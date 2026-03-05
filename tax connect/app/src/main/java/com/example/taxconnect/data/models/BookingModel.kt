package com.example.taxconnect.data.models

import java.io.Serializable

data class BookingModel(
    var id: String? = null,
    var caId: String? = null,
    var userId: String? = null,
    var userName: String? = null,         // User's name for CA to see
    var caName: String? = null,           // CA's name for User to see
    var serviceId: String? = null,
    var serviceName: String? = null,      // Selected service label
    var appointmentTimestamp: Long = 0,
    var appointmentDate: String? = null,  // Human-readable date string, e.g. "21 Mar 2026"
    var appointmentTime: String? = null,  // Human-readable time string, e.g. "10:00 AM"
    var status: String? = null,           // PENDING, ACCEPTED, COMPLETED, CANCELLED, REJECTED, EXPIRED
    var chatId: String? = null,           // Set after CA accepts — links to conversation
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = 0,
    var message: String? = null,
    var rejectionReason: String? = null,  // Reason provided by CA when rejecting
    var appointmentHour: Int = -1,        // 24-hour component of the slot (0-23); -1 = not set
    var appointmentMinute: Int = 0,       // Minute component of the slot
    var totalAmount: Double = 0.0,
    var advanceAmount: Double = 0.0
) : Serializable
