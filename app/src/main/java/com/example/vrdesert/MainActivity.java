package com.example.vrdesert;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.opengl.GLSurfaceView;
import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements InteractionManager.InteractionListener {

    public enum VRState {
        LANDING,
        TRANSITIONING,
        INSIDE_CAVE
    }

    // ── GL / VR ────────────────────────────────────────────────────────────
    private GLSurfaceView glSurfaceView;
    private VRRenderer vrRenderer;
    private SensorHandler sensorHandler;
    private MoveServer moveServer;
    private AudioEngine audioEngine;
    private InteractionManager interactionManager;

    // ── UI ─────────────────────────────────────────────────────────────────
    private View sceneTransitionOverlay;
    private View infoCardContainer;
    private TextView infoCardTitle;
    private TextView infoCardBody;

    // ── State management ──────────────────────────────────────────────────
    private VRState currentState = VRState.LANDING;
    private int selectedCaveIndex = -1;
    private boolean transitioning = false;
    private TextToSpeech tts;

    // ── Audio ──────────────────────────────────────────────────────────────
    private MediaPlayer narrationPlayer;
    private MediaPlayer menuMusicPlayer;
    private Handler stateHandler = new Handler(Looper.getMainLooper());

    // ── Scene data ─────────────────────────────────────────────────────────
    private static final String[] CAVE_NAMES = {
            "Ajanta Caves (India)",
            "Ellora Caves (India)",
            "Badami Caves (India)",
            "Waitomo Glowworm Caves (New Zealand)",
            "Lascaux Cave (France)",
            "Vatnajökull Ice Cave (Iceland)",
            "Son Doong Cave (Vietnam)"
    };

    private static final String[] CAVE_STATES = {
            "Maharashtra, India",
            "Maharashtra, India",
            "Karnataka, India",
            "Waitomo, New Zealand",
            "Montignac, France",
            "Vatnajökull, Iceland",
            "Quang Binh, Vietnam"
    };
    // Note: You can add all 28 states here. The menu will automatically arrange
    // them in a circle.

    private static final String[] NARRATION_TEXTS = {
            "The Ajanta Caves are a series of 29 Buddhist cave monuments in Maharashtra, India. They are famous for their magnificent ancient murals and sculptures dating back to the 2nd century BCE.",
            "The Ellora Caves are a UNESCO World Heritage site in India, featuring Hindu, Buddhist, and Jain monuments. The masterpiece is the monolithic Kailasa Temple, carved from a single rock.",
            "The Badami cave temples are a complex of Hindu and Jain cave temples located in Karnataka, India. They are famous for their rock-cut architecture dating back to the 6th century.",
            "Welcome to the Waitomo Glowworm Caves in New Zealand. Look up at the ceiling to see thousands of Arachnocampa luminosa glowworms, creating a magical starry sky underground.",
            "The Lascaux Cave in France is famous for its Paleolithic cave paintings, which are estimated to be over 17,000 years old. They depict large animals and human figures in stunning detail.",
            "Step into the Vatnajökull Ice Cave in Iceland. Formed within the largest glacier in Europe, these caves feature deep blue translucent ice walls that change shape every year.",
            "You are inside Son Doong, the world's largest cave. Located in Vietnam, this massive cavern is so large it has its own internal jungle, river, and even its own weather system."
    };

    // ── Insight Data (7 caves, 3 hotspots each) ──────────────────────────
    private static final int[] INTERIOR_DRAWABLES = {
            R.drawable.interior_ajanta,
            R.drawable.interior_ellora,
            R.drawable.interior_badami,
            R.drawable.interior_waitomo,
            R.drawable.interior_lascaux,
            R.drawable.interior_vatnajokull,
            R.drawable.interior_sondoong
    };

    private static final int[][] INSIGHT_DRAWABLES = {
            { R.drawable.insight_ajanta_1, R.drawable.insight_ajanta_2, R.drawable.insight_ajanta_3 },
            { R.drawable.insight_ellora_1, R.drawable.insight_ellora_2, R.drawable.insight_ellora_3 },
            { R.drawable.insight_badami_1, R.drawable.insight_badami_2, R.drawable.insight_badami_3 },
            { R.drawable.insight_waitomo_1, R.drawable.insight_waitomo_2, R.drawable.insight_waitomo_3 },
            { R.drawable.insight_lascaux_1, R.drawable.insight_lascaux_2, R.drawable.insight_lascaux_3 },
            { R.drawable.insight_vatnajokull_1, R.drawable.insight_vatnajokull_2, R.drawable.insight_vatnajokull_3 },
            { R.drawable.insight_sondoong_1, R.drawable.insight_sondoong_2, R.drawable.insight_sondoong_3 }
    };

    private static final String[][][] INSIGHT_TEXTS = {
            // Ajanta
            {
                    { "The Center Statue", "This magnificent statue of Buddha represents the state of deep meditation. Notice the intricate details of the hands, symbolic of mudras." },
                    { "Left Carvings", "These wall carvings depict scenes from the Jataka tales, stories of the previous lives of Buddha before enlightenment." },
                    { "Right Carvings", "The right gallery features detailed architecture showing how ancient monks carved directly into the basalt rock." }
            },
            // Ellora
            {
                    { "Central Dome", "The interior of the Kailasa temple shows the incredible engineering required to carve a temple from the top down." },
                    { "Left Pillars", "These massive monolithic pillars were carved from the same rock as the temple itself, providing structural and aesthetic grandeur." },
                    { "Right Pillars", "The right side of the main hall contains carvings of various deities, showing the religious diversity of the site." }
            },
            // Badami
            {
                    { "The Entrance", "Looking back at the entrance, you can see how the morning sun illuminates the red sandstone interior." },
                    { "Wall Carvings", "These carvings depict ancient legends, showing the high level of craftsmanship in the 6th century Chalukya period." },
                    { "Pillars", "The fluted pillars are a signature of Badami architecture, supporting the weight of the massive rock above." }
            },
            // Waitomo
            {
                    { "Glow Ceiling", "Looking up, you see thousands of bioluminescent glowworms. They use this light to attract tiny insects in the dark." },
                    { "Water Reflection", "The still water below creates a perfect mirror, doubling the starry effect of the glowworms on the ceiling." },
                    { "Cave Depth", "Further into the cave, the passages narrow, leading into deeper, unexplored limestone chambers." }
            },
            // Lascaux
            {
                    { "Main Wall Painting", "The Great Hall of the Bulls contains some of the most famous prehistoric art in the world, over 17,000 years old." },
                    { "Animal Close-up", "Notice how the ancient artists used the natural bumps in the cave wall to give the animals a 3D, muscular appearance." },
                    { "Texture Detail", "The red and yellow ochre paints were made from crushed minerals and have survived for millennia in the cave's constant environment." }
            },
            // Vatnajokull
            {
                    { "Ice Ceiling", "The ceiling is made of deep, compressed glacial ice. The bubbles have been squeezed out, leaving only pure blue crystal." },
                    { "Ice Textures", "These scalloped textures are formed by warm air currents melting the ice in a specific pattern over time." },
                    { "Ice Formations", "Notice the columns of ice where water has frozen mid-drip, creating a temporary museum of natural sculpture." }
            },
            // Son Doong
            {
                    { "The Skylight", "This massive hole in the ceiling, called a doline, allows sunlight to reach the cave floor, creating an internal jungle." },
                    { "Cave Vegetation", "The jungle inside, called the Garden of Edam, contains plants and animals found nowhere else on earth." },
                    { "Cave Depth", "Son Doong is so large that a 40-story skyscraper could fit inside its largest chamber." }
            }
    };

    private static final float[][][] HOTSPOT_POSITIONS = {
            { {0.0f, 1.5f, -3.0f}, {-2.0f, 1.5f, -2.5f}, {2.0f, 1.5f, -2.5f} }, // Ajanta
            { {0.0f, 1.5f, -3.0f}, {-2.5f, 1.5f, -2.5f}, {2.5f, 1.5f, -2.5f} }, // Ellora
            { {0.0f, 1.5f, -3.0f}, {-2.0f, 1.5f, -2.0f}, {2.0f, 1.5f, -2.0f} }, // Badami
            { {0.0f, 8.0f, -1.0f}, {0.0f, -6.0f, -3.0f},  {0.0f, 1.0f, -12.0f} }, // Waitomo (Up, Down, Deep)
            { {0.0f, 1.5f, -3.0f}, {-2.0f, 1.5f, -2.5f}, {2.0f, 1.5f, -2.5f} }, // Lascaux
            { {0.0f, 2.0f, -3.0f}, {-2.0f, 1.5f, -2.5f}, {2.0f, 1.5f, -2.5f} }, // Vatnajokull
            { {0.0f, 2.0f, -3.0f}, {-2.0f, 1.5f, -2.5f}, {2.0f, 1.5f, -2.5f} }  // Sondoong
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
        interactionManager = new InteractionManager(this);

        vrRenderer = new VRRenderer(this, sensorHandler, interactionManager);
        vrRenderer.setCaveData(CAVE_NAMES);
        glSurfaceView.setRenderer(vrRenderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // UI references
        sceneTransitionOverlay = findViewById(R.id.sceneTransitionOverlay);
        infoCardContainer = findViewById(R.id.infoCardContainer);
        infoCardTitle = findViewById(R.id.infoCardTitle);
        infoCardBody = findViewById(R.id.infoCardBody);

        // Info card close
        Button btnClose = findViewById(R.id.btnCloseInfo);
        if (btnClose != null)
            btnClose.setOnClickListener(v -> hideInfoCard());

        // Audio engine
        audioEngine = new AudioEngine(this);

        // Initialize TTS
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                tts.setLanguage(Locale.US);
            }
        });
    }

    private void startMenuMusic() {
        // Background music removed
    }

    private void playNarration(int index) {
        String name = CAVE_NAMES[index % CAVE_NAMES.length];
        String state = CAVE_STATES[index % CAVE_STATES.length];
        String speechText = name + ", " + state;
        if (tts != null) {
            tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, null);
        }
        Toast.makeText(this, "Selected: " + name, Toast.LENGTH_SHORT).show();
    }

    private void playFullHistory(int index) {
        if (narrationPlayer != null) {
            narrationPlayer.release();
        }
        String history = NARRATION_TEXTS[index % NARRATION_TEXTS.length];
        if (tts != null) {
            tts.speak(history, TextToSpeech.QUEUE_ADD, null, null);
        }

        // Mocking narration finish for auto-exit logic
        stateHandler.postDelayed(() -> {
            onNarrationFinished();
        }, 15000);
    }

    private void onNarrationFinished() {
        if (currentState == VRState.INSIDE_CAVE) {
            stateHandler.postDelayed(() -> {
                returnToLanding();
            }, 3000); // 3 seconds wait
        }
    }

    private void returnToLanding() {
        if (currentState == VRState.INSIDE_CAVE) {
            interactionManager.resetGaze();
            currentState = VRState.LANDING;
        }

        if (transitioning)
            return;
        transitioning = true;

        sceneTransitionOverlay.setVisibility(View.VISIBLE);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(sceneTransitionOverlay, "alpha", 0f, 1f);
        fadeIn.setDuration(500);
        fadeIn.start();

        stateHandler.postDelayed(() -> {
            currentState = VRState.LANDING;
            vrRenderer.setState(VRRenderer.State.LANDING);

            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(sceneTransitionOverlay, "alpha", 1f, 0f);
            fadeOut.setDuration(500);
            fadeOut.start();
            stateHandler.postDelayed(() -> {
                sceneTransitionOverlay.setVisibility(View.GONE);
                transitioning = false;
            }, 500);
        }, 600);
    }

    // ── Move Logic ─────────────────────────────────────────────────────────
    public void enterCave(int caveIndex) {
        if (currentState == VRState.LANDING) {
            interactionManager.resetGaze();
            currentState = VRState.INSIDE_CAVE;
        }

        if (transitioning)
            return;
        selectedCaveIndex = caveIndex;
        transitioning = true;

        sceneTransitionOverlay.setVisibility(View.VISIBLE);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(sceneTransitionOverlay, "alpha", 0f, 1f);
        fadeIn.setDuration(500);
        fadeIn.start();

        stateHandler.postDelayed(() -> {
            interactionManager.resetGaze();
            currentState = VRState.INSIDE_CAVE;
            
            // Set up specific cave scene data in renderer
            vrRenderer.setCaveScene(caveIndex);
            vrRenderer.setHotspotPositions(HOTSPOT_POSITIONS[caveIndex]);
            
            // Start audio narration
            audioEngine.stop();
            audioEngine.setScene(caveIndex);
            audioEngine.speak(NARRATION_TEXTS[caveIndex]);

            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(sceneTransitionOverlay, "alpha", 1f, 0f);
            fadeOut.setDuration(500);
            fadeOut.start();
            stateHandler.postDelayed(() -> {
                sceneTransitionOverlay.setVisibility(View.GONE);
                transitioning = false;
            }, 500);
        }, 600);
    }

    // ── Insight Cards (Legacy 2D support, updated to use new data) ────────
    private void showInsight(int buttonIndex) {
        String[] data = INSIGHT_TEXTS[selectedCaveIndex != -1 ? selectedCaveIndex : 0][buttonIndex];
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

    // ── UI helpers removed ──────────────────────────────────────────────────

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
        if (moveServer != null)
            moveServer.stopServer();
    }

    @Override
    public void onGazeStarted(int objectId) {
        if (currentState == VRState.LANDING) {
            vrRenderer.highlightCard(objectId, true);
        }
    }

    @Override
    public void onGazeEnded(int objectId) {
        if (currentState == VRState.LANDING) {
            vrRenderer.highlightCard(objectId, false);
        }
    }

    @Override
    public void onNarrationTriggered(int objectId) {
        if (currentState == VRState.LANDING) {
            playNarration(objectId);
        } else if (currentState == VRState.INSIDE_CAVE && objectId >= 100 && objectId <= 102) {
            int insightIdx = objectId - 100;
            String[] data = INSIGHT_TEXTS[selectedCaveIndex][insightIdx];

            // Show insight in VR
            vrRenderer.showInsightPanel(insightIdx);

            // Speak description
            audioEngine.stop();
            audioEngine.speak(data[0] + ". " + data[1]);
        }
    }

    @Override
    public void onTransitionTriggered(int caveId) {
        if (currentState == VRState.LANDING) {
            enterCave(caveId);
        }
    }

    @Override
    public void onObjectCollected(int objectId, GameObject.Type type) {
        // Legacy support or new object logic
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
