package com.example.taxconnect.data.models

data class MessageModel(
    var senderId: String? = null,
    var receiverId: String? = null,
    var chatId: String? = null, // Unique ID for the conversation (uid1_uid2 sorted)
    var message: String? = null,
    var timestamp: Long = 0,
    var type: String? = null, // "TEXT" or "PROPOSAL"
    
    // Proposal specific fields
    var proposalDescription: String? = null,
    var proposalAmount: String? = null,
    var proposalStatus: String? = null, // "PENDING", "ACCEPTED", "REJECTED"
    var proposalAdvanceAmount: String? = null,
    var proposalFinalAmount: String? = null,
    var proposalAdvancePaid: Boolean? = null,
    var proposalFinalPaid: Boolean? = null,
    var proposalPaymentStage: String? = null,
    var proposalRejectionReason: String? = null,
    var proposalVersion: Int? = null,
    var proposalAdvancePaymentRef: String? = null,
    var proposalFinalPaymentRef: String? = null,
    var id: String? = null, // Firestore Document ID
    var imageUrl: String? = null, // For image messages

    // Payment Request Fields
    var paymentRequestAmount: String? = null,
    var paymentRequestTotal: String? = null,
    var paymentRequestInstallment: Int? = null,
    var paymentRequestStatus: String? = null,
    var paymentDeclineReason: String? = null,
    var paymentStage: String? = null, // "ADVANCE" or "FINAL"
    var isRead: Boolean = false
)
