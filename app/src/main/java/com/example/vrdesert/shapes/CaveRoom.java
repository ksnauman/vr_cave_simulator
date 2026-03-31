package com.example.vrdesert.shapes;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * CaveRoom — renders a box-shaped cave enclosure (4 walls + floor + ceiling)
 * as inward-facing quads textured with a tiling stone texture.
 *
 * Dimensions: 30 (W) x 15 (H) x 30 (D) units, centred on the origin.
 * UV tiling:  4x horizontal, 2x vertical on walls; 4x4 on floor/ceiling.
 *
 * Compatible with the existing tunnelShaderProgram uniforms:
 *   uMVPMatrix, uTexture, uAlphaMultiplier, uOffset
 */
public class CaveRoom {

    // Half-extents of the room
    private static final float HW = 15f;  // half width  (X)
    private static final float HH = 7.5f; // half height (Y)
    private static final float HD = 15f;  // half depth  (Z)

    // UV tile counts
    private static final float U_WALL  = 4.0f;
    private static final float V_WALL  = 2.0f;
    private static final float U_FLOOR = 4.0f;
    private static final float V_FLOOR = 4.0f;

    private static final int COORDS_PER_VERTEX  = 3;
    private static final int COORDS_PER_TEXUV   = 2;
    private static final int BYTES_PER_FLOAT    = 4;

    private FloatBuffer vertexBuffer;
    private FloatBuffer uvBuffer;
    private int vertexCount;

    public CaveRoom() {
        buildGeometry();
    }

    /**
     * Builds vertex + UV data for 6 faces (4 walls + floor + ceiling).
     * Each face = 2 triangles = 6 vertices.
     * Winding is counter-clockwise when viewed from **inside** the room.
     */
    private void buildGeometry() {
        // 6 faces × 6 verts × 3 coords
        float[] verts = new float[6 * 6 * COORDS_PER_VERTEX];
        float[] uvs   = new float[6 * 6 * COORDS_PER_TEXUV];

        int vi = 0, ti = 0;

        // ── FRONT WALL  (z = -HD, normal +Z, viewed from -Z side, so inward = +Z) ──
        // Quad corners (CCW when seen from inside, i.e. from +Z looking toward -Z)
        vi = putQuad(verts, vi,
            -HW, -HH, -HD,
             HW, -HH, -HD,
             HW,  HH, -HD,
            -HW,  HH, -HD);
        ti = putUV(uvs, ti, U_WALL, V_WALL, true);

        // ── BACK WALL  (z = +HD, inward normal = -Z) ──
        vi = putQuad(verts, vi,
             HW, -HH, HD,
            -HW, -HH, HD,
            -HW,  HH, HD,
             HW,  HH, HD);
        ti = putUV(uvs, ti, U_WALL, V_WALL, false);

        // ── LEFT WALL  (x = -HW, inward normal = +X) ──
        vi = putQuad(verts, vi,
            -HW, -HH,  HD,
            -HW, -HH, -HD,
            -HW,  HH, -HD,
            -HW,  HH,  HD);
        ti = putUV(uvs, ti, U_WALL, V_WALL, true);

        // ── RIGHT WALL  (x = +HW, inward normal = -X) ──
        vi = putQuad(verts, vi,
             HW, -HH, -HD,
             HW, -HH,  HD,
             HW,  HH,  HD,
             HW,  HH, -HD);
        ti = putUV(uvs, ti, U_WALL, V_WALL, false);

        // ── FLOOR  (y = -HH, inward normal = +Y) ──
        vi = putQuad(verts, vi,
            -HW, -HH, -HD,
             HW, -HH, -HD,
             HW, -HH,  HD,
            -HW, -HH,  HD);
        ti = putUV(uvs, ti, U_FLOOR, V_FLOOR, true);

        // ── CEILING  (y = +HH, inward normal = -Y) ──
        vi = putQuad(verts, vi,
            -HW, HH,  HD,
             HW, HH,  HD,
             HW, HH, -HD,
            -HW, HH, -HD);
        ti = putUV(uvs, ti, U_FLOOR, V_FLOOR, false);

        vertexCount = vi / COORDS_PER_VERTEX;

        ByteBuffer vb = ByteBuffer.allocateDirect(verts.length * BYTES_PER_FLOAT);
        vb.order(ByteOrder.nativeOrder());
        vertexBuffer = vb.asFloatBuffer();
        vertexBuffer.put(verts);
        vertexBuffer.position(0);

        ByteBuffer tb = ByteBuffer.allocateDirect(uvs.length * BYTES_PER_FLOAT);
        tb.order(ByteOrder.nativeOrder());
        uvBuffer = tb.asFloatBuffer();
        uvBuffer.put(uvs);
        uvBuffer.position(0);
    }

    /**
     * Stores a quad (two CCW triangles) given four corners bl→br→tr→tl.
     * Triangle 1: bl, br, tr
     * Triangle 2: bl, tr, tl
     */
    private int putQuad(float[] buf, int i,
                        float x0, float y0, float z0,   // bottom-left
                        float x1, float y1, float z1,   // bottom-right
                        float x2, float y2, float z2,   // top-right
                        float x3, float y3, float z3) { // top-left
        // Triangle 1
        buf[i++]=x0; buf[i++]=y0; buf[i++]=z0;
        buf[i++]=x1; buf[i++]=y1; buf[i++]=z1;
        buf[i++]=x2; buf[i++]=y2; buf[i++]=z2;
        // Triangle 2
        buf[i++]=x0; buf[i++]=y0; buf[i++]=z0;
        buf[i++]=x2; buf[i++]=y2; buf[i++]=z2;
        buf[i++]=x3; buf[i++]=y3; buf[i++]=z3;
        return i;
    }

    /**
     * Stores UV coordinates for a tiled quad.
     * @param uTile  horizontal repeat count
     * @param vTile  vertical repeat count
     * @param flip   flip U axis (mirrors alternate walls so bricks align)
     */
    private int putUV(float[] buf, int i, float uTile, float vTile, boolean flip) {
        float u0 = flip ? uTile : 0f;
        float u1 = flip ? 0f    : uTile;
        // Tri 1: bl, br, tr
        buf[i++]=u0;   buf[i++]=vTile;
        buf[i++]=u1;   buf[i++]=vTile;
        buf[i++]=u1;   buf[i++]=0f;
        // Tri 2: bl, tr, tl
        buf[i++]=u0;   buf[i++]=vTile;
        buf[i++]=u1;   buf[i++]=0f;
        buf[i++]=u0;   buf[i++]=0f;
        return i;
    }

    /**
     * Draw all 6 cave surfaces using the shared tunnel shader program.
     *
     * @param shaderProgram  the compiled GLES program (tunnelShaderProgram)
     * @param mvpMatrix      combined model-view-projection matrix
     * @param textureId      OpenGL texture handle (stone texture)
     */
    public void draw(int shaderProgram, float[] mvpMatrix, int textureId) {
        GLES20.glUseProgram(shaderProgram);

        // MVP
        int mvpHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);

        // Alpha = 1 (fully opaque)
        int alphaHandle = GLES20.glGetUniformLocation(shaderProgram, "uAlphaMultiplier");
        if (alphaHandle != -1) GLES20.glUniform1f(alphaHandle, 1.0f);

        // UV offset = 0 (walls don't scroll)
        int offsetHandle = GLES20.glGetUniformLocation(shaderProgram, "uOffset");
        if (offsetHandle != -1) GLES20.glUniform1f(offsetHandle, 0.0f);

        // Texture
        int texHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(texHandle, 0);

        // Vertex positions
        int posHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition");
        GLES20.glEnableVertexAttribArray(posHandle);
        GLES20.glVertexAttribPointer(posHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false, COORDS_PER_VERTEX * BYTES_PER_FLOAT, vertexBuffer);

        // UV coordinates
        int uvHandle = GLES20.glGetAttribLocation(shaderProgram, "aTexCoordinate");
        GLES20.glEnableVertexAttribArray(uvHandle);
        GLES20.glVertexAttribPointer(uvHandle, COORDS_PER_TEXUV,
                GLES20.GL_FLOAT, false, COORDS_PER_TEXUV * BYTES_PER_FLOAT, uvBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        GLES20.glDisableVertexAttribArray(posHandle);
        GLES20.glDisableVertexAttribArray(uvHandle);
    }
}
