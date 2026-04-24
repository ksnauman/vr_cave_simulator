package com.example.vrdesert.shapes;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

/**
 * GPU-friendly particle system for snow, dust, and ice sparkles.
 * Renders as GL_POINTS with fixed size and alpha fade.
 */
public class ParticleSystem {

    private static final int MAX_PARTICLES = 200;

    private float[] positions  = new float[MAX_PARTICLES * 3];
    private float[] velocities = new float[MAX_PARTICLES * 3];
    private float[] lifetimes  = new float[MAX_PARTICLES];
    private float[] maxLife    = new float[MAX_PARTICLES];

    private FloatBuffer positionBuffer;
    private int shaderProgram;
    private final Random rand = new Random();

    // ── Shaders ──────────────────────────────────────────────────────────
    private static final String VERT =
        "uniform mat4 uMVPMatrix;" +
        "uniform float uPointSize;" +
        "attribute vec4 vPosition;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * vPosition;" +
        // Size attenuation: bigger when closer
        "  gl_PointSize = uPointSize / gl_Position.w;" +
        "}";

    private static final String FRAG =
        "precision mediump float;" +
        "uniform float uTime;" +
        "void main() {" +
        // Soft circular point
        "  vec2 coord = gl_PointCoord - vec2(0.5);" +
        "  float dist = length(coord);" +
        "  if (dist > 0.5) discard;" +
        "  float alpha = smoothstep(0.5, 0.1, dist);" +
        // Icy sparkle: blue-white
        "  vec3 color = vec3(0.75, 0.88, 1.0);" +
        // Twinkle
        "  float twinkle = 0.6 + 0.4 * sin(uTime * 5.0 + gl_PointCoord.x * 15.0 + gl_PointCoord.y * 10.0);" +
        "  gl_FragColor = vec4(color * twinkle, alpha * 0.7);" +
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

    public void initGL() {
        int vert = loadShader(GLES20.GL_VERTEX_SHADER, VERT);
        int frag = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAG);
        shaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(shaderProgram, vert);
        GLES20.glAttachShader(shaderProgram, frag);
        GLES20.glLinkProgram(shaderProgram);

        // Verify link status
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            android.util.Log.e("ParticleSystem", "Shader link failed: " + GLES20.glGetProgramInfoLog(shaderProgram));
            shaderProgram = 0;
        }
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

            // Gentle lateral drift
            positions[idx] += (float) Math.sin(lifetimes[i] * 2f + i) * 0.003f;
        }
    }

    private void respawn(int i) {
        int idx = i * 3;
        float angle = rand.nextFloat() * (float)(Math.PI * 2);
        float r = 2f + rand.nextFloat() * 10f;
        float h = -2f + rand.nextFloat() * 6f;

        positions[idx]     = (float) Math.cos(angle) * r;
        positions[idx + 1] = h;
        positions[idx + 2] = (float) Math.sin(angle) * r;

        velocities[idx]     = (rand.nextFloat() - 0.5f) * 0.2f;
        velocities[idx + 1] = -0.15f - rand.nextFloat() * 0.4f;
        velocities[idx + 2] = (rand.nextFloat() - 0.5f) * 0.2f;

        lifetimes[i] = 0f;
        maxLife[i]   = 5f + rand.nextFloat() * 8f;
    }

    public void draw(float[] mvpMatrix, float time) {
        if (shaderProgram == 0) return;

        positionBuffer.clear();
        positionBuffer.put(positions);
        positionBuffer.position(0);

        GLES20.glUseProgram(shaderProgram);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glDepthMask(false);

        int posHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition");
        GLES20.glEnableVertexAttribArray(posHandle);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 12, positionBuffer);

        int mvpHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);

        int timeHandle = GLES20.glGetUniformLocation(shaderProgram, "uTime");
        GLES20.glUniform1f(timeHandle, time);

        // Fixed point size — large enough to be clearly visible
        int sizeHandle = GLES20.glGetUniformLocation(shaderProgram, "uPointSize");
        GLES20.glUniform1f(sizeHandle, 25.0f);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, MAX_PARTICLES);

        GLES20.glDisableVertexAttribArray(posHandle);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private int loadShader(int type, String src) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);
        // Check compile status
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            android.util.Log.e("ParticleSystem", "Shader compile failed: " + GLES20.glGetShaderInfoLog(shader));
        }
        return shader;
    }
}
