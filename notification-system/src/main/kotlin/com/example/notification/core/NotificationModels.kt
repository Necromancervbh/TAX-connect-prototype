package com.example.notification.core

import java.util.*

/**
 * Core notification model representing a single notification event.
 */
data class NotificationEvent(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val category: NotificationCategory,
    val priority: Priority = Priority.MEDIUM,
    val payload: Map<String, Any>,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

enum class NotificationCategory {
    TRANSACTIONAL, MARKETING, SECURITY, SYSTEM
}

enum class Priority {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * Routing logic to determine which channels should receive the notification
 * based on user preferences and channel health.
 */
class NotificationRouter(
    private val preferenceService: PreferenceService,
    private val channelHealthMonitor: ChannelHealthMonitor
) {
    suspend fun route(event: NotificationEvent): List<DeliveryChannel> {
        val userPreferences = preferenceService.getPreferences(event.userId)
        val preferredChannels = userPreferences.getEnabledChannels(event.category)
        
        return preferredChannels.filter { channel ->
            channelHealthMonitor.isHealthy(channel)
        }
    }
}

interface PreferenceService {
    suspend fun getPreferences(userId: String): UserPreferences
}

interface ChannelHealthMonitor {
    fun isHealthy(channel: DeliveryChannel): Boolean
}

enum class DeliveryChannel {
    EMAIL, SMS, PUSH, IN_APP, WEBHOOK
}

data class UserPreferences(
    val userId: String,
    val channelSettings: Map<NotificationCategory, Set<DeliveryChannel>>
) {
    fun getEnabledChannels(category: NotificationCategory): Set<DeliveryChannel> {
        return channelSettings[category] ?: emptySet()
    }
}
