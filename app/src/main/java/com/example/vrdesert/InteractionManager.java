package com.example.vrdesert;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class InteractionManager {

    public interface InteractionListener {
        void onObjectCollected(int objectId, GameObject.Type type);
        void onHoverEnter(int objectId);
        void onHoverExit();
    }

    private final InteractionListener listener;
    private final Handler mainHandler;

    private int currentGazedObjectId = -1;
    private long gazeStartTime = 0;
    
    // Timer constants
    private static final long GAZE_COLLECT_MS = 1200;
    
    private static final String TAG = "InteractionManager";
    
    // Allow public boolean check for crosshair feedback
    public boolean isTargeting() {
        return currentGazedObjectId != -1;
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
        
        // Vector from camera to object
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
        if (dot > 0.90f && dist < 50f) { 
            if (currentGazedObjectId != objectId) {
                // New object gazed
                currentGazedObjectId = objectId;
                Log.d(TAG, "Gaze target acquired on Object " + objectId);
                
                mainHandler.post(() -> listener.onHoverEnter(objectId));
            }
        } else {
            if (currentGazedObjectId == objectId) {
                // Looked away
                currentGazedObjectId = -1;
                Log.d(TAG, "Gaze lost on Object " + objectId);
                
                mainHandler.post(() -> listener.onHoverExit());
            }
        }
    }
}
