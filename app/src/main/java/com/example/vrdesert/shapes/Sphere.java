package com.example.vrdesert.shapes;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class Sphere {
    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;
    private ShortBuffer indexBuffer;

    private int numIndices;

    private static final int COORDS_PER_VERTEX = 3;
    private static final int TEXCOORDS_PER_VERTEX = 2;
    private static final int VERTEX_STRIDE = COORDS_PER_VERTEX * 4;
    private static final int TEXCOORD_STRIDE = TEXCOORDS_PER_VERTEX * 4;

    public Sphere(float radius, int rings, int sectors) {
        float[] vertices = new float[(rings + 1) * (sectors + 1) * COORDS_PER_VERTEX];
        float[] texCoords = new float[(rings + 1) * (sectors + 1) * TEXCOORDS_PER_VERTEX];
        short[] indices = new short[rings * sectors * 6];

        int vIndex = 0;
        int tIndex = 0;

        for (int r = 0; r <= rings; r++) {
            float v = (float) r / rings;
            float phi = v * (float) Math.PI;

            for (int s = 0; s <= sectors; s++) {
                float u = (float) s / sectors;
                float theta = u * (float) Math.PI * 2f;

                float x = (float) (Math.sin(phi) * Math.cos(theta));
                float y = (float) Math.cos(phi);
                float z = (float) (Math.sin(phi) * Math.sin(theta));

                vertices[vIndex++] = x * radius;
                vertices[vIndex++] = y * radius;
                vertices[vIndex++] = z * radius;

                // 1.0f - u flips horizontally so the panorama reads correctly from inside
                // 1.0f = one full photo wraps the entire sphere (no tiling)
                texCoords[tIndex++] = (1.0f - u) * 1.0f;
                texCoords[tIndex++] = v;
            }
        }

        int iIndex = 0;
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < sectors; s++) {
                short first = (short) ((r * (sectors + 1)) + s);
                short second = (short) (first + sectors + 1);

                // Reversed triangle winding for inside-out render
                indices[iIndex++] = first;
                indices[iIndex++] = (short) (first + 1);
                indices[iIndex++] = second;

                indices[iIndex++] = second;
                indices[iIndex++] = (short) (first + 1);
                indices[iIndex++] = (short) (second + 1);
            }
        }

        numIndices = indices.length;

        ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);

        ByteBuffer tb = ByteBuffer.allocateDirect(texCoords.length * 4);
        tb.order(ByteOrder.nativeOrder());
        textureBuffer = tb.asFloatBuffer();
        textureBuffer.put(texCoords);
        textureBuffer.position(0);

        ByteBuffer ib = ByteBuffer.allocateDirect(indices.length * 2);
        ib.order(ByteOrder.nativeOrder());
        indexBuffer = ib.asShortBuffer();
        indexBuffer.put(indices);
        indexBuffer.position(0);
    }

    public void draw(int shaderProgram, float[] mvpMatrix, int textureId, float alpha, float uOffset, float glow) {
        GLES20.glUseProgram(shaderProgram);
        
        int uOffsetHandle = GLES20.glGetUniformLocation(shaderProgram, "uOffset");
        if (uOffsetHandle != -1) GLES20.glUniform1f(uOffsetHandle, uOffset);

        int glowHandle = GLES20.glGetUniformLocation(shaderProgram, "uGlow");
        if (glowHandle != -1) GLES20.glUniform1f(glowHandle, glow);

        int positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, VERTEX_STRIDE, vertexBuffer);

        int texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "aTexCoordinate");
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, TEXCOORD_STRIDE, textureBuffer);

        int mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        int alphaHandle = GLES20.glGetUniformLocation(shaderProgram, "uAlphaMultiplier");
        if (alphaHandle != -1) GLES20.glUniform1f(alphaHandle, alpha);

        int textureHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(textureHandle, 0);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, numIndices, GLES20.GL_UNSIGNED_SHORT, indexBuffer);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
    }
}
