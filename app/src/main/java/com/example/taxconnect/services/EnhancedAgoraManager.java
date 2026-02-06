package com.example.taxconnect.services;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.video.VideoEncoderConfiguration;

/**
 * Enhanced Agora Manager with adaptive bitrate, connection quality monitoring,
 * and comprehensive error handling for enterprise-grade video calls.
 */
public class EnhancedAgoraManager {
    private static final String TAG = "EnhancedAgoraManager";
    private static final String APP_ID = com.example.taxconnect.BuildConfig.AGORA_APP_ID;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 2000;
    
    // Error code constants
    private static final int ERR_INVALID_VENDOR_KEY = 101;
    private static final int ERR_INVALID_CHANNEL_NAME = 102;
    private static final int ERR_JOIN_CHANNEL_REJECTED = 103;
    private static final int ERR_LEAVE_CHANNEL_REJECTED = 104;
    
    private static EnhancedAgoraManager instance;
    private RtcEngine rtcEngine;
    private final Context context;
    private final ConcurrentHashMap<String, ConnectionQuality> connectionQualityMap;
    private final AtomicInteger retryAttempts;
    private NetworkQuality currentNetworkQuality;
    private boolean isAdaptiveBitrateEnabled = true;
    
    public enum ConnectionQuality {
        EXCELLENT(4), GOOD(3), POOR(2), BAD(1), UNKNOWN(0);
        
        private final int quality;
        ConnectionQuality(int quality) {
            this.quality = quality;
        }
        
        public int getQuality() {
            return quality;
        }
    }
    
    public enum NetworkQuality {
        EXCELLENT(0, 1920, 1080, 30, 3000),
        GOOD(1, 1280, 720, 30, 1500),
        POOR(2, 854, 480, 24, 800),
        BAD(3, 640, 360, 15, 400);
        
        public final int agoraQuality;
        public final int width;
        public final int height;
        public final int fps;
        public final int bitrate;
        
        NetworkQuality(int agoraQuality, int width, int height, int fps, int bitrate) {
            this.agoraQuality = agoraQuality;
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.bitrate = bitrate;
        }
    }
    
    public interface ConnectionCallback {
        void onConnectionSuccess(String channel, int uid);
        void onConnectionFailed(String error, int errorCode);
        void onConnectionQualityChanged(ConnectionQuality quality);
        void onNetworkQualityChanged(NetworkQuality quality);
        void onUserJoined(int uid);
        void onUserOffline(int uid, int reason);
        void onRemoteVideoStateChanged(int uid, int state, int reason);
        void onAudioVolumeIndication(int uid, int volume, int vad);
        void onRtcStats(IRtcEngineEventHandler.RtcStats stats);
    }
    
    private ConnectionCallback connectionCallback;
    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            Log.d(TAG, "onJoinChannelSuccess: " + channel + " uid: " + uid);
            resetRetryAttempts();
            if (connectionCallback != null) {
                connectionCallback.onConnectionSuccess(channel, uid);
            }
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            Log.d(TAG, "onUserJoined: " + uid);
            if (connectionCallback != null) {
                connectionCallback.onUserJoined(uid);
            }
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            Log.d(TAG, "onUserOffline: " + uid + " reason: " + reason);
            if (connectionCallback != null) {
                connectionCallback.onUserOffline(uid, reason);
            }
        }

        @Override
        public void onRemoteVideoStateChanged(int uid, int state, int reason, int elapsed) {
            super.onRemoteVideoStateChanged(uid, state, reason, elapsed);
            Log.d(TAG, "onRemoteVideoStateChanged: uid=" + uid + " state=" + state + " reason=" + reason);
            if (connectionCallback != null) {
                connectionCallback.onRemoteVideoStateChanged(uid, state, reason);
            }
        }

        @Override
        public void onAudioVolumeIndication(AudioVolumeInfo[] speakers, int totalVolume) {
            if (speakers.length > 0 && connectionCallback != null) {
                for (AudioVolumeInfo info : speakers) {
                    if (info.uid != 0) { // Ignore local user
                        connectionCallback.onAudioVolumeIndication(info.uid, info.volume, info.vad);
                    }
                }
            }
        }

        @Override
        public void onRtcStats(RtcStats stats) {
            super.onRtcStats(stats);
            updateNetworkQuality(stats);
            if (connectionCallback != null) {
                connectionCallback.onRtcStats(stats);
            }
        }

        @Override
        public void onNetworkQuality(int uid, int txQuality, int rxQuality) {
            super.onNetworkQuality(uid, txQuality, rxQuality);
            updateConnectionQuality(uid, txQuality, rxQuality);
        }

        @Override
        public void onConnectionStateChanged(int state, int reason) {
            super.onConnectionStateChanged(state, reason);
            Log.d(TAG, "onConnectionStateChanged: state=" + state + " reason=" + reason);
            handleConnectionStateChange(state, reason);
        }

        @Override
        public void onError(int errorCode) {
            super.onError(errorCode);
            Log.e(TAG, "onError: " + errorCode);
            handleConnectionError(errorCode);
        }
    };

    private EnhancedAgoraManager(Context context) {
        this.context = context.getApplicationContext();
        this.connectionQualityMap = new ConcurrentHashMap<>();
        this.retryAttempts = new AtomicInteger(0);
        this.currentNetworkQuality = NetworkQuality.GOOD;
        initializeAgoraEngine();
    }

    public static synchronized EnhancedAgoraManager getInstance(Context context) {
        if (instance == null) {
            instance = new EnhancedAgoraManager(context);
        }
        return instance;
    }

    private void initializeAgoraEngine() {
        try {
            rtcEngine = RtcEngine.create(context, APP_ID, mRtcEventHandler);
            
            // Enable video with adaptive configuration
            rtcEngine.enableVideo();
            
            // Set channel profile to communication
            rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);
            
            // Set client role to broadcaster
            rtcEngine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);
            
            // Enable audio volume indication for better UX
            rtcEngine.enableAudioVolumeIndication(200, 3, true);
            
            // Set initial network quality configuration
            applyNetworkQualityConfiguration(NetworkQuality.GOOD);
            
            Log.i(TAG, "Agora engine initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Agora RtcEngine", e);
            if (connectionCallback != null) {
                connectionCallback.onConnectionFailed("Failed to initialize video engine", -1);
            }
        }
    }

    public void setConnectionCallback(ConnectionCallback callback) {
        this.connectionCallback = callback;
    }

    public void joinChannel(String channelName, String token, int uid) {
        if (rtcEngine == null) {
            if (connectionCallback != null) {
                connectionCallback.onConnectionFailed("Video engine not initialized", -1);
            }
            return;
        }

        try {
            Log.i(TAG, "Joining channel: " + channelName + " with uid: " + uid);
            rtcEngine.joinChannel(token, channelName, "Enhanced Video Call", uid);
        } catch (Exception e) {
            Log.e(TAG, "Error joining channel", e);
            handleConnectionError(-1);
        }
    }

    public void leaveChannel() {
        if (rtcEngine != null) {
            try {
                rtcEngine.leaveChannel();
                Log.i(TAG, "Left channel successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error leaving channel", e);
            }
        }
    }

    public void enableAdaptiveBitrate(boolean enabled) {
        this.isAdaptiveBitrateEnabled = enabled;
        Log.i(TAG, "Adaptive bitrate " + (enabled ? "enabled" : "disabled"));
    }

    public void setVideoEnabled(boolean enabled) {
        if (rtcEngine != null) {
            rtcEngine.enableLocalVideo(enabled);
            Log.i(TAG, "Local video " + (enabled ? "enabled" : "disabled"));
        }
    }

    public void setAudioEnabled(boolean enabled) {
        if (rtcEngine != null) {
            rtcEngine.muteLocalAudioStream(!enabled);
            Log.i(TAG, "Local audio " + (enabled ? "unmuted" : "muted"));
        }
    }

    public void switchCamera() {
        if (rtcEngine != null) {
            rtcEngine.switchCamera();
            Log.i(TAG, "Camera switched");
        }
    }

    public NetworkQuality getCurrentNetworkQuality() {
        return currentNetworkQuality;
    }

    public ConnectionQuality getConnectionQuality(String uid) {
        return connectionQualityMap.getOrDefault(uid, ConnectionQuality.UNKNOWN);
    }

    private void updateNetworkQuality(IRtcEngineEventHandler.RtcStats stats) {
        // Analyze network statistics to determine quality
        NetworkQuality newQuality = analyzeNetworkStats(stats);
        
        if (newQuality != currentNetworkQuality) {
            currentNetworkQuality = newQuality;
            Log.i(TAG, "Network quality changed to: " + newQuality);
            
            if (isAdaptiveBitrateEnabled) {
                applyNetworkQualityConfiguration(newQuality);
            }
            
            if (connectionCallback != null) {
                connectionCallback.onNetworkQualityChanged(newQuality);
            }
        }
    }

    private NetworkQuality analyzeNetworkStats(IRtcEngineEventHandler.RtcStats stats) {
        // Analyze various metrics to determine network quality
        if (stats.gatewayRtt > 300 || stats.rxPacketLossRate > 5 || stats.txPacketLossRate > 5) {
            return NetworkQuality.BAD;
        } else if (stats.gatewayRtt > 150 || stats.rxPacketLossRate > 2 || stats.txPacketLossRate > 2) {
            return NetworkQuality.POOR;
        } else if (stats.gatewayRtt > 50 || stats.rxPacketLossRate > 0.5 || stats.txPacketLossRate > 0.5) {
            return NetworkQuality.GOOD;
        } else {
            return NetworkQuality.EXCELLENT;
        }
    }

    private void updateConnectionQuality(int uid, int txQuality, int rxQuality) {
        ConnectionQuality quality = convertAgoraQualityToConnectionQuality(txQuality, rxQuality);
        connectionQualityMap.put(String.valueOf(uid), quality);
        
        if (connectionCallback != null) {
            connectionCallback.onConnectionQualityChanged(quality);
        }
    }

    private ConnectionQuality convertAgoraQualityToConnectionQuality(int txQuality, int rxQuality) {
        int avgQuality = (txQuality + rxQuality) / 2;
        
        if (avgQuality <= 1) {
            return ConnectionQuality.EXCELLENT;
        } else if (avgQuality <= 2) {
            return ConnectionQuality.GOOD;
        } else if (avgQuality <= 3) {
            return ConnectionQuality.POOR;
        } else {
            return ConnectionQuality.BAD;
        }
    }

    private void applyNetworkQualityConfiguration(NetworkQuality quality) {
        if (rtcEngine == null) return;
        
        try {
            VideoEncoderConfiguration config = new VideoEncoderConfiguration(
                new VideoEncoderConfiguration.VideoDimensions(quality.width, quality.height),
                VideoEncoderConfiguration.FRAME_RATE.values()[quality.fps - 1],
                quality.bitrate,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
            );
            
            rtcEngine.setVideoEncoderConfiguration(config);
            Log.i(TAG, "Applied video configuration for " + quality + " quality");
            
        } catch (Exception e) {
            Log.e(TAG, "Error applying video configuration", e);
        }
    }

    private void handleConnectionStateChange(int state, int reason) {
        switch (state) {
            case Constants.CONNECTION_STATE_CONNECTED:
                Log.i(TAG, "Connection established");
                resetRetryAttempts();
                break;
                
            case Constants.CONNECTION_STATE_DISCONNECTED:
                Log.w(TAG, "Connection disconnected, reason: " + reason);
                if (retryAttempts.get() < MAX_RETRY_ATTEMPTS) {
                    scheduleReconnect();
                }
                break;
                
            case Constants.CONNECTION_STATE_FAILED:
                Log.e(TAG, "Connection failed, reason: " + reason);
                handleConnectionError(reason);
                break;
                
            case Constants.CONNECTION_STATE_RECONNECTING:
                Log.i(TAG, "Connection reconnecting");
                break;
        }
    }

    private void handleConnectionError(int errorCode) {
        String errorMessage = getErrorMessage(errorCode);
        
        if (retryAttempts.get() < MAX_RETRY_ATTEMPTS) {
            retryAttempts.incrementAndGet();
            Log.w(TAG, "Connection error (attempt " + retryAttempts.get() + "): " + errorMessage);
            scheduleReconnect();
        } else {
            Log.e(TAG, "Max retry attempts reached, giving up");
            if (connectionCallback != null) {
                connectionCallback.onConnectionFailed(errorMessage, errorCode);
            }
        }
    }

    private String getErrorMessage(int errorCode) {
        // Use generic error messages for common error codes
        if (errorCode == ERR_INVALID_VENDOR_KEY) {
            return "Invalid app ID. Please check your configuration.";
        } else if (errorCode == ERR_INVALID_CHANNEL_NAME) {
            return "Invalid channel name. Please try again.";
        } else if (errorCode == ERR_JOIN_CHANNEL_REJECTED) {
            return "Failed to join channel. The channel may be full or unavailable.";
        } else if (errorCode == ERR_LEAVE_CHANNEL_REJECTED) {
            return "Failed to leave channel properly.";
        } else {
            return "Connection error occurred. Please check your network and try again.";
        }
    }

    private void scheduleReconnect() {
        // Implement exponential backoff for retries
        long delay = RETRY_DELAY_MS * (long) Math.pow(2, retryAttempts.get() - 1);
        
        android.os.Handler handler = new android.os.Handler(context.getMainLooper());
        handler.postDelayed(() -> {
            Log.i(TAG, "Attempting reconnection (attempt " + retryAttempts.get() + ")");
            // Reconnection logic would go here
        }, delay);
    }

    private void resetRetryAttempts() {
        retryAttempts.set(0);
    }

    public RtcEngine getRtcEngine() {
        return rtcEngine;
    }

    public void destroy() {
        if (rtcEngine != null) {
            try {
                leaveChannel();
                RtcEngine.destroy();
                rtcEngine = null;
                Log.i(TAG, "Agora engine destroyed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error destroying Agora engine", e);
            }
        }
        instance = null;
        connectionQualityMap.clear();
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }
}