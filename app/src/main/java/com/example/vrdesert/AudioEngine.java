package com.example.vrdesert;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
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
    
    private android.speech.tts.TextToSpeech tts;
    private MediaPlayer menuMusic;
    private Context context;

    public AudioEngine(Context context) {
        this.context = context;
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
        
        tts = new android.speech.tts.TextToSpeech(context, status -> {
            if (status != android.speech.tts.TextToSpeech.ERROR) {
                tts.setLanguage(java.util.Locale.US);
                tts.setPitch(0.9f);
                tts.setSpeechRate(0.9f);
            }
        });
    }

    public void speak(String text) {
        if (tts != null) {
            tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public void startMenuMusic() {
        // if (menuMusic == null) {
        //     menuMusic = MediaPlayer.create(context, R.raw.menu_music);
        //     menuMusic.setLooping(true);
        // }
        // menuMusic.start();
    }

    public void stopMenuMusic() {
        if (menuMusic != null) {
            menuMusic.stop();
            menuMusic.release();
            menuMusic = null;
        }
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
                float windVolume, dripProb, dripVolume, rumbleAmount, shimmerAmount;
                switch (scene) {
                    case 0: // Ajanta - Temple feel, low hum
                    case 1: // Ellora
                    case 2: // Badami
                        windVolume = 2500f; dripProb = 0.9998f; dripVolume = 2000f; rumbleAmount = 0.2f; shimmerAmount = 0.05f;
                        break;
                    case 3: // Waitomo - Magical bioluminescent drip
                        windVolume = 1500f; dripProb = 0.9985f; dripVolume = 4500f; rumbleAmount = 0.05f; shimmerAmount = 0.4f;
                        break;
                    case 4: // Lascaux - Deep prehistoric silence
                        windVolume = 1000f; dripProb = 0.9999f; dripVolume = 1500f; rumbleAmount = 0.1f; shimmerAmount = 0f;
                        break;
                    case 5: // Vatnajokull - Ice cracking, high wind
                        windVolume = 6000f; dripProb = 0.9995f; dripVolume = 3000f; rumbleAmount = 0.1f; shimmerAmount = 0.1f;
                        // Occasional sharp ice crack handled by shimmer in higher freq
                        break;
                    case 6: // Son Doong - Jungle cavern, river rumble
                        windVolume = 4000f; dripProb = 0.9988f; dripVolume = 3500f; rumbleAmount = 0.5f; shimmerAmount = 0.2f;
                        break;
                    default:
                        windVolume = 3000f; dripProb = 0.9996f; dripVolume = 3000f; rumbleAmount = 0.1f; shimmerAmount = 0f;
                }

                for (int i = 0; i < bufferSize; i++) {
                    // ── Brown noise (cave wind) ──────────────────────────
                    float whiteL = (rand.nextFloat() * 2f - 1f);
                    float whiteR = (rand.nextFloat() * 2f - 1f);
                    float brownL = (lastBrownL + (0.04f * whiteL)) / 1.04f;
                    float brownR = (lastBrownR + (0.04f * whiteR)) / 1.04f;
                    lastBrownL = brownL;
                    lastBrownR = brownR;

                    // ── Shimmer / Crackle (High freq components) ─────────
                    float shimmer = 0f;
                    if (shimmerAmount > 0f && rand.nextFloat() > (1.0f - shimmerAmount * 0.01f)) {
                        shimmer = (rand.nextFloat() * 2f - 1f) * 4000f;
                    }

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
                                  + (rumble * 2000f * panL)
                                  + (shimmer * panL);
                    float sampleR = (brownR * windVolume * modulation * panR)
                                  + (drip * dripVolume * dripR)
                                  + (rumble * 2000f * panR)
                                  + (shimmer * panR);

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
