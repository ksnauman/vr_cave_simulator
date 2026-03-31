package com.example.vrdesert;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import java.util.Random;
import android.content.Context;
import com.example.vrdesert.shapes.CaveRoom;
import com.example.vrdesert.shapes.Cube;
import com.example.vrdesert.shapes.TextureHelper;
import com.example.vrdesert.shapes.ImageBillboard;
import com.example.vrdesert.shapes.Crosshair;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class VRRenderer implements GLSurfaceView.Renderer {

    private final SensorHandler sensorHandler;
    private final InteractionManager interactionManager;

    private Context context;
    private ImageBillboard tunnelBillboard;
    private CaveRoom caveRoom;
    private int caveTextureId;
    private int tunnelShaderProgram;
    private int mushroomTexId;
    private int plantTexId;
    private int fungusTexId;

    // View & Projection
    private float[] leftProjectionMatrix = new     float[16];
    private float[] rightProjectionMatrix = new float[16];
    private float[] viewMatrix = new float[16];
    private float[] vPMatrix = new float[16];
    private float[] scratchMatrix = new float[16];
    private float[] modelMatrix = new float[16];

    // Camera state
    private float camX = 0f;
    private float camY = 1.0f; 
    private float camZ = 0f;
    
    // Static system: camera doesn't move, only visual velocity changes
    private float visualVelocity = 0f;
    private float totalVisualDistance = 0f;
    private float visualYaw = 0f;
    
    // IPD for stereoscopic effect
    private static final float EYE_OFFSET = 0.05f;

    // Objects in Scene (Spawns 15 randomized items)
    private GameObject[] objects = new GameObject[15];

    // UI Elements
    private Crosshair crosshair;
    private float[] uiProjectionMatrix = new float[16];
    private float[] uiModelMatrix = new float[16];
    private float[] uiMVPMatrix = new float[16];

    private int width, height;



    private final String tunnelVertexShaderCode =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 vPosition;" +
        "attribute vec2 aTexCoordinate;" +
        "varying vec2 vTexCoordinate;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * vPosition;" +
        "  vTexCoordinate = aTexCoordinate;" +
        "}";

    private final String tunnelFragmentShaderCode =
        "precision mediump float;" +
        "uniform sampler2D uTexture;" +
        "uniform float uAlphaMultiplier;" +
        "uniform float uOffset;" +
        "varying vec2 vTexCoordinate;" +
        "void main() {" +
        "  vec2 scrolledTexCoord = vec2(vTexCoordinate.x, vTexCoordinate.y + uOffset);" +
        "  vec4 texColor = texture2D(uTexture, scrolledTexCoord);" +
        "  if (texColor.a < 0.1) discard;" +
        "  gl_FragColor = texColor * vec4(1.0, 1.0, 1.0, uAlphaMultiplier);" +
        "}";

    public VRRenderer(Context context, SensorHandler sensorHandler, InteractionManager interactionManager) {
        this.context = context;
        this.sensorHandler = sensorHandler;
        this.interactionManager = interactionManager;
    }

    public void moveForward() {
        // Record direction of burst
        visualYaw = (float) Math.toRadians(sensorHandler.getYaw());
        // Boost visual velocity!
        visualVelocity += 2.0f; 
    }

    private int loadShader(int type, String shaderCode){
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // Cavern Ambient Color: Warm Earthy Gray/Brown
        GLES20.glClearColor(0.12f, 0.10f, 0.08f, 1.0f);
        GLES20.glDisable(GLES20.GL_CULL_FACE); // Show both faces of cave walls
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // Procedural brownish stone texture — no image asset required
        caveTextureId = TextureHelper.generateCaveStoneTexture();
        caveRoom = new CaveRoom();

        tunnelBillboard = new ImageBillboard();
        
        int tVertexShader = loadShader(GLES20.GL_VERTEX_SHADER, tunnelVertexShaderCode);
        int tFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, tunnelFragmentShaderCode);
        tunnelShaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(tunnelShaderProgram, tVertexShader);
        GLES20.glAttachShader(tunnelShaderProgram, tFragmentShader);
        GLES20.glLinkProgram(tunnelShaderProgram);

        crosshair = new Crosshair();
        
        mushroomTexId = TextureHelper.generateEmojiTexture("🍄");
        plantTexId = TextureHelper.generateEmojiTexture("🌿");
        fungusTexId = TextureHelper.generateEmojiTexture("☠️");

        // Scatter random objects around the cave immediately
        Random rand = new Random();
        GameObject.Type[] types = GameObject.Type.values();
        for (int i = 0; i < objects.length; i++) {
            float rx = (rand.nextFloat() * 40f) - 20f;
            float rz = (rand.nextFloat() * 40f) - 20f;
            
            // Prevent spawning directly on the origin camera (inside the player)
            if (Math.abs(rx) < 2f) rx += 2f;
            if (Math.abs(rz) < 2f) rz += 2f;
            
            GameObject.Type t = types[rand.nextInt(types.length)];
            objects[i] = new GameObject(rx, 0f, rz, t);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        this.width = width;
        this.height = height;

        GLES20.glViewport(0, 0, width, height);

        // Aspect ratio for half the screen
        float ratio = (float) (width / 2) / height;
        Matrix.perspectiveM(leftProjectionMatrix, 0, 75f, ratio, 0.1f, 100f);
        Matrix.perspectiveM(rightProjectionMatrix, 0, 75f, ratio, 0.1f, 100f);
        
        // UI orthographic projection (Origin at top-left 0,0) mapped accurately to pixel densities
        Matrix.orthoM(uiProjectionMatrix, 0, 0, width / 2, height, 0, -1, 1);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        // Integrate visual velocity for scrolling
        totalVisualDistance += visualVelocity * 0.05f;
        // Decay velocity for smooth stop
        visualVelocity *= 0.92f;
        if (visualVelocity < 0.01f) visualVelocity = 0f;
        
        // camX / camZ remain at 0 per "static camera" instruction
        camX = 0f;
        camZ = 0f;
        
        // Pass Gaze targeting boolean direct to internal crosshair rendering!
        if (crosshair != null) {
            crosshair.setTargeting(interactionManager.isTargeting());
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        float pitchInfo = sensorHandler.getPitch();
        float yawInfo = sensorHandler.getYaw();

        // Convert to radians to compute look vectors
        float yawRad = (float) Math.toRadians(yawInfo);
        float pitchRad = (float) Math.toRadians(pitchInfo);

        float forwardX = (float) (-Math.sin(yawRad) * Math.cos(pitchRad));
        float forwardY = (float) Math.sin(-pitchRad);
        float forwardZ = (float) (-Math.cos(yawRad) * Math.cos(pitchRad));

        float targetX = camX + forwardX;
        float targetY = camY + forwardY;
        float targetZ = camZ + forwardZ;

        // Up vector (assuming purely vertical Y-up most of the time is safe enough given limits)
        float upX = 0f;
        float upY = 1f;
        float upZ = 0f;

        // Draw Left Eye
        GLES20.glViewport(0, 0, width / 2, height);
        float leftOffX =  (float) Math.cos(yawRad) * EYE_OFFSET; 
        float leftOffZ = (float) -Math.sin(yawRad) * EYE_OFFSET; 
        Matrix.setLookAtM(viewMatrix, 0, 
                camX - leftOffX, camY, camZ - leftOffZ, 
                targetX - leftOffX, targetY, targetZ - leftOffZ, 
                upX, upY, upZ);
        Matrix.multiplyMM(vPMatrix, 0, leftProjectionMatrix, 0, viewMatrix, 0);
        drawScene(vPMatrix, forwardX, forwardY, forwardZ);
        drawUI();

        // Draw Right Eye
        GLES20.glViewport(width / 2, 0, width / 2, height);
        float rightOffX = (float) -Math.cos(yawRad) * EYE_OFFSET; 
        float rightOffZ = (float) Math.sin(yawRad) * EYE_OFFSET;
        Matrix.setLookAtM(viewMatrix, 0, 
                camX - rightOffX, camY, camZ - rightOffZ, 
                targetX - rightOffX, targetY, targetZ - rightOffZ, 
                upX, upY, upZ);
        Matrix.multiplyMM(vPMatrix, 0, rightProjectionMatrix, 0, viewMatrix, 0);
        drawScene(vPMatrix, forwardX, forwardY, forwardZ);
        drawUI();
    }

    private void drawUI() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        Matrix.setIdentityM(uiModelMatrix, 0);
        float eyeWidth = width / 2f;
        Matrix.translateM(uiModelMatrix, 0, eyeWidth / 2f, height / 2f, 0f);
        Matrix.multiplyMM(uiMVPMatrix, 0, uiProjectionMatrix, 0, uiModelMatrix, 0);
        crosshair.draw(uiMVPMatrix);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private void drawScene(float[] projectionAndViewMatrix, float fX, float fY, float fZ) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        // Place the cave room centred on the camera
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, camX, camY, camZ);

        // Subtle ambient pulse on Move (scale breathes slightly)
        float scaleMod = 1.0f + ((float) Math.sin(totalVisualDistance * 0.5f) * 0.03f);
        Matrix.scaleM(modelMatrix, 0, scaleMod, scaleMod, scaleMod);

        Matrix.multiplyMM(scratchMatrix, 0, projectionAndViewMatrix, 0, modelMatrix, 0);

        // Draw the 4-wall + floor + ceiling cave enclosure with stone texture
        caveRoom.draw(tunnelShaderProgram, scratchMatrix, caveTextureId);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        for(int i = 0; i < objects.length; i++) {
            if (objects[i].isCollected) continue;

            float moveScale = 0.5f; 
            float dxVisual = (float) Math.sin(visualYaw) * totalVisualDistance * moveScale;
            float dzVisual = (float) Math.cos(visualYaw) * totalVisualDistance * moveScale;

            float ox = objects[i].x + dxVisual;
            float oy = objects[i].y; 
            float oz = objects[i].z + dzVisual;

            if (ox > 15f) objects[i].x -= 30f;
            if (ox < -15f) objects[i].x += 30f;
            if (oz > 15f) objects[i].z -= 30f;
            if (oz < -15f) objects[i].z += 30f;

            // Updated position gaze check
            float originalX = objects[i].x;
            float originalZ = objects[i].z;
            objects[i].x = ox;
            objects[i].z = oz;
            interactionManager.checkGaze(0f, camY, 0f, fX, fY, fZ, objects[i], i);
            objects[i].x = originalX;
            objects[i].z = originalZ;

            float rotY = (float) Math.toDegrees(Math.atan2(ox, oz));

            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.translateM(modelMatrix, 0, ox, oy + 0.5f, oz); 
            Matrix.rotateM(modelMatrix, 0, rotY, 0f, 1f, 0f); 
            
            if (objects[i].type == GameObject.Type.EDIBLE_MUSHROOM) {
                Matrix.scaleM(modelMatrix, 0, 0.5f, 0.5f, 0.5f);
                Matrix.multiplyMM(scratchMatrix, 0, projectionAndViewMatrix, 0, modelMatrix, 0);
                tunnelBillboard.draw(tunnelShaderProgram, scratchMatrix, mushroomTexId, 1.0f);
            } else if (objects[i].type == GameObject.Type.CAVE_PLANT) {
                Matrix.scaleM(modelMatrix, 0, 0.5f, 0.5f, 0.5f);
                Matrix.multiplyMM(scratchMatrix, 0, projectionAndViewMatrix, 0, modelMatrix, 0);
                tunnelBillboard.draw(tunnelShaderProgram, scratchMatrix, plantTexId, 1.0f);
            } else if (objects[i].type == GameObject.Type.TOXIC_FUNGUS) {
                Matrix.scaleM(modelMatrix, 0, 0.5f, 0.5f, 0.5f);
                Matrix.multiplyMM(scratchMatrix, 0, projectionAndViewMatrix, 0, modelMatrix, 0);
                tunnelBillboard.draw(tunnelShaderProgram, scratchMatrix, fungusTexId, 1.0f);
            }
        }
    }
}
