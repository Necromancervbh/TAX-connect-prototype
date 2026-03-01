package com.example.taxconnect.data.models

data class ConversationModel(
    var conversationId: String? = null,
    var participantIds: List<String>? = null, // [uid1, uid2]
    var lastMessage: String? = null,
    var lastMessageTimestamp: Long = 0,
    var otherUserName: String? = null, // Cached name for display
    var otherUserEmail: String? = null, // Cached email
    var otherUserProfileImage: String? = null, // Cached profile image
    var workflowState: String = STATE_DISCUSSION,
    var isVideoCallAllowed: Boolean = false, // Default disabled
    var callStatus: String? = null, // ACTIVE, REJECTED, ENDED
    var currentServiceCycleId: String? = null,
    var serviceCycleSequence: Int = 0,
    var lastServiceCompletedAt: Long = 0,
    var unreadCounts: MutableMap<String, Int> = HashMap()
) {
    companion object {
        // Workflow States
        const val STATE_REQUESTED = "Requested"
        const val STATE_REFUSED = "Refused"
        const val STATE_ACCEPTED = "Accepted"
        const val STATE_DISCUSSION = "Discussion"
        const val STATE_PRICE_NEGOTIATION = "Price Negotiation"
        const val STATE_PRICE_AGREEMENT = "Price Agreement"
        const val STATE_ADVANCE_PAYMENT = "Advance Payment"
        const val STATE_DOCS_REQUEST = "Document Request"
        const val STATE_FINAL_PAYMENT = "Final Payment"
        const val STATE_COMPLETED = "Completed"
        const val STATE_DOCS_PENDING = "Documents Pending"
        const val STATE_PAYMENT_PENDING = "Payment Pending"
    }
}
