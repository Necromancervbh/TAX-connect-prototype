package com.example.taxconnect.features.videocall

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.taxconnect.data.repositories.AnalyticsRepository
import com.example.taxconnect.data.repositories.ConversationRepository
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.data.repositories.DataRepository.DataCallback
import com.example.taxconnect.data.services.AgoraManager
import com.example.taxconnect.data.models.ConversationModel
import com.example.taxconnect.R
import com.example.taxconnect.databinding.ActivityVideoCallBinding
import com.example.taxconnect.core.base.BaseActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.ListenerRegistration
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration

class VideoCallActivity : BaseActivity<ActivityVideoCallBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityVideoCallBinding = ActivityVideoCallBinding::inflate

    // Removed lateinit vars as we use binding now
    // private lateinit var localVideoContainer: FrameLayout
    // ...

    private var rtcEngine: RtcEngine? = null
    private var channelName: String? = null
    private var isMuted = false
    private var isVideoMuted = false
    private var isRemoteAudioMuted = false
    private var isRemoteVideoMuted = false

    private val timerHandler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private val timerRunnable = object : Runnable {
        override fun run() {
            val millis = System.currentTimeMillis() - startTime
            var seconds = (millis / 1000).toInt()
            val minutes = seconds / 60
            seconds %= 60

            binding.tvCallDuration.text = String.format("%02d:%02d", minutes, seconds)
            timerHandler.postDelayed(this, 500)
        }
    }

    private var callStatusListener: ListenerRegistration? = null

    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                binding.tvStatus.visibility = View.GONE
                startTimer()
                setupRemoteVideo(uid)
                val d = hashMapOf<String, Any>(
                    "event" to "remote_user_joined",
                    "uid" to uid,
                    "channelName" to (channelName ?: "")
                )
                AnalyticsRepository.getInstance()?.log("remote_user_joined", d)
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread { onRemoteUserLeft() }
        }

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread {
                channelName?.let {
                    ConversationRepository.getInstance().updateCallStatus(it, "ACTIVE", null)
                }
                val d = hashMapOf<String, Any>(
                    "event" to "join_channel_success",
                    "channelName" to (channelName ?: ""),
                    "uid" to uid
                )
                AnalyticsRepository.getInstance()?.log("join_channel_success", d)
            }
        }

        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            super.onRemoteVideoStateChanged(uid, state, reason, elapsed)
            if (state == Constants.REMOTE_VIDEO_STATE_STARTING || state == Constants.REMOTE_VIDEO_STATE_DECODING) {
                runOnUiThread { setupRemoteVideo(uid) }
            }
            val muted = state == Constants.REMOTE_VIDEO_STATE_STOPPED || state == Constants.REMOTE_VIDEO_STATE_FAILED
            runOnUiThread { updateRemoteVideoState(muted) }
        }

        override fun onUserMuteAudio(uid: Int, muted: Boolean) {
            runOnUiThread { updateRemoteAudioState(muted) }
        }

        override fun onError(err: Int) {
            runOnUiThread {
                var errorMsg = getString(R.string.agora_error, err.toString())
                if (err == 110) errorMsg += getString(R.string.invalid_token)
                if (err == 109) errorMsg += getString(R.string.token_expired)
                if (err == 101) errorMsg += getString(R.string.invalid_app_id)
                showToast(errorMsg)
            }
        }
    }

    private val PERMISSION_REQ_ID = 22
    private val REQUESTED_PERMISSIONS = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO
    )

    override fun initViews() {
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
        setupLocalVideo() // Renamed from setupVideoContainer

        if (checkPermissions()) {
            initAgora()
        } else {
            androidx.core.app.ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_REQ_ID)
        }
        
        setupButtons()
        
        channelName = intent.getStringExtra("CHANNEL_NAME")
        val roomUuid = intent.getStringExtra("ROOM_UUID")
        val callerName = intent.getStringExtra("CALLER_NAME")
        val isIncoming = intent.getBooleanExtra("INCOMING_CALL", false)
        val action = intent.getStringExtra("ACTION")
        
        if (isIncoming) {
            binding.tvStatus.text = "Incoming call from $callerName..."
            if (action == "ANSWER") {
                if (channelName != null) {
                    joinChannel()
                }
            } else {
                if (channelName != null) {
                    joinChannel()
                }
            }
        } else {
             binding.tvStatus.text = "Calling $callerName..."
             if (channelName != null) {
                 joinChannel()
             }
        }

        listenForCallStatus()
    }

    override fun setupListeners() {
        binding.btnEndCall.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            announceForAccessibility("Call ended")
            leaveChannel()
            finish()
        }

        binding.btnMute.setOnClickListener {
            if (rtcEngine == null) return@setOnClickListener
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            isMuted = !isMuted
            rtcEngine?.muteLocalAudioStream(isMuted)
            updateMuteButtonState()
            announceForAccessibility(if (isMuted) "Microphone muted" else "Microphone unmuted")
        }

        binding.btnToggleVideo.setOnClickListener {
            if (rtcEngine == null) return@setOnClickListener
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            isVideoMuted = !isVideoMuted
            rtcEngine?.muteLocalVideoStream(isVideoMuted)
            if (binding.localVideoContainer.childCount > 0) {
                binding.localVideoContainer.getChildAt(0).visibility = if (isVideoMuted) View.INVISIBLE else View.VISIBLE
            }
            updateVideoButtonState()
            announceForAccessibility(if (isVideoMuted) "Camera off" else "Camera on")
        }

        binding.btnSwitchCamera.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            rtcEngine?.switchCamera()
        }

        binding.btnMinimize.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPipWithHints()
            }
        }
        
        setupDragListener()
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

    override fun observeViewModel() {
        // No ViewModel used currently
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
                initAgora()
            } else {
                showToast("Permissions required for video call")
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
            if (startTime > 0) binding.tvCallDuration.visibility = View.VISIBLE
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

    private fun startTimer() {
        startTime = System.currentTimeMillis()
        binding.tvCallDuration.visibility = View.VISIBLE
        timerHandler.postDelayed(timerRunnable, 0)
    }

    private fun stopTimer() {
        timerHandler.removeCallbacks(timerRunnable)
        startTime = 0
        binding.tvCallDuration.visibility = View.GONE
    }

    private fun listenForCallStatus() {
        callStatusListener?.remove()
        callStatusListener = ConversationRepository.getInstance().listenToConversation(channelName!!, object : DataCallback<ConversationModel?> {
            override fun onSuccess(conversation: ConversationModel?) {
                if (conversation != null) {
                    when (conversation.callStatus) {
                        "REJECTED" -> {
                            val d = hashMapOf<String, Any>(
                                "status" to "REJECTED",
                                "channelName" to (channelName ?: "")
                            )
                            AnalyticsRepository.getInstance()?.log("call_status_change", d)
                            showToast("Call Declined")
                            leaveChannel()
                            finish()
                        }
                        "BUSY" -> {
                            val d = hashMapOf<String, Any>(
                                "status" to "BUSY",
                                "channelName" to (channelName ?: "")
                            )
                            AnalyticsRepository.getInstance()?.log("call_status_change", d)
                            showToast("User is busy")
                            leaveChannel()
                            finish()
                        }
                        "ENDED" -> {
                            val d = hashMapOf<String, Any>(
                                "status" to "ENDED",
                                "channelName" to (channelName ?: "")
                            )
                            AnalyticsRepository.getInstance()?.log("call_status_change", d)
                            leaveChannel()
                            finish()
                        }
                    }
                }
            }

            override fun onError(error: String?) {
                // Ignore errors
            }
        })
    }

    private fun initAgora() {
        try {
            val agoraManager = AgoraManager.getInstance(this)
            rtcEngine = agoraManager.rtcEngine

            if (rtcEngine == null) {
                showToast("Failed to initialize video engine")
                finish()
                return
            }

            rtcEngine?.addHandler(mRtcEventHandler)
            rtcEngine?.enableVideo()
            rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            rtcEngine?.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
            rtcEngine?.setAudioProfile(Constants.AUDIO_PROFILE_MUSIC_HIGH_QUALITY, Constants.AUDIO_SCENARIO_GAME_STREAMING)

            val config = VideoEncoderConfiguration(
                VideoEncoderConfiguration.VD_1280x720,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30,
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
            )
            rtcEngine?.setVideoEncoderConfiguration(config)
            rtcEngine?.startPreview()

            setupLocalVideo()
            // joinChannel() moved to initViews logic

        } catch (e: Exception) {
            timber.log.Timber.e(e, "Agora initialization failed")
            showToast(getString(R.string.error_with_message, e.message))
            finish()
        }
    }

    private fun setupLocalVideo() {
        val surfaceView = SurfaceView(this)
        surfaceView.setZOrderMediaOverlay(true)
        binding.localVideoContainer.removeAllViews()
        binding.localVideoContainer.addView(surfaceView)
        rtcEngine?.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
    }

    private fun setupRemoteVideo(uid: Int) {
        if (binding.remoteVideoContainer.childCount >= 1) {
            val existingView = binding.remoteVideoContainer.getChildAt(0)
            if (existingView is SurfaceView) {
                rtcEngine?.setupRemoteVideo(VideoCanvas(existingView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
            }
            return
        }
        val surfaceView = SurfaceView(this)
        surfaceView.setZOrderMediaOverlay(false)
        binding.remoteVideoContainer.removeAllViews()
        binding.remoteVideoContainer.addView(surfaceView)

        rtcEngine?.setupRemoteVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
        surfaceView.visibility = View.VISIBLE

        showToast("Remote video setup for: $uid")
    }

    private fun onRemoteUserLeft() {
        stopTimer()
        binding.remoteVideoContainer.removeAllViews()
        showToast(getString(R.string.call_ended))
        channelName?.let {
            ConversationRepository.getInstance().updateCallStatus(it, "ENDED", null)
        }
        leaveChannel()
        finish()
    }

    private fun setupButtons() {
        updateMuteButtonState()
        updateVideoButtonState()
    }
    
    private fun joinChannel() {
        if (!channelName.isNullOrEmpty()) {
            rtcEngine?.joinChannel(null, channelName, "Optional Data", 0)
        } else {
            showToast(getString(R.string.channel_name_missing))
            finish()
        }
    }

    private fun leaveChannel() {
        rtcEngine?.let {
            it.leaveChannel()
            it.stopPreview()
            it.removeHandler(mRtcEventHandler)
        }
    }

    private fun announceForAccessibility(text: String) {
        val root = findViewById<View>(android.R.id.content)
        root?.announceForAccessibility(text)
    }

    private fun updateMuteButtonState() {
        binding.btnMute.setIconResource(if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on)
        binding.btnMute.contentDescription = if (isMuted) "Unmute microphone" else "Mute microphone"
        // Use white for active (unmuted), red for muted/disabled state if desired, or just toggle icon
        // For this design, let's keep the button transparent/white and just change icon, 
        // OR toggle background to indicate 'OFF' state more clearly.
        // Let's stick to the plan: Circular buttons.
        val bgColor = ContextCompat.getColor(this, if (isMuted) R.color.white else android.R.color.transparent)
        val iconColor = ContextCompat.getColor(this, if (isMuted) R.color.black else R.color.white)
        // Adjusting logic: 
        // Default (Unmuted): Translucent BG, White Icon
        // Muted: White BG, Black Icon (Active state for "Muted")
        
        if (isMuted) {
             binding.btnMute.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
             binding.btnMute.iconTint = ColorStateList.valueOf(android.graphics.Color.BLACK)
        } else {
             binding.btnMute.backgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
             binding.btnMute.iconTint = ColorStateList.valueOf(android.graphics.Color.WHITE)
        }
    }

    private fun updateVideoButtonState() {
        binding.btnToggleVideo.setIconResource(if (isVideoMuted) R.drawable.ic_videocam_off else R.drawable.ic_videocam_on)
        binding.btnToggleVideo.contentDescription = if (isVideoMuted) "Turn camera on" else "Turn camera off"
        
        if (isVideoMuted) {
             binding.btnToggleVideo.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
             binding.btnToggleVideo.iconTint = ColorStateList.valueOf(android.graphics.Color.BLACK)
        } else {
             binding.btnToggleVideo.backgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
             binding.btnToggleVideo.iconTint = ColorStateList.valueOf(android.graphics.Color.WHITE)
        }
    }

    private fun updateRemoteAudioState(muted: Boolean) {
        isRemoteAudioMuted = muted
    }

    private fun updateRemoteVideoState(muted: Boolean) {
        // Implementation can be added if needed
    }

    companion object {
        var isInCall = false
    }
}
