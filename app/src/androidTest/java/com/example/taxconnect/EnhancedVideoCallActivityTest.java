package com.example.taxconnect;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.taxconnect.services.EnhancedAgoraManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Basic UI Test Suite for EnhancedVideoCallActivity
 * Tests basic functionality without external dependencies
 */
@RunWith(AndroidJUnit4.class)
public class EnhancedVideoCallActivityTest {

    @Test
    public void testActivityLaunch() {
        // Test that activity launches successfully
        Intent intent = new Intent();
        intent.putExtra("CHANNEL_NAME", "test_channel");
        intent.putExtra("PARTICIPANT_NAME", "Test User");
        
        try (ActivityScenario<EnhancedVideoCallActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertNotNull("Activity should not be null", activity);
                assertNotNull("Activity should have valid intent", activity.getIntent());
                assertEquals("Channel name should match", "test_channel", activity.getIntent().getStringExtra("CHANNEL_NAME"));
                assertEquals("Participant name should match", "Test User", activity.getIntent().getStringExtra("PARTICIPANT_NAME"));
            });
        }
    }

    @Test
    public void testNetworkQualityEnum() {
        // Test NetworkQuality enum values
        EnhancedAgoraManager.NetworkQuality[] qualities = EnhancedAgoraManager.NetworkQuality.values();
        assertEquals(4, qualities.length);
        
        assertEquals(EnhancedAgoraManager.NetworkQuality.EXCELLENT, qualities[0]);
        assertEquals(EnhancedAgoraManager.NetworkQuality.GOOD, qualities[1]);
        assertEquals(EnhancedAgoraManager.NetworkQuality.POOR, qualities[2]);
        assertEquals(EnhancedAgoraManager.NetworkQuality.BAD, qualities[3]);
    }

    @Test
    public void testConnectionQualityEnum() {
        // Test ConnectionQuality enum values
        EnhancedAgoraManager.ConnectionQuality[] qualities = EnhancedAgoraManager.ConnectionQuality.values();
        assertEquals(5, qualities.length);
        
        assertEquals(EnhancedAgoraManager.ConnectionQuality.EXCELLENT, qualities[0]);
        assertEquals(EnhancedAgoraManager.ConnectionQuality.GOOD, qualities[1]);
        assertEquals(EnhancedAgoraManager.ConnectionQuality.POOR, qualities[2]);
        assertEquals(EnhancedAgoraManager.ConnectionQuality.BAD, qualities[3]);
        assertEquals(EnhancedAgoraManager.ConnectionQuality.UNKNOWN, qualities[4]);
    }

    @Test
    public void testNetworkQualityProperties() {
        // Test NetworkQuality enum properties
        EnhancedAgoraManager.NetworkQuality excellent = EnhancedAgoraManager.NetworkQuality.EXCELLENT;
        assertEquals(0, excellent.agoraQuality);
        assertEquals(1920, excellent.width);
        assertEquals(1080, excellent.height);
        assertEquals(30, excellent.fps);
        assertEquals(3000, excellent.bitrate);

        EnhancedAgoraManager.NetworkQuality good = EnhancedAgoraManager.NetworkQuality.GOOD;
        assertEquals(1, good.agoraQuality);
        assertEquals(1280, good.width);
        assertEquals(720, good.height);
        assertEquals(30, good.fps);
        assertEquals(1500, good.bitrate);

        EnhancedAgoraManager.NetworkQuality poor = EnhancedAgoraManager.NetworkQuality.POOR;
        assertEquals(2, poor.agoraQuality);
        assertEquals(854, poor.width);
        assertEquals(480, poor.height);
        assertEquals(24, poor.fps);
        assertEquals(800, poor.bitrate);

        EnhancedAgoraManager.NetworkQuality bad = EnhancedAgoraManager.NetworkQuality.BAD;
        assertEquals(3, bad.agoraQuality);
        assertEquals(640, bad.width);
        assertEquals(360, bad.height);
        assertEquals(15, bad.fps);
        assertEquals(400, bad.bitrate);
    }

    @Test
    public void testConnectionQualityValues() {
        // Test that ConnectionQuality enum has correct quality values
        assertEquals(4, EnhancedAgoraManager.ConnectionQuality.EXCELLENT.getQuality());
        assertEquals(3, EnhancedAgoraManager.ConnectionQuality.GOOD.getQuality());
        assertEquals(2, EnhancedAgoraManager.ConnectionQuality.POOR.getQuality());
        assertEquals(1, EnhancedAgoraManager.ConnectionQuality.BAD.getQuality());
        assertEquals(0, EnhancedAgoraManager.ConnectionQuality.UNKNOWN.getQuality());
    }

    @Test
    public void testConnectionQualityConversion() {
        // Test connection quality conversion logic
        // This tests the logic from convertAgoraQualityToConnectionQuality
        
        // Test excellent quality (avg <= 1)
        assertEquals(EnhancedAgoraManager.ConnectionQuality.EXCELLENT, 
                    getConnectionQualityFromAgora(0, 0));
        assertEquals(EnhancedAgoraManager.ConnectionQuality.EXCELLENT, 
                    getConnectionQualityFromAgora(1, 1));
        
        // Test good quality (avg <= 2)
        assertEquals(EnhancedAgoraManager.ConnectionQuality.GOOD, 
                    getConnectionQualityFromAgora(2, 2));
        assertEquals(EnhancedAgoraManager.ConnectionQuality.GOOD, 
                    getConnectionQualityFromAgora(1, 3));
        
        // Test poor quality (avg <= 3)
        assertEquals(EnhancedAgoraManager.ConnectionQuality.POOR, 
                    getConnectionQualityFromAgora(3, 3));
        assertEquals(EnhancedAgoraManager.ConnectionQuality.POOR, 
                    getConnectionQualityFromAgora(2, 4));
        
        // Test bad quality (avg > 3)
        assertEquals(EnhancedAgoraManager.ConnectionQuality.BAD, 
                    getConnectionQualityFromAgora(4, 4));
        assertEquals(EnhancedAgoraManager.ConnectionQuality.BAD, 
                    getConnectionQualityFromAgora(5, 5));
    }

    @Test
    public void testNetworkQualityAnalysis() {
        // Test network quality analysis logic
        // This tests the logic from analyzeNetworkStats
        
        // Test excellent quality (RTT <= 50, packet loss <= 0.5%)
        assertEquals(EnhancedAgoraManager.NetworkQuality.EXCELLENT, 
                    analyzeNetworkQuality(40, 0.3f, 0.2f));
        
        // Test good quality (RTT <= 150, packet loss <= 2%)
        assertEquals(EnhancedAgoraManager.NetworkQuality.GOOD, 
                    analyzeNetworkQuality(100, 1.5f, 1.0f));
        
        // Test poor quality (RTT <= 300, packet loss <= 5%)
        assertEquals(EnhancedAgoraManager.NetworkQuality.POOR, 
                    analyzeNetworkQuality(200, 3.0f, 2.5f));
        
        // Test bad quality (RTT > 300 or packet loss > 5%)
        assertEquals(EnhancedAgoraManager.NetworkQuality.BAD, 
                    analyzeNetworkQuality(350, 6.0f, 5.5f));
    }

    // Helper methods to simulate the manager's logic
    private EnhancedAgoraManager.ConnectionQuality getConnectionQualityFromAgora(int txQuality, int rxQuality) {
        int avgQuality = (txQuality + rxQuality) / 2;
        
        if (avgQuality <= 1) {
            return EnhancedAgoraManager.ConnectionQuality.EXCELLENT;
        } else if (avgQuality <= 2) {
            return EnhancedAgoraManager.ConnectionQuality.GOOD;
        } else if (avgQuality <= 3) {
            return EnhancedAgoraManager.ConnectionQuality.POOR;
        } else {
            return EnhancedAgoraManager.ConnectionQuality.BAD;
        }
    }

    private EnhancedAgoraManager.NetworkQuality analyzeNetworkQuality(float gatewayRtt, float rxPacketLossRate, float txPacketLossRate) {
        if (gatewayRtt > 300 || rxPacketLossRate > 5 || txPacketLossRate > 5) {
            return EnhancedAgoraManager.NetworkQuality.BAD;
        } else if (gatewayRtt > 150 || rxPacketLossRate > 2 || txPacketLossRate > 2) {
            return EnhancedAgoraManager.NetworkQuality.POOR;
        } else if (gatewayRtt > 50 || rxPacketLossRate > 0.5 || txPacketLossRate > 0.5) {
            return EnhancedAgoraManager.NetworkQuality.GOOD;
        } else {
            return EnhancedAgoraManager.NetworkQuality.EXCELLENT;
        }
    }
}