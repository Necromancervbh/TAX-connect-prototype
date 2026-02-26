package com.example.taxconnect.data.models

import com.google.firebase.Timestamp

data class MilestoneModel(
    var id: String? = null,
    var bookingId: String? = null,
    var title: String? = null,
    var description: String? = null,
    var status: String? = null, // PENDING, IN_PROGRESS, COMPLETED
    var timestamp: Timestamp? = null
)
