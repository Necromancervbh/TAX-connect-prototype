package com.example.taxconnect;

import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;

import com.example.taxconnect.services.AgoraManager;
import com.google.android.material.button.MaterialButton;

import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.video.VideoCanvas;

import android.widget.ImageButton;
import android.widget.TextView;
import android.app.PictureInPictureParams;
import android.util.Rational;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import androidx.annotation.RequiresApi;

public class VideoCallActivity extends AppCompatActivity {

    private FrameLayout localVideoContainer;
    private FrameLayout remoteVideoContainer;
    private TextView tvStatus;
    private TextView tvCallDuration;
    private MaterialButton btnEndCall;
    private MaterialButton btnMute;
    private MaterialButton btnSwitchCamera;
    private MaterialButton btnToggleVideo;
    private ImageButton btnMinimize;
    
    private RtcEngine rtcEngine;
    private String channelName;
    private int remoteUid;
    private boolean isMuted = false;
    private boolean isVideoMuted = false;
    private boolean isRemoteAudioMuted = false;
    private boolean isRemoteVideoMuted = false;

    public static boolean isInCall = false;

    private android.os.Handler timerHandler = new android.os.Handler();
    private long startTime = 0L;
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long millis = System.currentTimeMillis() - startTime;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;

            if (tvCallDuration != null) {
                tvCallDuration.setText(String.format("%02d:%02d", minutes, seconds));
            }

            timerHandler.postDelayed(this, 500);
        }
    };

    private static final int PERMISSION_REQ_ID = 22;
    private com.google.firebase.firestore.ListenerRegistration callStatusListener;

    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onUserJoined(int uid, int elapsed) {
            runOnUiThread(() -> {
                // Toast.makeText(VideoCallActivity.this, "User joined: " + uid, Toast.LENGTH_SHORT).show();
                if (tvStatus != null) tvStatus.setVisibility(View.GONE);
                startTimer();
                setupRemoteVideo(uid);
                java.util.Map<String, Object> d = new java.util.HashMap<>();
                d.put("event", "remote_user_joined");
                d.put("uid", uid);
                d.put("channelName", channelName);
                com.example.taxconnect.repository.AnalyticsRepository.getInstance().log("remote_user_joined", d);
            });
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            runOnUiThread(() -> onRemoteUserLeft());
        }

        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            runOnUiThread(() -> {
                if (channelName != null) {
                    com.example.taxconnect.repository.ConversationRepository.getInstance().updateCallStatus(channelName, "ACTIVE", null);
                }
                java.util.Map<String, Object> d = new java.util.HashMap<>();
                d.put("event", "join_channel_success");
                d.put("channelName", channelName);
                d.put("uid", uid);
                com.example.taxconnect.repository.AnalyticsRepository.getInstance().log("join_channel_success", d);
            });
        }

        @Override
        public void onRemoteVideoStateChanged(int uid, int state, int reason, int elapsed) {
             super.onRemoteVideoStateChanged(uid, state, reason, elapsed);
             // 1 = Starting, 2 = Decoding
             if (state == Constants.REMOTE_VIDEO_STATE_STARTING || state == Constants.REMOTE_VIDEO_STATE_DECODING) {
                 runOnUiThread(() -> setupRemoteVideo(uid));
             }
             boolean muted = state == Constants.REMOTE_VIDEO_STATE_STOPPED || state == Constants.REMOTE_VIDEO_STATE_FAILED;
             runOnUiThread(() -> updateRemoteVideoState(muted));
        }

        @Override
        public void onUserMuteAudio(int uid, boolean muted) {
            runOnUiThread(() -> updateRemoteAudioState(muted));
        }

        @Override
        public void onError(int err) {
            runOnUiThread(() -> {
                String errorMsg = "Agora Error: " + err;
                if (err == 110) errorMsg += " (Invalid Token)";
                if (err == 109) errorMsg += " (Token Expired)";
                if (err == 101) errorMsg += " (Invalid App ID)";
                Toast.makeText(VideoCallActivity.this, errorMsg, Toast.LENGTH_LONG).show();
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep screen on during video call
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_video_call);

        localVideoContainer = findViewById(R.id.localVideoContainer);
        remoteVideoContainer = findViewById(R.id.remoteVideoContainer);
        tvStatus = findViewById(R.id.tvStatus);
        tvCallDuration = findViewById(R.id.tvCallDuration);
        btnEndCall = findViewById(R.id.btnEndCall);
        btnMute = findViewById(R.id.btnMute);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);
        btnToggleVideo = findViewById(R.id.btnToggleVideo);
        btnMinimize = findViewById(R.id.btnMinimize);

        channelName = getIntent().getStringExtra("CHANNEL_NAME");
        if (channelName == null) {
            finish();
            return;
        }
        String actionExtra = getIntent().getStringExtra("ACTION");
        if ("ANSWER".equals(actionExtra) && channelName != null) {
            com.example.taxconnect.repository.ConversationRepository.getInstance().updateCallStatus(channelName, "ACCEPTED", null);
        }
        isInCall = true;
        String callerName = getIntent().getStringExtra("CALLER_NAME");
        if (callerName != null && !callerName.isEmpty()) {
            tvStatus.setText("Connecting to " + callerName + "...");
        }

        // Listen for call status changes (e.g., REJECTED)
        listenForCallStatus();

        if (checkPermissions()) {
            initAgora();
        }
        
        btnEndCall.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            announceForAccessibility("Call ended");
            leaveChannel();
            finish();
        });

        btnMute.setOnClickListener(v -> {
            if (rtcEngine == null) return;
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            isMuted = !isMuted;
            rtcEngine.muteLocalAudioStream(isMuted);
            updateMuteButtonState();
            announceForAccessibility(isMuted ? "Microphone muted" : "Microphone unmuted");
        });

        btnToggleVideo.setOnClickListener(v -> {
            if (rtcEngine == null) return;
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            isVideoMuted = !isVideoMuted;
            rtcEngine.muteLocalVideoStream(isVideoMuted);
            // Update local video view visibility
            if (localVideoContainer.getChildCount() > 0) {
                 localVideoContainer.getChildAt(0).setVisibility(isVideoMuted ? View.INVISIBLE : View.VISIBLE);
            }
            updateVideoButtonState();
            announceForAccessibility(isVideoMuted ? "Camera off" : "Camera on");
        });

        btnSwitchCamera.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            if (rtcEngine != null) rtcEngine.switchCamera();
        });

        btnMinimize.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPipWithHints();
            }
        });
        updateMuteButtonState();
        updateVideoButtonState();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        int visibility = isInPictureInPictureMode ? View.GONE : View.VISIBLE;
        
        findViewById(R.id.controlsContainer).setVisibility(visibility);
        findViewById(R.id.btnMinimize).setVisibility(visibility);
        findViewById(R.id.scrimTop).setVisibility(visibility);
        findViewById(R.id.scrimBottom).setVisibility(visibility);
        findViewById(R.id.cvLocalVideo).setVisibility(visibility);
        
        if (isInPictureInPictureMode) {
            if (tvStatus != null) tvStatus.setVisibility(View.GONE);
            if (tvCallDuration != null) tvCallDuration.setVisibility(View.GONE);
        } else {
             // If we are back to full screen, show duration if running
             if (tvCallDuration != null && startTime > 0) tvCallDuration.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Auto-enter PiP on Android 12+ with source rect hints
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(buildPipParams(true));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipWithHints();
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void enterPipWithHints() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams params = buildPipParams(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);
            enterPictureInPictureMode(params);
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private PictureInPictureParams buildPipParams(boolean autoEnter) {
        Rational aspectRatio = new Rational(9, 16);
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio);
        Rect hint = computeSourceRectHint();
        if (hint != null) {
            builder.setSourceRectHint(hint);
        }
        if (autoEnter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true);
        }
        return builder.build();
    }

    private Rect computeSourceRectHint() {
        View anchor = remoteVideoContainer != null && remoteVideoContainer.getVisibility() == View.VISIBLE
                ? remoteVideoContainer
                : localVideoContainer;
        if (anchor == null) return null;
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        return new Rect(location[0], location[1],
                location[0] + anchor.getWidth(),
                location[1] + anchor.getHeight());
    }

    private void startTimer() {
        startTime = System.currentTimeMillis();
        if (tvCallDuration != null) {
            tvCallDuration.setVisibility(View.VISIBLE);
        }
        timerHandler.postDelayed(timerRunnable, 0);
    }

    private void stopTimer() {
        timerHandler.removeCallbacks(timerRunnable);
        startTime = 0;
        if (tvCallDuration != null) {
            tvCallDuration.setVisibility(View.GONE);
        }
    }

    private void listenForCallStatus() {
        if (callStatusListener != null) {
            callStatusListener.remove();
        }
        callStatusListener = com.example.taxconnect.repository.ConversationRepository.getInstance().listenToConversation(channelName, new com.example.taxconnect.DataRepository.DataCallback<com.example.taxconnect.model.ConversationModel>() {
            @Override
            public void onSuccess(com.example.taxconnect.model.ConversationModel conversation) {
                if (conversation != null) {
                    String status = conversation.getCallStatus();
                    if ("REJECTED".equals(status)) {
                        java.util.Map<String, Object> d = new java.util.HashMap<>();
                        d.put("status", "REJECTED");
                        d.put("channelName", channelName);
                        com.example.taxconnect.repository.AnalyticsRepository.getInstance().log("call_status_change", d);
                        Toast.makeText(VideoCallActivity.this, "Call Declined", Toast.LENGTH_SHORT).show();
                        leaveChannel();
                        finish();
                    } else if ("BUSY".equals(status)) {
                        java.util.Map<String, Object> d = new java.util.HashMap<>();
                        d.put("status", "BUSY");
                        d.put("channelName", channelName);
                        com.example.taxconnect.repository.AnalyticsRepository.getInstance().log("call_status_change", d);
                        Toast.makeText(VideoCallActivity.this, "User is busy", Toast.LENGTH_SHORT).show();
                        leaveChannel();
                        finish();
                    } else if ("ENDED".equals(status)) {
                        java.util.Map<String, Object> d = new java.util.HashMap<>();
                        d.put("status", "ENDED");
                        d.put("channelName", channelName);
                        com.example.taxconnect.repository.AnalyticsRepository.getInstance().log("call_status_change", d);
                        leaveChannel();
                        finish();
                    }
                }
            }

            @Override
            public void onError(String error) {
                // Ignore errors
            }
        });
    }

    private void initAgora() {
        try {
            AgoraManager agoraManager = AgoraManager.getInstance(this);
            rtcEngine = agoraManager.getRtcEngine();
            
            if (rtcEngine == null) {
                Toast.makeText(this, "Failed to initialize video engine", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            // In v4, we can add multiple handlers
            rtcEngine.addHandler(mRtcEventHandler);
            rtcEngine.enableVideo();
            
            // Set channel profile to communication
            rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);
            // Set client role to broadcaster
            rtcEngine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);

            // Enable video module
            rtcEngine.enableVideo();
            
            // Set Audio Profile to Music High Quality for better voice clarity
            rtcEngine.setAudioProfile(Constants.AUDIO_PROFILE_MUSIC_HIGH_QUALITY, Constants.AUDIO_SCENARIO_GAME_STREAMING);
            
            // High Quality Video Config (720p, 30fps)
            io.agora.rtc2.video.VideoEncoderConfiguration config = new io.agora.rtc2.video.VideoEncoderConfiguration(
                io.agora.rtc2.video.VideoEncoderConfiguration.VD_1280x720,
                io.agora.rtc2.video.VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30,
                io.agora.rtc2.video.VideoEncoderConfiguration.STANDARD_BITRATE,
                io.agora.rtc2.video.VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
            );
            rtcEngine.setVideoEncoderConfiguration(config);

            rtcEngine.startPreview();

            setupLocalVideo();
            joinChannel();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupLocalVideo() {
        SurfaceView surfaceView = new SurfaceView(this);
        surfaceView.setZOrderMediaOverlay(true);
        localVideoContainer.removeAllViews();
        localVideoContainer.addView(surfaceView);
        rtcEngine.setupLocalVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0));
    }

    private void setupRemoteVideo(int uid) {
        if (remoteVideoContainer.getChildCount() >= 1) {
            // Already set up, but let's re-attach just in case
            View existingView = remoteVideoContainer.getChildAt(0);
            if (existingView instanceof SurfaceView) {
                 rtcEngine.setupRemoteVideo(new VideoCanvas(existingView, VideoCanvas.RENDER_MODE_HIDDEN, uid));
            }
            return;
        }
        SurfaceView surfaceView = new SurfaceView(this);
        surfaceView.setZOrderMediaOverlay(false); 
        remoteVideoContainer.removeAllViews();
        remoteVideoContainer.addView(surfaceView);
        
        rtcEngine.setupRemoteVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid));
        surfaceView.setVisibility(View.VISIBLE);
        
        Toast.makeText(this, "Remote video setup for: " + uid, Toast.LENGTH_SHORT).show();
    }

    private void onRemoteUserLeft() {
        stopTimer();
        remoteVideoContainer.removeAllViews();
        Toast.makeText(this, "Call Ended", Toast.LENGTH_SHORT).show();
        if (channelName != null) {
            com.example.taxconnect.repository.ConversationRepository.getInstance().updateCallStatus(channelName, "ENDED", null);
        }
        leaveChannel();
        finish();
    }

    private void joinChannel() {
        // Token is null for testing (if app certificate is disabled in console, otherwise need token)
        // Using "null" token implies testing mode or App ID only mode.
        if (channelName != null && !channelName.isEmpty()) {
            rtcEngine.joinChannel(null, channelName, "Optional Data", 0);
        } else {
            Toast.makeText(this, "Channel name is missing", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void leaveChannel() {
        if (rtcEngine != null) {
            rtcEngine.leaveChannel();
            rtcEngine.stopPreview();
            rtcEngine.removeHandler(mRtcEventHandler);
        }
    }

    private void announceForAccessibility(String text) {
        View root = findViewById(android.R.id.content);
        if (root != null && text != null) {
            root.announceForAccessibility(text);
        }
    }

    private void updateMuteButtonState() {
        btnMute.setIconResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic_on);
        btnMute.setContentDescription(isMuted ? "Unmute microphone" : "Mute microphone");
        int bgColor = ContextCompat.getColor(this, isMuted ? R.color.error : R.color.slate_100);
        int iconColor = ContextCompat.getColor(this, isMuted ? R.color.white : R.color.black);
        btnMute.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
        btnMute.setIconTint(android.content.res.ColorStateList.valueOf(iconColor));
    }

    private void updateVideoButtonState() {
        btnToggleVideo.setIconResource(isVideoMuted ? android.R.drawable.ic_menu_close_clear_cancel : android.R.drawable.ic_menu_camera);
        btnToggleVideo.setContentDescription(isVideoMuted ? "Turn camera on" : "Turn camera off");
        int bgColor = ContextCompat.getColor(this, isVideoMuted ? R.color.error : R.color.slate_100);
        int iconColor = ContextCompat.getColor(this, isVideoMuted ? R.color.white : R.color.black);
        btnToggleVideo.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
        btnToggleVideo.setIconTint(android.content.res.ColorStateList.valueOf(iconColor));
    }

    private void updateRemoteAudioState(boolean muted) {
        isRemoteAudioMuted = muted;
        if (tvStatus != null) {
            if (muted) {
                tvStatus.setText("Remote microphone muted");
                tvStatus.setVisibility(View.VISIBLE);
            } else if (!isRemoteVideoMuted) {
                tvStatus.setVisibility(View.GONE);
            }
        }
    }

    private void updateRemoteVideoState(boolean muted) {
        isRemoteVideoMuted = muted;
        if (tvStatus != null) {
            if (muted) {
                tvStatus.setText("Remote camera off");
                tvStatus.setVisibility(View.VISIBLE);
            } else if (!isRemoteAudioMuted) {
                tvStatus.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        leaveChannel();
        // Destroy the engine to ensure background process is killed
        AgoraManager.getInstance(this).destroy();
        if (callStatusListener != null) {
            callStatusListener.remove();
            callStatusListener = null;
        }
        isInCall = false;
    }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.CAMERA
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
                initAgora();
            } else {
                Toast.makeText(this, "Permissions required for video call", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
