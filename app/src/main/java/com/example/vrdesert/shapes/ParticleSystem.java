package com.example.vrdesert.shapes;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

/**
 * GPU-friendly particle system for environmental ambiance.
 * Supports textures, color tints, and different movement types.
 */
public class ParticleSystem {

    public enum Type {
        DUST,      // Floating slow
        SNOW,      // Falling down
        GLOW       // Floating with random flicker (fireflies)
    }

    private static final int MAX_PARTICLES = 150;

    private float[] positions  = new float[MAX_PARTICLES * 3];
    private float[] velocities = new float[MAX_PARTICLES * 3];
    private float[] lifetimes  = new float[MAX_PARTICLES];
    private float[] maxLife    = new float[MAX_PARTICLES];

    private FloatBuffer positionBuffer;
    private int shaderProgram;
    private int textureId = -1;
    private final Random rand = new Random();
    
    private float[] currentColor = {1.0f, 1.0f, 1.0f};
    private Type currentType = Type.DUST;

    private static final String VERT =
        "uniform mat4 uMVPMatrix;" +
        "uniform float uPointSize;" +
        "attribute vec4 vPosition;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * vPosition;" +
        "  gl_PointSize = uPointSize / gl_Position.w;" +
        "}";

    private static final String FRAG =
        "precision mediump float;" +
        "uniform sampler2D uTexture;" +
        "uniform vec3 uColor;" +
        "uniform float uTime;" +
        "uniform float uType;" + // 0=Dust, 1=Snow, 2=Glow
        "void main() {" +
        "  vec4 texColor = texture2D(uTexture, gl_PointCoord);" +
        "  if (texColor.a < 0.1) discard;" +
        "  float twinkle = 1.0;" +
        "  if (uType > 1.5) {" + // Glow type (Fireflies)
        "    twinkle = 0.5 + 0.5 * sin(uTime * 3.0 + gl_PointCoord.x * 20.0);" +
        "  } else {" +
        "    twinkle = 0.8 + 0.2 * sin(uTime * 2.0);" +
        "  }" +
        "  gl_FragColor = vec4(uColor * twinkle, texColor.a * 0.7);" +
        "}";

    public ParticleSystem() {
        ByteBuffer bb = ByteBuffer.allocateDirect(MAX_PARTICLES * 3 * 4);
        bb.order(ByteOrder.nativeOrder());
        positionBuffer = bb.asFloatBuffer();

        for (int i = 0; i < MAX_PARTICLES; i++) {
            respawn(i);
            lifetimes[i] = rand.nextFloat() * maxLife[i];
        }
    }

    public void setConfig(Type type, float[] color, int textureId) {
        this.currentType = type;
        this.currentColor = color;
        this.textureId = textureId;
    }

    public void initGL() {
        int vert = loadShader(GLES20.GL_VERTEX_SHADER, VERT);
        int frag = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAG);
        shaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(shaderProgram, vert);
        GLES20.glAttachShader(shaderProgram, frag);
        GLES20.glLinkProgram(shaderProgram);
    }

    public void update(float dt) {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            lifetimes[i] += dt;
            if (lifetimes[i] >= maxLife[i]) {
                respawn(i);
                continue;
            }

            int idx = i * 3;
            positions[idx]     += velocities[idx]     * dt;
            positions[idx + 1] += velocities[idx + 1] * dt;
            positions[idx + 2] += velocities[idx + 2] * dt;

            // Drift logic
            if (currentType == Type.SNOW) {
                positions[idx] += (float) Math.sin(lifetimes[i] + i) * 0.01f;
            } else {
                positions[idx] += (float) Math.sin(lifetimes[i] * 0.5f + i) * 0.005f;
                positions[idx + 1] += (float) Math.cos(lifetimes[i] * 0.3f + i) * 0.003f;
            }
        }
    }

    private void respawn(int i) {
        int idx = i * 3;
        float angle = rand.nextFloat() * (float)(Math.PI * 2);
        float r = 1f + rand.nextFloat() * 8f;
        float h = -3f + rand.nextFloat() * 8f;

        positions[idx]     = (float) Math.cos(angle) * r;
        positions[idx + 1] = h;
        positions[idx + 2] = (float) Math.sin(angle) * r;

        if (currentType == Type.SNOW) {
            velocities[idx]     = (rand.nextFloat() - 0.5f) * 0.1f;
            velocities[idx + 1] = -0.3f - rand.nextFloat() * 0.5f; // Falling down
            velocities[idx + 2] = (rand.nextFloat() - 0.5f) * 0.1f;
        } else {
            velocities[idx]     = (rand.nextFloat() - 0.5f) * 0.05f;
            velocities[idx + 1] = (rand.nextFloat() - 0.5f) * 0.05f; // Floating
            velocities[idx + 2] = (rand.nextFloat() - 0.5f) * 0.05f;
        }

        lifetimes[i] = 0f;
        maxLife[i]   = 4f + rand.nextFloat() * 10f;
    }

    public void draw(float[] mvpMatrix, float time) {
        if (shaderProgram == 0 || textureId == -1) return;

        positionBuffer.clear();
        positionBuffer.put(positions);
        positionBuffer.position(0);

        GLES20.glUseProgram(shaderProgram);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE); // Additive for glow
        GLES20.glDepthMask(false);

        int posHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition");
        GLES20.glEnableVertexAttribArray(posHandle);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 12, positionBuffer);

        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix"), 1, false, mvpMatrix, 0);
        GLES20.glUniform3f(GLES20.glGetUniformLocation(shaderProgram, "uColor"), currentColor[0], currentColor[1], currentColor[2]);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(shaderProgram, "uTime"), time);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(shaderProgram, "uType"), (float)currentType.ordinal());
        GLES20.glUniform1f(GLES20.glGetUniformLocation(shaderProgram, "uPointSize"), currentType == Type.GLOW ? 35f : 15f);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(shaderProgram, "uTexture"), 0);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, MAX_PARTICLES);

        GLES20.glDisableVertexAttribArray(posHandle);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private int loadShader(int type, String src) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
