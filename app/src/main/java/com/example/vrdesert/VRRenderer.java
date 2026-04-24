package com.example.vrdesert;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.content.Context;
import com.example.vrdesert.shapes.Sphere;
import com.example.vrdesert.shapes.TextureHelper;
import com.example.vrdesert.shapes.Crosshair;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * VRRenderer — Ice Cave journey renderer.
 *
 * Renders a large inside-out Sphere mapped with one of 4 real ice-cave photo
 * textures. Scene switches (scene 0→1→2→3) via setScene() called from
 * MainActivity during frost transitions.
 *
 * Gyro head-tracking, look-back recentering, and stereoscopic split-screen
 * are fully preserved from the original implementation.
 */
public class VRRenderer implements GLSurfaceView.Renderer {

    // ── Dependencies ───────────────────────────────────────────────────────
    private final Context            context;
    private final SensorHandler      sensorHandler;
    private final InteractionManager interactionManager;

    // ── Scene state ────────────────────────────────────────────────────────
    private int currentScene = 0;  // 0–3, changed by setScene()

    // ── GL objects ─────────────────────────────────────────────────────────
    private Sphere   caveSphere;
    private Crosshair crosshair;
    private int      tunnelShaderProgram;

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
    private float visualVelocity     = 0f;
    private float totalVisualDistance = 0f;
    private float visualYaw          = 0f;

    // ── Screen size ────────────────────────────────────────────────────────
    private int width, height;

    // ── Shaders ────────────────────────────────────────────────────────────
    private static final String VERT_SRC =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 vPosition;" +
        "attribute vec2 aTexCoordinate;" +
        "varying vec2 vTexCoordinate;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * vPosition;" +
        "  vTexCoordinate = aTexCoordinate;" +
        "}";

    /**
     * Fragment shader with:
     *  - icy blue-white tint (cool cave atmosphere)
     *  - subtle vignette darkening at UV edges for depth
     *  - uOffset scrolls UV during MOVE burst for motion feel
     */
    private static final String FRAG_SRC =
        "precision mediump float;" +
        "uniform sampler2D uTexture;" +
        "uniform float uAlphaMultiplier;" +
        "uniform float uOffset;" +
        "varying vec2 vTexCoordinate;" +
        "void main() {" +
        "  vec2 uv = vec2(vTexCoordinate.x, vTexCoordinate.y + uOffset);" +
        "  vec4 texColor = texture2D(uTexture, uv);" +
        "  if (texColor.a < 0.05) discard;" +
        // Ice-blue tint: subtly cool the photo toward glacial blues
        "  vec3 iceTint = vec3(0.82, 0.92, 1.0);" +
        "  vec3 tinted = texColor.rgb * iceTint;" +
        // Vignette: darken toward edges of each tile for depth
        "  vec2 tileUV = fract(uv);" +
        "  float vig = 1.0 - smoothstep(0.3, 0.5, max(abs(tileUV.x - 0.5), abs(tileUV.y - 0.5)));" +
        "  vig = mix(0.55, 1.0, vig);" +
        "  gl_FragColor = vec4(tinted * vig, texColor.a * uAlphaMultiplier);" +
        "}";

    // ── Constructor ────────────────────────────────────────────────────────
    public VRRenderer(Context context, SensorHandler sensorHandler,
                      InteractionManager interactionManager) {
        this.context            = context;
        this.sensorHandler      = sensorHandler;
        this.interactionManager = interactionManager;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /** Switch the displayed cave scene (0–3). Called between frost transitions. */
    public void setScene(int sceneIndex) {
        if (sceneIndex >= 0 && sceneIndex < sceneTextureIds.length) {
            currentScene = sceneIndex;
        }
    }

    /** Trigger a visual velocity burst (called by MOVE button and MoveServer). */
    public void moveForward() {
        visualYaw = (float) Math.toRadians(sensorHandler.getYaw());
        visualVelocity += 1.5f;
    }

    // ── GLSurfaceView.Renderer ─────────────────────────────────────────────
    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0.04f, 0.08f, 0.14f, 1.0f); // deep icy dark blue
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // ── Scene textures using real ice cave photos ────────────────────────
        // Scene 0: Dark tunnel — entering the cave
        // Scene 1: Close-up icicle curtains (drippings)
        // Scene 2: Dense icicle ceiling (drippings 2)
        // Scene 3: View from inside cave looking outward
        sceneTextureIds[0] = TextureHelper.loadTexture(context, R.drawable.cave_entering);
        sceneTextureIds[1] = TextureHelper.loadTexture(context, R.drawable.cave_drippingings);
        sceneTextureIds[2] = TextureHelper.loadTexture(context, R.drawable.cave_drippingings2);
        sceneTextureIds[3] = TextureHelper.loadTexture(context, R.drawable.fromcave_outside_view);

        // Large inside-out sphere (radius 30, 48 rings × 96 sectors for smooth photo mapping)
        caveSphere = new Sphere(30f, 48, 96);

        // Build shader program
        tunnelShaderProgram = buildProgram(VERT_SRC, FRAG_SRC);

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
        // Integrate visual velocity
        totalVisualDistance += visualVelocity * 0.05f;
        visualVelocity      *= 0.92f;
        if (visualVelocity < 0.01f) visualVelocity = 0f;

        if (crosshair != null) {
            crosshair.setTargeting(false); // no gaze targeting in exploration mode
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        float pitchRad = (float) Math.toRadians(sensorHandler.getPitch());
        float yawRad   = (float) Math.toRadians(sensorHandler.getYaw());

        float fX = (float)(-Math.sin(yawRad) * Math.cos(pitchRad));
        float fY = (float)(Math.sin(-pitchRad));
        float fZ = (float)(-Math.cos(yawRad) * Math.cos(pitchRad));

        float tX = fX, tY = CAM_Y + fY, tZ = fZ;

        // Left eye
        GLES20.glViewport(0, 0, width / 2, height);
        float lox = (float) Math.cos(yawRad) * EYE_OFFSET;
        float loz = (float)-Math.sin(yawRad) * EYE_OFFSET;
        Matrix.setLookAtM(viewMatrix, 0,
            -lox, CAM_Y, -loz,
            tX - lox, tY, tZ - loz,
            0f, 1f, 0f);
        Matrix.multiplyMM(vPMatrix, 0, leftProjectionMatrix, 0, viewMatrix, 0);
        drawScene(vPMatrix);
        drawUI();

        // Right eye
        GLES20.glViewport(width / 2, 0, width / 2, height);
        float rox = (float)-Math.cos(yawRad) * EYE_OFFSET;
        float roz = (float) Math.sin(yawRad) * EYE_OFFSET;
        Matrix.setLookAtM(viewMatrix, 0,
            -rox, CAM_Y, -roz,
            tX - rox, tY, tZ - roz,
            0f, 1f, 0f);
        Matrix.multiplyMM(vPMatrix, 0, rightProjectionMatrix, 0, viewMatrix, 0);
        drawScene(vPMatrix);
        drawUI();
    }

    // ── Draw helpers ───────────────────────────────────────────────────────

    private void drawScene(float[] vpMatrix) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        // Sphere centred on camera (moves with camera for infinite-sky feel)
        Matrix.setIdentityM(modelMatrix, 0);
        // Subtle breathing scale on move
        float scale = 1.0f + (float)(Math.sin(totalVisualDistance * 0.4f) * 0.015f);
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale);
        Matrix.multiplyMM(scratchMatrix, 0, vpMatrix, 0, modelMatrix, 0);

        // UV scroll driven by motion burst
        float uOffset = totalVisualDistance * 0.008f;

        caveSphere.draw(tunnelShaderProgram, scratchMatrix,
            sceneTextureIds[currentScene], 1.0f, uOffset);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
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
        return prog;
    }

    private int loadShader(int type, String src) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);
        return shader;
    }

    /**
     * Loads a drawable as a GL texture.
     * Real PNG/JPG images load normally.
     * XML shape drawables (placeholders) are detected via BitmapFactory bounds probe —
     * they return outWidth==-1. In that case a solid icy-dark-blue 4×4 bitmap is used
     * so the sphere renders as a dark tinted environment rather than nothing.
     */
    private int loadSafe(int resourceId) {
        // Probe: does this resource decode as a real bitmap?
        android.graphics.BitmapFactory.Options probe = new android.graphics.BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeResource(context.getResources(), resourceId, probe);

        if (probe.outWidth > 0 && probe.outHeight > 0) {
            // Real PNG/JPG image — load it as a texture
            return TextureHelper.loadTexture(context, resourceId);
        }

        // XML placeholder or missing bitmap — generate an in-memory icy-blue tile
        android.graphics.Bitmap fallback = android.graphics.Bitmap.createBitmap(4, 4,
                android.graphics.Bitmap.Config.ARGB_8888);
        fallback.eraseColor(0xFF0A1828); // dark ice blue
        int[] handle = new int[1];
        android.opengl.GLES20.glGenTextures(1, handle, 0);
        if (handle[0] != 0) {
            android.opengl.GLES20.glBindTexture(android.opengl.GLES20.GL_TEXTURE_2D, handle[0]);
            android.opengl.GLES20.glTexParameteri(android.opengl.GLES20.GL_TEXTURE_2D,
                    android.opengl.GLES20.GL_TEXTURE_MIN_FILTER, android.opengl.GLES20.GL_LINEAR);
            android.opengl.GLES20.glTexParameteri(android.opengl.GLES20.GL_TEXTURE_2D,
                    android.opengl.GLES20.GL_TEXTURE_MAG_FILTER, android.opengl.GLES20.GL_LINEAR);
            android.opengl.GLUtils.texImage2D(android.opengl.GLES20.GL_TEXTURE_2D, 0, fallback, 0);
            fallback.recycle();
        }
        return handle[0];
    }
}
