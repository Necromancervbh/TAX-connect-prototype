package com.example.taxconnect.core.common

object Constants {
    
    // Firebase Collections
    object Collections {
        const val USERS = "users"
        const val BOOKINGS = "bookings"
        const val CONVERSATIONS = "conversations"
        const val MESSAGES = "messages"
        const val TRANSACTIONS = "transactions"
        const val NOTIFICATIONS = "notifications"
        const val RATINGS = "ratings"
        const val POSTS = "posts"
        const val COMMENTS = "comments"
        const val SERVICES = "services"
        const val MILESTONES = "milestones"
        const val DOCUMENTS = "documents"
        const val CERTIFICATES = "certificates"
        const val REQUESTS = "requests"
        const val ANALYTICS = "analytics"
    }
    
    // User Roles
    object UserRoles {
        const val CA = "CA"
        const val CUSTOMER = "Customer"
        const val ADMIN = "Admin"
    }
    
    // Booking Status
    object BookingStatus {
        const val PENDING = "pending"
        const val CONFIRMED = "confirmed"
        const val COMPLETED = "completed"
        const val CANCELLED = "cancelled"
        const val RESCHEDULED = "rescheduled"
    }
    
    // Request Status
    object RequestStatus {
        const val PENDING = "pending"
        const val ACCEPTED = "accepted"
        const val REJECTED = "rejected"
        const val COMPLETED = "completed"
    }
    
    // Payment Status
    object PaymentStatus {
        const val PENDING = "pending"
        const val SUCCESS = "success"
        const val FAILED = "failed"
        const val REFUNDED = "refunded"
    }
    
    // Notification Types
    object NotificationTypes {
        const val BOOKING_REQUEST = "booking_request"
        const val BOOKING_CONFIRMED = "booking_confirmed"
        const val BOOKING_CANCELLED = "booking_cancelled"
        const val PAYMENT_RECEIVED = "payment_received"
        const val PAYMENT_SENT = "payment_sent"
        const val NEW_MESSAGE = "new_message"
        const val RATING_RECEIVED = "rating_received"
        const val DOCUMENT_SHARED = "document_shared"
        const val MILESTONE_UPDATED = "milestone_updated"
    }
    
    // Intent Keys
    object IntentKeys {
        const val USER_ID = "USER_ID"
        const val CA_ID = "CA_ID"
        const val BOOKING_ID = "BOOKING_ID"
        const val CONVERSATION_ID = "CONVERSATION_ID"
        const val OTHER_USER_ID = "OTHER_USER_ID"
        const val OTHER_USER_NAME = "OTHER_USER_NAME"
        const val POST_ID = "POST_ID"
        const val DOCUMENT_ID = "DOCUMENT_ID"
        const val VIDEO_URL = "VIDEO_URL"
        const val TITLE = "TITLE"
        const val CHANNEL_NAME = "CHANNEL_NAME"
        const val TOKEN = "TOKEN"
        const val IS_CALLER = "IS_CALLER"
    }
    
    // API Keys - Use BuildConfig fields instead of hardcoding
    // Keys are read from local.properties via build.gradle.kts BuildConfig fields:
    //   BuildConfig.RAZORPAY_API_KEY
    //   BuildConfig.AGORA_APP_ID
    //   BuildConfig.CLOUDINARY_CLOUD_NAME
    //   BuildConfig.CLOUDINARY_UPLOAD_PRESET
    
    // Pagination
    object Pagination {
        const val DEFAULT_PAGE_SIZE = 20
        const val CA_PAGE_SIZE = 10
        const val MESSAGE_PAGE_SIZE = 50
        const val NOTIFICATION_PAGE_SIZE = 30
    }
    
    // Timeouts
    object Timeouts {
        const val NETWORK_TIMEOUT = 30000L // 30 seconds
        const val CALL_TIMEOUT = 60000L // 60 seconds
        const val UPLOAD_TIMEOUT = 120000L // 120 seconds
    }
    
    // File Types
    object FileTypes {
        const val IMAGE = "image"
        const val VIDEO = "video"
        const val DOCUMENT = "document"
        const val PDF = "pdf"
        const val EXCEL = "excel"
        const val OTHER = "other"
    }
    
    // File Extensions
    object FileExtensions {
        val IMAGES = listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
        val VIDEOS = listOf("mp4", "avi", "mov", "wmv", "flv", "mkv")
        val DOCUMENTS = listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt")
    }
    
    // Validation Rules
    object Validation {
        const val MIN_PASSWORD_LENGTH = 6
        const val MAX_PASSWORD_LENGTH = 128
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 50
        const val MIN_PHONE_LENGTH = 10
        const val MAX_PHONE_LENGTH = 15
        const val MAX_BIO_LENGTH = 500
        const val MAX_SPECIALIZATION_LENGTH = 200
    }
    
    // Error Messages
    object ErrorMessages {
        const val NETWORK_ERROR = "Network error. Please check your connection."
        const val AUTHENTICATION_ERROR = "Authentication failed. Please login again."
        const val PERMISSION_DENIED = "Permission denied."
        const val DOCUMENT_NOT_FOUND = "Document not found."
        const val INVALID_INPUT = "Invalid input. Please check your data."
        const val UPLOAD_FAILED = "Upload failed. Please try again."
        const val PAYMENT_FAILED = "Payment failed. Please try again."
        const val UNKNOWN_ERROR = "An unknown error occurred."
    }
}