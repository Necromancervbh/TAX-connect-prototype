package com.example.taxconnect.features.videocall

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.util.Rational
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.taxconnect.R
import com.example.taxconnect.databinding.ActivityVideoCallBinding
import com.example.taxconnect.core.base.BaseActivity
import com.example.taxconnect.data.repositories.AnalyticsRepository
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.launch

class VideoCallActivity : BaseActivity<ActivityVideoCallBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityVideoCallBinding = ActivityVideoCallBinding::inflate

    private val viewModel: VideoCallViewModel by viewModels()

    private val PERMISSION_REQ_ID = 22
    private val REQUESTED_PERMISSIONS = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO
    )

    private var channelName: String? = null
    private var isFinishingCall = false

    override fun initViews() {
        isInCall = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Cancel the incoming call notification to stop the ringtone
        channelName = intent.getStringExtra("CHANNEL_NAME")
        channelName?.let {
            androidx.core.app.NotificationManagerCompat.from(this).cancel(it.hashCode())
        }
        val isIncoming = intent.getBooleanExtra("INCOMING_CALL", false)
        val callerName = intent.getStringExtra("CALLER_NAME")

        if (isIncoming) {
            binding.tvStatus.text = "Incoming call from $callerName..."
        } else {
            binding.tvStatus.text = "Calling $callerName..."
        }

        if (checkPermissions()) {
            startCallLogic()
        } else {
            androidx.core.app.ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_REQ_ID)
        }

        setupButtons()
        setupDragListener()
    }

    private fun startCallLogic() {
        val callerName = intent.getStringExtra("CALLER_NAME") ?: ""
        val roomUuid = intent.getStringExtra("ROOM_UUID") ?: ""
        val callerAvatar = intent.getStringExtra("CALLER_AVATAR") ?: ""
        val isIncoming = intent.getBooleanExtra("INCOMING_CALL", false)

        if (channelName != null) {
            viewModel.initCall(channelName!!, roomUuid, isIncoming, callerName)
        } else {
            showToast(getString(R.string.channel_name_missing))
            finish()
        }
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.callState.collect { state ->
                        handleCallState(state)
                    }
                }
                launch {
                    viewModel.isMuted.collect { muted ->
                        updateMuteButtonState(muted)
                    }
                }
                launch {
                    viewModel.isVideoDisabled.collect { muted ->
                        updateVideoButtonState(muted)
                    }
                }
                launch {
                    viewModel.callDuration.collect { duration ->
                        binding.tvCallDuration.text = duration
                        if (duration != "00:00" && !isFinishingCall) {
                            binding.tvCallDuration.visibility = View.VISIBLE
                        }
                    }
                }
                launch {
                    viewModel.toastMessage.collect { msg ->
                        if (msg != null) {
                            showToast(msg)
                            viewModel.clearToast()
                        }
                    }
                }
                launch {
                    viewModel.remoteUid.collect { uid ->
                        if (uid != null && uid != 0) {
                            setupRemoteVideo(uid)
                        } else {
                            binding.remoteVideoContainer.removeAllViews()
                        }
                    }
                }
            }
        }
    }

    private fun handleCallState(state: CallState) {
        when (state) {
            CallState.INIT -> {
                binding.tvStatus.visibility = View.VISIBLE
            }
            CallState.RINGING -> {
                binding.tvStatus.visibility = View.VISIBLE
            }
            CallState.ACTIVE -> {
                binding.tvStatus.visibility = View.GONE
                setupLocalVideo()
            }
            CallState.ENDED, CallState.MISSED -> {
                if (!isFinishingCall) {
                    isFinishingCall = true
                    timber.log.Timber.d("VideoCall: Call state changed to $state, terminating activity")
                    if (state == CallState.MISSED) showToast("Call Missed")
                    showCallQualityAndFinish()
                } else {
                    // Safety check: if we are already finishing but still receiving ENDED signals,
                    // ensure the activity eventually closes.
                    timber.log.Timber.d("VideoCall: Received $state while already finishing")
                    binding.root.postDelayed({
                        if (!isDestroyed && !isFinishing) {
                            timber.log.Timber.d("VideoCall: Force finishing activity after delay")
                            finish()
                        }
                    }, 2000)
                }
            }
            CallState.ERROR -> {
                if (!isFinishingCall) {
                    isFinishingCall = true
                    timber.log.Timber.e("VideoCall: Call state changed to ERROR, finishing")
                    showToast(getString(R.string.error_video_engine))
                    finish()
                }
            }
        }
    }



    override fun setupListeners() {
        binding.btnEndCall.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            announceForAccessibility("Call ended")
            viewModel.endCall()
            if (!isFinishingCall) {
                isFinishingCall = true
                showCallQualityAndFinish()
            } else {
                // If it was already finishing but got stuck, force finish now
                finish()
            }
        }

        binding.btnMute.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            viewModel.toggleMute()
            announceForAccessibility(if (viewModel.isMuted.value) "Microphone unmuted" else "Microphone muted")
        }

        binding.btnToggleVideo.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            viewModel.toggleVideo()
            announceForAccessibility(if (viewModel.isVideoDisabled.value) "Camera on" else "Camera off")
            if (binding.localVideoContainer.childCount > 0) {
                 binding.localVideoContainer.getChildAt(0).visibility = if (!viewModel.isVideoDisabled.value) View.INVISIBLE else View.VISIBLE
            }
        }

        binding.btnSwitchCamera.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            viewModel.switchCamera()
        }

        binding.btnMinimize.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPipWithHints()
            }
        }
    }

    private fun setupDragListener() {
        var dX = 0f
        var dY = 0f
        binding.cvLocalVideo.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    view.animate()
                        .x(event.rawX + dX)
                        .y(event.rawY + dY)
                        .setDuration(0)
                        .start()
                    true
                }
                else -> false
            }
        }
    }

    private fun checkPermissions(): Boolean {
        for (perm in REQUESTED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startCallLogic()
            } else {
                showToast(getString(R.string.permissions_required_video_call))
                finish()
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE

        binding.controlsContainer.visibility = visibility
        binding.btnMinimize.visibility = visibility
        binding.scrimTop.visibility = visibility
        binding.scrimBottom.visibility = visibility
        binding.cvLocalVideo.visibility = visibility

        if (isInPictureInPictureMode) {
            binding.tvStatus.visibility = View.GONE
            binding.tvCallDuration.visibility = View.GONE
        } else {
            if (viewModel.callDuration.value != "00:00") {
                binding.tvCallDuration.visibility = View.VISIBLE
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(buildPipParams(true))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipWithHints()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPipWithHints() {
        val params = buildPipParams(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        enterPictureInPictureMode(params)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(autoEnter: Boolean): PictureInPictureParams {
        val aspectRatio = Rational(9, 16)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
        val hint = computeSourceRectHint()
        if (hint != null) {
            builder.setSourceRectHint(hint)
        }
        if (autoEnter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
        }
        return builder.build()
    }

    private fun computeSourceRectHint(): Rect? {
        val anchor = if (binding.remoteVideoContainer.visibility == View.VISIBLE) binding.remoteVideoContainer else binding.localVideoContainer
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        return Rect(
            location[0], location[1],
            location[0] + anchor.width,
            location[1] + anchor.height
        )
    }

    private fun setupLocalVideo() {
        if (binding.localVideoContainer.childCount > 0) return
        val surfaceView = SurfaceView(this)
        surfaceView.setZOrderMediaOverlay(true)
        binding.localVideoContainer.removeAllViews()
        binding.localVideoContainer.addView(surfaceView)
        viewModel.rtcEngine?.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
    }

    private fun setupRemoteVideo(uid: Int) {
        if (binding.remoteVideoContainer.childCount >= 1) {
            val existingView = binding.remoteVideoContainer.getChildAt(0)
            if (existingView is SurfaceView) {
                viewModel.rtcEngine?.setupRemoteVideo(VideoCanvas(existingView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
            }
            return
        }
        val surfaceView = SurfaceView(this)
        surfaceView.setZOrderMediaOverlay(false)
        binding.remoteVideoContainer.removeAllViews()
        binding.remoteVideoContainer.addView(surfaceView)

        viewModel.rtcEngine?.setupRemoteVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
        surfaceView.visibility = View.VISIBLE
    }

    private fun showCallQualityAndFinish() {
        if (isDestroyed || isFinishing) return
        
        try {
            timber.log.Timber.d("VideoCall: Attempting to show CallQualityBottomSheet")
            val channel = channelName ?: intent.getStringExtra("CHANNEL_NAME") ?: ""
            val bottomSheet = CallQualityBottomSheet.newInstance(channel)
            bottomSheet.show(supportFragmentManager, "CallQualityBottomSheet")
            
            // Set a fallback timer: if the user doesn't interact with the bottom sheet,
            // we should still finish the activity eventually (though BottomSheet typically handles this).
            binding.root.postDelayed({
                if (!isDestroyed && !isFinishing) {
                    timber.log.Timber.d("VideoCall: Fallback finish triggered after bottom sheet timeout")
                    finish()
                }
            }, 30000) // 30 seconds fallback

        } catch (e: Exception) {
            timber.log.Timber.e(e, "VideoCall: Failed to show CallQualityBottomSheet")
            // If showing the bottom sheet fails for any reason, we MUST finish the activity
            // so the user doesn't get stuck.
            finish()
        }
    }

    private fun setupButtons() {
        updateMuteButtonState(viewModel.isMuted.value)
        updateVideoButtonState(viewModel.isVideoDisabled.value)
    }

    private fun announceForAccessibility(text: String) {
        val root = findViewById<View>(android.R.id.content)
        root?.announceForAccessibility(text)
    }

    private fun updateMuteButtonState(isMuted: Boolean) {
        binding.btnMute.setImageResource(if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on)
        binding.btnMute.contentDescription = if (isMuted) "Unmute microphone" else "Mute microphone"
        
        if (isMuted) {
             binding.btnMute.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
             binding.btnMute.imageTintList = ColorStateList.valueOf(android.graphics.Color.BLACK)
        } else {
             binding.btnMute.backgroundTintList = null
             binding.btnMute.imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
        }
    }

    private fun updateVideoButtonState(isVideoMuted: Boolean) {
        binding.btnToggleVideo.setImageResource(if (isVideoMuted) R.drawable.ic_videocam_off else R.drawable.ic_videocam_on)
        binding.btnToggleVideo.contentDescription = if (isVideoMuted) "Turn camera on" else "Turn camera off"
        
        if (isVideoMuted) {
             binding.btnToggleVideo.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
             binding.btnToggleVideo.imageTintList = ColorStateList.valueOf(android.graphics.Color.BLACK)
        } else {
             binding.btnToggleVideo.backgroundTintList = null
             binding.btnToggleVideo.imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isInCall = false
        // ViewModel handles its own cleanup upon onCleared()
    }

    companion object {
        var isInCall = false
    }
}

