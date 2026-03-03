package com.example.taxconnect.data.models

import java.io.Serializable

data class CertificateModel(
    var id: String? = null,
    var name: String? = null,
    var url: String? = null,
    var type: String? = null, // "image" or "pdf"
    var timestamp: Long = 0
) : Serializable
