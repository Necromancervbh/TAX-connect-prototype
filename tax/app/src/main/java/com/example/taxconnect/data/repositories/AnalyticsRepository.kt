package com.example.taxconnect.data.repositories

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AnalyticsRepository private constructor(context: Context?) {
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private var firebaseAnalytics: FirebaseAnalytics? = null

    init {
        if (context != null) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
        }
    }

    fun log(event: String, details: Map<String, Any>?) {
        // Log to Firestore
        val payload = HashMap<String, Any>()
        payload["event"] = event
        payload["details"] = details ?: HashMap<String, Any>()
        firebaseAuth.currentUser?.let { user ->
            payload["uid"] = user.uid
        }
        payload["timestamp"] = FieldValue.serverTimestamp()
        firestore.collection("analytics").add(payload)

        // Log to Firebase Analytics
        firebaseAnalytics?.let { analytics ->
            val bundle = Bundle()
            details?.forEach { (key, value) ->
                when (value) {
                    is String -> bundle.putString(key, value)
                    is Int -> bundle.putInt(key, value)
                    is Long -> bundle.putLong(key, value)
                    is Double -> bundle.putDouble(key, value)
                    is Boolean -> bundle.putBoolean(key, value)
                }
            }
            analytics.logEvent(event, bundle)
        }
    }

    companion object {
        @Volatile
        private var instance: AnalyticsRepository? = null

        @JvmStatic
        fun getInstance(context: Context): AnalyticsRepository {
            return instance ?: synchronized(this) {
                instance ?: AnalyticsRepository(context).also { instance = it }
            }
        }

        @JvmStatic
        fun getInstance(): AnalyticsRepository? {
            return instance
        }
    }
}
