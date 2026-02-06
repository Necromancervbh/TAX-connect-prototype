package com.example.taxconnect.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.core.app.NotificationManagerCompat;

import com.example.taxconnect.VideoCallActivity;
import com.example.taxconnect.DataRepository;

public class CallActionReceiver extends BroadcastReceiver {

    public static final String ACTION_ANSWER = "com.example.taxconnect.ACTION_ANSWER";
    public static final String ACTION_DECLINE = "com.example.taxconnect.ACTION_DECLINE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        int notificationId = intent.getIntExtra("notification_id", -1);
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId);
        }

        String action = intent.getAction();
        String channelName = intent.getStringExtra("CHANNEL_NAME");
        String roomUuid = intent.getStringExtra("ROOM_UUID");
        String callerName = intent.getStringExtra("CALLER_NAME");
        String callerAvatar = intent.getStringExtra("CALLER_AVATAR");

        if (ACTION_ANSWER.equals(action)) {
            if (VideoCallActivity.isInCall) {
                if (channelName != null) {
                    com.example.taxconnect.repository.ConversationRepository.getInstance().updateCallStatus(channelName, "BUSY", null);
                }
                java.util.Map<String, Object> d = new java.util.HashMap<>();
                d.put("channelName", channelName);
                d.put("roomUuid", roomUuid);
                com.example.taxconnect.repository.AnalyticsRepository.getInstance().log("call_busy", d);
                return;
            }
            if (channelName != null) {
                com.example.taxconnect.repository.ConversationRepository.getInstance().updateCallStatus(channelName, "ACCEPTED", null);
            }
            Intent callIntent = new Intent(context, VideoCallActivity.class);
            callIntent.putExtra("CHANNEL_NAME", channelName);
            callIntent.putExtra("ROOM_UUID", roomUuid);
            callIntent.putExtra("CALLER_NAME", callerName);
            callIntent.putExtra("CALLER_AVATAR", callerAvatar);
            callIntent.putExtra("INCOMING_CALL", true);
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(callIntent);
            java.util.Map<String, Object> d = new java.util.HashMap<>();
            d.put("channelName", channelName);
            d.put("roomUuid", roomUuid);
            com.example.taxconnect.repository.AnalyticsRepository.getInstance().log("call_answered", d);
        } else if (ACTION_DECLINE.equals(action)) {
            if (channelName != null) {
                com.example.taxconnect.repository.ConversationRepository.getInstance().updateCallStatus(channelName, "REJECTED", null);
            }
            Log.d("CallActionReceiver", "Call Declined");
            java.util.Map<String, Object> d = new java.util.HashMap<>();
            d.put("channelName", channelName);
            d.put("roomUuid", roomUuid);
            com.example.taxconnect.repository.AnalyticsRepository.getInstance().log("call_declined", d);
        }
    }
}
