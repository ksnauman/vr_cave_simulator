package com.example.vrdesert;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.opengl.GLSurfaceView;
import androidx.appcompat.app.AppCompatActivity;

public class CalibrationActivity extends AppCompatActivity implements RemoteControlListener {

    private GLSurfaceView glSurfaceView;
    private CalibrationRenderer renderer;
    private SensorHandler sensorHandler;
    private MoveServer moveServer;

    private TextView textStatsLeft, textStatsRight;
    private int selectedParamIndex = 0; // 0: IPD, 1: FOV, 2: Lens Offset
    private String[] paramNames = {"IPD", "FOV", "Lens Offset"};

    private float ipd = 0.065f;
    private float fov = 90.0f;
    private float lensOffset = 0.02f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);

        // Hide system UI
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        textStatsLeft = findViewById(R.id.textStatsLeft);
        textStatsRight = findViewById(R.id.textStatsRight);

        sensorHandler = new SensorHandler(this);
        renderer = new CalibrationRenderer(sensorHandler);

        glSurfaceView = findViewById(R.id.calibrationSurfaceView);
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setRenderer(renderer);

        // Remote control server for cycling modes
        moveServer = new MoveServer(this);
        moveServer.start();
        
        updateStats();
        setupSeekBars();
    }

    private void setupSeekBars() {
        // Left side
        SeekBar sbIpdL = findViewById(R.id.seekBarIpdLeft);
        sbIpdL.setProgress((int)((ipd - 0.02f) / (0.12f - 0.02f) * 100));
        sbIpdL.setOnSeekBarChangeListener(createSeekBarListener(0));

        SeekBar sbFovL = findViewById(R.id.seekBarFovLeft);
        sbFovL.setProgress((int)((fov - 70f) / (120f - 70f) * 100));
        sbFovL.setOnSeekBarChangeListener(createSeekBarListener(1));

        SeekBar sbLensL = findViewById(R.id.seekBarLensLeft);
        sbLensL.setProgress((int)(lensOffset / 0.05f * 100));
        sbLensL.setOnSeekBarChangeListener(createSeekBarListener(2));

        // Right side (mirrored)
        SeekBar sbIpdR = findViewById(R.id.seekBarIpdRight);
        sbIpdR.setProgress(sbIpdL.getProgress());
        sbIpdR.setOnSeekBarChangeListener(createSeekBarListener(0));

        SeekBar sbFovR = findViewById(R.id.seekBarFovRight);
        sbFovR.setProgress(sbFovL.getProgress());
        sbFovR.setOnSeekBarChangeListener(createSeekBarListener(1));

        SeekBar sbLensR = findViewById(R.id.seekBarLensRight);
        sbLensR.setProgress(sbLensL.getProgress());
        sbLensR.setOnSeekBarChangeListener(createSeekBarListener(2));
    }

    private SeekBar.OnSeekBarChangeListener createSeekBarListener(final int paramIndex) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    selectedParamIndex = paramIndex; // Sync selection
                    float val = progress / 100f;
                    if (paramIndex == 0) { // IPD
                        ipd = 0.02f + val * (0.12f - 0.02f);
                        renderer.setIpd(ipd);
                    } else if (paramIndex == 1) { // FOV
                        fov = 70f + val * (120f - 70f);
                        renderer.setFov(fov);
                    } else { // Lens
                        lensOffset = val * 0.05f;
                        renderer.setLensCenterOffset(lensOffset);
                    }
                    updateStats();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    @Override
    public void onRemoteClick() {
        // Cycle through: Grid(IPD) -> Grid(FOV) -> Grid(Lens) -> Crosshair -> Color -> Depth
        runOnUiThread(() -> {
            CalibrationRenderer.Mode current = renderer.getMode();
            if (current == CalibrationRenderer.Mode.GRID) {
                if (selectedParamIndex < 2) {
                    selectedParamIndex++;
                } else {
                    selectedParamIndex = 0;
                    renderer.setMode(CalibrationRenderer.Mode.CROSSHAIR);
                }
            } else if (current == CalibrationRenderer.Mode.CROSSHAIR) {
                renderer.setMode(CalibrationRenderer.Mode.COLOR_CHANNEL);
            } else if (current == CalibrationRenderer.Mode.COLOR_CHANNEL) {
                renderer.setMode(CalibrationRenderer.Mode.DEPTH_TEST);
            } else {
                renderer.setMode(CalibrationRenderer.Mode.GRID);
                selectedParamIndex = 0;
            }
            updateStats();
        });
    }

    private void updateStats() {
        String stats = String.format("MODE: %s\nADJUSTING: > %s <\n\nIPD: %.3f\nFOV: %.1f\nLens Offset: %.3f",
                renderer.getMode().name(),
                paramNames[selectedParamIndex],
                ipd, fov, lensOffset);
        textStatsLeft.setText(stats);
        textStatsRight.setText(stats);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            adjustParameter(0.001f, 1.0f);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            adjustParameter(-0.001f, -1.0f);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void adjustParameter(float smallStep, float largeStep) {
        switch (selectedParamIndex) {
            case 0: // IPD
                ipd = Math.max(0.02f, Math.min(0.12f, ipd + smallStep));
                renderer.setIpd(ipd);
                break;
            case 1: // FOV
                fov = Math.max(70f, Math.min(120f, fov + largeStep));
                renderer.setFov(fov);
                break;
            case 2: // Lens Offset
                lensOffset = Math.max(0f, Math.min(0.05f, lensOffset + smallStep));
                renderer.setLensCenterOffset(lensOffset);
                break;
        }
        updateStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();
        sensorHandler.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
        sensorHandler.stop();
        if (moveServer != null) moveServer.stopServer();
    }
}
