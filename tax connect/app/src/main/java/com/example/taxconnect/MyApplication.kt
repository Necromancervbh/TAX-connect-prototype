package com.example.taxconnect

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.taxconnect.data.repositories.AnalyticsRepository
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.data.services.CloudinaryHelper
import com.example.taxconnect.core.ui.ThemeHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.perf.FirebasePerformance
import com.razorpay.Checkout
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter

@HiltAndroidApp
class MyApplication : Application(), DefaultLifecycleObserver {
    override fun onCreate() {
        super<Application>.onCreate()
        
        // Register lifecycle observer for online/offline status
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        try {
            FirebaseApp.initializeApp(this)
        } catch (t: Throwable) {
            android.util.Log.e("MyApplication", "Firebase init failed", t)
        }

        try {
            AnalyticsRepository.getInstance(this)
        } catch (t: Throwable) {
            android.util.Log.e("MyApplication", "Analytics init failed", t)
        }

        try {
            DataRepository.getInstance().init(this)
        } catch (t: Throwable) {
            android.util.Log.e("MyApplication", "DataRepository init failed", t)
        }

        try {
            FirebasePerformance.getInstance().isPerformanceCollectionEnabled = true
        } catch (t: Throwable) {
            android.util.Log.e("MyApplication", "Firebase Perf init failed", t)
        }

        if (BuildConfig.DEBUG) {
            try {
                Timber.plant(Timber.DebugTree())
            } catch (t: Throwable) {
                // Timber init failed
            }
        }

        try {
            ThemeHelper.applyTheme(this)
        } catch (t: Throwable) {
             android.util.Log.e("MyApplication", "ThemeHelper init failed", t)
        }
        
        try {
            CloudinaryHelper.init(this)
        } catch (t: Throwable) {
             android.util.Log.e("MyApplication", "Cloudinary init failed", t)
        }
        
        try {
            Checkout.preload(applicationContext)
        } catch (t: Throwable) {
             android.util.Log.e("MyApplication", "Razorpay init failed", t)
        }

        installGlobalCrashHandler()
    }

    /**
     * Catches any uncaught exception across all threads, logs it to Firestore
     * under `crashes/{timestamp}` so crashes are visible even without Crashlytics.
     * Always re-throws so Android can show its normal crash dialog / restart.
     */
    private fun installGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString().take(8000) // Firestore field limit

                val uid = FirebaseAuth.getInstance().uid ?: "anonymous"
                val report = hashMapOf(
                    "uid"         to uid,
                    "thread"      to thread.name,
                    "message"     to (throwable.message ?: throwable.javaClass.simpleName),
                    "stackTrace"  to stackTrace,
                    "device"      to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                    "osVersion"   to "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
                    "appVersion"  to (packageManager.getPackageInfo(packageName, 0).versionName ?: "?"),
                    "timestamp"   to com.google.firebase.Timestamp.now()
                )

                // Fire-and-forget — use addOnCompleteListener so it flushes before process dies
                FirebaseFirestore.getInstance()
                    .collection("crashes")
                    .add(report)
                    .addOnCompleteListener {
                        defaultHandler?.uncaughtException(thread, throwable)
                    }

                // Give Firestore up to 2 s to flush, then let the default handler run
                Thread.sleep(2000)
            } catch (_: Exception) {
                // Never block crash propagation
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        updateUserStatus(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        updateUserStatus(false)
    }

    private fun updateUserStatus(isOnline: Boolean) {
        val uid = FirebaseAuth.getInstance().uid
        if (uid != null) {
            DataRepository.getInstance().updateUserStatus(uid, isOnline, object : DataRepository.DataCallback<Void?> {
                override fun onSuccess(data: Void?) {
                    // Status updated successfully
                }
                override fun onError(error: String?) {
                    // Failed to update status
                    android.util.Log.e("MyApplication", "Status update failed: $error")
                }
            })
        }
    }
}