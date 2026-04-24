package com.example.vrdesert;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.content.Context;
import com.example.vrdesert.shapes.Sphere;
import com.example.vrdesert.shapes.TextureHelper;
import com.example.vrdesert.shapes.Crosshair;
import com.example.vrdesert.shapes.ParticleSystem;
import com.example.vrdesert.shapes.BreathFog;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * VRRenderer — Ice Cave journey renderer.
 *
 * Features:
 *  1. Particle system (snow / dust / sparkles)
 *  2. Gaze flashlight (spotlight follows gaze direction)
 *  3. Spatial audio hook (scene change callback)
 *  4. Gaze-triggered info buttons (3D spheres)
 *  5. Animated scene transitions (camera zoom burst)
 *  6. Dynamic lighting shifts (color temperature per scene)
 *  7. Breath fog effect (bottom-of-screen wisps)
 */
public class VRRenderer implements GLSurfaceView.Renderer {

    // ── Dependencies ───────────────────────────────────────────────────────
    private final Context            context;
    private final SensorHandler      sensorHandler;
    private final InteractionManager interactionManager;

    // ── Scene state ────────────────────────────────────────────────────────
    private int currentScene = 0;  // 0–3, changed by setScene()

    // ── GL objects ─────────────────────────────────────────────────────────
    private Sphere         caveSphere;
    private Crosshair      crosshair;
    private int            tunnelShaderProgram;
    private Sphere         infoButtonSphere;
    private int            infoButtonProgram;
    private GameObject[]   infoButtons;
    private ParticleSystem particleSystem;
    private BreathFog      breathFog;

    // One texture per scene (loaded once in onSurfaceCreated)
    private int[] sceneTextureIds = new int[4];

    // ── Matrices ───────────────────────────────────────────────────────────
    private float[] leftProjectionMatrix  = new float[16];
    private float[] rightProjectionMatrix = new float[16];
    private float[] viewMatrix            = new float[16];
    private float[] vPMatrix              = new float[16];
    private float[] scratchMatrix         = new float[16];
    private float[] modelMatrix           = new float[16];
    private float[] uiProjectionMatrix    = new float[16];
    private float[] uiModelMatrix         = new float[16];
    private float[] uiMVPMatrix           = new float[16];

    // ── Camera ─────────────────────────────────────────────────────────────
    private static final float CAM_Y      = 1.0f;
    private static final float EYE_OFFSET = 0.05f; // IPD

    // ── Visual motion (MOVE burst) ─────────────────────────────────────────
    private float visualVelocity      = 0f;
    private float totalVisualDistance  = 0f;
    private float visualYaw           = 0f;

    // ── Transition animation ───────────────────────────────────────────────
    private float transitionProgress = 0f;  // 0 = idle, >0 = animating
    private float transitionFOVBurst = 0f;  // FOV kick during scene change

    // ── Timing ─────────────────────────────────────────────────────────────
    private long  startTimeMs = 0;
    private long  lastFrameMs = 0;
    private float elapsedSec  = 0f;

    // ── Gaze direction (for flashlight) ────────────────────────────────────
    private float gazeForwardX, gazeForwardY, gazeForwardZ;

    // ── Screen size ────────────────────────────────────────────────────────
    private int width, height;

    // ── Dynamic lighting per scene ─────────────────────────────────────────
    // Each scene has [R, G, B] tint that shifts from warm → deep blue
    private static final float[][] SCENE_TINTS = {
        { 1.00f, 0.95f, 0.85f },  // Scene 0: warm white (entrance, daylight spill)
        { 0.70f, 0.82f, 1.00f },  // Scene 1: cool blue (icicle curtains)
        { 0.45f, 0.60f, 1.00f },  // Scene 2: deep glacial blue (crystal cathedral)
        { 0.90f, 0.85f, 0.75f },  // Scene 3: warm amber (looking outside to sunlight)
    };
    // Current interpolated tint (smoothly transitions between scenes)
    private float tintR = 1.00f, tintG = 0.95f, tintB = 0.85f;

    // ── Shaders ────────────────────────────────────────────────────────────
    private static final String VERT_SRC =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 vPosition;" +
        "attribute vec2 aTexCoordinate;" +
        "varying vec2 vTexCoordinate;" +
        "varying vec3 vWorldPos;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * vPosition;" +
        "  vTexCoordinate = aTexCoordinate;" +
        "  vWorldPos = vPosition.xyz;" +
        "}";

    /**
     * Fragment shader with:
     *  - Dynamic ice tint (changes per scene via uniforms)
     *  - Gaze flashlight (spotlight cone following user's gaze)
     *  - Vignette darkening for depth
     *  - uOffset scrolls UV during MOVE burst
     */
    private static final String FRAG_SRC =
        "precision mediump float;" +
        "uniform sampler2D uTexture;" +
        "uniform float uAlphaMultiplier;" +
        "uniform float uOffset;" +
        "uniform vec3 uTint;" +
        "uniform vec3 uGazeDir;" +
        "varying vec2 vTexCoordinate;" +
        "varying vec3 vWorldPos;" +
        "void main() {" +
        "  vec2 uv = vec2(vTexCoordinate.x, vTexCoordinate.y + uOffset);" +
        "  vec4 texColor = texture2D(uTexture, uv);" +
        "  if (texColor.a < 0.05) discard;" +
        // Dynamic tint from uniform — very visible color shift
        "  vec3 tinted = texColor.rgb * uTint;" +
        // Vignette
        "  vec2 tileUV = fract(uv);" +
        "  float vig = 1.0 - smoothstep(0.3, 0.5, max(abs(tileUV.x - 0.5), abs(tileUV.y - 0.5)));" +
        "  vig = mix(0.55, 1.0, vig);" +
        // Gaze flashlight: DRAMATIC spotlight — dark everywhere except where you look
        "  vec3 fragDir = normalize(vWorldPos);" +
        "  float spotDot = max(dot(fragDir, uGazeDir), 0.0);" +
        // Wide soft spotlight (pow 3), range 0.25 to 1.0 — very obvious
        "  float spotlight = pow(spotDot, 3.0) * 0.75 + 0.25;" +
        "  gl_FragColor = vec4(tinted * vig * spotlight, texColor.a * uAlphaMultiplier);" +
        "}";

    private static final String INFO_VERT =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 vPosition;" +
        "attribute vec2 aTexCoordinate;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * vPosition;" +
        "}";

    private static final String INFO_FRAG =
        "precision mediump float;" +
        "uniform sampler2D uTexture;" +
        "uniform float uAlphaMultiplier;" +
        "uniform float uTime;" +
        "void main() {" +
        // Pulsing glow color
        "  float pulse = 0.6 + 0.4 * sin(uTime * 3.0);" +
        "  vec3 color = vec3(0.3, 0.75, 1.0) * pulse;" +
        // Soft edge on sphere
        "  gl_FragColor = vec4(color, 0.85 * uAlphaMultiplier);" +
        "}";

    // ── Constructor ────────────────────────────────────────────────────────
    public VRRenderer(Context context, SensorHandler sensorHandler,
                      InteractionManager interactionManager) {
        this.context            = context;
        this.sensorHandler      = sensorHandler;
        this.interactionManager = interactionManager;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /** Switch the displayed cave scene (0–3). */
    public void setScene(int sceneIndex) {
        if (sceneIndex >= 0 && sceneIndex < sceneTextureIds.length) {
            currentScene = sceneIndex;
            // Trigger transition animation
            transitionProgress = 1.0f;
            transitionFOVBurst = 12f;
        }
    }

    /** Trigger a visual velocity burst. */
    public void moveForward() {
        visualYaw = (float) Math.toRadians(sensorHandler.getYaw());
        visualVelocity += 1.5f;
    }

    public int getCurrentScene() {
        return currentScene;
    }

    private void positionInfoButtons() {
        float r = 15f;
        float pitch = (float) Math.toRadians(-15);
        float[] yaws = { -30f, 0f, 30f };
        for (int i = 0; i < 3; i++) {
            float yaw = (float) Math.toRadians(yaws[i]);
            infoButtons[i].x = (float) (-Math.sin(yaw) * Math.cos(pitch)) * r;
            infoButtons[i].y = (float) (Math.sin(-pitch)) * r;
            infoButtons[i].z = (float) (-Math.cos(yaw) * Math.cos(pitch)) * r;
        }
    }

    public void resetInfoButtons() {
        if (infoButtons != null) {
            for (GameObject obj : infoButtons) {
                obj.isCollected = false;
            }
        }
    }

    // ── GLSurfaceView.Renderer ─────────────────────────────────────────────
    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0.04f, 0.08f, 0.14f, 1.0f);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        startTimeMs = System.currentTimeMillis();
        lastFrameMs = startTimeMs;

        // Scene textures
        sceneTextureIds[0] = TextureHelper.loadTexture(context, R.drawable.cave_entering);
        sceneTextureIds[1] = TextureHelper.loadTexture(context, R.drawable.cave_drippingings);
        sceneTextureIds[2] = TextureHelper.loadTexture(context, R.drawable.cave_drippingings2);
        sceneTextureIds[3] = TextureHelper.loadTexture(context, R.drawable.fromcave_outside_view);

        // Cave sphere
        caveSphere = new Sphere(30f, 48, 96);
        tunnelShaderProgram = buildProgram(VERT_SRC, FRAG_SRC);

        // Info buttons
        infoButtonSphere = new Sphere(0.4f, 16, 16);
        infoButtonProgram = buildProgram(INFO_VERT, INFO_FRAG);
        infoButtons = new GameObject[] {
            new GameObject(0, 0, 0, GameObject.Type.INFO_BUTTON_0),
            new GameObject(0, 0, 0, GameObject.Type.INFO_BUTTON_1),
            new GameObject(0, 0, 0, GameObject.Type.INFO_BUTTON_2)
        };
        positionInfoButtons();

        // Particle system
        particleSystem = new ParticleSystem();
        particleSystem.initGL();

        // Breath fog
        breathFog = new BreathFog();
        breathFog.initGL();

        crosshair = new Crosshair();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        this.width  = width;
        this.height = height;
        GLES20.glViewport(0, 0, width, height);

        float ratio = (float)(width / 2) / height;
        Matrix.perspectiveM(leftProjectionMatrix,  0, 75f, ratio, 0.1f, 200f);
        Matrix.perspectiveM(rightProjectionMatrix, 0, 75f, ratio, 0.1f, 200f);
        Matrix.orthoM(uiProjectionMatrix, 0, 0, width / 2f, height, 0, -1, 1);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        // ── Timing ────────────────────────────────────────────────────────
        long now = System.currentTimeMillis();
        float dt = (now - lastFrameMs) / 1000f;
        dt = Math.min(dt, 0.1f); // cap to avoid huge jumps
        lastFrameMs = now;
        elapsedSec = (now - startTimeMs) / 1000f;

        // ── Visual velocity ───────────────────────────────────────────────
        totalVisualDistance += visualVelocity * 0.05f;
        visualVelocity      *= 0.92f;
        if (visualVelocity < 0.01f) visualVelocity = 0f;

        // ── Transition animation ──────────────────────────────────────────
        if (transitionProgress > 0f) {
            transitionProgress -= dt * 1.5f;
            if (transitionProgress < 0f) transitionProgress = 0f;
        }
        if (transitionFOVBurst > 0f) {
            transitionFOVBurst -= dt * 15f;
            if (transitionFOVBurst < 0f) transitionFOVBurst = 0f;
        }

        // ── Dynamic lighting: smooth tint interpolation ───────────────────
        float[] target = SCENE_TINTS[currentScene];
        tintR += (target[0] - tintR) * dt * 2f;
        tintG += (target[1] - tintG) * dt * 2f;
        tintB += (target[2] - tintB) * dt * 2f;

        // ── Particle update ───────────────────────────────────────────────
        if (particleSystem != null) {
            particleSystem.update(dt);
        }

        // ── Recalculate projection if FOV is bursting ─────────────────────
        if (transitionFOVBurst > 0.1f) {
            float ratio = (float)(width / 2) / height;
            float fov = 75f + transitionFOVBurst;
            Matrix.perspectiveM(leftProjectionMatrix,  0, fov, ratio, 0.1f, 200f);
            Matrix.perspectiveM(rightProjectionMatrix, 0, fov, ratio, 0.1f, 200f);
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        float pitchRad = (float) Math.toRadians(sensorHandler.getPitch());
        float yawRad   = (float) Math.toRadians(sensorHandler.getYaw());

        float fX = (float)(-Math.sin(yawRad) * Math.cos(pitchRad));
        float fY = (float)(Math.sin(-pitchRad));
        float fZ = (float)(-Math.cos(yawRad) * Math.cos(pitchRad));

        // Store gaze direction for flashlight shader
        gazeForwardX = fX;
        gazeForwardY = fY;
        gazeForwardZ = fZ;

        float tX = fX, tY = CAM_Y + fY, tZ = fZ;

        // ── Gaze check for info buttons ───────────────────────────────────
        if (infoButtons != null) {
            for (int i = 0; i < infoButtons.length; i++) {
                interactionManager.checkGaze(0, CAM_Y, 0, fX, fY, fZ, infoButtons[i], i);
            }
        }
        if (crosshair != null) {
            crosshair.setTargeting(interactionManager.isTargeting());
        }

        // ── Left eye ─────────────────────────────────────────────────────
        GLES20.glViewport(0, 0, width / 2, height);
        float lox = (float) Math.cos(yawRad) * EYE_OFFSET;
        float loz = (float)-Math.sin(yawRad) * EYE_OFFSET;
        Matrix.setLookAtM(viewMatrix, 0,
            -lox, CAM_Y, -loz,
            tX - lox, tY, tZ - loz,
            0f, 1f, 0f);
        Matrix.multiplyMM(vPMatrix, 0, leftProjectionMatrix, 0, viewMatrix, 0);
        drawScene(vPMatrix);
        drawParticles(vPMatrix);
        drawUI();
        drawBreathFog();

        // ── Right eye ────────────────────────────────────────────────────
        GLES20.glViewport(width / 2, 0, width / 2, height);
        float rox = (float)-Math.cos(yawRad) * EYE_OFFSET;
        float roz = (float) Math.sin(yawRad) * EYE_OFFSET;
        Matrix.setLookAtM(viewMatrix, 0,
            -rox, CAM_Y, -roz,
            tX - rox, tY, tZ - roz,
            0f, 1f, 0f);
        Matrix.multiplyMM(vPMatrix, 0, rightProjectionMatrix, 0, viewMatrix, 0);
        drawScene(vPMatrix);
        drawParticles(vPMatrix);
        drawUI();
        drawBreathFog();
    }

    // ── Draw helpers ───────────────────────────────────────────────────────

    private void drawScene(float[] vpMatrix) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        // Sphere centred on camera
        Matrix.setIdentityM(modelMatrix, 0);
        float scale = 1.0f + (float)(Math.sin(totalVisualDistance * 0.4f) * 0.015f);
        // Transition zoom effect
        if (transitionProgress > 0f) {
            float zoom = 1.0f + transitionProgress * 0.08f;
            scale *= zoom;
        }
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale);
        Matrix.multiplyMM(scratchMatrix, 0, vpMatrix, 0, modelMatrix, 0);

        float uOffset = totalVisualDistance * 0.008f;

        // Set dynamic uniforms
        GLES20.glUseProgram(tunnelShaderProgram);
        int tintHandle = GLES20.glGetUniformLocation(tunnelShaderProgram, "uTint");
        if (tintHandle != -1) GLES20.glUniform3f(tintHandle, tintR, tintG, tintB);

        int gazeHandle = GLES20.glGetUniformLocation(tunnelShaderProgram, "uGazeDir");
        if (gazeHandle != -1) GLES20.glUniform3f(gazeHandle, gazeForwardX, gazeForwardY, gazeForwardZ);

        caveSphere.draw(tunnelShaderProgram, scratchMatrix,
            sceneTextureIds[currentScene], 1.0f, uOffset);

        // Draw info buttons
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glUseProgram(infoButtonProgram);
        int timeHandle = GLES20.glGetUniformLocation(infoButtonProgram, "uTime");
        if (timeHandle != -1) GLES20.glUniform1f(timeHandle, elapsedSec);

        for (GameObject obj : infoButtons) {
            if (obj.isCollected) continue;
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.translateM(modelMatrix, 0, obj.x, obj.y, obj.z);
            // Floating bobbing animation
            float bob = (float) Math.sin(elapsedSec * 1.5f + obj.x) * 0.15f;
            Matrix.translateM(modelMatrix, 0, 0, bob, 0);
            float btnScale = 1.0f + 0.15f * (float) Math.sin(elapsedSec * 3.0f + obj.z);
            Matrix.scaleM(modelMatrix, 0, btnScale, btnScale, btnScale);
            Matrix.multiplyMM(scratchMatrix, 0, vpMatrix, 0, modelMatrix, 0);
            infoButtonSphere.draw(infoButtonProgram, scratchMatrix, sceneTextureIds[0], 1.0f, 0f);
        }
        GLES20.glDisable(GLES20.GL_BLEND);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private void drawParticles(float[] vpMatrix) {
        if (particleSystem == null) return;
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        particleSystem.draw(vpMatrix, elapsedSec);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private void drawBreathFog() {
        if (breathFog == null) return;
        // Intensity increases as you go deeper (scene 0=0.5, scene 3=1.0)
        float intensity = 0.5f + currentScene * 0.17f;
        breathFog.draw(elapsedSec, intensity);
    }

    private void drawUI() {
        if (crosshair == null) return;
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        Matrix.setIdentityM(uiModelMatrix, 0);
        float eyeW = width / 2f;
        Matrix.translateM(uiModelMatrix, 0, eyeW / 2f, height / 2f, 0f);
        Matrix.multiplyMM(uiMVPMatrix, 0, uiProjectionMatrix, 0, uiModelMatrix, 0);
        crosshair.draw(uiMVPMatrix);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    // ── Shader utilities ───────────────────────────────────────────────────

    private int buildProgram(String vertSrc, String fragSrc) {
        int vert = loadShader(GLES20.GL_VERTEX_SHADER,   vertSrc);
        int frag = loadShader(GLES20.GL_FRAGMENT_SHADER, fragSrc);
        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vert);
        GLES20.glAttachShader(prog, frag);
        GLES20.glLinkProgram(prog);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            android.util.Log.e("VRRenderer", "Program link failed: " + GLES20.glGetProgramInfoLog(prog));
        }
        return prog;
    }

    private int loadShader(int type, String src) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            android.util.Log.e("VRRenderer", "Shader compile error (" + (type == GLES20.GL_VERTEX_SHADER ? "vert" : "frag") + "): " + GLES20.glGetShaderInfoLog(shader));
        }
        return shader;
    }
}
