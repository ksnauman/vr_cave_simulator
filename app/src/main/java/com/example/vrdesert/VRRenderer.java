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

public class VRRenderer implements GLSurfaceView.Renderer {

    public enum State {
        LANDING,
        INSIDE
    }

    private final Context context;
    private final SensorHandler sensorHandler;
    private final InteractionManager interactionManager;

    private State currentState = State.LANDING;
    private int currentCaveIndex = -1;
    private int caveCount = 7; 

    private Sphere caveSphere;
    private Crosshair crosshair;
    private int tunnelShaderProgram;
    private int cardShaderProgram;
    private int gridProgram;
    private int textureProgram;
    private Sphere infoButtonSphere;
    private int infoButtonProgram;
    private GameObject[] infoButtons;
    private ParticleSystem particleSystem;
    private BreathFog breathFog;
    private com.example.vrdesert.shapes.ProgressCircle progressCircle;

    private int[] caveCardTextures;
    private int[] caveLabelTextures;
    private String[] caveNames;
    private com.example.vrdesert.shapes.ImageBillboard[] caveCards;
    private com.example.vrdesert.shapes.ImageBillboard[] labelCards;

    private int highlightedCardIndex = -1;
    private float[] cardScales;

    private float[] leftProjectionMatrix = new float[16];
    private float[] rightProjectionMatrix = new float[16];
    private float[] viewMatrix = new float[16];
    private float[] vPMatrix = new float[16];
    private float[] scratchMatrix = new float[16];
    private float[] modelMatrix = new float[16];
    private float[] uiProjectionMatrix = new float[16];
    private float[] uiModelMatrix = new float[16];
    private float[] uiMVPMatrix = new float[16];

    private static final float CAM_Y = 1.0f;
    private static final float EYE_OFFSET = 0.05f; 

    private float visualVelocity = 0f;
    private float totalVisualDistance = 0f;
    private float visualYaw = 0f;

    private float transitionAlpha = 0f;
    private boolean isTransitioning = false;
    private long transitionStartTime = 0;
    private float transitionFOVBurst = 0f;

    private long startTimeMs = 0;
    private long lastFrameMs = 0;
    private float elapsedSec = 0f;

    private float gazeForwardX, gazeForwardY, gazeForwardZ;
    private int width, height;

    private static final float[][] SCENE_TINTS = {
            { 1.00f, 0.85f, 0.65f }, { 0.85f, 0.85f, 0.90f }, { 1.00f, 0.65f, 0.45f },
            { 0.40f, 0.80f, 1.00f }, { 0.80f, 0.60f, 0.40f }, { 0.60f, 0.90f, 1.00f },
            { 0.80f, 1.00f, 0.80f }
    };

    private static final int[] INTERIOR_RES_IDS = {
            R.drawable.interior_ajanta, R.drawable.interior_ellora, R.drawable.interior_badami,
            R.drawable.interior_waitomo, R.drawable.interior_lascaux, R.drawable.interior_vatnajokull,
            R.drawable.interior_sondoong
    };

    private static final int[][] INSIGHT_RES_IDS = {
            { R.drawable.insight_ajanta_1, R.drawable.insight_ajanta_2, R.drawable.insight_ajanta_3 },
            { R.drawable.insight_ellora_1, R.drawable.insight_ellora_2, R.drawable.insight_ellora_3 },
            { R.drawable.insight_badami_1, R.drawable.insight_badami_2, R.drawable.insight_badami_3 },
            { R.drawable.insight_waitomo_1, R.drawable.insight_waitomo_2, R.drawable.insight_waitomo_3 },
            { R.drawable.insight_lascaux_1, R.drawable.insight_lascaux_2, R.drawable.insight_lascaux_3 },
            { R.drawable.insight_vatnajokull_1, R.drawable.insight_vatnajokull_2, R.drawable.insight_vatnajokull_3 },
            { R.drawable.insight_sondoong_1, R.drawable.insight_sondoong_2, R.drawable.insight_sondoong_3 }
    };

    private int[] caveInteriorTextures = new int[7];
    private int[][] caveInsightTextures = new int[7][3];
    private int bgLandingTexture;
    private int infoIconTexture;
    private int particleTexture;

    private float[][] currentHotspotPositions = new float[3][3];
    private int activeInsightIdx = -1;
    private long insightStartTime = 0;
    private float tintR = 1.00f, tintG = 0.95f, tintB = 0.85f;

    private static final String VERT_SRC = "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "attribute vec2 aTexCoordinate;" +
            "varying vec2 vTexCoordinate;" +
            "varying vec3 vWorldPos;" +
            "void main() {" +
            "  gl_Position = uMVPMatrix * vPosition;" +
            "  vTexCoordinate = aTexCoordinate;" +
            "  vWorldPos = vPosition.xyz;" +
            "}";

    private static final String FRAG_SRC = "precision mediump float;" +
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
            "  float vig = 1.0 - smoothstep(0.2, 0.8, length(vTexCoordinate - 0.5));" +
            "  vec3 fragDir = normalize(vWorldPos);" +
            "  float spotlight = pow(max(dot(fragDir, uGazeDir), 0.0), 6.0) * 0.85 + 0.15;" +
            "  vec3 finalColor = tinted * vig * spotlight;" +
            "  finalColor += uGlow * vec3(1.0, 0.9, 0.6);" +
            "  gl_FragColor = vec4(finalColor, texColor.a * uAlphaMultiplier);" +
            "}";

    private static final String CARD_VERT = "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "attribute vec2 aTexCoordinate;" +
            "varying vec2 vTexCoord;" +
            "void main() {" +
            "  gl_Position = uMVPMatrix * vPosition;" +
            "  vTexCoord = aTexCoordinate;" +
            "}";

    private static final String CARD_FRAG = 
        "precision mediump float;" +
        "uniform vec4 uCardColor;" +
        "uniform float uGlow;" +
        "uniform float uTime;" +
        "uniform sampler2D uTexture;" +
        "uniform float uUseTexture;" +
        "varying vec2 vTexCoord;" +
        "void main() {" +
        "  vec2 uv = vTexCoord;" +
        "  float bx = smoothstep(0.0, 0.05, uv.x) * smoothstep(1.0, 0.95, uv.x);" +
        "  float by = smoothstep(0.0, 0.07, uv.y) * smoothstep(1.0, 0.93, uv.y);" +
        "  float mask = bx * by;" +
        "  vec4 texColor = texture2D(uTexture, uv);" +
        "  vec3 glassCol = mix(uCardColor.rgb * 0.8, texColor.rgb, uUseTexture);" +
        "  float rim = 1.0 - mask;" +
        "  vec3 rimCol = mix(vec3(1.0), vec3(1.0, 0.8, 0.4), uGlow);" +
        "  vec3 finalCol = glassCol + rim * 0.4 * rimCol;" +
        "  finalCol += uGlow * 0.3 * vec3(1.0, 0.9, 0.7);" +
        "  float alpha = mix(0.7, 0.95, mask);" +
        "  if (uUseTexture > 0.5) alpha = texColor.a * (0.8 + 0.2 * mask);" +
        "  gl_FragColor = vec4(finalCol, alpha);" +
        "}";

    private static final String GRID_VERT = "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "void main() {" +
            "  gl_Position = uMVPMatrix * vPosition;" +
            "}";
    private static final String GRID_FRAG = "precision mediump float;" +
            "void main() {" +
            "  gl_FragColor = vec4(0.5, 0.5, 0.5, 0.2);" +
            "}";
    private static final float[][] CARD_COLORS = {
            { 0.95f, 0.82f, 0.55f }, { 0.55f, 0.82f, 0.95f }, { 0.75f, 0.95f, 0.65f }, { 0.95f, 0.65f, 0.65f },
            { 0.80f, 0.65f, 0.95f }, { 0.65f, 0.95f, 0.90f }, { 0.95f, 0.90f, 0.55f }, { 0.65f, 0.75f, 0.95f },
    };

    private static final String INFO_VERT = "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "attribute vec2 aTexCoordinate;" +
            "varying vec2 vTexCoord;" +
            "void main() {" +
            "  gl_Position = uMVPMatrix * vPosition;" +
            "  vTexCoord = aTexCoordinate;" +
            "}";

    private static final String INFO_FRAG = "precision mediump float;" +
            "uniform sampler2D uTexture;" +
            "uniform float uAlphaMultiplier;" +
            "uniform float uTime;" +
            "varying vec2 vTexCoord;" +
            "void main() {" +
            "  vec4 texColor = texture2D(uTexture, vTexCoord);" +
            "  float pulse = 0.8 + 0.2 * sin(uTime * 4.0);" +
            "  vec3 color = texColor.rgb * pulse;" +
            "  float luma = dot(texColor.rgb, vec3(0.299, 0.587, 0.114));" +
            "  float alpha = smoothstep(0.05, 0.3, luma);" +
            "  float dist = distance(vTexCoord, vec2(0.5, 0.5));" +
            "  alpha *= smoothstep(0.5, 0.45, dist);" +
            "  gl_FragColor = vec4(color, alpha * uAlphaMultiplier);" +
            "}";

    private static final String TEXTURE_VERT = "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "attribute vec2 aTexCoordinate;" +
            "varying vec2 vTexCoord;" +
            "void main() {" +
            "  gl_Position = uMVPMatrix * vPosition;" +
            "  vTexCoord = aTexCoordinate;" +
            "}";

    private static final String TEXTURE_FRAG = "precision mediump float;" +
            "uniform sampler2D uTexture;" +
            "uniform float uAlphaMultiplier;" +
            "varying vec2 vTexCoord;" +
            "void main() {" +
            "  vec2 uv = vTexCoord;" +
            "  float dist = length(max(abs(uv - 0.5) - 0.4, 0.0));" +
            "  float alphaMask = smoothstep(0.1, 0.08, dist);" +
            "  vec4 texColor = texture2D(uTexture, uv);" +
            "  gl_FragColor = vec4(texColor.rgb, texColor.a * uAlphaMultiplier * alphaMask);" +
            "}";

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
        this.context = context;
        this.sensorHandler = sensorHandler;
        this.interactionManager = interactionManager;
    }

    public void setState(State state) {
        this.currentState = state;
        if (state == State.LANDING) {
            visualVelocity = 0f;
            visualYaw = 0f;
            totalVisualDistance = 0f;
            resetInfoButtons();
            highlightedCardIndex = -1;
        }
    }

    public void setCaveScene(int index) {
        currentCaveIndex = index;
        isTransitioning = true;
        transitionStartTime = System.currentTimeMillis();
        transitionFOVBurst = 45f;
        resetInfoButtons();
        activeInsightIdx = -1;
        updateParticleSystem();
    }

    private void updateParticleSystem() {
        if (particleSystem == null) return;
        float[] color = {1f, 1f, 1f};
        ParticleSystem.Type type = ParticleSystem.Type.DUST;
        
        switch(currentCaveIndex) {
            case 0: color = new float[]{1.0f, 0.8f, 0.5f}; break; // Ajanta
            case 1: color = new float[]{0.9f, 0.9f, 0.9f}; break; // Ellora
            case 2: color = new float[]{1.0f, 0.7f, 0.4f}; break; // Badami
            case 3: color = new float[]{0.4f, 0.8f, 1.0f}; type = ParticleSystem.Type.GLOW; break; // Waitomo
            case 4: color = new float[]{0.8f, 0.7f, 0.6f}; break; // Lascaux
            case 5: color = new float[]{0.9f, 0.95f, 1.0f}; type = ParticleSystem.Type.SNOW; break; // Vatnajokull
            case 6: color = new float[]{0.6f, 1.0f, 0.6f}; type = ParticleSystem.Type.GLOW; break; // Son Doong
        }
        particleSystem.setConfig(type, color, particleTexture);
    }

    public void setHotspotPositions(float[][] pos) {
        for (int i = 0; i < 3; i++) {
            currentHotspotPositions[i] = pos[i];
            infoButtons[i].x = pos[i][0];
            infoButtons[i].y = pos[i][1];
            infoButtons[i].z = pos[i][2];
        }
    }

    public void showInsightPanel(int index) {
        activeInsightIdx = index;
        insightStartTime = System.currentTimeMillis();
    }

    public void highlightCard(int index, boolean highlight) {
        if (highlight) {
            highlightedCardIndex = index;
        } else if (highlightedCardIndex == index) {
            highlightedCardIndex = -1;
        }
    }

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
            infoButtons[i].x = (float) (Math.sin(yaw) * Math.cos(pitch)) * r;
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

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        startTimeMs = System.currentTimeMillis();
        lastFrameMs = startTimeMs;

        int[] caveCardResIds = {
            R.drawable.cave_ajanta, R.drawable.cave_ellora, R.drawable.cave_badami,
            R.drawable.cave_waitomo, R.drawable.cave_lascaux, R.drawable.cave_vatnajokull,
            R.drawable.cave_sondoong
        };

        int placeholder = TextureHelper.loadTexture(context, R.drawable.cave_entering);
        for (int i = 0; i < caveCount; i++) {
            caveInteriorTextures[i] = TextureHelper.loadTexture(context, INTERIOR_RES_IDS[i]);
            if (i < caveCardResIds.length) {
                caveCardTextures[i] = TextureHelper.loadTexture(context, caveCardResIds[i]);
            } else {
                caveCardTextures[i] = placeholder;
            }
            for (int j = 0; j < 3; j++) {
                caveInsightTextures[i][j] = TextureHelper.loadTexture(context, INSIGHT_RES_IDS[i][j]);
            }
            if (caveNames != null && i < caveNames.length) {
                caveLabelTextures[i] = com.example.vrdesert.shapes.TextTextureHelper.createTextTexture(caveNames[i]);
            }
        }

        for (int i = 0; i < caveCount; i++) {
            caveCards[i] = new com.example.vrdesert.shapes.ImageBillboard();
            labelCards[i] = new com.example.vrdesert.shapes.ImageBillboard();
        }

        caveSphere = new Sphere(50f, 48, 96);
        tunnelShaderProgram = buildProgram(VERT_SRC, FRAG_SRC);
        cardShaderProgram = buildProgram(CARD_VERT, CARD_FRAG);
        gridProgram = buildProgram(GRID_VERT, GRID_FRAG);
        textureProgram = buildProgram(TEXTURE_VERT, TEXTURE_FRAG);

        bgLandingTexture = TextureHelper.loadTexture(context, R.drawable.bg_landing);
        infoIconTexture = TextureHelper.loadTexture(context, R.drawable.ui_info_icon);
        particleTexture = TextureHelper.loadTexture(context, R.drawable.particle_dot);

        infoButtonSphere = new Sphere(0.5f, 16, 16);
        infoButtonProgram = buildProgram(INFO_VERT, INFO_FRAG);
        infoButtons = new GameObject[] {
                new GameObject(0, 0, 0, GameObject.Type.INFO_BUTTON_0),
                new GameObject(0, 0, 0, GameObject.Type.INFO_BUTTON_1),
                new GameObject(0, 0, 0, GameObject.Type.INFO_BUTTON_2)
        };
        positionInfoButtons();

        particleSystem = new ParticleSystem();
        particleSystem.initGL();
        updateParticleSystem(); // Initial config

        breathFog = new BreathFog();
        breathFog.initGL();
        progressCircle = new com.example.vrdesert.shapes.ProgressCircle();
        crosshair = new Crosshair();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        this.width = width;
        this.height = height;
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) (width / 2) / height;
        Matrix.perspectiveM(leftProjectionMatrix, 0, 75f, ratio, 0.1f, 200f);
        Matrix.perspectiveM(rightProjectionMatrix, 0, 75f, ratio, 0.1f, 200f);
        Matrix.orthoM(uiProjectionMatrix, 0, 0, width / 2f, height, 0, -1, 1);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        long now = System.currentTimeMillis();
        float dt = (now - lastFrameMs) / 1000f;
        dt = Math.min(dt, 0.1f);
        lastFrameMs = now;
        elapsedSec = (now - startTimeMs) / 1000f;

        totalVisualDistance += visualVelocity * 0.05f;
        visualVelocity *= 0.92f;
        if (visualVelocity < 0.01f) visualVelocity = 0f;

        // Update transition logic
        if (isTransitioning) {
            long transElapsed = System.currentTimeMillis() - transitionStartTime;
            if (transElapsed < 800) { // Fade out + swap
                transitionAlpha = Math.min(1.0f, transElapsed / 400f);
                if (transElapsed > 400 && currentState == State.LANDING) {
                    currentState = State.INSIDE;
                    visualYaw = (float) Math.toRadians(sensorHandler.getYaw());
                }
            } else if (transElapsed < 1600) { // Fade in
                transitionAlpha = Math.max(0.0f, 1.0f - (transElapsed - 800) / 800f);
            } else {
                isTransitioning = false;
                transitionAlpha = 0f;
            }
        }

        if (transitionFOVBurst > 0f) {
            transitionFOVBurst -= dt * 30f;
            if (transitionFOVBurst < 0f) transitionFOVBurst = 0f;
        }

        float[] target = (currentCaveIndex != -1) ? SCENE_TINTS[currentCaveIndex] : SCENE_TINTS[0];
        tintR += (target[0] - tintR) * dt * 2f;
        tintG += (target[1] - tintG) * dt * 2f;
        tintB += (target[2] - tintB) * dt * 2f;

        for (int i = 0; i < caveCount; i++) {
            float tScale = (i == highlightedCardIndex) ? 1.2f : 1.0f;
            cardScales[i] += (tScale - cardScales[i]) * dt * 8f;
        }

        if (particleSystem != null) particleSystem.update(dt);

        if (transitionFOVBurst > 0.1f) {
            float ratio = (float) (width / 2) / height;
            float fov = 75f + transitionFOVBurst;
            Matrix.perspectiveM(leftProjectionMatrix, 0, fov, ratio, 0.1f, 200f);
            Matrix.perspectiveM(rightProjectionMatrix, 0, fov, ratio, 0.1f, 200f);
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        float pitchRad = (float) Math.toRadians(sensorHandler.getPitch());
        float yawRad = (float) Math.toRadians(sensorHandler.getYaw());

        gazeForwardX = (float) (-Math.sin(yawRad) * Math.cos(pitchRad));
        gazeForwardY = (float) (Math.sin(-pitchRad));
        gazeForwardZ = (float) (-Math.cos(yawRad) * Math.cos(pitchRad));

        float tX = gazeForwardX, tY = CAM_Y + gazeForwardY, tZ = gazeForwardZ;

        if (currentState == State.LANDING) {
            float cardDist = 14f;
            float angleStep = 360f / caveCount;
            for (int i = 0; i < caveCount; i++) {
                float angle = (float) Math.toRadians(i * angleStep);
                GameObject cardObj = new GameObject((float)Math.sin(angle)*cardDist, 2.6f, (float)-Math.cos(angle)*cardDist, GameObject.Type.INFO_BUTTON_0);
                interactionManager.checkGaze(0, CAM_Y, 0, gazeForwardX, gazeForwardY, gazeForwardZ, cardObj, i);
            }
        }
        if (crosshair != null) crosshair.setTargeting(interactionManager.isTargeting());

        // Left eye
        GLES20.glViewport(0, 0, width / 2, height);
        float cosYaw = (float) Math.cos(yawRad);
        float sinYaw = (float) Math.sin(yawRad);
        float lox = cosYaw * EYE_OFFSET;
        float loz = -sinYaw * EYE_OFFSET;
        Matrix.setLookAtM(viewMatrix, 0, -lox, CAM_Y, -loz, tX - lox, tY, tZ - loz, 0f, 1f, 0f);
        Matrix.multiplyMM(vPMatrix, 0, leftProjectionMatrix, 0, viewMatrix, 0);
        renderFullView(vPMatrix);

        // Right eye
        GLES20.glViewport(width / 2, 0, width / 2, height);
        float rox = -cosYaw * EYE_OFFSET;
        float roz = sinYaw * EYE_OFFSET;
        Matrix.setLookAtM(viewMatrix, 0, -rox, CAM_Y, -roz, tX - rox, tY, tZ - roz, 0f, 1f, 0f);
        Matrix.multiplyMM(vPMatrix, 0, rightProjectionMatrix, 0, viewMatrix, 0);
        renderFullView(vPMatrix);
    }

    private void renderFullView(float[] vpMatrix) {
        if (currentState == State.LANDING) {
            drawLandingMenu(vpMatrix);
        } else {
            drawScene(vpMatrix);
        }
        
        // Draw environmental particles in both states for "alive" feel
        if (particleSystem != null) {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            particleSystem.draw(vpMatrix, elapsedSec);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        }
        
        if (currentState == State.INSIDE && breathFog != null) {
            float intensity = 0.5f + (currentCaveIndex != -1 ? currentCaveIndex : 0) * 0.17f;
            breathFog.draw(elapsedSec, intensity);
        }
        drawUI();
    }

    private void drawLandingMenu(float[] vpMatrix) {
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        
        // Draw background nebula sphere
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glUseProgram(tunnelShaderProgram);
        // Set required uniforms for the tunnel shader - add slow pulsing tint
        float pulse = 0.85f + 0.15f * (float)Math.sin(elapsedSec * 0.5f);
        int tintH = GLES20.glGetUniformLocation(tunnelShaderProgram, "uTint");
        if (tintH != -1) GLES20.glUniform3f(tintH, pulse, pulse * 0.9f, pulse * 1.1f);
        int gazeH = GLES20.glGetUniformLocation(tunnelShaderProgram, "uGazeDir");
        if (gazeH != -1) GLES20.glUniform3f(gazeH, gazeForwardX, gazeForwardY, gazeForwardZ);
        // Sphere is centered at origin — MVP = VP * Identity = VP
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.multiplyMM(scratchMatrix, 0, vpMatrix, 0, modelMatrix, 0);
        caveSphere.draw(tunnelShaderProgram, scratchMatrix, bgLandingTexture, 1.0f, 0f, 0f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        GLES20.glUseProgram(cardShaderProgram);
        int timeHandle = GLES20.glGetUniformLocation(cardShaderProgram, "uTime");
        if (timeHandle != -1) GLES20.glUniform1f(timeHandle, elapsedSec);

        float cardDist = 14f;
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

            Matrix.setIdentityM(modelMatrix, 0);
            float bob = (float)Math.sin(elapsedSec * 1.5f + i) * 0.15f;
            Matrix.translateM(modelMatrix, 0, (float)Math.sin(angleRad)*cardDist, 1.2f + bob, (float)-Math.cos(angleRad)*cardDist);
            Matrix.rotateM(modelMatrix, 0, -angleDegrees, 0, 1, 0);

            System.arraycopy(modelMatrix, 0, scratchMatrix, 0, 16);
            float currentScale = 2.2f * cardScales[i];
            Matrix.scaleM(scratchMatrix, 0, currentScale, 3.5f * cardScales[i], 1.0f);
            Matrix.multiplyMM(uiMVPMatrix, 0, vpMatrix, 0, scratchMatrix, 0);

            int useTexHandle = GLES20.glGetUniformLocation(cardShaderProgram, "uUseTexture");
            if (useTexHandle != -1) GLES20.glUniform1f(useTexHandle, 1.0f); 
            caveCards[i].draw(cardShaderProgram, uiMVPMatrix, caveCardTextures[i], 1.0f, highlighted ? 1.0f : 0.0f);

            // Label
            System.arraycopy(modelMatrix, 0, scratchMatrix, 0, 16);
            Matrix.translateM(scratchMatrix, 0, 0f, 2.2f * cardScales[i], 0.01f);
            Matrix.scaleM(scratchMatrix, 0, 1.8f * cardScales[i], 0.5f * cardScales[i], 1.0f);
            Matrix.multiplyMM(uiMVPMatrix, 0, vpMatrix, 0, scratchMatrix, 0);
            labelCards[i].draw(textureProgram, uiMVPMatrix, caveLabelTextures[i], 1.0f, 0f);
        }
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void drawScene(float[] vpMatrix) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        // Set required uniforms for the tunnel shader
        GLES20.glUseProgram(tunnelShaderProgram);
        int tintH = GLES20.glGetUniformLocation(tunnelShaderProgram, "uTint");
        if (tintH != -1) GLES20.glUniform3f(tintH, tintR, tintG, tintB);
        int gazeH = GLES20.glGetUniformLocation(tunnelShaderProgram, "uGazeDir");
        if (gazeH != -1) GLES20.glUniform3f(gazeH, gazeForwardX, gazeForwardY, gazeForwardZ);
        // Sphere is centered at origin — MVP = VP * Identity
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.multiplyMM(scratchMatrix, 0, vpMatrix, 0, modelMatrix, 0);
        int activeTexture = (currentCaveIndex != -1) ? caveInteriorTextures[currentCaveIndex] : bgLandingTexture;
        caveSphere.draw(tunnelShaderProgram, scratchMatrix, activeTexture, 1.0f, 0f, 0f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glUseProgram(infoButtonProgram);
        int timeHandle = GLES20.glGetUniformLocation(infoButtonProgram, "uTime");
        if (timeHandle != -1) GLES20.glUniform1f(timeHandle, elapsedSec);

        float yawRad = (float) Math.toRadians(sensorHandler.getYaw());

        for (int i = 0; i < infoButtons.length; i++) {
            GameObject obj = infoButtons[i];
            interactionManager.checkGaze(0, CAM_Y, 0, gazeForwardX, gazeForwardY, gazeForwardZ, obj, 100 + i);
            boolean isGazed = interactionManager.getTargetId() == (100 + i);
            
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.translateM(modelMatrix, 0, obj.x, obj.y, obj.z);
            Matrix.rotateM(modelMatrix, 0, (float)Math.toDegrees(yawRad), 0, 1, 0);
            
            float bob = (float) Math.sin(elapsedSec * 2.5f + i) * 0.08f;
            Matrix.translateM(modelMatrix, 0, 0, bob, 0);
            
            float baseScale = isGazed ? 0.35f : 0.25f; // Scale up on gaze
            float pulse = baseScale + 0.04f * (float) Math.sin(elapsedSec * 4.0f);
            Matrix.scaleM(modelMatrix, 0, pulse, pulse, pulse);
            
            Matrix.multiplyMM(scratchMatrix, 0, vpMatrix, 0, modelMatrix, 0);
            float glow = isGazed ? 1.0f : 0.0f; // Increase glow on gaze
            caveCards[0].draw(infoButtonProgram, scratchMatrix, infoIconTexture, 1.0f, glow);
        }

        if (activeInsightIdx != -1) {
            float alpha = Math.min(1.0f, (System.currentTimeMillis() - insightStartTime) / 500f);
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.translateM(modelMatrix, 0, gazeForwardX * 2.5f, CAM_Y + gazeForwardY * 2.5f + 0.1f, gazeForwardZ * 2.5f);
            Matrix.rotateM(modelMatrix, 0, (float)Math.toDegrees(yawRad), 0, 1, 0);
            Matrix.scaleM(modelMatrix, 0, 1.2f, 0.8f, 1.0f);
            Matrix.multiplyMM(scratchMatrix, 0, vpMatrix, 0, modelMatrix, 0);
            caveCards[0].draw(textureProgram, scratchMatrix, caveInsightTextures[currentCaveIndex][activeInsightIdx], alpha, 0f);
        }
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void drawUI() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        float eyeW = width / 2f;
        float centerX = eyeW / 2f;
        float centerY = height / 2f;

        Matrix.setIdentityM(uiModelMatrix, 0);
        Matrix.translateM(uiModelMatrix, 0, centerX, centerY, 0f);
        float cs = (eyeW * 0.05f) / 15f;
        Matrix.scaleM(uiModelMatrix, 0, cs, cs, 1.0f);
        Matrix.multiplyMM(uiMVPMatrix, 0, uiProjectionMatrix, 0, uiModelMatrix, 0);
        crosshair.draw(uiMVPMatrix);

        float progress = interactionManager.getGazeProgress();
        if (progress > 0) {
            Matrix.setIdentityM(uiModelMatrix, 0);
            Matrix.translateM(uiModelMatrix, 0, centerX, centerY, 0f);
            float circleScale = (eyeW * 0.08f);
            Matrix.scaleM(uiModelMatrix, 0, circleScale, circleScale, 1f);
            Matrix.multiplyMM(uiMVPMatrix, 0, uiProjectionMatrix, 0, uiModelMatrix, 0);
            progressCircle.draw(infoButtonProgram, uiMVPMatrix, progress);
        }
        // 3. Draw Fullscreen Fade Overlay
        if (transitionAlpha > 0.01f) {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            
            // Draw a simple black quad across the screen
            // Re-use crosshair or similar logic for full-eye quad
            Matrix.setIdentityM(uiModelMatrix, 0);
            Matrix.scaleM(uiModelMatrix, 0, eyeW, height, 1.0f);
            Matrix.multiplyMM(uiMVPMatrix, 0, uiProjectionMatrix, 0, uiModelMatrix, 0);
            
            // Use tunnel shader with black tint and no texture for fade
            GLES20.glUseProgram(tunnelShaderProgram);
            GLES20.glUniform3f(GLES20.glGetUniformLocation(tunnelShaderProgram, "uTint"), 0f, 0f, 0f);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(tunnelShaderProgram, "uAlphaMultiplier"), transitionAlpha);
            
            // Draw as a billboard covering the eye
            caveCards[0].draw(tunnelShaderProgram, uiMVPMatrix, 0, transitionAlpha, 0f);
            
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        }

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private int buildProgram(String vertSrc, String fragSrc) {
        int vert = loadShader(GLES20.GL_VERTEX_SHADER, vertSrc);
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
}
