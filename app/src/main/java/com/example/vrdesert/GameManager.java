package com.example.vrdesert;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class GameManager {
    private int health = 100;
    private final int MAX_HEALTH = 100;
    private boolean gameLocked = false;
    private Context context;
    private ProgressBar healthBarLeft;
    private ProgressBar healthBarRight;
    private TextView timerLeft;
    private TextView timerRight;
    private Handler decayHandler;
    private Runnable decayRunnable;
    private int timeRemaining = 60;
    private int tickCounter = 0;

    public GameManager(Context context, ProgressBar healthBarLeft, ProgressBar healthBarRight, TextView timerLeft, TextView timerRight) {
        this.context = context;
        this.healthBarLeft = healthBarLeft;
        this.healthBarRight = healthBarRight;
        this.timerLeft = timerLeft;
        this.timerRight = timerRight;
        updateHealthBar();
        updateTimerDisplay();
    }

    private void updateTimerDisplay() {
        if (timerLeft != null && timerRight != null) {
            String timeText = "Time: " + timeRemaining + "s";
            timerLeft.post(() -> {
                timerLeft.setText(timeText);
                timerRight.setText(timeText);
            });
        }
    }

    public boolean isGameLocked() {
        return gameLocked;
    }

    public void processItemCollection(GameObject.Type type) {
        if (gameLocked) return;

        switch (type) {
            case EDIBLE_MUSHROOM:
                health = Math.min(health + 10, MAX_HEALTH);
                break;
            case CAVE_PLANT:
                health = Math.min(health + 5, MAX_HEALTH);
                break;
            case TOXIC_FUNGUS:
                health = Math.max(health - 20, 0);
                Toast.makeText(context, "You consumed toxic fungus!", Toast.LENGTH_SHORT).show();
                break;
        }
        updateHealthBar();
        
        if (health == 0) {
            triggerGameOver();
        }
    }

    private void updateHealthBar() {
        if (healthBarLeft != null && healthBarRight != null) {
            healthBarLeft.post(() -> {
                healthBarLeft.setProgress(health);
                healthBarRight.setProgress(health);
                
                int color = android.graphics.Color.GREEN;
                if (health <= 30) color = android.graphics.Color.RED;
                else if (health <= 60) color = android.graphics.Color.YELLOW;
                
                healthBarLeft.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
                healthBarRight.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
            });
        }
    }

    public void startHealthDecay() {
        if (decayHandler == null) {
            decayHandler = new Handler(Looper.getMainLooper());
            decayRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!gameLocked) {
                        timeRemaining--;
                        updateTimerDisplay();

                        if (timeRemaining <= 0) {
                            gameLocked = true;
                            if (health > 60) {
                                ((MainActivity)context).showWin();
                            } else {
                                ((MainActivity)context).showLose();
                            }
                            return;
                        }

                        tickCounter++;
                        if (tickCounter >= 3) {
                            tickCounter = 0;
                            health = Math.max(health - 1, 0);
                            updateHealthBar();
                            if (health == 0) {
                                triggerGameOver();
                                return;
                            }
                        }
                        
                        decayHandler.postDelayed(this, 1000); // 1 second loop
                    }
                }
            };
        }
        decayHandler.postDelayed(decayRunnable, 1000);
    }

    public void stopHealthDecay() {
        if (decayHandler != null && decayRunnable != null) {
            decayHandler.removeCallbacks(decayRunnable);
        }
    }

    private void triggerGameOver() {
        gameLocked = true;
        ((MainActivity)context).showLose();
    }
}
