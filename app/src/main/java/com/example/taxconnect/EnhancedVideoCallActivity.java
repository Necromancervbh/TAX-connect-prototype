package com.example.taxconnect;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.taxconnect.services.EnhancedAgoraManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.video.VideoEncoderConfiguration;

/**
 * Enhanced Video Call Activity with adaptive bitrate streaming,
 * comprehensive error handling, and enterprise-grade performance.
 */
public class EnhancedVideoCallActivity extends AppCompatActivity {
    
    private static final String TAG = "EnhancedVideoCallActivity";
    private static final int PERMISSION_REQ_ID = 22;
    private static final int CONNECTION_TIMEOUT_MS = 10000; // 10 seconds
    private static final int QUALITY_CHECK_INTERVAL_MS = 2000; // 2 seconds
    
    // UI Components
    private ConstraintLayout rootLayout;
    private androidx.cardview.widget.CardView localVideoContainer;
    private androidx.cardview.widget.CardView remoteVideoContainer;
    private TextView tvStatus;
    private TextView tvCallDuration;
    private TextView tvNetworkQuality;
    private TextView tvParticipantName;
    private FloatingActionButton btnEndCall;
    private FloatingActionButton btnMute;
    private FloatingActionButton btnSwitchCamera;
    private FloatingActionButton btnToggleVideo;
    private FloatingActionButton btnSpeaker;
    private FloatingActionButton btnMoreOptions;
    
    // Call State
    private EnhancedAgoraManager agoraManager;
    private String channelName;
    private String participantName;
    private int remoteUid = -1;
    private boolean isMuted = false;
    private boolean isVideoMuted = false;
    private boolean isSpeakerOn = true;
    private long callStartTime = 0;
    private Handler callTimerHandler;
    private Handler qualityCheckHandler;
    private Runnable callTimerRunnable;
    private Runnable qualityCheckRunnable;
    
    // Connection Management
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private int connectionAttempts = 0;
    private Handler connectionTimeoutHandler;
    
    // Performance Metrics
    private int totalFrameDrops = 0;
    private long lastQualityUpdate = 0;
    private EnhancedAgoraManager.NetworkQuality currentNetworkQuality;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enhanced window configuration
        setupWindowConfiguration();
        setContentView(R.layout.activity_enhanced_video_call);
        
        // Initialize components
        initializeViews();
        extractIntentData();
        setupClickListeners();
        initializeHandlers();
        
        // Check permissions and initialize
        if (checkPermissions()) {
            initializeVideoCall();
        }
    }
    
    private void setupWindowConfiguration() {
        // Keep screen on during video call
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Set layout to fill entire screen (immersive mode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        
        // Enable hardware acceleration for better video performance
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
    }
    
    private void initializeViews() {
        rootLayout = findViewById(R.id.rootLayout);
        localVideoContainer = findViewById(R.id.localVideoContainer);
        remoteVideoContainer = findViewById(R.id.remoteVideoContainer);
        tvStatus = findViewById(R.id.tvStatus);
        tvCallDuration = findViewById(R.id.tvCallDuration);
        tvNetworkQuality = findViewById(R.id.tvNetworkQuality);
        tvParticipantName = findViewById(R.id.tvParticipantName);
        btnEndCall = findViewById(R.id.btnEndCall);
        btnMute = findViewById(R.id.btnMute);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);
        btnToggleVideo = findViewById(R.id.btnToggleVideo);
        btnSpeaker = findViewById(R.id.btnSpeaker);
        btnMoreOptions = findViewById(R.id.btnMoreOptions);
        
        // Initial UI state
        updateUIState(VideoCallState.CONNECTING);
    }
    
    private void extractIntentData() {
        channelName = getIntent().getStringExtra("CHANNEL_NAME");
        participantName = getIntent().getStringExtra("PARTICIPANT_NAME");
        
        if (channelName == null || channelName.isEmpty()) {
            showError("Invalid call configuration", true);
            return;
        }
        
        tvParticipantName.setText(participantName != null ? participantName : "Connecting...");
    }
    
    private void setupClickListeners() {
        btnEndCall.setOnClickListener(v -> handleEndCall());
        btnMute.setOnClickListener(v -> toggleMute());
        btnToggleVideo.setOnClickListener(v -> toggleVideo());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        btnMoreOptions.setOnClickListener(v -> showMoreOptions());
        
        // Handle PiP mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            btnMoreOptions.setOnLongClickListener(v -> {
                enterPictureInPictureMode();
                return true;
            });
        }
    }
    
    private void initializeHandlers() {
        callTimerHandler = new Handler(Looper.getMainLooper());
        qualityCheckHandler = new Handler(Looper.getMainLooper());
        connectionTimeoutHandler = new Handler(Looper.getMainLooper());
        
        callTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isConnected && callStartTime > 0) {
                    long elapsed = System.currentTimeMillis() - callStartTime;
                    updateCallDuration(elapsed);
                    callTimerHandler.postDelayed(this, 1000);
                }
            }
        };
        
        qualityCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (isConnected) {
                    checkNetworkQuality();
                    qualityCheckHandler.postDelayed(this, QUALITY_CHECK_INTERVAL_MS);
                }
            }
        };
    }
    
    private void initializeVideoCall() {
        showStatus("Initializing video call...");
        
        agoraManager = EnhancedAgoraManager.getInstance(this);
        agoraManager.setConnectionCallback(new EnhancedAgoraManager.ConnectionCallback() {
            @Override
            public void onConnectionSuccess(String channel, int uid) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Connection successful: " + channel);
                    isConnected = true;
                    isConnecting = false;
                    callStartTime = System.currentTimeMillis();
                    startCallTimer();
                    startQualityMonitoring();
                    updateUIState(VideoCallState.CONNECTED);
                    cancelConnectionTimeout();
                });
            }
            
            @Override
            public void onConnectionFailed(String error, int errorCode) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Connection failed: " + error);
                    showError(error, false);
                    handleConnectionFailure();
                });
            }
            
            @Override
            public void onConnectionQualityChanged(EnhancedAgoraManager.ConnectionQuality quality) {
                runOnUiThread(() -> updateConnectionQualityIndicator(quality));
            }
            
            @Override
            public void onNetworkQualityChanged(EnhancedAgoraManager.NetworkQuality quality) {
                runOnUiThread(() -> updateNetworkQualityIndicator(quality));
            }
            
            @Override
            public void onUserJoined(int uid) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Remote user joined: " + uid);
                    remoteUid = uid;
                    setupRemoteVideo(uid);
                });
            }
            
            @Override
            public void onUserOffline(int uid, int reason) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Remote user offline: " + uid + " reason: " + reason);
                    handleRemoteUserLeft();
                });
            }
            
            @Override
            public void onRemoteVideoStateChanged(int uid, int state, int reason) {
                runOnUiThread(() -> {
                    if (state == io.agora.rtc2.Constants.REMOTE_VIDEO_STATE_DECODING) {
                        Log.d(TAG, "Remote video decoding started");
                        updateUIState(VideoCallState.VIDEO_ACTIVE);
                    }
                });
            }
            
            @Override
            public void onAudioVolumeIndication(int uid, int volume, int vad) {
                // Handle audio volume changes for better UX
            }
            
            @Override
            public void onRtcStats(io.agora.rtc2.IRtcEngineEventHandler.RtcStats stats) {
                // Monitor performance metrics
                if (stats != null) {
                    // Track general performance metrics using available fields
                    // Note: totalVideoFreezeTime field may not be available in all SDK versions
                    // Using basic stats monitoring instead
                    Log.d(TAG, "RTC Stats - Gateway RTT: " + stats.gatewayRtt + 
                           ", RX Packet Loss: " + stats.rxPacketLossRate + 
                           ", TX Packet Loss: " + stats.txPacketLossRate);
                }
            }
        });
        
        setupLocalVideo();
        joinChannel();
        startConnectionTimeout();
    }
    
    private void setupLocalVideo() {
        try {
            android.view.SurfaceView surfaceView = new android.view.SurfaceView(this);
            surfaceView.setZOrderMediaOverlay(true);
            localVideoContainer.removeAllViews();
            localVideoContainer.addView(surfaceView);
            
            if (agoraManager.getRtcEngine() != null) {
                agoraManager.getRtcEngine().setupLocalVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up local video", e);
            showError("Failed to setup local video", false);
        }
    }
    
    private void setupRemoteVideo(int uid) {
        try {
            android.view.SurfaceView surfaceView = new android.view.SurfaceView(this);
            surfaceView.setZOrderMediaOverlay(false);
            remoteVideoContainer.removeAllViews();
            remoteVideoContainer.addView(surfaceView);
            
            if (agoraManager.getRtcEngine() != null) {
                agoraManager.getRtcEngine().setupRemoteVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid));
            }
            
            surfaceView.setVisibility(android.view.View.VISIBLE);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up remote video", e);
            showError("Failed to setup remote video", false);
        }
    }
    
    private void joinChannel() {
        try {
            isConnecting = true;
            connectionAttempts++;
            showStatus("Connecting to call...");
            agoraManager.joinChannel(channelName, null, 0);
        } catch (Exception e) {
            Log.e(TAG, "Error joining channel", e);
            showError("Failed to join call", false);
            handleConnectionFailure();
        }
    }
    
    private void handleEndCall() {
        // Show confirmation dialog for better UX
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("End Call")
                .setMessage("Are you sure you want to end this call?")
                .setPositiveButton("End Call", (dialog, which) -> {
                    endCall();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void endCall() {
        stopCallTimer();
        stopQualityMonitoring();
        cancelConnectionTimeout();
        
        if (agoraManager != null) {
            agoraManager.leaveChannel();
        }
        
        // Log call statistics
        logCallStatistics();
        
        finish();
    }
    
    private void toggleMute() {
        isMuted = !isMuted;
        if (agoraManager != null) {
            agoraManager.setAudioEnabled(!isMuted);
        }
        
        // Update button appearance
        btnMute.setImageResource(isMuted ? android.R.drawable.ic_lock_silent_mode : android.R.drawable.ic_btn_speak_now);
        btnMute.setColorFilter(isMuted ? android.graphics.Color.RED : android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN);
        
        showToast(isMuted ? "Microphone muted" : "Microphone unmuted");
    }
    
    private void toggleVideo() {
        isVideoMuted = !isVideoMuted;
        if (agoraManager != null) {
            agoraManager.setVideoEnabled(!isVideoMuted);
        }
        
        // Update local video visibility
        if (localVideoContainer.getChildCount() > 0) {
            localVideoContainer.getChildAt(0).setVisibility(isVideoMuted ? android.view.View.INVISIBLE : android.view.View.VISIBLE);
        }
        
        btnToggleVideo.setImageResource(isVideoMuted ? android.R.drawable.ic_menu_close_clear_cancel : android.R.drawable.ic_menu_camera);
        showToast(isVideoMuted ? "Video paused" : "Video resumed");
    }
    
    private void switchCamera() {
        if (agoraManager != null) {
            agoraManager.switchCamera();
            showToast("Camera switched");
        }
    }
    
    private void toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;
        // Speaker mode implementation would go here
        btnSpeaker.setColorFilter(isSpeakerOn ? android.graphics.Color.GREEN : android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN);
        showToast(isSpeakerOn ? "Speaker on" : "Speaker off");
    }
    
    private void showMoreOptions() {
        // Implement more options menu
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, btnMoreOptions);
        popup.getMenuInflater().inflate(R.menu.video_call_options, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_flip_camera) {
                switchCamera();
                return true;
            } else if (itemId == R.id.action_toggle_flash) {
                // Toggle flash implementation
                return true;
            } else if (itemId == R.id.action_report_issue) {
                // Report issue implementation
                return true;
            } else {
                return false;
            }
        });
        popup.show();
    }
    
    public void enterPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.util.Rational aspectRatio = new android.util.Rational(9, 16);
            android.app.PictureInPictureParams params = new android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build();
            super.enterPictureInPictureMode(params);
        }
    }
    
    private void updateCallDuration(long elapsed) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60;
        tvCallDuration.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }
    
    private void updateUIState(VideoCallState state) {
        switch (state) {
            case CONNECTING:
                tvStatus.setVisibility(android.view.View.VISIBLE);
                tvCallDuration.setVisibility(android.view.View.GONE);
                tvNetworkQuality.setVisibility(android.view.View.GONE);
                showStatus("Connecting...");
                break;
                
            case CONNECTED:
                tvStatus.setVisibility(android.view.View.GONE);
                tvCallDuration.setVisibility(android.view.View.VISIBLE);
                tvNetworkQuality.setVisibility(android.view.View.VISIBLE);
                break;
                
            case VIDEO_ACTIVE:
                // Additional UI updates when video is active
                break;
                
            case CONNECTION_FAILED:
                showError("Connection failed", false);
                break;
        }
    }
    
    private void updateConnectionQualityIndicator(EnhancedAgoraManager.ConnectionQuality quality) {
        // Update connection quality UI
        String qualityText = "Connection: " + quality.name();
        // Implementation for quality indicator
    }
    
    private void updateNetworkQualityIndicator(EnhancedAgoraManager.NetworkQuality quality) {
        currentNetworkQuality = quality;
        String qualityText = "Network: " + quality.name();
        tvNetworkQuality.setText(qualityText);
        
        // Change color based on quality
        int color;
        switch (quality) {
            case EXCELLENT:
                color = android.graphics.Color.GREEN;
                break;
            case GOOD:
                color = android.graphics.Color.YELLOW;
                break;
            case POOR:
                color = android.graphics.Color.rgb(255, 165, 0); // Orange
                break;
            case BAD:
                color = android.graphics.Color.RED;
                break;
            default:
                color = android.graphics.Color.GRAY;
        }
        tvNetworkQuality.setTextColor(color);
    }
    
    private void checkNetworkQuality() {
        // Additional network quality checks
        if (agoraManager != null) {
            EnhancedAgoraManager.NetworkQuality quality = agoraManager.getCurrentNetworkQuality();
            updateNetworkQualityIndicator(quality);
        }
    }
    
    private void handleRemoteUserLeft() {
        stopCallTimer();
        showStatus("Call ended");
        
        // Delay before finishing to show status
        new Handler().postDelayed(() -> {
            endCall();
        }, 2000);
    }
    
    private void handleConnectionFailure() {
        isConnecting = false;
        cancelConnectionTimeout();
        
        if (connectionAttempts < 3) {
            // Retry connection with exponential backoff
            long delay = (long) Math.pow(2, connectionAttempts) * 1000;
            new Handler().postDelayed(() -> {
                if (!isFinishing()) {
                    joinChannel();
                }
            }, delay);
        } else {
            showError("Unable to establish connection. Please check your network and try again.", true);
        }
    }
    
    private void startCallTimer() {
        callTimerHandler.post(callTimerRunnable);
    }
    
    private void stopCallTimer() {
        callTimerHandler.removeCallbacks(callTimerRunnable);
    }
    
    private void startQualityMonitoring() {
        qualityCheckHandler.post(qualityCheckRunnable);
    }
    
    private void stopQualityMonitoring() {
        qualityCheckHandler.removeCallbacks(qualityCheckRunnable);
    }
    
    private void startConnectionTimeout() {
        connectionTimeoutHandler.postDelayed(() -> {
            if (!isConnected && isConnecting) {
                handleConnectionFailure();
            }
        }, CONNECTION_TIMEOUT_MS);
    }
    
    private void cancelConnectionTimeout() {
        connectionTimeoutHandler.removeCallbacksAndMessages(null);
    }
    
    private void logCallStatistics() {
        long callDuration = callStartTime > 0 ? System.currentTimeMillis() - callStartTime : 0;
        Log.i(TAG, "Call Statistics - Duration: " + callDuration + "ms, Frame Drops: " + totalFrameDrops + 
                   ", Network Quality: " + currentNetworkQuality + ", Connection Attempts: " + connectionAttempts);
    }
    
    private void showStatus(String message) {
        tvStatus.setText(message);
        tvStatus.setVisibility(android.view.View.VISIBLE);
    }
    
    private void showError(String message, boolean finishActivity) {
        Snackbar.make(rootLayout, message, Snackbar.LENGTH_LONG)
                .setAction("RETRY", v -> {
                    if (finishActivity) {
                        endCall();
                    } else {
                        initializeVideoCall();
                    }
                })
                .show();
    }
    
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
            }, PERMISSION_REQ_ID);
            return false;
        }
        return true;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeVideoCall();
            } else {
                showError("Permissions required for video call", true);
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Handle PiP mode or background processing
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Resume video processing
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCallTimer();
        stopQualityMonitoring();
        cancelConnectionTimeout();
        
        if (agoraManager != null) {
            agoraManager.destroy();
        }
    }
    
    @Override
    public void onBackPressed() {
        // Show confirmation dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Leave Call")
                .setMessage("Are you sure you want to leave this call?")
                .setPositiveButton("Leave", (dialog, which) -> {
                    super.onBackPressed();
                })
                .setNegativeButton("Stay", null)
                .show();
    }
    
    private enum VideoCallState {
        CONNECTING,
        CONNECTED,
        VIDEO_ACTIVE,
        CONNECTION_FAILED
    }
}