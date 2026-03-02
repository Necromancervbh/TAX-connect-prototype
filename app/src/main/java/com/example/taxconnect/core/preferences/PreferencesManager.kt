package com.example.taxconnect.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREFS_NAME = "taxconnect_preferences"
        
        // User Preferences
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_PHOTO_URL = "user_photo_url"
        private const val KEY_IS_FIRST_TIME = "is_first_time"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        
        // App Preferences
        private const val KEY_SELECTED_THEME = "selected_theme"
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_CURRENCY = "currency"
        
        // Cache Preferences
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_CACHE_EXPIRY = "cache_expiry"
        private const val KEY_OFFLINE_MODE = "offline_mode"
        
        // Feature Flags
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        
        // Default Values
        private const val DEFAULT_LANGUAGE = "en"
        private const val DEFAULT_CURRENCY = "INR"
        private const val DEFAULT_THEME = "system"
    }

    // User Preferences
    var userId: String?
        get() = sharedPreferences.getString(KEY_USER_ID, null)
        set(value) = sharedPreferences.edit { putString(KEY_USER_ID, value) }

    var userName: String?
        get() = sharedPreferences.getString(KEY_USER_NAME, null)
        set(value) = sharedPreferences.edit { putString(KEY_USER_NAME, value) }

    var userEmail: String?
        get() = sharedPreferences.getString(KEY_USER_EMAIL, null)
        set(value) = sharedPreferences.edit { putString(KEY_USER_EMAIL, value) }

    var userRole: String?
        get() = sharedPreferences.getString(KEY_USER_ROLE, null)
        set(value) = sharedPreferences.edit { putString(KEY_USER_ROLE, value) }

    var userPhotoUrl: String?
        get() = sharedPreferences.getString(KEY_USER_PHOTO_URL, null)
        set(value) = sharedPreferences.edit { putString(KEY_USER_PHOTO_URL, value) }

    var isFirstTime: Boolean
        get() = sharedPreferences.getBoolean(KEY_IS_FIRST_TIME, true)
        set(value) = sharedPreferences.edit { putBoolean(KEY_IS_FIRST_TIME, value) }

    var isLoggedIn: Boolean
        get() = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = sharedPreferences.edit { putBoolean(KEY_IS_LOGGED_IN, value) }

    // App Preferences
    var selectedTheme: String
        get() = sharedPreferences.getString(KEY_SELECTED_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        set(value) = sharedPreferences.edit { putString(KEY_SELECTED_THEME, value) }

    var isNotificationEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_NOTIFICATION_ENABLED, true)
        set(value) = sharedPreferences.edit { putBoolean(KEY_NOTIFICATION_ENABLED, value) }

    var isSoundEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = sharedPreferences.edit { putBoolean(KEY_SOUND_ENABLED, value) }

    var isVibrationEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = sharedPreferences.edit { putBoolean(KEY_VIBRATION_ENABLED, value) }

    var language: String
        get() = sharedPreferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = sharedPreferences.edit { putString(KEY_LANGUAGE, value) }

    var currency: String
        get() = sharedPreferences.getString(KEY_CURRENCY, DEFAULT_CURRENCY) ?: DEFAULT_CURRENCY
        set(value) = sharedPreferences.edit { putString(KEY_CURRENCY, value) }

    // Cache Preferences
    var lastSyncTime: Long
        get() = sharedPreferences.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = sharedPreferences.edit { putLong(KEY_LAST_SYNC_TIME, value) }

    var cacheExpiry: Long
        get() = sharedPreferences.getLong(KEY_CACHE_EXPIRY, 24 * 60 * 60 * 1000L) // 24 hours default
        set(value) = sharedPreferences.edit { putLong(KEY_CACHE_EXPIRY, value) }

    var isOfflineMode: Boolean
        get() = sharedPreferences.getBoolean(KEY_OFFLINE_MODE, false)
        set(value) = sharedPreferences.edit { putBoolean(KEY_OFFLINE_MODE, value) }

    // Feature Flags
    var isBiometricEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = sharedPreferences.edit { putBoolean(KEY_BIOMETRIC_ENABLED, value) }

    var isDarkModeEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_DARK_MODE_ENABLED, false)
        set(value) = sharedPreferences.edit { putBoolean(KEY_DARK_MODE_ENABLED, value) }

    var isAutoBackupEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_BACKUP_ENABLED, true)
        set(value) = sharedPreferences.edit { putBoolean(KEY_AUTO_BACKUP_ENABLED, value) }

    // Clear Methods
    fun clearUserData() {
        sharedPreferences.edit {
            remove(KEY_USER_ID)
            remove(KEY_USER_NAME)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_ROLE)
            remove(KEY_USER_PHOTO_URL)
            putBoolean(KEY_IS_LOGGED_IN, false)
        }
    }

    fun clearAll() {
        sharedPreferences.edit { clear() }
    }

    fun clearCache() {
        sharedPreferences.edit {
            remove(KEY_LAST_SYNC_TIME)
        }
    }

    // Utility Methods
    fun hasUserData(): Boolean {
        return !userId.isNullOrEmpty() && isLoggedIn
    }

    fun isCacheExpired(): Boolean {
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastSyncTime) > cacheExpiry
    }

    fun updateSyncTime() {
        lastSyncTime = System.currentTimeMillis()
    }

    // Extension function for SharedPreferences.Editor
    private inline fun SharedPreferences.edit(operation: SharedPreferences.Editor.() -> Unit) {
        val editor = edit()
        operation(editor)
        editor.apply()
    }
}