package com.example.vrdesert;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class SensorHandler implements SensorEventListener {

    private final SensorManager sensorManager;
    private final Sensor gyroscope;

    // Target rotations (where the hardware claims we are)
    private float targetYaw = 0f;
    private float targetPitch = 0f;

    // Smoothed rotations (where the camera actually renders)
    private float currentYaw = 0f;
    private float currentPitch = 0f;

    // Time tracking
    private long lastTime = 0;
    private static final float NS2S = 1.0f / 1000000000.0f;
    
    // Stabilization constants
    private static final float NOISE_THRESHOLD = 0.05f; // Ignore tiny shakes
    private static final float RECENTER_SPEED = 8.0f;   // Degrees per sec to firmly drift back to 0
    private static final float LERP_FACTOR = 0.15f;     // Camera catch-up speed
    
    private long lastPitchMoveTimeNs = 0;
    private long misalignedStartTimeNs = 0;
    
    private static final long STILLNESS_DELAY_NS = 3500000000L; // 3.5 seconds
    private static final long MAX_MISALIGN_DELAY_NS = 7500000000L; // 7.5 seconds

    public SensorHandler(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        } else {
            gyroscope = null;
        }
    }

    public void start() {
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void stop() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            if (lastTime != 0) {
                final float dT = (event.timestamp - lastTime) * NS2S;
                
                // Since the app sits in Landscape mode, the physical axes of the phone are swapped relative to your eye line!
                // Event.values[0] is the short edge (causing Yaw / turning sideways)
                // Event.values[1] is the long edge (causing Pitch / looking up and down)
                float yawRate = event.values[0];
                float pitchRate = event.values[1];

                // Track actively when the user physically pitches their head
                if (Math.abs(pitchRate) > NOISE_THRESHOLD) {
                    lastPitchMoveTimeNs = event.timestamp;
                }

                // 1. IGNORE SMALL NOISE (Deadzone)
                if (Math.abs(yawRate) < NOISE_THRESHOLD) yawRate = 0;
                if (Math.abs(pitchRate) < NOISE_THRESHOLD) pitchRate = 0;

                // Scale to degrees
                float pitchDelta = pitchRate * dT * (180f / (float)Math.PI) * 0.5f;
                float yawDelta = -yawRate * dT * (180f / (float)Math.PI) * 0.5f;

                targetPitch += pitchDelta;
                targetYaw += yawDelta;

                // 2. CLAMP VERTICAL ROTATION (Between -45 and 45)
                if (targetPitch > 45f) targetPitch = 45f;
                if (targetPitch < -45f) targetPitch = -45f;
                
                // 3. DUAL-CONDITION AUTO RECENTER SYSTEM
                if (Math.abs(targetPitch) < 0.5f) {
                    misalignedStartTimeNs = 0; // Fundamentally centered
                } else if (misalignedStartTimeNs == 0) {
                    misalignedStartTimeNs = event.timestamp; // Started looking away from horizon
                }

                boolean isStillTooLong = (event.timestamp - lastPitchMoveTimeNs) > STILLNESS_DELAY_NS;
                boolean isMisalignedTooLong = (misalignedStartTimeNs != 0) && ((event.timestamp - misalignedStartTimeNs) > MAX_MISALIGN_DELAY_NS);

                // If user doesn't move their head for 3.5s, OR they stare up/down continuously for >7.5s, pull them back to 0!
                if (isStillTooLong || isMisalignedTooLong) {
                    if (targetPitch > 0) {
                        targetPitch = Math.max(0, targetPitch - (RECENTER_SPEED * dT));
                    } else if (targetPitch < 0) {
                        targetPitch = Math.min(0, targetPitch + (RECENTER_SPEED * dT));
                    }
                }
                
                // 4. SMOOTH ROTATION (Lerp towards the target to eliminate jitters)
                currentPitch += (targetPitch - currentPitch) * LERP_FACTOR;
                currentYaw += (targetYaw - currentYaw) * LERP_FACTOR;
            }
            lastTime = event.timestamp;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for gyro
    }

    public float getYaw() {
        return currentYaw;
    }

    public float getPitch() {
        return currentPitch;
    }
}
