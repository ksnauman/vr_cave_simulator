package com.example.vrdesert;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;

import java.util.Random;

public class AudioEngine {

    private ToneGenerator toneGenerator;
    private AudioTrack windTrack;
    private Thread windThread;
    private boolean isPlaying = false;

    public AudioEngine() {
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
    }

    public void playCollectionSound() {
        // Simple distinct hardware beep requiring no heavy soundfile loading
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
    }

    public void startCaveAmbiance() {
        int sampleRate = 8000; // Low frequency base
        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        
        windTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, 
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, 
                bufferSize, AudioTrack.MODE_STREAM);
                
        isPlaying = true;
        windTrack.play();
        
        windThread = new Thread(() -> {
            short[] buffer = new short[bufferSize];
            Random rand = new Random();
            float lastVal = 0;
            int dripTimer = 0;
            int dripPitch = 600;
            
            while (isPlaying) {
                for (int i = 0; i < bufferSize; i++) {
                    // Generate brown noise / low frequency room tone
                    float white = (rand.nextFloat() * 2f - 1f);
                    float brown = (lastVal + (0.05f * white)) / 1.05f; 
                    lastVal = brown;
                    
                    // Modulate volume slowly using a sine wave to simulate cavern echoes
                    float modulation = (float) Math.sin((System.currentTimeMillis() % 10000) / 10000f * Math.PI * 2) * 0.5f + 0.5f;
                    
                    // Add dripping water sound
                    float drip = 0;
                    if (rand.nextFloat() > 0.9997f && dripTimer == 0) {
                        dripTimer = 3000; // Trigger drip length
                        dripPitch = rand.nextInt(300) + 500; // Randomize pitch slightly
                    }
                    if (dripTimer > 0) {
                        dripTimer--;
                        // Droplet "plink" sound (fast attack, exponential decay on a sine wave)
                        float decay = (float) Math.pow((dripTimer / 3000f), 4);
                        drip = (float) Math.sin((dripTimer * (float)dripPitch) / 8000f * Math.PI * 2) * decay;
                    }
                    
                    // Scale volume heavily down so it's subtle background ambiance
                    buffer[i] = (short) ((brown * 6000 * modulation) + (drip * 4000)); 
                }
                windTrack.write(buffer, 0, buffer.length);
            }
        });
        windThread.start();
    }

    public void stopAudio() {
        isPlaying = false;
        if (windTrack != null) {
            windTrack.stop();
            windTrack.release();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}
