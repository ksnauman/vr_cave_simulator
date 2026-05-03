package com.example.vrdesert;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import com.example.vrdesert.shapes.Cube;
import com.example.vrdesert.shapes.Grid;
import com.example.vrdesert.shapes.Crosshair;
import com.example.vrdesert.shapes.Frame;
import com.example.vrdesert.shapes.Vignette;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CalibrationRenderer implements GLSurfaceView.Renderer {

    public enum Mode {
        GRID,
        CROSSHAIR,
        COLOR_CHANNEL,
        DEPTH_TEST
    }

    private Mode currentMode = Mode.GRID;
    private final SensorHandler sensorHandler;

    // Parameters
    private float ipd = 0.085f; // Increased default
    private float fov = 85.0f;  // Slightly reduced for "smaller" look
    private float lensCenterOffset = 0.04f; // Increased default

    // Viewport scaling (consistent with VRRenderer)
    private static final float VIEWPORT_MARGIN_X = 0.12f;
    private static final float VIEWPORT_MARGIN_Y = 0.06f;

    // GL Objects
    private Grid grid;
    private Crosshair crosshair;
    private Cube cube;
    private Frame frame;
    private Vignette vignette;

    // Matrices
    private final float[] leftProjectionMatrix = new float[16];
    private final float[] rightProjectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    private int width, height;

    public CalibrationRenderer(SensorHandler sensorHandler) {
        this.sensorHandler = sensorHandler;
    }

    public void setMode(Mode mode) {
        this.currentMode = mode;
    }

    public Mode getMode() {
        return currentMode;
    }

    public void setIpd(float ipd) {
        this.ipd = ipd;
        updateProjections();
    }

    public void setFov(float fov) {
        this.fov = fov;
        updateProjections();
    }

    public void setLensCenterOffset(float offset) {
        this.lensCenterOffset = offset;
        updateProjections();
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        grid = new Grid(20, 10f);
        crosshair = new Crosshair();
        cube = new Cube();
        frame = new Frame();
        vignette = new Vignette();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        this.width = width;
        this.height = height;
        updateProjections();
    }

    private void updateProjections() {
        if (width <= 0 || height <= 0) return;
        float viewW = (width / 2f) * (1.0f - 2.0f * VIEWPORT_MARGIN_X);
        float viewH = height * (1.0f - 2.0f * VIEWPORT_MARGIN_Y);
        float ratio = viewW / viewH;
        Matrix.perspectiveM(leftProjectionMatrix, 0, fov, ratio, 0.1f, 100f);
        Matrix.perspectiveM(rightProjectionMatrix, 0, fov, ratio, 0.1f, 100f);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        float pitchRad = (float) Math.toRadians(sensorHandler.getPitch());
        float yawRad = (float) Math.toRadians(sensorHandler.getYaw());

        float fX = (float) (-Math.sin(yawRad) * Math.cos(pitchRad));
        float fY = (float) (Math.sin(-pitchRad));
        float fZ = (float) (-Math.cos(yawRad) * Math.cos(pitchRad));

        // Calculate dimensions
        int viewW = (int)((width / 2f) * (1.0f - 2.0f * VIEWPORT_MARGIN_X));
        int viewH = (int)(height * (1.0f - 2.0f * VIEWPORT_MARGIN_Y));
        int padX  = (int)((width / 2f) * VIEWPORT_MARGIN_X);
        int padY  = (int)(height * VIEWPORT_MARGIN_Y);

        // Left Eye
        GLES20.glViewport(padX, padY, viewW, viewH);
        renderEye(leftProjectionMatrix, yawRad, pitchRad, fX, fY, fZ, true);

        // Right Eye
        GLES20.glViewport(width / 2 + padX, padY, viewW, viewH);
        renderEye(rightProjectionMatrix, yawRad, pitchRad, fX, fY, fZ, false);

        // Draw Divider
        drawDivider();
    }

    private void renderEye(float[] projectionMatrix, float yaw, float pitch, float fX, float fY, float fZ, boolean isLeft) {
        float eyeOffset = isLeft ? -ipd / 2f : ipd / 2f;
        
        // Apply Lens Center Offset to the projection or view? 
        // User says "Lens Center Offset (0.0 to 0.05, default 0.02)"
        // Usually this means shifting the viewport center.
        float lensShift = isLeft ? lensCenterOffset : -lensCenterOffset;
        
        float lox = (float) Math.cos(yaw) * eyeOffset;
        float loz = (float) -Math.sin(yaw) * eyeOffset;

        Matrix.setLookAtM(viewMatrix, 0,
                lox, 0, loz,
                fX + lox, fY, fZ + loz,
                0f, 1f, 0f);

        // We can also shift the projection matrix for lens offset
        float[] shiftedProj = projectionMatrix.clone();
        Matrix.translateM(shiftedProj, 0, lensShift, 0, 0);

        Matrix.multiplyMM(mvpMatrix, 0, shiftedProj, 0, viewMatrix, 0);

        switch (currentMode) {
            case GRID:
                drawGrid(mvpMatrix);
                break;
            case CROSSHAIR:
                drawCrosshair(isLeft);
                break;
            case COLOR_CHANNEL:
                drawColorChannel(isLeft);
                break;
            case DEPTH_TEST:
                drawDepthScene(mvpMatrix);
                break;
        }

        drawMaskingFrame();
        vignette.draw(0.12f, 0.05f);
    }

    private void drawGrid(float[] mvp) {
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 0, 0, -5f); // Grid in front
        float[] finalMvp = new float[16];
        Matrix.multiplyMM(finalMvp, 0, mvp, 0, modelMatrix, 0);
        grid.draw(finalMvp, new float[]{1f, 1f, 1f, 1f});
    }

    private void drawCrosshair(boolean isLeft) {
        // Crosshair is usually static in UI space
        // But the user says "centered crosshair in each eye; when correct, both merge"
        // This implies it should be at a specific depth or in screen space.
        // If it's in screen space with no offset, it should merge at infinity.
        float[] uiMvp = new float[16];
        Matrix.setIdentityM(uiMvp, 0);
        // We want it centered in the viewport.
        crosshair.draw(uiMvp);
    }

    private void drawColorChannel(boolean isLeft) {
        GLES20.glClearColor(isLeft ? 1f : 0f, 0f, isLeft ? 0f : 1f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        // Reset clear color for next eye
        GLES20.glClearColor(0f, 0f, 0f, 1f);
    }

    private void drawDepthScene(float[] vp) {
        // Draw multiple cubes at different depths
        float[][] positions = {
                {0, 0, -2f},
                {-1.5f, 0.5f, -4f},
                {1.5f, -0.5f, -6f},
                {0, 1.0f, -8f}
        };
        float[][] colors = {
                {1f, 0.2f, 0.2f, 1f},
                {0.2f, 1f, 0.2f, 1f},
                {0.2f, 0.2f, 1f, 1f},
                {1f, 1f, 0.2f, 1f}
        };

        for (int i = 0; i < positions.length; i++) {
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.translateM(modelMatrix, 0, positions[i][0], positions[i][1], positions[i][2]);
            // Slow rotation
            float time = (float) (System.currentTimeMillis() % 10000L) / 10000L * 360f;
            Matrix.rotateM(modelMatrix, 0, time, 0.5f, 1f, 0f);
            Matrix.scaleM(modelMatrix, 0, 0.3f, 0.3f, 0.3f);
            Matrix.multiplyMM(mvpMatrix, 0, vp, 0, modelMatrix, 0);
            cube.draw(mvpMatrix, colors[i]);
        }
    }

    private void drawMaskingFrame() {
        float[] frameMvp = new float[16];
        Matrix.setIdentityM(frameMvp, 0);
        // Scale it to cover the view edges
        Matrix.scaleM(frameMvp, 0, 0.98f, 0.98f, 1.0f);
        float[] borderColor = {0.1f, 0.1f, 0.1f, 1.0f}; // Dark gray border
        frame.draw(frameMvp, borderColor, 12.0f);
    }

    private void drawDivider() {
        // Black divider line between eyes
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(width / 2 - 2, 0, 4, height);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }
}
