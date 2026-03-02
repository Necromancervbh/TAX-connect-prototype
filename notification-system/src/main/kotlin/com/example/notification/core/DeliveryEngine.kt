package com.example.notification.core

import kotlinx.coroutines.flow.Flow

/**
 * Interface for the event streaming backbone (Kafka/Pulsar).
 * Ensures sub-second latency and high throughput.
 */
interface EventStream {
    suspend fun publish(event: NotificationEvent)
    fun consume(): Flow<NotificationEvent>
}

/**
 * Deduplication service to ensure at-least-once delivery without duplicates.
 * Uses Redis for high-speed lookups.
 */
interface Deduplicator {
    suspend fun isDuplicate(eventId: String): Boolean
    suspend fun markProcessed(eventId: String)
}

/**
 * Rate limiter to protect downstream providers and comply with user limits.
 */
interface RateLimiter {
    suspend fun isAllowed(userId: String, channel: DeliveryChannel): Boolean
}

/**
 * Delivery engine responsible for executing the actual delivery via providers.
 */
class DeliveryEngine(
    private val eventStream: EventStream,
    private val router: NotificationRouter,
    private val deduplicator: Deduplicator,
    private val rateLimiter: RateLimiter,
    private val auditLogger: AuditLogger
) {
    suspend fun startProcessing() {
        eventStream.consume().collect { event ->
            if (deduplicator.isDuplicate(event.id)) return@collect
            
            val channels = router.route(event)
            channels.forEach { channel ->
                if (rateLimiter.isAllowed(event.userId, channel)) {
                    deliver(event, channel)
                }
            }
            
            deduplicator.markProcessed(event.id)
        }
    }

    private suspend fun deliver(event: NotificationEvent, channel: DeliveryChannel) {
        // Implementation for specific channel delivery (e.g., SendGrid, Twilio, FCM)
        auditLogger.logDelivery(event, channel, DeliveryStatus.SUCCESS)
    }
}

interface AuditLogger {
    suspend fun logDelivery(event: NotificationEvent, channel: DeliveryChannel, status: DeliveryStatus)
}

enum class DeliveryStatus {
    SUCCESS, FAILED, RETRYING, RATE_LIMITED
}
