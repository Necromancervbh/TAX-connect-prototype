package com.example.taxconnect.core.base

import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewAnimationUtils
import android.widget.EditText
import android.widget.RatingBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.example.taxconnect.R
import com.example.taxconnect.data.models.FeedbackModel
import com.example.taxconnect.core.ui.ThemeHelper
import com.example.taxconnect.data.repositories.DataRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.hypot

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    private var _binding: VB? = null
    protected val binding get() = _binding!!

    companion object {
        private var themeScreenshot: Bitmap? = null
        private var revealX: Int = 0
        private var revealY: Int = 0
    }

    abstract val bindingInflater: (LayoutInflater) -> VB

    override fun onCreate(savedInstanceState: Bundle?) {
        setupTheme()
        setupDataRepository()
        super.onCreate(savedInstanceState)
        _binding = bindingInflater(layoutInflater)
        setContentView(binding.root)
        
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            if (v is androidx.drawerlayout.widget.DrawerLayout) {
                val contentView = v.getChildAt(0)
                contentView?.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                
                val navView = v.findViewById<View>(R.id.nav_view)
                navView?.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            } else {
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            }
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        
        initViews()
        observeViewModel()
        setupListeners()
    }

    private fun setupDataRepository() {
        try {
            DataRepository.getInstance().init(this)
        } catch (e: Exception) {
            android.util.Log.e("BaseActivity", "DataRepository init failed", e)
        }
    }

    private fun setupTheme() {
        try {
            ThemeHelper.applyTheme(this)
            val theme = ThemeHelper.getSelectedTheme(this)
            setTheme(ThemeHelper.getThemeResource(theme))
        } catch (e: Exception) {
            setTheme(com.example.taxconnect.R.style.Theme_MyApplication)
            ThemeHelper.saveTheme(this, ThemeHelper.THEME_SYSTEM)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    // Abstract methods for child classes to implement
    protected abstract fun initViews()
    protected abstract fun observeViewModel()
    protected abstract fun setupListeners()

    // Optional lifecycle methods
    protected fun showFeedbackDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_feedback, null)
        val etFeedback = dialogView.findViewById<EditText>(R.id.etFeedback)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)

        MaterialAlertDialogBuilder(this)
            .setTitle("Send Feedback")
            .setMessage("We noticed you shook your device! Want to share your feedback or report a bug?")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val feedbackText = etFeedback.text.toString()
                val rating = ratingBar.rating
                if (feedbackText.isNotBlank()) {
                    submitFeedback(feedbackText, rating)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    protected fun submitFeedback(message: String, rating: Float) {
        val user = FirebaseAuth.getInstance().currentUser
        val feedback = FeedbackModel(
            userId = user?.uid,
            userName = user?.displayName ?: "Anonymous",
            message = message,
            rating = rating
        )

        DataRepository.getInstance().saveFeedback(feedback, object : DataRepository.DataCallback<Void?> {
            override fun onSuccess(data: Void?) {
                showToast("Thank you for your feedback!")
            }

            override fun onError(error: String?) {
                showToast(error ?: "Failed to submit feedback")
            }
        })
    }

    protected fun handleError(message: String?, retry: (() -> Unit)? = null) {
        val errorMessage = message ?: "Unknown Error"
        if (retry != null) {
            Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG)
                .setAction("Retry") { retry() }
                .show()
        } else {
            showToast(errorMessage)
        }
    }

    // Utility methods
    public fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        // Upgrade to Snackbar for better UX
        val snackbarDuration = if (duration == Toast.LENGTH_LONG) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT
        if (_binding != null) {
            try {
                Snackbar.make(binding.root, message, snackbarDuration).show()
            } catch (e: Exception) {
                // Fallback in case of any issue with Snackbar
                Toast.makeText(this, message, duration).show()
            }
        } else {
            Toast.makeText(this, message, duration).show()
        }
    }

    protected fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        if (_binding != null) {
            Snackbar.make(binding.root, message, duration).show()
        }
    }

    protected fun getCurrentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    protected fun isUserLoggedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    // Theme transition animation
    protected fun animateThemeChange(x: Int, y: Int) {
        revealX = x
        revealY = y
        recreate()
    }

    protected fun animateThemeTransition() {
        try {
            val rootView = window.decorView.findViewById<View>(android.R.id.content)
            themeScreenshot = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(themeScreenshot!!)
            rootView.draw(canvas)

            val location = IntArray(2)
            rootView.getLocationInWindow(location)
            revealX = location[0] + rootView.width / 2
            revealY = location[1] + rootView.height / 2

            val anim = ViewAnimationUtils.createCircularReveal(
                rootView,
                revealX,
                revealY,
                0f,
                hypot(rootView.width.toDouble(), rootView.height.toDouble()).toFloat()
            )
            anim.duration = 300
            anim.start()
        } catch (e: Exception) {
            // Animation failed, continue without it
        }
    }
}