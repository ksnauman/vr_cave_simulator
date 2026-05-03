package com.example.vrdesert;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class InteractionManager {

    public interface InteractionListener {
        void onGazeStarted(int objectId);
        void onGazeEnded(int objectId);
        void onNarrationTriggered(int objectId);
        void onTransitionTriggered(int objectId);
        void onObjectCollected(int objectId, GameObject.Type type);
    }

    private final InteractionListener listener;
    private final Handler mainHandler;

    private int currentGazedObjectId = -1;
    private long gazeStartTime = 0;
    
    // Timer constants
    private static final long GAZE_AUDIO_MS      = 3000;
    private static final long GAZE_TRANSITION_MS = 3000;
    
    private boolean audioTriggered = false;
    private boolean transitionTriggered = false;
    
    private static final String TAG = "InteractionManager";
    
    // Allow public boolean check for crosshair feedback
    public boolean isTargeting() {
        return currentGazedObjectId != -1;
    }

    public int getTargetId() {
        return currentGazedObjectId;
    }

    public float getGazeProgress() {
        if (currentGazedObjectId == -1) return 0;
        long elapsed = System.currentTimeMillis() - gazeStartTime;
        return Math.min(1.0f, (float) elapsed / GAZE_TRANSITION_MS);
    }

    public InteractionManager(InteractionListener listener) {
        this.listener = listener;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Checks if the gaze is currently hitting an object and handles the logic.
     */
    public void checkGaze(float camX, float camY, float camZ, 
                          float forwardX, float forwardY, float forwardZ,
                          GameObject obj, int objectId) {
        
        // Ignore already collected objects
        if (obj.isCollected) {
            return;
        }

        // Vector from camera to object
        // NOTE: we approximate object center by adding 0.5f to Y here, matching the renderer drawing logic
        float dirX = obj.x - camX;
        float dirY = (obj.y + 0.5f) - camY;
        float dirZ = obj.z - camZ;

        // Distance to object
        float dist = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        
        // Normalize direction
        if (dist > 0) {
            dirX /= dist;
            dirY /= dist;
            dirZ /= dist;
        }

        // Dot product between forward vector and direction to object
        float dot = (forwardX * dirX) + (forwardY * dirY) + (forwardZ * dirZ);

        // If the dot product is close to 1, the object is in the center of the gaze
        // Set to 0.95f for a slightly larger hitbox as requested
        if (dot > 0.95f && dist < 50f) { 
            if (currentGazedObjectId != objectId) {
                // New object gazed
                if (currentGazedObjectId != -1) {
                    listener.onGazeEnded(currentGazedObjectId);
                }
                currentGazedObjectId = objectId;
                gazeStartTime = System.currentTimeMillis();
                audioTriggered = false;
                transitionTriggered = false;
                Log.d(TAG, "Gaze target acquired on Object " + objectId);
                listener.onGazeStarted(objectId);
            } else {
                // Still gazing at the same object
                long elapsed = System.currentTimeMillis() - gazeStartTime;
                
                if (elapsed >= GAZE_AUDIO_MS && !audioTriggered) {
                    audioTriggered = true;
                    Log.d(TAG, "Narration Triggered: Object " + objectId);
                    mainHandler.post(() -> listener.onNarrationTriggered(objectId));
                }
                
                if (elapsed >= GAZE_TRANSITION_MS && !transitionTriggered) {
                    transitionTriggered = true;
                    Log.d(TAG, "Transition Triggered: Object " + objectId);
                    mainHandler.post(() -> listener.onTransitionTriggered(objectId));
                }
            }
        } else {
            if (currentGazedObjectId == objectId) {
                // Looked away
                Log.d(TAG, "Gaze lost on Object " + objectId + " - Timer Reset");
                listener.onGazeEnded(objectId);
                currentGazedObjectId = -1;
                audioTriggered = false;
                transitionTriggered = false;
            }
        }
    }

    public void resetGaze() {
        if (currentGazedObjectId != -1) {
            listener.onGazeEnded(currentGazedObjectId);
            currentGazedObjectId = -1;
        }
        audioTriggered = false;
        transitionTriggered = false;
    }
}
