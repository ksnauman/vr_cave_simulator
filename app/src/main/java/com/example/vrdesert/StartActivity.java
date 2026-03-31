package com.example.vrdesert;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class StartActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        Button btnBegin = findViewById(R.id.btnBegin);
        btnBegin.setOnClickListener(v -> {
            Intent intent = new Intent(StartActivity.this, StoryActivity.class);
            startActivity(intent);
        });
    }
}
