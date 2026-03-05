package com.example.taxconnect.features.videocall

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taxconnect.data.repositories.AnalyticsRepository
import com.example.taxconnect.data.repositories.ConversationRepository
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.BuildConfig
import com.google.firebase.firestore.ListenerRegistration
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CallState {
    INIT, RINGING, ACTIVE, ENDED, MISSED, ERROR
}

class VideoCallViewModel(application: Application) : AndroidViewModel(application) {

    private val _callState = MutableStateFlow(CallState.INIT)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _callDuration = MutableStateFlow("00:00")
    val callDuration: StateFlow<String> = _callDuration.asStateFlow()

    private val _remoteUserJoined = MutableStateFlow(false)
    val remoteUserJoined: StateFlow<Boolean> = _remoteUserJoined.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isVideoDisabled = MutableStateFlow(false)
    val isVideoDisabled: StateFlow<Boolean> = _isVideoDisabled.asStateFlow()

    private val _remoteVideoMuted = MutableStateFlow(false)
    val remoteVideoMuted: StateFlow<Boolean> = _remoteVideoMuted.asStateFlow()

    private val _remoteAudioMuted = MutableStateFlow(false)
    val remoteAudioMuted: StateFlow<Boolean> = _remoteAudioMuted.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _remoteUid = MutableStateFlow<Int?>(null)
    val remoteUid: StateFlow<Int?> = _remoteUid.asStateFlow()

    var rtcEngine: RtcEngine? = null
        private set

    private var callStatusListener: ListenerRegistration? = null
    private var timerJob: Job? = null
    private var ringingTimeoutJob: Job? = null
    private var startTime: Long = 0
    private var channelName: String? = null
    private var isCaller: Boolean = false
    private var targetUserId: String? = null // For sending silent push

    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) {
            viewModelScope.launch {
                timber.log.Timber.d("VideoCall: onUserJoined: $uid")
                _remoteUserJoined.value = true
                _callState.value = CallState.ACTIVE
                _remoteUid.value = uid
                ringingTimeoutJob?.cancel() // Cancel timeout if answered
                startTimer()
                logAnalytics("remote_user_joined", mapOf("uid" to uid))
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            viewModelScope.launch {
                timber.log.Timber.d("VideoCall: onUserOffline: $uid reason: $reason")
                endCallLocally()
            }
        }

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            viewModelScope.launch {
                timber.log.Timber.d("VideoCall: onJoinChannelSuccess: $channel uid: $uid")
                channelName?.let {
                    ConversationRepository.getInstance().updateCallStatus(it, "ACTIVE", null)
                }
                logAnalytics("join_channel_success", mapOf("uid" to uid))
            }
        }

        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            viewModelScope.launch {
                timber.log.Timber.d("VideoCall: onRemoteVideoStateChanged: $uid state: $state")
                val muted = state == Constants.REMOTE_VIDEO_STATE_STOPPED || state == Constants.REMOTE_VIDEO_STATE_FAILED
                _remoteVideoMuted.value = muted
                if (state == Constants.REMOTE_VIDEO_STATE_STARTING || state == Constants.REMOTE_VIDEO_STATE_DECODING) {
                    // Trigger view update
                    _remoteUid.value = uid
                }
            }
        }

        override fun onUserMuteAudio(uid: Int, muted: Boolean) {
            viewModelScope.launch {
                timber.log.Timber.d("VideoCall: onUserMuteAudio: $uid muted: $muted")
                _remoteAudioMuted.value = muted
            }
        }

        override fun onError(err: Int) {
            viewModelScope.launch {
                timber.log.Timber.e("VideoCall: Agora Error: $err")
                var errorMsg = "Agora Error: $err"
                if (err == 110) errorMsg += " (Invalid token)"
                if (err == 109) errorMsg += " (Token expired)"
                if (err == 101) errorMsg += " (Invalid App ID)"
                _toastMessage.value = errorMsg
                if (err == 101 || err == 110) {
                     _callState.value = CallState.ERROR
                }
            }
        }
    }

    fun initCall(channel: String?, token: String?, isIncoming: Boolean, callerId: String?) {
        this.channelName = channel
        this.isCaller = !isIncoming
        this.targetUserId = callerId // In a real app, you'd fetch the callee ID if you are the caller. 
        // For simplicity, we just need to ensure the caller can send a cancellation.

        initAgora()
        
        if (isCaller) {
            _callState.value = CallState.RINGING
            startRingingTimeout()
        }

        listenForCallStatus()

        // If it's a direct join, start the process
        if (channel != null) {
            joinChannel(null) // Assuming demo uses null/temp tokens
        }
    }
    
    fun clearToast() {
        _toastMessage.value = null
    }

    private fun initAgora() {
        try {
            val config = RtcEngineConfig()
            config.mContext = getApplication()
            config.mAppId = BuildConfig.AGORA_APP_ID
            config.mEventHandler = mRtcEventHandler
            rtcEngine = RtcEngine.create(config)

            rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            rtcEngine?.enableVideo()
            rtcEngine?.enableAudio()
            
            // Set initial states
            rtcEngine?.muteLocalAudioStream(_isMuted.value)
            rtcEngine?.muteLocalVideoStream(_isVideoDisabled.value)
            
        } catch (e: Exception) {
            _toastMessage.value = "Failed to initialize Agora: ${e.message}"
            _callState.value = CallState.ERROR
        }
    }

    fun joinChannel(token: String? = null) {
        if (channelName == null) return
        // In demo apps using only App ID, the token must be explicitly null
        rtcEngine?.joinChannel(null, channelName, null, 0)
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        rtcEngine?.muteLocalAudioStream(_isMuted.value)
    }

    fun toggleVideo() {
        _isVideoDisabled.value = !_isVideoDisabled.value
        rtcEngine?.muteLocalVideoStream(_isVideoDisabled.value)
    }

    fun switchCamera() {
        rtcEngine?.switchCamera()
    }

    fun endCall() {
        if (_callState.value == CallState.RINGING && isCaller) {
            // Caller hung up before answer
            _callState.value = CallState.MISSED
            channelName?.let {
                ConversationRepository.getInstance().updateCallStatus(it, "MISSED", null)
                // Fire silent FCM to target so their phone stops ringing
                // In a perfect system we'd know exactly who we are calling to send this directly.
                ConversationRepository.getInstance().sendCallCancelledNotification(it)
            }
        } else {
            _callState.value = CallState.ENDED
            channelName?.let {
                ConversationRepository.getInstance().endCallSession(it, null)
            }
        }
        endCallLocally()
    }

    private var isCallEnded = false

    private fun endCallLocally() {
        if (isCallEnded) return
        isCallEnded = true
        
        timber.log.Timber.d("VideoCall: endCallLocally triggered")
        ringingTimeoutJob?.cancel()
        timerJob?.cancel()
        leaveChannel()
        callStatusListener?.remove()
        callStatusListener = null
        if (_callState.value != CallState.MISSED && _callState.value != CallState.ENDED) {
            _callState.value = CallState.ENDED
        }
    }

    private fun leaveChannel() {
        timber.log.Timber.d("VideoCall: Leaving Agora channel")
        // Note: We don't call RtcEngine.destroy() here anymore to avoid deadlocks.
        // It will be called in onCleared() when the ViewModel is destroyed.
        rtcEngine?.leaveChannel()
        rtcEngine = null
    }

    private fun listenForCallStatus() {
        if (channelName == null) return
        timber.log.Timber.d("VideoCall: Starting Firestore call status listener for channel: $channelName")
        callStatusListener = ConversationRepository.getInstance().listenToCallStatus(channelName!!) { status ->
            timber.log.Timber.d("VideoCall: Firestore call status changed to: $status")
            when (status) {
                "ENDED", "DECLINED", "MISSED" -> {
                    if (_callState.value != CallState.ENDED && _callState.value != CallState.MISSED) {
                        _callState.value = if (status == "ENDED") CallState.ENDED else CallState.MISSED
                        _toastMessage.value = if (status == "DECLINED") "Call declined" else "Call ended"
                        endCallLocally()
                    }
                }
            }
        }
    }

    private fun startTimer() {
        startTime = System.currentTimeMillis()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val millis = System.currentTimeMillis() - startTime
                var seconds = (millis / 1000).toInt()
                val minutes = seconds / 60
                seconds %= 60
                _callDuration.value = String.format("%02d:%02d", minutes, seconds)
                delay(500)
            }
        }
    }

    private fun startRingingTimeout() {
        ringingTimeoutJob?.cancel()
        ringingTimeoutJob = viewModelScope.launch {
            delay(45000) // 45 seconds timeout
            if (_callState.value == CallState.RINGING) {
                _toastMessage.value = "No answer"
                _callState.value = CallState.MISSED
                endCall()
            }
        }
    }

    private fun logAnalytics(event: String, data: Map<String, Any>) {
        val completeData = data.toMutableMap()
        channelName?.let { completeData["channelName"] = it }
        AnalyticsRepository.getInstance()?.log(event, completeData)
    }

    override fun onCleared() {
        super.onCleared()
        timber.log.Timber.d("VideoCall: ViewModel onCleared - finalizing cleanup")
        endCallLocally()
        // IMPORTANT: RtcEngine.destroy() MUST be called on the main thread
        // and NOT from within an Agora callback.
        RtcEngine.destroy()
    }
}
