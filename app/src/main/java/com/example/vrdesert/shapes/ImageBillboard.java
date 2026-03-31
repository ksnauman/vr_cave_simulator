package com.example.vrdesert.shapes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import android.opengl.GLES20;

public class ImageBillboard {
    private final FloatBuffer vertexBuffer;
    private final FloatBuffer texCoordBuffer;
    
    // Coordinates
    static float coords[] = {
        -1.0f,  1.0f, 0.0f, 
        -1.0f, -1.0f, 0.0f,
         1.0f, -1.0f, 0.0f,
        -1.0f,  1.0f, 0.0f,
         1.0f, -1.0f, 0.0f,
         1.0f,  1.0f, 0.0f
    };
    
    // UV Mapping
    static float uvs[] = {
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 1.0f,
        1.0f, 0.0f
    };

    public ImageBillboard() {
        ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(coords);
        vertexBuffer.position(0);

        ByteBuffer tb = ByteBuffer.allocateDirect(uvs.length * 4);
        tb.order(ByteOrder.nativeOrder());
        texCoordBuffer = tb.asFloatBuffer();
        texCoordBuffer.put(uvs);
        texCoordBuffer.position(0);
    }

    public void draw(int programId, float[] mvpMatrix, int textureId, float alphaMultiplier) {
        GLES20.glUseProgram(programId);
        
        // Pass Alpha Multiplier
        int alphaHandle = GLES20.glGetUniformLocation(programId, "uAlphaMultiplier");
        if(alphaHandle != -1) {
            GLES20.glUniform1f(alphaHandle, alphaMultiplier);
        }

        int mPositionHandle = GLES20.glGetAttribLocation(programId, "vPosition");
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);

        int mTexCoordHandle = GLES20.glGetAttribLocation(programId, "aTexCoordinate");
        if(mTexCoordHandle != -1) {
            GLES20.glEnableVertexAttribArray(mTexCoordHandle);
            GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer);
        }

        // Bind Texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        int mTextureUniform = GLES20.glGetUniformLocation(programId, "uTexture");
        if(mTextureUniform != -1) {
            GLES20.glUniform1i(mTextureUniform, 0);
        }

        int mVPMatrixHandle = GLES20.glGetUniformLocation(programId, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mVPMatrixHandle, 1, false, mvpMatrix, 0);

        // Enable Blending for transparent fade overlaps
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        if(mTexCoordHandle != -1) GLES20.glDisableVertexAttribArray(mTexCoordHandle);
    }
}
