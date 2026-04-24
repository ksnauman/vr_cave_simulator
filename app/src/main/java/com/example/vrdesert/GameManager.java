package com.example.vrdesert;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * GameManager — kept as a stub to avoid removing it from the project
 * (other classes may still reference it). All game logic is disabled;
 * the Ice Cave Explorer no longer uses health/timer mechanics.
 */
public class GameManager {

    public GameManager(Context context,
                       ProgressBar healthBarLeft, ProgressBar healthBarRight,
                       TextView timerLeft, TextView timerRight) {
        // No-op: exploration mode has no health or timer
    }

    public boolean isGameLocked() { return false; }

    public void processItemCollection(GameObject.Type type) { /* no-op */ }

    public void startHealthDecay() { /* no-op */ }

    public void stopHealthDecay()  { /* no-op */ }
}
