package com.example.taxconnect.services;

import android.content.Context;
import android.util.Log;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;

public class AgoraManager {
    private static final String TAG = "AgoraManager";
    private static final String APP_ID = com.example.taxconnect.BuildConfig.AGORA_APP_ID;
    private static final String APP_CERTIFICATE = null; // Set to null for App ID only mode
    
    private static AgoraManager instance;
    private RtcEngine rtcEngine;
    private final Context context;

    private AgoraManager(Context context) {
        this.context = context.getApplicationContext();
        initializeAgoraEngine();
    }

    public static synchronized AgoraManager getInstance(Context context) {
        if (instance == null) {
            instance = new AgoraManager(context);
        }
        return instance;
    }

    private void initializeAgoraEngine() {
        try {
            rtcEngine = RtcEngine.create(context, APP_ID, new IRtcEngineEventHandler() {
                @Override
                public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
                    Log.d(TAG, "onJoinChannelSuccess: " + channel + " uid: " + uid);
                }

                @Override
                public void onUserJoined(int uid, int elapsed) {
                    Log.d(TAG, "onUserJoined: " + uid);
                }

                @Override
                public void onUserOffline(int uid, int reason) {
                    Log.d(TAG, "onUserOffline: " + uid);
                }
            });
            // Enable video by default for video calls
            rtcEngine.enableVideo();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Agora RtcEngine", e);
        }
    }

    public void joinChannel(String channelName, String token, int uid) {
        if (rtcEngine != null) {
            rtcEngine.joinChannel(token, channelName, "Extra Optional Data", uid);
        }
    }

    public void leaveChannel() {
        if (rtcEngine != null) {
            rtcEngine.leaveChannel();
        }
    }
    
    public RtcEngine getRtcEngine() {
        return rtcEngine;
    }

    public void destroy() {
        if (rtcEngine != null) {
            RtcEngine.destroy();
            rtcEngine = null;
        }
        instance = null;
    }
}
