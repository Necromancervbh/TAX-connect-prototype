package com.example.taxconnect.features.auth

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.example.taxconnect.core.ui.ThemeHelper
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

import android.widget.ImageView
import android.widget.TextView
import android.view.LayoutInflater
import com.example.taxconnect.R
import com.example.taxconnect.databinding.ActivitySplashBinding
import com.example.taxconnect.features.home.MainActivity
import com.example.taxconnect.core.base.BaseActivity

class SplashActivity : BaseActivity<ActivitySplashBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivitySplashBinding = ActivitySplashBinding::inflate

    override fun initViews() {
        val logo = binding.ivLogo
        val title = binding.tvAppName
        val tagline = binding.tvTagline
        
        // Initial State
        logo.alpha = 0f
        logo.scaleX = 0.5f
        logo.scaleY = 0.5f
        
        title.alpha = 0f
        title.translationY = 100f

        tagline.alpha = 0f

        // Logo Animation (Pop effect)
        val logoAlpha = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f)
        val logoScaleX = ObjectAnimator.ofFloat(logo, "scaleX", 0.5f, 1f)
        val logoScaleY = ObjectAnimator.ofFloat(logo, "scaleY", 0.5f, 1f)

        val logoSet = AnimatorSet().apply {
            playTogether(logoAlpha, logoScaleX, logoScaleY)
            duration = 1000
            interpolator = OvershootInterpolator()
        }

        // Title Animation (Slide Up)
        val titleAlpha = ObjectAnimator.ofFloat(title, "alpha", 0f, 1f)
        val titleTranslation = ObjectAnimator.ofFloat(title, "translationY", 100f, 0f)

        val titleSet = AnimatorSet().apply {
            playTogether(titleAlpha, titleTranslation)
            duration = 800
            startDelay = 500 // Start after logo is halfway
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        // Tagline Animation (Fade In)
        val taglineAlpha = ObjectAnimator.ofFloat(tagline, "alpha", 0f, 1f).apply {
            duration = 800
            startDelay = 1200
        }

        // Play All
        AnimatorSet().apply {
            playTogether(logoSet, titleSet, taglineAlpha)
            start()
        }

        // Delay for 3 seconds then move to MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            checkBiometric()
        }, 3000)
    }

    override fun observeViewModel() {
        // No ViewModel needed for Splash
    }

    override fun setupListeners() {
        // No listeners needed for Splash
    }

    private fun checkBiometric() {
        // Biometric login removed as requested
        proceedToMain()
    }

    private fun showBiometricPrompt() {
        // Biometric prompt removed
        proceedToMain()
    }

    private fun proceedToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
