package com.example.taxconnect;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.ivLogo);
        TextView title = findViewById(R.id.tvAppName);
        TextView tagline = findViewById(R.id.tvTagline);

        // Initial State
        logo.setAlpha(0f);
        logo.setScaleX(0.5f);
        logo.setScaleY(0.5f);
        
        title.setAlpha(0f);
        title.setTranslationY(100f);

        tagline.setAlpha(0f);

        // Logo Animation (Pop effect)
        ObjectAnimator logoAlpha = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f);
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(logo, "scaleX", 0.5f, 1f);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(logo, "scaleY", 0.5f, 1f);

        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(logoAlpha, logoScaleX, logoScaleY);
        logoSet.setDuration(1000);
        logoSet.setInterpolator(new OvershootInterpolator());

        // Title Animation (Slide Up)
        ObjectAnimator titleAlpha = ObjectAnimator.ofFloat(title, "alpha", 0f, 1f);
        ObjectAnimator titleTranslation = ObjectAnimator.ofFloat(title, "translationY", 100f, 0f);

        AnimatorSet titleSet = new AnimatorSet();
        titleSet.playTogether(titleAlpha, titleTranslation);
        titleSet.setDuration(800);
        titleSet.setStartDelay(500); // Start after logo is halfway
        titleSet.setInterpolator(new AccelerateDecelerateInterpolator());
        
        // Tagline Animation (Fade In)
        ObjectAnimator taglineAlpha = ObjectAnimator.ofFloat(tagline, "alpha", 0f, 1f);
        taglineAlpha.setDuration(800);
        taglineAlpha.setStartDelay(1200);

        // Play All
        AnimatorSet fullSet = new AnimatorSet();
        fullSet.playTogether(logoSet, titleSet, taglineAlpha);
        fullSet.start();

        // Delay for 3 seconds then move to MainActivity
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }, 3000);
    }
}
