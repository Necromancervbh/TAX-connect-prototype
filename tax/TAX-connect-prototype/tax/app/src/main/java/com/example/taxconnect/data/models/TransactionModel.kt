package com.example.taxconnect.data.models

import com.google.firebase.Timestamp

data class TransactionModel(
    var transactionId: String? = null,
    var userId: String? = null,
    var caId: String? = null,
    var caName: String? = null,
    var description: String? = null,
    var amount: Double = 0.0,
    var status: String? = null, // "SUCCESS", "FAILED", "PENDING"
    var timestamp: Timestamp? = null,
    var razorpayPaymentId: String? = null,
    
    // Enhanced Fields for better querying
    var type: String? = null, // "WALLET_DEPOSIT", "WALLET_WITHDRAWAL", "SERVICE_PAYMENT", "SERVICE_INCOME"
    var userName: String? = null, // Denormalized for easy display
    var userEmail: String? = null // Denormalized for easy search
) {
    constructor(transactionId: String, userId: String, caId: String, caName: String, description: String, amount: Double, status: String) : this(
        transactionId = transactionId,
        userId = userId,
        caId = caId,
        caName = caName,
        description = description,
        amount = amount,
        status = status,
        timestamp = Timestamp.now(),
        razorpayPaymentId = null,
        type = "GENERAL",
        userName = null,
        userEmail = null
    )
}
