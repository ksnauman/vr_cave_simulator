package com.example.vrdesert;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;

import java.util.Random;

/**
 * AudioEngine — Procedural spatial audio for the ice cave.
 *
 * Generates cave ambiance that evolves per scene:
 *   Scene 0 (Gateway): Light wind, occasional drips
 *   Scene 1 (Icicle Curtains): More drips, deeper resonance
 *   Scene 2 (Crystal Cathedral): Deep rumble, echoing drips, near-silence feel
 *   Scene 3 (Blue Window): Outside wind blending in, meltwater trickle
 *
 * Stereo panning based on head yaw for basic spatial audio.
 */
public class AudioEngine {

    private ToneGenerator toneGenerator;
    private AudioTrack windTrack;
    private Thread windThread;
    private volatile boolean isPlaying = false;
    private volatile int currentScene = 0;
    private volatile float headYaw = 0f; // -180..180 degrees for spatial panning

    public AudioEngine() {
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
    }

    public void setScene(int scene) {
        this.currentScene = scene;
    }

    public void setHeadYaw(float yaw) {
        this.headYaw = yaw;
    }

    public void playCollectionSound() {
        // Crystalline chime for info button activation
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
    }

    public void playGazeEventSound() {
        // Short sparkle tone when gaze triggers an event
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_PRESSHOLDKEY_LITE, 100);
    }

    public void startCaveAmbiance() {
        int sampleRate = 16000; // Higher quality than before
        int bufferSize = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);

        windTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2, AudioTrack.MODE_STREAM);

        isPlaying = true;
        windTrack.play();

        windThread = new Thread(() -> {
            short[] buffer = new short[bufferSize * 2]; // stereo: L,R interleaved
            Random rand = new Random();
            float lastBrownL = 0, lastBrownR = 0;
            int dripTimer = 0;
            int dripPitch = 600;
            float dripPan = 0f; // -1 left, +1 right
            int echoTimer = 0;
            float echoDecay = 0f;

            while (isPlaying) {
                int scene = currentScene;
                float yaw = headYaw;

                // Scene-dependent parameters
                float windVolume, dripProb, dripVolume, rumbleAmount;
                switch (scene) {
                    case 0:  // Gateway — light wind, sparse drips
                        windVolume = 3000f; dripProb = 0.9996f; dripVolume = 3000f; rumbleAmount = 0f;
                        break;
                    case 1:  // Icicle Curtains — more drips, medium wind
                        windVolume = 4000f; dripProb = 0.9992f; dripVolume = 4500f; rumbleAmount = 0.1f;
                        break;
                    case 2:  // Crystal Cathedral — deep rumble, echo, sparse drips
                        windVolume = 2000f; dripProb = 0.9994f; dripVolume = 5000f; rumbleAmount = 0.4f;
                        break;
                    case 3:  // Blue Window — outside wind, meltwater
                        windVolume = 5500f; dripProb = 0.9990f; dripVolume = 3500f; rumbleAmount = 0.05f;
                        break;
                    default:
                        windVolume = 3000f; dripProb = 0.9996f; dripVolume = 3000f; rumbleAmount = 0f;
                }

                for (int i = 0; i < bufferSize; i++) {
                    // ── Brown noise (cave wind) ──────────────────────────
                    float whiteL = (rand.nextFloat() * 2f - 1f);
                    float whiteR = (rand.nextFloat() * 2f - 1f);
                    float brownL = (lastBrownL + (0.04f * whiteL)) / 1.04f;
                    float brownR = (lastBrownR + (0.04f * whiteR)) / 1.04f;
                    lastBrownL = brownL;
                    lastBrownR = brownR;

                    // Slow modulation (cavern echo feel)
                    float modulation = (float) Math.sin(
                        (System.currentTimeMillis() % 12000) / 12000f * Math.PI * 2) * 0.4f + 0.6f;

                    // ── Deep rumble (low frequency sine) ─────────────────
                    float rumble = 0f;
                    if (rumbleAmount > 0f) {
                        rumble = (float) Math.sin(i * 0.003f) * rumbleAmount;
                    }

                    // ── Water drips ──────────────────────────────────────
                    float drip = 0;
                    if (rand.nextFloat() > dripProb && dripTimer == 0) {
                        dripTimer = 2000 + rand.nextInt(2000);
                        dripPitch = rand.nextInt(400) + 400;
                        dripPan = rand.nextFloat() * 2f - 1f; // random left/right
                    }
                    if (dripTimer > 0) {
                        dripTimer--;
                        float maxDrip = (dripTimer > 1500) ? 3000f : 2000f;
                        float decay = (float) Math.pow((dripTimer / maxDrip), 5);
                        drip = (float) Math.sin((dripTimer * (float) dripPitch) / sampleRate
                                * Math.PI * 2) * decay;
                    }

                    // ── Echo effect ──────────────────────────────────────
                    if (dripTimer == 0 && echoTimer > 0) {
                        echoTimer--;
                        echoDecay *= 0.998f;
                        drip += (float) Math.sin(echoTimer * 0.05f) * echoDecay;
                    } else if (dripTimer == 1) {
                        // Start echo when drip ends
                        echoTimer = 800;
                        echoDecay = 0.15f;
                    }

                    // ── Spatial panning based on head yaw ────────────────
                    // Simple cosine panning
                    float panAngle = (yaw % 360f) / 180f * (float) Math.PI;
                    float panL = 0.5f + 0.3f * (float) Math.cos(panAngle);
                    float panR = 0.5f - 0.3f * (float) Math.cos(panAngle);

                    // Drip panning: offset by drip source position
                    float dripL = 0.5f + 0.5f * Math.max(-1f, Math.min(1f, -dripPan));
                    float dripR = 0.5f + 0.5f * Math.max(-1f, Math.min(1f, dripPan));

                    // Combine
                    float sampleL = (brownL * windVolume * modulation * panL)
                                  + (drip * dripVolume * dripL)
                                  + (rumble * 2000f * panL);
                    float sampleR = (brownR * windVolume * modulation * panR)
                                  + (drip * dripVolume * dripR)
                                  + (rumble * 2000f * panR);

                    // Interleaved stereo
                    int idx = i * 2;
                    buffer[idx]     = (short) Math.max(-32000, Math.min(32000, sampleL));
                    buffer[idx + 1] = (short) Math.max(-32000, Math.min(32000, sampleR));
                }
                windTrack.write(buffer, 0, buffer.length);
            }
        });
        windThread.setPriority(Thread.MIN_PRIORITY);
        windThread.start();
    }

    public void stopAudio() {
        isPlaying = false;
        if (windTrack != null) {
            try {
                windTrack.stop();
            } catch (Exception e) { /* ignore */ }
            windTrack.release();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}
