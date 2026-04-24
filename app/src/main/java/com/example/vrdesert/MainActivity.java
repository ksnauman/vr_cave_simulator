package com.example.vrdesert;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.opengl.GLSurfaceView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity
        implements InteractionManager.InteractionListener {

    // ── GL / VR ────────────────────────────────────────────────────────────
    private GLSurfaceView glSurfaceView;
    private VRRenderer    vrRenderer;
    private SensorHandler sensorHandler;
    private MoveServer    moveServer;
    private AudioEngine   audioEngine;

    // ── UI ─────────────────────────────────────────────────────────────────
    private TextView movesCounter;
    private TextView sceneLabel;
    private View     sceneTransitionOverlay;
    private View     infoCardContainer;
    private TextView infoCardTitle;
    private TextView infoCardBody;

    // ── Move state ─────────────────────────────────────────────────────────
    private int movesLeft = 3;
    private int currentScene = 0;   // 0..3
    private boolean transitioning  = false;

    // ── Scene data ─────────────────────────────────────────────────────────
    private static final String[] SCENE_LABELS = {
        "The Frozen Gateway",       // cave_entering.jpg  — dark tunnel, entry point
        "The Icicle Curtains",      // cave_drippingings.jpg — close-up frozen waterfall
        "The Crystal Cathedral",    // cave_drippingings2.jpg — dense icicle ceiling
        "The Blue Window"           // fromcave_outside_view.jpg — looking out to world
    };

    // Insight data: [scene 0‑3][button 0‑2][title, body]
    private static final String[][][] INSIGHTS = {
        // ── Scene 0 — The Frozen Gateway (dark ice tunnel, silhouettes walking in) ──
        {
            { "❄ How Tunnels Form",
              "This perfectly oval passage was carved not by hand but by meltwater boring through glacial ice over decades. Liquid water always finds the smallest weakness in solid ice — drilling downward and outward until a passage like this forms." },
            { "💡 The Light Ahead",
              "That circle of daylight at the tunnel's far end is the strongest magnet in any ice cave. Ice transmits blue wavelengths most efficiently, which is why the glow has an ethereal, almost supernatural quality compared to ordinary sunlight." },
            { "🧊 Temperature Drop",
              "As you step inside, the temperature drops 10–15°C within a few metres. The ice surrounding you acts as an insulator far more effective than concrete — maintaining a near-constant subzero environment regardless of what the weather is doing outside." }
        },
        // ── Scene 1 — The Icicle Curtains (frozen waterfall columns, dark rock behind) ──
        {
            { "🏔 Frozen Waterfalls",
              "What you see here are called ice curtains or frozen waterfalls. When water seeps through cracks in the rock above and meets subzero cave air, it freezes mid-flow. Each column grew one freeze-cycle at a time — some of these formations are decades old." },
            { "🪨 Rock Behind Ice",
              "Notice the dark layered rock visible behind the ice. These are sedimentary strata — each horizontal band represents thousands of years of geological sedimentation. The ice clinging to them is a newcomer; the rock itself may be 400 million years old." },
            { "⚖ Weight of Ice",
              "The largest ice curtain columns you see can weigh over one tonne each. Despite their delicate, translucent appearance, they are structurally dense — the ice near the base is compressed so hard that it contains almost no air bubbles at all." }
        },
        // ── Scene 2 — The Crystal Cathedral (thousands of icicles hanging from ceiling) ──
        {
            { "💧 Icicle Growth",
              "Each single icicle here grows roughly 1 centimetre per day under ideal conditions. A water droplet arrives, partially freezes, and leaves a thin ice ring before the next drop comes. The largest ones here may have been growing for 10 years or more." },
            { "🔵 Why Blue Light?",
              "The ethereal blue-grey light filtering through the ceiling comes from the physics of glacial ice. Dense ice absorbs red and yellow wavelengths and lets only blue pass through. The deeper and denser the ice, the purer and more intense the blue." },
            { "🤫 Total Silence",
              "Ice caves are among the quietest places on Earth. The ice and rock around you absorb nearly all sound — no echo, no ambient hum. Scientists have measured near-zero decibel levels in chambers like this. Many first-time visitors hear their own heartbeat for the first time." }
        },
        // ── Scene 3 — The Blue Window (looking out from cave interior to outside world) ──
        {
            { "🪟 The Portal Effect",
              "This view — looking out from the cave's inner chamber — is one of the most photographed compositions in glacial photography. The blue glacial ice frames the external world like a stained-glass window, creating a stark contrast between frozen stillness inside and life outside." },
            { "🌋 Outside the Ice",
              "What you see beyond the cave mouth is Iceland's volcanic rock plain, covered in a thin frost layer. The glacier you are standing inside sits directly atop ancient lava flows — a river of compacted ice, thousands of years old, moving millimetres per day toward the sea." },
            { "⏳ Ice as History",
              "The ice forming the ceiling above you may be 500–1,000 years old. Climate scientists drill ice cores from glaciers like this to study ancient atmospheres — each compressed layer is a year of snowfall, preserving bubbles of air from centuries gone by." }
        }
    };


    // ── Lifecycle ──────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // GL Surface
        glSurfaceView = findViewById(R.id.glSurfaceView);
        glSurfaceView.setEGLContextClientVersion(2);

        sensorHandler = new SensorHandler(this);
        // InteractionManager to handle gaze collection of 3D objects
        InteractionManager interactionManager = new InteractionManager(this);

        vrRenderer = new VRRenderer(this, sensorHandler, interactionManager);
        glSurfaceView.setRenderer(vrRenderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // UI references
        movesCounter           = findViewById(R.id.movesCounter);
        sceneLabel             = findViewById(R.id.sceneLabel);
        sceneTransitionOverlay = findViewById(R.id.sceneTransitionOverlay);
        infoCardContainer      = findViewById(R.id.infoCardContainer);
        infoCardTitle          = findViewById(R.id.infoCardTitle);
        infoCardBody           = findViewById(R.id.infoCardBody);

        updateMovesCounter();
        updateSceneLabel();

        // MOVE button
        Button btnMove = findViewById(R.id.btnMove);
        btnMove.setOnClickListener(v -> attemptMove());

        // Info card close
        Button btnClose = findViewById(R.id.btnCloseInfo);
        btnClose.setOnClickListener(v -> hideInfoCard());

        // Audio engine
        audioEngine = new AudioEngine();

        // Remote controller server
        moveServer = new MoveServer(vrRenderer);
        moveServer.start();
    }

    // ── Move Logic ─────────────────────────────────────────────────────────
    public void attemptMove() {
        if (transitioning) return;

        if (movesLeft <= 0) {
            Toast.makeText(this, "You've reached the heart of the cave", Toast.LENGTH_SHORT).show();
            return;
        }

        movesLeft--;
        currentScene = Math.min(currentScene + 1, 3);
        transitioning = true;

        // Frost-white transition animation
        sceneTransitionOverlay.setVisibility(View.VISIBLE);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(sceneTransitionOverlay, "alpha", 0f, 1f);
        fadeIn.setDuration(350);
        fadeIn.start();

        sceneTransitionOverlay.postDelayed(() -> {
            // Switch scene in renderer
            vrRenderer.setScene(currentScene);
            vrRenderer.moveForward(); // keeps visual motion burst

            // Update audio scene
            if (audioEngine != null) {
                audioEngine.setScene(currentScene);
            }

            // Update UI
            updateMovesCounter();
            updateSceneLabel();

            // Fade overlay back out
            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(sceneTransitionOverlay, "alpha", 1f, 0f);
            fadeOut.setDuration(500);
            fadeOut.start();
            sceneTransitionOverlay.postDelayed(() -> {
                sceneTransitionOverlay.setVisibility(View.GONE);
                transitioning = false;
            }, 500);
        }, 380);
    }

    // ── Insight Cards ──────────────────────────────────────────────────────
    private void showInsight(int buttonIndex) {
        String[] data = INSIGHTS[currentScene][buttonIndex];
        infoCardTitle.setText(data[0]);
        infoCardBody.setText(data[1]);

        infoCardContainer.setAlpha(0f);
        infoCardContainer.setVisibility(View.VISIBLE);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(infoCardContainer, "alpha", 0f, 1f);
        fadeIn.setDuration(250);
        fadeIn.start();
    }

    private void hideInfoCard() {
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(infoCardContainer, "alpha", 1f, 0f);
        fadeOut.setDuration(200);
        fadeOut.start();
        infoCardContainer.postDelayed(() -> {
            infoCardContainer.setVisibility(View.GONE);
            if (vrRenderer != null) {
                vrRenderer.resetInfoButtons();
            }
        }, 200);
    }

    // ── UI helpers ─────────────────────────────────────────────────────────
    private void updateMovesCounter() {
        movesCounter.setText("Moves: " + movesLeft + " / 3");
    }

    private void updateSceneLabel() {
        sceneLabel.setText(SCENE_LABELS[currentScene]);
    }



    // ── Lifecycle: GL + Sensor ─────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();
        sensorHandler.start();
        if (audioEngine != null) {
            audioEngine.startCaveAmbiance();
        }
        // Feed head yaw to audio engine periodically
        startAudioYawUpdater();
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
        sensorHandler.stop();
        if (audioEngine != null) {
            audioEngine.stopAudio();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (moveServer != null) moveServer.stopServer();
    }

    @Override
    public void onObjectCollected(int objectId, GameObject.Type type) {
        if (audioEngine != null) {
            audioEngine.playCollectionSound();
        }
        if (type == GameObject.Type.INFO_BUTTON_0) {
            showInsight(0);
        } else if (type == GameObject.Type.INFO_BUTTON_1) {
            showInsight(1);
        } else if (type == GameObject.Type.INFO_BUTTON_2) {
            showInsight(2);
        }
    }

    // ── Audio yaw updater ─────────────────────────────────────────────────
    private android.os.Handler audioHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable audioYawRunnable;

    private void startAudioYawUpdater() {
        audioYawRunnable = () -> {
            if (audioEngine != null && sensorHandler != null) {
                audioEngine.setHeadYaw(sensorHandler.getYaw());
            }
            audioHandler.postDelayed(audioYawRunnable, 100); // 10Hz update
        };
        audioHandler.post(audioYawRunnable);
    }
}
