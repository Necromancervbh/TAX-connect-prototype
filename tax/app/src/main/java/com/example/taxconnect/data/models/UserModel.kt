package com.example.taxconnect.data.models

import com.google.firebase.firestore.PropertyName
import java.io.Serializable

data class UserModel(
    var uid: String? = null,
    var email: String? = null,
    var name: String? = null,
    var role: String? = null, // "CUSTOMER" or "CA"
    var city: String? = null,
    @get:PropertyName("online")
    @set:PropertyName("online")
    var isOnline: Boolean = false,
    var rating: Double = 0.0,
    var ratingCount: Int = 0,
    var blockedUsers: List<String> = ArrayList(),
    var bio: String? = null,

    // CA Specific Fields
    var caNumber: String? = null,
    var experience: String? = null,
    var specialization: String? = null,
    var minCharges: String? = null,
    var clientCount: Int = 0,

    // Customer Specific Fields
    var priceRange: String? = null,
    
    var profileImageUrl: String? = null,
    var introVideoUrl: String? = null,
    var fcmToken: String? = null,
    var notifyMessages: Boolean = true,
    var notifyCalls: Boolean = true,
    var notifyRequests: Boolean = true,
    var notifyBookings: Boolean = true,
    var quietHoursEnabled: Boolean = false,
    var quietHoursStart: String? = "22:00",
    var quietHoursEnd: String? = "07:00",
    var certificates: List<CertificateModel>? = null,
    var walletBalance: Double = 0.0,
    var isVerified: Boolean = false,
    var isVerificationRequested: Boolean = false,
    var phoneNumber: String? = null,
    
    // Archival and Audit Fields
    var isArchived: Boolean = false,
    var archivedAt: Long? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) : Serializable {
}
