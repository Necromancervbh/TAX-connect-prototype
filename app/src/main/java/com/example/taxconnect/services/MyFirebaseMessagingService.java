package com.example.taxconnect.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.example.taxconnect.ChatActivity;
import com.example.taxconnect.R;
import com.example.taxconnect.RequestsActivity;
import com.example.taxconnect.VideoCallActivity;
import com.google.firebase.messaging.FirebaseMessagingService;
import android.annotation.SuppressLint;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "tax_connect_notifications";
    private static final String CHANNEL_NAME = "TaxConnect Notifications";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        // Save to Shared Preferences
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putString("fcm_token", token).apply();
        
        com.example.taxconnect.repository.UserRepository.getInstance().updateFcmToken(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // Handle data payload
        if (remoteMessage.getData().size() > 0) {
            handleDataMessage(remoteMessage.getData());
        }
        // Handle notification payload only if data payload didn't already trigger a notification
        // logic: handleDataMessage always sends a notification. 
        // So we only process getNotification() if data is empty.
        else if (remoteMessage.getNotification() != null) {
            sendNotification(remoteMessage.getNotification().getTitle(), remoteMessage.getNotification().getBody(), null);
        }
    }

    private void handleDataMessage(Map<String, String> data) {
        String type = data.get("type");
        String title = data.get("title");
        String body = data.get("body");
        
        Intent intent = null;

        if ("message".equals(type)) {
            String senderId = data.get("senderId");
            String senderName = data.get("senderName");
            intent = new Intent(this, ChatActivity.class);
            if (senderId != null) intent.putExtra("RECEIVER_ID", senderId);
            if (senderName != null) intent.putExtra("RECEIVER_NAME", senderName);
        } else if ("request".equals(type)) {
            intent = new Intent(this, RequestsActivity.class);
        } else if ("call".equals(type)) {
            String channelName = data.get("channelName");
            String roomUuid = data.get("roomUuid");
            String callerName = data.get("callerName");
            String callerAvatar = data.get("callerAvatar");
            if (com.example.taxconnect.VideoCallActivity.isInCall) {
                if (channelName != null) {
                    com.example.taxconnect.repository.ConversationRepository.getInstance().updateCallStatus(channelName, "BUSY", null);
                    java.util.Map<String, Object> d = new java.util.HashMap<>();
                    d.put("channelName", channelName);
                    d.put("roomUuid", roomUuid);
                    d.put("callerName", callerName);
                    com.example.taxconnect.repository.AnalyticsRepository.getInstance().log("call_busy", d);
                }
                return;
            }
            sendCallNotification(title, body, channelName, roomUuid, callerName, callerAvatar);
            return;
        }
        
        sendNotification(title, body, intent);
    }

    @SuppressLint("NotificationTrampoline")
    private void sendCallNotification(String title, String body, String channelName, String roomUuid, String callerName, String callerAvatar) {
        int notificationId = (int) System.currentTimeMillis(); // Unique ID per call

        // 1. Answer Action (launch activity directly to avoid notification trampolines)
        Intent answerIntent = new Intent(this, VideoCallActivity.class);
        answerIntent.putExtra("CHANNEL_NAME", channelName);
        answerIntent.putExtra("ROOM_UUID", roomUuid);
        answerIntent.putExtra("CALLER_NAME", callerName);
        answerIntent.putExtra("CALLER_AVATAR", callerAvatar);
        answerIntent.putExtra("INCOMING_CALL", true);
        answerIntent.putExtra("ACTION", "ANSWER");
        answerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent answerPendingIntent = PendingIntent.getActivity(this, notificationId + 1, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 2. Decline Action
        Intent declineIntent = new Intent(this, CallActionReceiver.class);
        declineIntent.setAction(CallActionReceiver.ACTION_DECLINE);
        declineIntent.putExtra("notification_id", notificationId);
        declineIntent.putExtra("CHANNEL_NAME", channelName);
        declineIntent.putExtra("ROOM_UUID", roomUuid);
        declineIntent.putExtra("CALLER_NAME", callerName);
        declineIntent.putExtra("CALLER_AVATAR", callerAvatar);
        PendingIntent declinePendingIntent = PendingIntent.getBroadcast(this, notificationId + 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 3. Full Screen Intent (Heads-up)
        Intent fullScreenIntent = new Intent(this, VideoCallActivity.class);
        fullScreenIntent.putExtra("CHANNEL_NAME", channelName);
        fullScreenIntent.putExtra("ROOM_UUID", roomUuid);
        fullScreenIntent.putExtra("CALLER_NAME", callerName);
        fullScreenIntent.putExtra("CALLER_AVATAR", callerAvatar);
        fullScreenIntent.putExtra("INCOMING_CALL", true);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, notificationId + 3, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE); // Use Ringtone for calls

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_video_call)
                        .setContentTitle(callerName != null && !callerName.isEmpty() ? callerName : (title != null ? title : "Incoming Video Call"))
                        .setContentText(body != null ? body : "Tap to answer")
                        .setPriority(NotificationCompat.PRIORITY_MAX) // MAX for heads-up
                        .setCategory(NotificationCompat.CATEGORY_CALL)
                        .setAutoCancel(true)
                        .setOngoing(true)
                        .setSound(defaultSoundUri)
                        .setFullScreenIntent(fullScreenPendingIntent, true)
                        .addAction(R.drawable.ic_video_call, "Answer", answerPendingIntent)
                        .addAction(R.drawable.ic_close, "Decline", declinePendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("TaxConnect Notifications");
            // Ensure sound/vibration is enabled for this channel
            channel.enableVibration(true);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(notificationId, notificationBuilder.build());
        java.util.Map<String, Object> d = new java.util.HashMap<>();
        d.put("channelName", channelName);
        d.put("roomUuid", roomUuid);
        d.put("callerName", callerName);
        com.example.taxconnect.repository.AnalyticsRepository.getInstance().log("incoming_call_shown", d);
    }

    private void sendNotification(String title, String body, Intent intent) {
        if (intent == null) {
            intent = new Intent(this, com.example.taxconnect.MainActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title != null ? title : "TaxConnect")
                        .setContentText(body != null ? body : "New Notification")
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
    }

    // Token update handled in the first onNewToken method

}
