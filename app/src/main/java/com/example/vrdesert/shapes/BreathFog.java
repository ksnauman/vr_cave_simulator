package com.example.vrdesert.shapes;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Full-screen breath fog effect — animated icy wisps at the bottom of the screen.
 * Intensity increases as the user goes deeper into the cave.
 */
public class BreathFog {

    private FloatBuffer vertexBuffer;
    private FloatBuffer texCoordBuffer;
    private int shaderProgram;

    private static final String VERT =
        "attribute vec4 vPosition;" +
        "attribute vec2 aTexCoord;" +
        "varying vec2 vTexCoord;" +
        "void main() {" +
        "  gl_Position = vPosition;" +
        "  vTexCoord = aTexCoord;" +
        "}";

    private static final String FRAG =
        "precision mediump float;" +
        "varying vec2 vTexCoord;" +
        "uniform float uTime;" +
        "uniform float uIntensity;" +
        "void main() {" +
        // Strong gradient from bottom
        "  float gradient = 1.0 - vTexCoord.y;" +
        "  gradient = gradient * gradient;" +
        // Animated wisps
        "  float wisp1 = sin(vTexCoord.x * 6.0 + uTime * 0.5) * 0.5 + 0.5;" +
        "  float wisp2 = sin(vTexCoord.x * 10.0 - uTime * 0.8 + 1.5) * 0.5 + 0.5;" +
        "  float wisp3 = sin(vTexCoord.x * 3.0 + uTime * 0.3 + 3.0) * 0.5 + 0.5;" +
        "  float wisps = (wisp1 + wisp2 + wisp3) / 3.0;" +
        // Breathing rhythm
        "  float breath = 0.5 + 0.5 * sin(uTime * 0.6);" +
        // Combine — much stronger alpha
        "  float alpha = gradient * wisps * (0.4 + 0.6 * breath) * uIntensity * 0.7;" +
        // Icy white-blue fog
        "  vec3 fogColor = vec3(0.8, 0.9, 1.0);" +
        "  gl_FragColor = vec4(fogColor, alpha);" +
        "}";

    // Quad covering bottom 40% of screen in NDC
    private static final float[] VERTICES = {
        -1f, -1f, 0f,
         1f, -1f, 0f,
        -1f, -0.2f, 0f,
         1f, -0.2f, 0f
    };

    private static final float[] TEX_COORDS = {
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 1f
    };

    public BreathFog() {
        ByteBuffer vb = ByteBuffer.allocateDirect(VERTICES.length * 4);
        vb.order(ByteOrder.nativeOrder());
        vertexBuffer = vb.asFloatBuffer();
        vertexBuffer.put(VERTICES);
        vertexBuffer.position(0);

        ByteBuffer tb = ByteBuffer.allocateDirect(TEX_COORDS.length * 4);
        tb.order(ByteOrder.nativeOrder());
        texCoordBuffer = tb.asFloatBuffer();
        texCoordBuffer.put(TEX_COORDS);
        texCoordBuffer.position(0);
    }

    public void initGL() {
        int vert = loadShader(GLES20.GL_VERTEX_SHADER, VERT);
        int frag = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAG);
        shaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(shaderProgram, vert);
        GLES20.glAttachShader(shaderProgram, frag);
        GLES20.glLinkProgram(shaderProgram);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            android.util.Log.e("BreathFog", "Shader link failed: " + GLES20.glGetProgramInfoLog(shaderProgram));
            shaderProgram = 0;
        }
    }

    public void draw(float time, float intensity) {
        if (shaderProgram == 0) return;

        GLES20.glUseProgram(shaderProgram);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        int posHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition");
        GLES20.glEnableVertexAttribArray(posHandle);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);

        int texHandle = GLES20.glGetAttribLocation(shaderProgram, "aTexCoord");
        GLES20.glEnableVertexAttribArray(texHandle);
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer);

        int timeHandle = GLES20.glGetUniformLocation(shaderProgram, "uTime");
        GLES20.glUniform1f(timeHandle, time);

        int intensityHandle = GLES20.glGetUniformLocation(shaderProgram, "uIntensity");
        GLES20.glUniform1f(intensityHandle, intensity);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(posHandle);
        GLES20.glDisableVertexAttribArray(texHandle);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private int loadShader(int type, String src) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            android.util.Log.e("BreathFog", "Shader compile failed: " + GLES20.glGetShaderInfoLog(shader));
        }
        return shader;
    }
}
