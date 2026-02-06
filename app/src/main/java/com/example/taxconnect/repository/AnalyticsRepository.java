package com.example.taxconnect.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AnalyticsRepository {

    private static AnalyticsRepository instance;
    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;

    private AnalyticsRepository() {
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    public static synchronized AnalyticsRepository getInstance() {
        if (instance == null) {
            instance = new AnalyticsRepository();
        }
        return instance;
    }

    public void log(String event, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("details", details != null ? details : new HashMap<>());
        if (firebaseAuth.getCurrentUser() != null) {
            payload.put("uid", firebaseAuth.getCurrentUser().getUid());
        }
        payload.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        firestore.collection("analytics").add(payload);
    }
}
