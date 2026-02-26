package com.example.taxconnect.data.models

import java.io.Serializable

data class ServiceModel(
    var id: String? = null,
    var caId: String? = null,
    var title: String? = null,
    var description: String? = null,
    var price: String? = null,
    var estimatedTime: String? = null // e.g., "2 Days"
) : Serializable
