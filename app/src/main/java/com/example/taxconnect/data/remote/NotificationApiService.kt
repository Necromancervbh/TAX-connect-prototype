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

data class NotificationResponse(
    val success: Boolean,
    val message: String?,
    val error: String?
)

interface NotificationApiService {
    @POST("sendChatNotification")
    suspend fun sendChatNotification(@Body request: ChatNotificationRequest): Response<NotificationResponse>
}
