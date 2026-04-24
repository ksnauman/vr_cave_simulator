package com.example.vrdesert;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import androidx.appcompat.app.AppCompatActivity;

public class StartActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        View frostOverlay = findViewById(R.id.frostOverlay);
        View btnEnter    = findViewById(R.id.btnEnterCave);

        btnEnter.setOnClickListener(v -> {
            frostOverlay.setVisibility(View.VISIBLE);

            // Fade to white-frost
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(frostOverlay, "alpha", 0f, 1f);
            fadeIn.setDuration(500);
            fadeIn.setInterpolator(new AccelerateDecelerateInterpolator());
            fadeIn.start();

            // Launch after frost peak
            frostOverlay.postDelayed(() -> {
                Intent intent = new Intent(StartActivity.this, MainActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0); // no default animation — frost handles it
            }, 550);
        });

        findViewById(R.id.btnCalibration).setOnClickListener(v -> {
            Intent intent = new Intent(StartActivity.this, CalibrationActivity.class);
            startActivity(intent);
        });
    }
}
