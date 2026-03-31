package com.example.vrdesert.shapes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import android.opengl.GLES20;

public class Plane {

    private final FloatBuffer vertexBuffer;
    private final FloatBuffer colorBuffer;
    
    // Shader program id
    private int mProgram;
    private int mPositionHandle;
    private int mColorHandle;
    private int mVPMatrixHandle;

    static final int COORDS_PER_VERTEX = 3;
    static final int COLORS_PER_VERTEX = 4;
    
    // Define a large floor plane using two triangles stretching from -50 to 50
    static float planeCoords[] = {
            -100.0f, -0.5f, -100.0f, // top left
            -100.0f, -0.5f,  100.0f, // bottom left
             100.0f, -0.5f,  100.0f, // bottom right
            
            -100.0f, -0.5f, -100.0f, // top left
             100.0f, -0.5f,  100.0f, // bottom right
             100.0f, -0.5f, -100.0f  // top right
    };

    // Sand color gradient to avoid single flat tone
    static float colors[] = {
            0.45f, 0.40f, 0.35f, 1.0f, // top left (warm rock)
            0.35f, 0.32f, 0.30f, 1.0f, // bottom left (slate rock)
            0.40f, 0.38f, 0.35f, 1.0f, // bottom right
            
            0.45f, 0.40f, 0.35f, 1.0f, // top left
            0.40f, 0.38f, 0.35f, 1.0f, // bottom right
            0.35f, 0.32f, 0.30f, 1.0f  // top right
    };

    private final int vertexCount = planeCoords.length / COORDS_PER_VERTEX;
    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex
    private final int colorStride = COLORS_PER_VERTEX * 4;

    public Plane(int programId) {
        this.mProgram = programId;

        ByteBuffer bb = ByteBuffer.allocateDirect(planeCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(planeCoords);
        vertexBuffer.position(0);

        ByteBuffer cb = ByteBuffer.allocateDirect(colors.length * 4);
        cb.order(ByteOrder.nativeOrder());
        colorBuffer = cb.asFloatBuffer();
        colorBuffer.put(colors);
        colorBuffer.position(0);
    }

    public void draw(float[] mvpMatrix) {
        GLES20.glUseProgram(mProgram);

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);

        mColorHandle = GLES20.glGetAttribLocation(mProgram, "vColor");
        GLES20.glEnableVertexAttribArray(mColorHandle);
        GLES20.glVertexAttribPointer(mColorHandle, COLORS_PER_VERTEX,
                GLES20.GL_FLOAT, false, colorStride, colorBuffer);

        mVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mVPMatrixHandle, 1, false, mvpMatrix, 0);

        // Draw the plane
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mColorHandle);
    }
}
