package com.example.vrdesert;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.opengl.GLSurfaceView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements InteractionManager.InteractionListener {

    private GLSurfaceView glSurfaceView;
    private VRRenderer vrRenderer;
    private SensorHandler sensorHandler;
    private InteractionManager interactionManager;

    private TextView feedbackLeft, feedbackRight;
    private TextView resultLeft, resultRight;

    private AudioEngine audioEngine;
    private GameManager gameManager;
    private MoveServer moveServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        glSurfaceView = findViewById(R.id.glSurfaceView);

        // Require OpenGL ES 2.0
        glSurfaceView.setEGLContextClientVersion(2);

        sensorHandler = new SensorHandler(this);
        interactionManager = new InteractionManager(this);

        vrRenderer = new VRRenderer(this, sensorHandler, interactionManager);
        glSurfaceView.setRenderer(vrRenderer);
        
        // Render only when data changes or animate continuously
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        feedbackLeft = findViewById(R.id.feedbackLeft);
        feedbackRight = findViewById(R.id.feedbackRight);
        resultLeft = findViewById(R.id.resultLeft);
        resultRight = findViewById(R.id.resultRight);

        audioEngine = new AudioEngine();
        audioEngine.startCaveAmbiance(); // Cavern rumbles

        ProgressBar healthBarLeft = findViewById(R.id.healthBarLeft);
        ProgressBar healthBarRight = findViewById(R.id.healthBarRight);
        TextView timerLeft = findViewById(R.id.timerLeft);
        TextView timerRight = findViewById(R.id.timerRight);
        gameManager = new GameManager(this, healthBarLeft, healthBarRight, timerLeft, timerRight);

        Button btnMove = findViewById(R.id.btnMove);
        btnMove.setOnClickListener(v -> vrRenderer.moveForward());

        // Spawn Native Web Controller Server targeting the Renderer on port 8080!
        moveServer = new MoveServer(vrRenderer);
        moveServer.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();
        sensorHandler.start();
        if (gameManager != null) gameManager.startHealthDecay();
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
        sensorHandler.stop();
        if (gameManager != null) gameManager.stopHealthDecay();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioEngine != null) {
            audioEngine.stopAudio();
        }
        if (moveServer != null) {
            moveServer.stopServer();
        }
    }

    @Override
    public void onObjectCollected(int objectId, GameObject.Type type) {
        if (gameManager.isGameLocked()) return; // Stop executing collection behaviors if final win-state initiated

        audioEngine.playCollectionSound(); // Ping!
        
        // Pass to logic threshold checker
        gameManager.processItemCollection(type);
        
        switch (type) {
            case EDIBLE_MUSHROOM: showFeedback("+10 Health", true); break;
            case CAVE_PLANT: showFeedback("+5 Health", true); break;
            case TOXIC_FUNGUS: showFeedback("-20 Health", false); break;
        }
    }

    private void showFeedback(String msg, boolean positive) {
        int color = positive ? 0xFF00FF00 : 0xFFFF0000;
        feedbackLeft.setTextColor(color);
        feedbackRight.setTextColor(color);
        feedbackLeft.setText(msg);
        feedbackRight.setText(msg);
        
        feedbackLeft.setVisibility(android.view.View.VISIBLE);
        feedbackRight.setVisibility(android.view.View.VISIBLE);
        feedbackLeft.setAlpha(1f);
        feedbackRight.setAlpha(1f);
        
        feedbackLeft.animate().alpha(0f).setStartDelay(1000).setDuration(500).start();
        feedbackRight.animate().alpha(0f).setStartDelay(1000).setDuration(500).start();
    }

    public void showWin() {
        runOnUiThread(() -> {
            resultLeft.setText("YOU SURVIVED");
            resultRight.setText("YOU SURVIVED");
            resultLeft.setTextColor(0xFF00FF00); // Green
            resultRight.setTextColor(0xFF00FF00);
            resultLeft.setVisibility(android.view.View.VISIBLE);
            resultRight.setVisibility(android.view.View.VISIBLE);
        });
    }

    public void showLose() {
        runOnUiThread(() -> {
            resultLeft.setText("GAME OVER");
            resultRight.setText("GAME OVER");
            resultLeft.setTextColor(0xFFFF0000); // Red
            resultRight.setTextColor(0xFFFF0000);
            resultLeft.setVisibility(android.view.View.VISIBLE);
            resultRight.setVisibility(android.view.View.VISIBLE);
        });
    }
}
