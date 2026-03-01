package com.example.taxconnect.data.models

import com.google.firebase.Timestamp

data class DocumentModel(
    var id: String? = null,
    var name: String? = null,
    var url: String? = null,
    var type: String? = null, // pdf, image, etc.
    var category: String? = null,
    var uploadedAt: Timestamp? = null
)
