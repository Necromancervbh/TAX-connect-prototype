package com.example.taxconnect.core.ui

import android.content.Context
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator

class Material3MotionHelper(private val context: Context) {
    
    fun applyButtonPressAnimation(view: View) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun applyButtonReleaseAnimation(view: View) {
        view.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(100)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    fun applyBounceAnimation(view: View) {
        view.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(150)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    fun applyElevationAnimation(view: View, hovered: Boolean) {
        val elevation = if (hovered) 8f else 2f
        view.animate()
            .translationZ(elevation)
            .setDuration(200)
            .start()
    }

    fun cleanup() {
        // No persistent resources to clean up in this implementation
    }

    fun applyStandardBounce(view: View) {
        applyBounceAnimation(view)
    }

    fun fadeIn(view: View, duration: Long = 300) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    fun fadeOut(view: View, duration: Long = 300) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
            }
            .start()
    }
}
