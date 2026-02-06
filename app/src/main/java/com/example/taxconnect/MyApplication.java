package com.example.taxconnect;

import android.app.Application;
import com.example.taxconnect.services.CloudinaryHelper;
import com.example.taxconnect.utils.ThemeHelper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.perf.FirebasePerformance;
import com.razorpay.Checkout;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        try {
            FirebasePerformance.getInstance().setPerformanceCollectionEnabled(true);
        } catch (Throwable t) {
            // Ignore if perf not available in some build variants
        }

        // Apply Theme
        ThemeHelper.applyTheme(this);
        
        // Initialize Cloudinary
        CloudinaryHelper.init(this);
        
        // Preload Razorpay
        Checkout.preload(getApplicationContext());
    }
}
