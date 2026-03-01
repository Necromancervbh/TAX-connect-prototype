package com.example.taxconnect.data.services

import android.content.Context
import android.util.Log
import com.example.taxconnect.BuildConfig
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine

class AgoraManager private constructor(context: Context) {
    private val appContext: Context = context.applicationContext
    var rtcEngine: RtcEngine? = null
        private set

    init {
        initializeAgoraEngine()
    }

    private fun initializeAgoraEngine() {
        try {
            rtcEngine = RtcEngine.create(appContext, APP_ID, object : IRtcEngineEventHandler() {
                override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
                    Log.d(TAG, "onJoinChannelSuccess: $channel uid: $uid")
                }

                override fun onUserJoined(uid: Int, elapsed: Int) {
                    Log.d(TAG, "onUserJoined: $uid")
                }

                override fun onUserOffline(uid: Int, reason: Int) {
                    Log.d(TAG, "onUserOffline: $uid")
                }
            })
            // Enable video by default for video calls
            rtcEngine?.enableVideo()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Agora RtcEngine", e)
        }
    }

    fun joinChannel(channelName: String?, token: String?, uid: Int) {
        rtcEngine?.joinChannel(token, channelName, "Extra Optional Data", uid)
    }

    fun leaveChannel() {
        rtcEngine?.leaveChannel()
    }

    fun destroy() {
        if (rtcEngine != null) {
            RtcEngine.destroy()
            rtcEngine = null
        }
        instance = null
    }

    companion object {
        private const val TAG = "AgoraManager"
        private val APP_ID: String = BuildConfig.AGORA_APP_ID
        
        @Volatile
        private var instance: AgoraManager? = null

        @JvmStatic
        fun getInstance(context: Context): AgoraManager {
            return instance ?: synchronized(this) {
                instance ?: AgoraManager(context).also { instance = it }
            }
        }
    }
}
