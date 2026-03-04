package com.example.taxconnect.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class ChatNotificationRequest(
    val recipientId: String,
    val senderId: String,
    val senderName: String?,
    val senderImage: String?,
    val chatId: String,
    val messageContent: String?,
    val messageType: String
)

data class BookingNotificationRequest(
    val recipientId: String,
    val status: String,
    val bookingId: String,
    val chatId: String = "",
    val otherUserId: String = "",
    val otherUserName: String = "",
    val serviceName: String = "",
    val date: String = "",
    val time: String = "",
    val isRequest: Boolean = false,
    val senderName: String = ""
)

data class NotificationResponse(
    val success: Boolean,
    val message: String?,
    val error: String?
)

interface NotificationApiService {
    @POST("sendChatNotification")
    suspend fun sendChatNotification(@Body request: ChatNotificationRequest): Response<NotificationResponse>

    @POST("sendBookingNotification")
    suspend fun sendBookingNotification(@Body request: BookingNotificationRequest): Response<NotificationResponse>
}
