package com.example.taxconnect.core.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.taxconnect.core.ui.Material3MotionHelper
import com.google.android.material.button.MaterialButton

/**
 * Material 3 Button with enhanced motion and haptic feedback
 * Demonstrates Material Design 3 motion principles and haptic feedback
 */
class Material3Button @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialButton(context, attrs, defStyleAttr) {

    private var motionHelper: Material3MotionHelper? = null
    private var isButtonPressed = false

    init {
        init(context)
    }

    private fun init(context: Context) {
        motionHelper = Material3MotionHelper(context)
        setupTouchFeedback()
    }

    /**
     * Setup enhanced touch feedback with motion and haptic
     */
    private fun setupTouchFeedback() {
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isButtonPressed) {
                        isButtonPressed = true
                        motionHelper?.applyButtonPressAnimation(this@Material3Button)
                        return@setOnTouchListener false // Allow normal button behavior
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isButtonPressed) {
                        isButtonPressed = false
                        motionHelper?.applyButtonReleaseAnimation(this@Material3Button)
                        return@setOnTouchListener false // Allow normal button behavior
                    }
                }
            }
            false
        }

        // Add click animation
        setOnClickListener {
            // Apply bounce animation for click feedback
            motionHelper?.applyBounceAnimation(this@Material3Button)

            // Call original click listener if set
            performClick()
        }
    }

    /**
     * Apply Material 3 elevation animation on hover (for large screens)
     */
    override fun onHoverChanged(hovered: Boolean) {
        super.onHoverChanged(hovered)
        motionHelper?.applyElevationAnimation(this, hovered)
    }

    /**
     * Apply Material 3 focus animation
     */
    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused) {
            motionHelper?.applyBounceAnimation(this)
        }
    }

    /**
     * Cleanup resources when view is detached
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        motionHelper?.cleanup()
    }

    /**
     * Enable or disable haptic feedback
     */
    override fun setHapticFeedbackEnabled(enabled: Boolean) {
        super.setHapticFeedbackEnabled(enabled)
        if (motionHelper != null) {
            // This would be implemented in the motion helper
        }
    }

    /**
     * Set custom animation duration
     */
    fun setAnimationDuration() {
        // This would be implemented to override default durations
    }
}
