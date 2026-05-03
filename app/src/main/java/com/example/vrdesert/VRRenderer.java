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
 *  8. Landing Menu: 360° cave card interactive carousel
 */
public class VRRenderer implements GLSurfaceView.Renderer {

    public enum State {
        LANDING,
        INSIDE
    }

    // ── Dependencies ───────────────────────────────────────────────────────
    private final Context            context;
    private final SensorHandler      sensorHandler;
    private final InteractionManager interactionManager;

    // ── Scene state ────────────────────────────────────────────────────────
    private State currentState = State.LANDING;
    private int   currentCaveIndex = -1;
    private int   caveCount = 4; // Default

    // ── GL objects ─────────────────────────────────────────────────────────
    private Sphere         caveSphere;
    private Crosshair      crosshair;
    private int            tunnelShaderProgram;
    private Sphere         infoButtonSphere;
    private int            infoButtonProgram;
    private GameObject[]   infoButtons;
    private ParticleSystem particleSystem;
    private BreathFog      breathFog;
    private com.example.vrdesert.shapes.ProgressCircle progressCircle;

    // One texture per scene (loaded once in onSurfaceCreated)
    private int[] caveInteriorTextures = new int[4];
    private int[] caveCardTextures     = new int[4];
    private int[] caveLabelTextures;
    private String[] caveNames;
    private com.example.vrdesert.shapes.ImageBillboard[] caveCards;
    private com.example.vrdesert.shapes.ImageBillboard[] labelCards;
    
    private int highlightedCardIndex = -1;
    private float[] cardScales;

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
        { 1.00f, 0.85f, 0.65f },  // Ajanta: Warm ancient stone, mural lighting
        { 0.85f, 0.85f, 0.80f },  // Ellora: Majestic grey rock, majestic atmosphere
        { 1.00f, 0.65f, 0.45f },  // Badami: Vibrant red sandstone
        { 0.75f, 0.75f, 0.85f },  // Elephanta: Cool, dim atmospheric cave light
    };
    // Current interpolated tint (smoothly transitions between scenes)
    private float tintR = 1.00f, tintG = 0.95f, tintB = 0.85f;

    // ── Shaders ────────────────────────────────────────────────────────────
    // Shared vertex shader for all geometry
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

    // Cave interior shader: spotlight + tint + vignette (used only when INSIDE)
    private static final String FRAG_SRC =
        "precision mediump float;" +
        "uniform sampler2D uTexture;" +
        "uniform float uAlphaMultiplier;" +
        "uniform float uOffset;" +
        "uniform vec3 uTint;" +
        "uniform vec3 uGazeDir;" +
        "uniform float uGlow;" +
        "varying vec2 vTexCoordinate;" +
        "varying vec3 vWorldPos;" +
        "void main() {" +
        "  vec2 uv = vec2(vTexCoordinate.x, vTexCoordinate.y + uOffset);" +
        "  vec4 texColor = texture2D(uTexture, uv);" +
        "  if (texColor.a < 0.05) discard;" +
        "  vec3 tinted = texColor.rgb * uTint;" +
        "  vec2 tileUV = fract(uv);" +
        "  float vig = 1.0 - smoothstep(0.3, 0.5, max(abs(tileUV.x-0.5), abs(tileUV.y-0.5)));" +
        "  vig = mix(0.55, 1.0, vig);" +
        "  vec3 fragDir = normalize(vWorldPos);" +
        "  float spotDot = max(dot(fragDir, uGazeDir), 0.0);" +
        "  float spotlight = pow(spotDot, 3.0) * 0.75 + 0.25;" +
        "  vec3 finalColor = tinted * vig * spotlight;" +
        "  finalColor += uGlow * vec3(1.0, 0.9, 0.6);" +
        "  gl_FragColor = vec4(finalColor, texColor.a * uAlphaMultiplier);" +
        "}";

    // Landing card shader: bright, flat colour with glow highlight — NO dark effects
    private static final String CARD_VERT =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 vPosition;" +
        "attribute vec2 aTexCoordinate;" +
        "varying vec2 vTexCoord;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * vPosition;" +
        "  vTexCoord = aTexCoordinate;" +
        "}";

    // Card face: white base + coloured border + highlight on hover
    private static final String CARD_FRAG =
        "precision mediump float;" +
        "uniform vec4 uCardColor;" +    // base card colour
        "uniform float uGlow;" +        // 0 = normal, 1 = highlighted
        "uniform float uTime;" +
        "varying vec2 vTexCoord;" +
        "void main() {" +
        // Rounded-rect border: darken edges
        "  vec2 uv = vTexCoord;" +
        "  float bx = smoothstep(0.0, 0.04, uv.x) * smoothstep(1.0, 0.96, uv.x);" +
        "  float by = smoothstep(0.0, 0.06, uv.y) * smoothstep(1.0, 0.94, uv.y);" +
        "  float border = bx * by;" +
        // Interior: a soft gradient from top to bottom
        "  vec3 topCol = uCardColor.rgb * 1.15;" +
        "  vec3 botCol = uCardColor.rgb * 0.75;" +
        "  vec3 col = mix(topCol, botCol, uv.y);" +
        // Glow pulse ring when highlighted
        "  float pulse = 0.7 + 0.3 * sin(uTime * 4.0);" +
        "  col = mix(col, vec3(1.0, 0.85, 0.3) * pulse, uGlow * (1.0 - border) * 0.0);" +
        "  col = mix(vec3(1.0, 0.85, 0.3) * pulse, col, border);" +
        // Highlight brighten whole card
        "  col += uGlow * 0.22 * vec3(1.0, 0.9, 0.5);" +
        "  float alpha = 0.93;" +
        "  gl_FragColor = vec4(col, alpha);" +
        "}";

    // Grid shader for ground stability
    private static final String GRID_VERT =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 vPosition;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * vPosition;" +
        "}";
    private static final String GRID_FRAG =
        "precision mediump float;" +
        "void main() {" +
        "  gl_FragColor = vec4(0.5, 0.5, 0.5, 0.2);" + // Subtle grey grid
        "}";
    private int gridProgram;

    private int cardShaderProgram;
    private static final float[][] CARD_COLORS = {
        {0.95f, 0.82f, 0.55f},  // warm golden
        {0.55f, 0.82f, 0.95f},  // sky blue
        {0.75f, 0.95f, 0.65f},  // soft green
        {0.95f, 0.65f, 0.65f},  // rose
        {0.80f, 0.65f, 0.95f},  // lavender
        {0.65f, 0.95f, 0.90f},  // teal
        {0.95f, 0.90f, 0.55f},  // citrus
        {0.65f, 0.75f, 0.95f},  // periwinkle
    };

    private static final String INFO_VERT =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 vPosition;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * vPosition;" +
        "}";

    private static final String INFO_FRAG =
        "precision mediump float;" +
        "uniform float uAlphaMultiplier;" +
        "uniform float uTime;" +
        "void main() {" +
        "  float pulse = 0.6 + 0.4 * sin(uTime * 3.0);" +
        "  vec3 color = vec3(0.3, 0.75, 1.0) * pulse;" +
        "  gl_FragColor = vec4(color, 0.85 * uAlphaMultiplier);" +
        "}";

    // Simple texture shader for labels (no lighting/spotlight)
    private static final String TEXTURE_VERT =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 vPosition;" +
        "attribute vec2 aTexCoordinate;" +
        "varying vec2 vTexCoord;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * vPosition;" +
        "  vTexCoord = aTexCoordinate;" +
        "}";
    private static final String TEXTURE_FRAG =
        "precision mediump float;" +
        "uniform sampler2D uTexture;" +
        "uniform float uAlphaMultiplier;" +
        "varying vec2 vTexCoord;" +
        "void main() {" +
        "  vec4 texColor = texture2D(uTexture, vTexCoord);" +
        "  gl_FragColor = vec4(texColor.rgb, texColor.a * uAlphaMultiplier);" +
        "}";
    private int textureProgram;

    public void setCaveData(String[] names) {
        this.caveCount = names.length;
        this.caveNames = names;
        this.caveInteriorTextures = new int[caveCount];
        this.caveCardTextures = new int[caveCount];
        this.caveLabelTextures = new int[caveCount];
        this.caveCards = new com.example.vrdesert.shapes.ImageBillboard[caveCount];
        this.labelCards = new com.example.vrdesert.shapes.ImageBillboard[caveCount];
        this.cardScales = new float[caveCount];
        for (int i = 0; i < caveCount; i++) {
            cardScales[i] = 1.0f;
        }
    }

    public VRRenderer(Context context, SensorHandler sensorHandler,
                      InteractionManager interactionManager) {
        this.context            = context;
        this.sensorHandler      = sensorHandler;
        this.interactionManager = interactionManager;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public void setState(State state) {
        this.currentState = state;
        if (state == State.LANDING) {
            visualVelocity = 0f;
            visualYaw = 0f;
            totalVisualDistance = 0f;
            resetInfoButtons();
        }
    }

    public void setCaveScene(int index) {
        this.currentCaveIndex = index;
        this.currentState = State.INSIDE;
        transitionProgress = 1.0f;
    }

    public void highlightCard(int index, boolean highlight) {
        if (highlight) {
            highlightedCardIndex = index;
        } else if (highlightedCardIndex == index) {
            highlightedCardIndex = -1;
        }
    }

    /** Trigger a visual velocity burst. */
    public void moveForward() {
        visualYaw = (float) Math.toRadians(sensorHandler.getYaw());
        visualVelocity += 1.5f;
    }

    public int getCurrentCaveIndex() {
        return currentCaveIndex;
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
        GLES20.glClearColor(0.88f, 0.94f, 1.0f, 1.0f); // Bright sky-blue
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        startTimeMs = System.currentTimeMillis();
        lastFrameMs = startTimeMs;

        // Scene textures - Load common placeholders
        int placeholder = TextureHelper.loadTexture(context, R.drawable.cave_entering);
        for (int i = 0; i < caveCount; i++) {
            caveInteriorTextures[i] = placeholder;
            caveCardTextures[i] = placeholder;
            if (caveNames != null && i < caveNames.length) {
                caveLabelTextures[i] = com.example.vrdesert.shapes.TextTextureHelper.createTextTexture(caveNames[i]);
            }
        }

        for (int i = 0; i < caveCount; i++) {
            caveCards[i] = new com.example.vrdesert.shapes.ImageBillboard();
            labelCards[i] = new com.example.vrdesert.shapes.ImageBillboard();
        }

        // Cave sphere (only used in INSIDE state)
        caveSphere = new Sphere(50f, 48, 96);
        tunnelShaderProgram = buildProgram(VERT_SRC, FRAG_SRC);
        // Card shader (used in LANDING state for bright coloured cards)
        cardShaderProgram = buildProgram(CARD_VERT, CARD_FRAG);
        gridProgram = buildProgram(GRID_VERT, GRID_FRAG);
        textureProgram = buildProgram(TEXTURE_VERT, TEXTURE_FRAG);

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

        progressCircle = new com.example.vrdesert.shapes.ProgressCircle();

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
        float[] target = (currentCaveIndex != -1) ? SCENE_TINTS[currentCaveIndex] : SCENE_TINTS[0];
        tintR += (target[0] - tintR) * dt * 2f;
        tintG += (target[1] - tintG) * dt * 2f;
        tintB += (target[2] - tintB) * dt * 2f;

        // ── Card Scaling Animation ────────────────────────────────────────
        for (int i = 0; i < caveCount; i++) {
            float targetScale = (i == highlightedCardIndex) ? 1.2f : 1.0f;
            cardScales[i] += (targetScale - cardScales[i]) * dt * 8f;
        }

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

        // ── Gaze check for cards ──────────────────────────────────────────
        if (currentState == State.LANDING) {
            float cardDist = 14f;
            float angleStep = 360f / caveCount;
            for (int i = 0; i < caveCount; i++) {
                float angle = (float) Math.toRadians(i * angleStep);
                float cardX = (float) Math.sin(angle) * cardDist;
                float cardZ = (float) -Math.cos(angle) * cardDist;
                
                // Simple gaze check for card (approximated as sphere for InteractionManager)
                GameObject cardObj = new GameObject(cardX, 1.0f, cardZ, GameObject.Type.INFO_BUTTON_0);
                interactionManager.checkGaze(0, CAM_Y, 0, fX, fY, fZ, cardObj, i);
            }
        }
        if (crosshair != null) {
            crosshair.setTargeting(interactionManager.isTargeting());
        }

        // ── Left eye ─────────────────────────────────────────────────────
        GLES20.glViewport(0, 0, width / 2, height);
        // Correct IPD offset: offset the camera perpendicular to the look direction
        float cosYaw = (float) Math.cos(yawRad);
        float sinYaw = (float) Math.sin(yawRad);
        float lox = cosYaw * EYE_OFFSET;
        float loz = -sinYaw * EYE_OFFSET;
        
        Matrix.setLookAtM(viewMatrix, 0,
            -lox, CAM_Y, -loz,
            tX - lox, tY, tZ - loz,
            0f, 1f, 0f);
        Matrix.multiplyMM(vPMatrix, 0, leftProjectionMatrix, 0, viewMatrix, 0);
        if (currentState == State.LANDING) {
            drawLandingMenu(vPMatrix);
        } else {
            drawScene(vPMatrix);
            drawParticles(vPMatrix);
            drawBreathFog();
        }
        drawUI();

        // ── Right eye ────────────────────────────────────────────────────
        GLES20.glViewport(width / 2, 0, width / 2, height);
        float rox = -cosYaw * EYE_OFFSET;
        float roz = sinYaw * EYE_OFFSET;
        
        Matrix.setLookAtM(viewMatrix, 0,
            -rox, CAM_Y, -roz,
            tX - rox, tY, tZ - roz,
            0f, 1f, 0f);
        Matrix.multiplyMM(vPMatrix, 0, rightProjectionMatrix, 0, viewMatrix, 0);
        if (currentState == State.LANDING) {
            drawLandingMenu(vPMatrix);
        } else {
            drawScene(vPMatrix);
            drawParticles(vPMatrix);
            drawBreathFog();
        }
        drawUI();
    }

    // ── Draw helpers ───────────────────────────────────────────────────────

    private void drawLandingMenu(float[] vpMatrix) {
        // Clear background is handled by glClearColor (Bright Sky)
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // 1. Draw a ground grid for stability/orientation
        GLES20.glUseProgram(gridProgram);
        for (int i = -10; i <= 10; i++) {
            // Horizontal lines
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.translateM(modelMatrix, 0, 0, -0.5f, i * 2.0f);
            Matrix.scaleM(modelMatrix, 0, 20f, 0.01f, 1f);
            Matrix.multiplyMM(scratchMatrix, 0, vpMatrix, 0, modelMatrix, 0);
            caveCards[0].draw(gridProgram, scratchMatrix, 0, 0.5f, 0f);

            // Vertical lines
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.translateM(modelMatrix, 0, i * 2.0f, -0.5f, 0);
            Matrix.scaleM(modelMatrix, 0, 1f, 0.01f, 20f);
            Matrix.multiplyMM(scratchMatrix, 0, vpMatrix, 0, modelMatrix, 0);
            caveCards[0].draw(gridProgram, scratchMatrix, 0, 0.5f, 0f);
        }

        GLES20.glUseProgram(cardShaderProgram);
        int timeHandle = GLES20.glGetUniformLocation(cardShaderProgram, "uTime");
        if (timeHandle != -1) GLES20.glUniform1f(timeHandle, elapsedSec);

        // 2. Arrange cards in a larger circle to fit 26+ items
        float cardDist = 14f; // Pushed further back
        float angleStep = 360f / caveCount;
        for (int i = 0; i < caveCount; i++) {
            float angleDegrees = i * angleStep;
            float angleRad = (float) Math.toRadians(angleDegrees);
            boolean highlighted = (i == highlightedCardIndex);

            float[] col = CARD_COLORS[i % CARD_COLORS.length];
            int colorHandle = GLES20.glGetUniformLocation(cardShaderProgram, "uCardColor");
            if (colorHandle != -1) GLES20.glUniform4f(colorHandle, col[0], col[1], col[2], 1.0f);

            int glowHandle = GLES20.glGetUniformLocation(cardShaderProgram, "uGlow");
            if (glowHandle != -1) GLES20.glUniform1f(glowHandle, highlighted ? 1.0f : 0.0f);

            float cardW = 1.8f * cardScales[i];
            float cardH = 2.8f * cardScales[i];

            // ── Draw Main Card ──
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.translateM(modelMatrix, 0,
                (float)Math.sin(angleRad) * cardDist,
                1.2f, 
                (float)-Math.cos(angleRad) * cardDist);
            Matrix.rotateM(modelMatrix, 0, -angleDegrees, 0, 1, 0);
            Matrix.scaleM(modelMatrix, 0, cardW, cardH, 1.0f);
            Matrix.multiplyMM(scratchMatrix, 0, vpMatrix, 0, modelMatrix, 0);
            caveCards[i].draw(cardShaderProgram, scratchMatrix, 0, 1.0f, highlighted ? 1.0f : 0.0f);

            // ── Draw Label Card ──
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.translateM(modelMatrix, 0,
                (float)Math.sin(angleRad) * cardDist,
                2.8f, // Positioned ABOVE the main card
                (float)-Math.cos(angleRad) * cardDist);
            Matrix.rotateM(modelMatrix, 0, -angleDegrees, 0, 1, 0);
            Matrix.scaleM(modelMatrix, 0, 2.0f, 0.5f, 1.0f); // Slightly wider to fit text
            Matrix.multiplyMM(scratchMatrix, 0, vpMatrix, 0, modelMatrix, 0);
            
            // Use simple texture shader for labels
            labelCards[i].draw(textureProgram, scratchMatrix, caveLabelTextures[i], 1.0f, 0f);
        }

        GLES20.glDisable(GLES20.GL_BLEND);
    }

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

        int activeTexture = (currentCaveIndex != -1) ? caveInteriorTextures[currentCaveIndex] : caveInteriorTextures[0];
        caveSphere.draw(tunnelShaderProgram, scratchMatrix,
            activeTexture, 1.0f, uOffset, 0f);

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
            infoButtonSphere.draw(infoButtonProgram, scratchMatrix, caveInteriorTextures[0], 1.0f, 0f, 0f);
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
        float intensity = 0.5f + (currentCaveIndex != -1 ? currentCaveIndex : 0) * 0.17f;
        breathFog.draw(elapsedSec, intensity);
    }

    private void drawUI() {
        if (crosshair == null) return;
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        
        float eyeW = width / 2f;
        float centerY = height / 2f;
        float centerX = eyeW / 2f;
        
        // 1. Draw Crosshair
        Matrix.setIdentityM(uiModelMatrix, 0);
        Matrix.translateM(uiModelMatrix, 0, centerX, centerY, 0f);
        // Scale crosshair to be a reasonable size (e.g., 5% of eye width)
        float crosshairScale = (eyeW * 0.05f) / 15f; 
        Matrix.scaleM(uiModelMatrix, 0, crosshairScale, crosshairScale, 1.0f);
        Matrix.multiplyMM(uiMVPMatrix, 0, uiProjectionMatrix, 0, uiModelMatrix, 0);
        
        crosshair.setTargeting(interactionManager.isTargeting());
        crosshair.draw(uiMVPMatrix);

        // 2. Draw Progress Circle
        float progress = interactionManager.getGazeProgress();
        if (progress > 0) {
            Matrix.setIdentityM(uiModelMatrix, 0);
            Matrix.translateM(uiModelMatrix, 0, centerX, centerY, 0f);
            // Circle slightly larger than crosshair
            float circleScale = (eyeW * 0.08f); 
            Matrix.scaleM(uiModelMatrix, 0, circleScale, circleScale, 1f);
            Matrix.multiplyMM(uiMVPMatrix, 0, uiProjectionMatrix, 0, uiModelMatrix, 0);
            
            progressCircle.draw(infoButtonProgram, uiMVPMatrix, progress);
        }
        
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
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
