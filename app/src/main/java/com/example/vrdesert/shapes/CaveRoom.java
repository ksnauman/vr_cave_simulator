package com.example.vrdesert.shapes;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Random;

/**
 * CaveRoom — renders an organic cave enclosure.
 *
 * Instead of a clean rectangular box, the geometry is subdivided and
 * perturbed so that walls bow in/out, the ceiling is an arched vault with
 * hanging stalactite spikes, the floor is uneven (stalagmite bumps), and
 * every surface has a rough undulating appearance.
 *
 * Geometry strategy
 * ─────────────────
 *  • Each flat face (front/back/left/right wall, floor, ceiling) is split
 *    into a SUBDIVISIONS × SUBDIVISIONS grid of quads.
 *  • Each vertex interior normal-direction coordinate is perturbed by a
 *    pseudo-random offset sampled from a smooth lattice (value noise) so
 *    adjacent vertices blend naturally rather than creating sharp spikes.
 *  • The ceiling has an additional arch curve plus random downward spikes
 *    (stalactites).
 *  • The floor has random upward bumps (stalagmites) at the corners.
 *  • Edge vertices are kept at full extent so there are no visible gaps
 *    between faces.
 *
 * Shader compatibility: same uniforms as tunnelShaderProgram
 *   (uMVPMatrix, uTexture, uAlphaMultiplier, uOffset)
 */
public class CaveRoom {

    // Room half-extents
    private static final float HW = 15f;   // half-width  (X)
    private static final float HH = 8.0f;  // half-height (Y)
    private static final float HD = 15f;   // half-depth  (Z)

    // Grid resolution for subdivision
    private static final int SUBDIVISIONS = 12;

    // Displacement magnitude for different surfaces
    private static final float WALL_DISP   = 2.2f;  // walls bow in/out by up to this
    private static final float CEIL_DISP   = 2.5f;  // ceiling rough bumps
    private static final float FLOOR_DISP  = 1.0f;  // floor roughness
    private static final float ARCH_DIP    = 2.5f;  // extra arch bowl at ceiling centre

    // UV tile counts
    private static final float U_WALL  = 3.0f;
    private static final float V_WALL  = 2.0f;
    private static final float U_FLOOR = 3.0f;
    private static final float V_FLOOR = 3.0f;

    private static final int   COORDS_PER_VERTEX = 3;
    private static final int   COORDS_PER_UV     = 2;
    private static final int   BYTES_PER_FLOAT   = 4;

    private FloatBuffer vertexBuffer;
    private FloatBuffer uvBuffer;
    private int         vertexCount;

    // Noise lattice (seeded for determinism)
    private final Random rng = new Random(9173);
    private final float[][] noiseGrid = new float[32][32];

    public CaveRoom() {
        // Populate smooth value-noise lattice
        for (int i = 0; i < 32; i++)
            for (int j = 0; j < 32; j++)
                noiseGrid[i][j] = rng.nextFloat() * 2f - 1f; // -1..+1

        buildGeometry();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Smooth interpolated noise sample (bilinear on the 32×32 lattice)
    // s, t ∈ [0..1]
    // ─────────────────────────────────────────────────────────────────────────
    private float noise(float s, float t) {
        float gx = s * 8f;   // scale to lattice space
        float gy = t * 8f;
        int x0 = (int) gx; int x1 = x0 + 1;
        int y0 = (int) gy; int y1 = y0 + 1;
        float fx = gx - x0;  float fy = gy - y0;
        // smooth step
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);

        float v00 = noiseGrid[x0 & 31][y0 & 31];
        float v10 = noiseGrid[x1 & 31][y0 & 31];
        float v01 = noiseGrid[x0 & 31][y1 & 31];
        float v11 = noiseGrid[x1 & 31][y1 & 31];
        return v00 + fx*(v10-v00) + fy*(v01-v00) + fx*fy*(v00-v10-v01+v11);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // fbm — fractional brownian motion (layered noise octaves)
    // ─────────────────────────────────────────────────────────────────────────
    private float fbm(float s, float t) {
        float v = 0f, amp = 1f, freq = 1f, maxV = 0f;
        for (int i = 0; i < 4; i++) {
            v    += noise(s * freq, t * freq) * amp;
            maxV += amp;
            amp  *= 0.5f;
            freq *= 2.1f;
        }
        return v / maxV; // normalised to -1..+1
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build all geometry
    // ─────────────────────────────────────────────────────────────────────────
    private void buildGeometry() {
        ArrayList<Float> vList = new ArrayList<>();
        ArrayList<Float> uList = new ArrayList<>();

        buildWall(vList, uList, Face.FRONT);
        buildWall(vList, uList, Face.BACK);
        buildWall(vList, uList, Face.LEFT);
        buildWall(vList, uList, Face.RIGHT);
        buildFloor(vList, uList);
        buildCeiling(vList, uList);

        vertexCount = vList.size() / COORDS_PER_VERTEX;

        float[] verts = toArray(vList);
        float[] uvs   = toArray(uList);

        ByteBuffer vb = ByteBuffer.allocateDirect(verts.length * BYTES_PER_FLOAT);
        vb.order(ByteOrder.nativeOrder());
        vertexBuffer = vb.asFloatBuffer();
        vertexBuffer.put(verts);
        vertexBuffer.position(0);

        ByteBuffer ub = ByteBuffer.allocateDirect(uvs.length * BYTES_PER_FLOAT);
        ub.order(ByteOrder.nativeOrder());
        uvBuffer = ub.asFloatBuffer();
        uvBuffer.put(uvs);
        uvBuffer.position(0);
    }

    private enum Face { FRONT, BACK, LEFT, RIGHT }

    /**
     * Build a subdivided wall face with displacement applied along the inward normal.
     */
    private void buildWall(ArrayList<Float> verts, ArrayList<Float> uvs, Face face) {
        int N = SUBDIVISIONS;

        // Vertex grid: (N+1) × (N+1) points
        float[][] vx = new float[N+1][N+1];
        float[][] vy = new float[N+1][N+1];
        float[][] vz = new float[N+1][N+1];

        for (int r = 0; r <= N; r++) {
            float t = (float) r / N;          // 0–1 vertical
            float rawY = -HH + t * (HH * 2f);

            for (int c = 0; c <= N; c++) {
                float s = (float) c / N;      // 0–1 horizontal

                // fbm displacement value for this grid point
                float d = fbm(s, t) * WALL_DISP;

                // Edge vertices: keep at wall boundary (no displacement)
                // so adjacent faces connect without gaps
                float edgeFactor = calcEdgeFactor(s, t);
                d *= edgeFactor;

                switch (face) {
                    case FRONT: {
                        float rawX = -HW + s * (HW * 2f);
                        vx[r][c] = rawX;
                        vy[r][c] = rawY;
                        vz[r][c] = -HD + d; // wall at -HD, displacement pushes inward (+Z)
                        break;
                    }
                    case BACK: {
                        float rawX = HW - s * (HW * 2f);
                        vx[r][c] = rawX;
                        vy[r][c] = rawY;
                        vz[r][c] = HD - d;  // wall at +HD, inward = -Z
                        break;
                    }
                    case LEFT: {
                        float rawZ = HD - s * (HD * 2f);
                        vx[r][c] = -HW + d; // wall at -HW, inward = +X
                        vy[r][c] = rawY;
                        vz[r][c] = rawZ;
                        break;
                    }
                    case RIGHT: {
                        float rawZ = -HD + s * (HD * 2f);
                        vx[r][c] = HW - d;  // wall at +HW, inward = -X
                        vy[r][c] = rawY;
                        vz[r][c] = rawZ;
                        break;
                    }
                }
            }
        }

        // Emit quads from the grid
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                float s0 = (float) c / N * (face == Face.LEFT || face == Face.RIGHT ? U_WALL : U_WALL);
                float s1 = (float)(c+1) / N * U_WALL;
                float t0 = (float) r / N * V_WALL;
                float t1 = (float)(r+1) / N * V_WALL;

                // Triangle 1: (r,c), (r,c+1), (r+1,c+1)
                addTriangle(verts, uvs,
                    vx[r][c],   vy[r][c],   vz[r][c],   s0, t0,
                    vx[r][c+1], vy[r][c+1], vz[r][c+1], s1, t0,
                    vx[r+1][c+1],vy[r+1][c+1],vz[r+1][c+1], s1, t1);

                // Triangle 2: (r,c), (r+1,c+1), (r+1,c)
                addTriangle(verts, uvs,
                    vx[r][c],   vy[r][c],   vz[r][c],   s0, t0,
                    vx[r+1][c+1],vy[r+1][c+1],vz[r+1][c+1], s1, t1,
                    vx[r+1][c], vy[r+1][c], vz[r+1][c], s0, t1);
            }
        }
    }

    /**
     * Build an uneven cave floor with stalagmite bumps at random spots.
     */
    private void buildFloor(ArrayList<Float> verts, ArrayList<Float> uvs) {
        int N = SUBDIVISIONS;
        float[][] vx = new float[N+1][N+1];
        float[][] vy = new float[N+1][N+1];
        float[][] vz = new float[N+1][N+1];

        for (int r = 0; r <= N; r++) {
            float t = (float) r / N;
            float rawZ = -HD + t * (HD * 2f);
            for (int c = 0; c <= N; c++) {
                float s = (float) c / N;
                float rawX = -HW + s * (HW * 2f);

                float d = fbm(s + 0.5f, t + 0.3f) * FLOOR_DISP;
                if (d < 0f) d = 0f; // only bump upward

                vx[r][c] = rawX;
                vy[r][c] = -HH + d;
                vz[r][c] = rawZ;
            }
        }

        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                float s0 = (float) c / N * U_FLOOR;
                float s1 = (float)(c+1) / N * U_FLOOR;
                float t0 = (float) r / N * V_FLOOR;
                float t1 = (float)(r+1) / N * V_FLOOR;

                addTriangle(verts, uvs,
                    vx[r][c],    vy[r][c],    vz[r][c],    s0, t0,
                    vx[r][c+1],  vy[r][c+1],  vz[r][c+1],  s1, t0,
                    vx[r+1][c+1],vy[r+1][c+1],vz[r+1][c+1],s1, t1);

                addTriangle(verts, uvs,
                    vx[r][c],    vy[r][c],    vz[r][c],    s0, t0,
                    vx[r+1][c+1],vy[r+1][c+1],vz[r+1][c+1],s1, t1,
                    vx[r+1][c],  vy[r+1][c],  vz[r+1][c],  s0, t1);
            }
        }
    }

    /**
     * Build an arched cave ceiling with stalactite spikes hanging down.
     * The base shape is inward-arched (lower at sides, higher at centre inverted
     * for a dome-like vault), then noise adds irregular bumps,
     * and a few random sharp downward spikes simulate stalactites.
     */
    private void buildCeiling(ArrayList<Float> verts, ArrayList<Float> uvs) {
        int N = SUBDIVISIONS;
        float[][] vx = new float[N+1][N+1];
        float[][] vy = new float[N+1][N+1];
        float[][] vz = new float[N+1][N+1];

        for (int r = 0; r <= N; r++) {
            float t = (float) r / N;
            float rawZ = HD - t * (HD * 2f);
            float zt = t * 2f - 1f; // -1..+1 for arch

            for (int c = 0; c <= N; c++) {
                float s = (float) c / N;
                float rawX = -HW + s * (HW * 2f);
                float xs = s * 2f - 1f; // -1..+1 for arch

                // Arch shape: bowl that dips down at edges (inverted dome)
                float archY = (xs*xs + zt*zt) * ARCH_DIP;

                // Noise roughness on top of arch
                float d = fbm(s + 0.9f, t + 0.7f) * CEIL_DISP;
                if (d < 0) d = -d; // always push downward from ceiling

                vx[r][c] = rawX;
                vy[r][c] = HH - archY - d;
                vz[r][c] = rawZ;
            }
        }

        // Add random stalactite spikes: pick random grid points and push them down
        Random spRng = new Random(4441);
        int NUM_STALACTITES = 8;
        for (int k = 0; k < NUM_STALACTITES; k++) {
            int sr = 1 + spRng.nextInt(N - 1);
            int sc = 1 + spRng.nextInt(N - 1);
            float spike = 1.5f + spRng.nextFloat() * 2.5f; // 1.5–4 unit stalactite drop
            // Spread spike to neighbours for a gradual point shape
            vy[sr][sc]   -= spike;
            vy[sr-1][sc] -= spike * 0.4f;
            vy[sr+1][sc] -= spike * 0.4f;
            vy[sr][sc-1] -= spike * 0.4f;
            vy[sr][sc+1] -= spike * 0.4f;
        }

        // Emit ceiling quads (winding reversed so normals face downward/inward)
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                float s0 = (float) c / N * U_FLOOR;
                float s1 = (float)(c+1) / N * U_FLOOR;
                float t0 = (float) r / N * V_FLOOR;
                float t1 = (float)(r+1) / N * V_FLOOR;

                // Reversed winding (ceiling faces down)
                addTriangle(verts, uvs,
                    vx[r][c],    vy[r][c],    vz[r][c],    s0, t0,
                    vx[r+1][c+1],vy[r+1][c+1],vz[r+1][c+1],s1, t1,
                    vx[r][c+1],  vy[r][c+1],  vz[r][c+1],  s1, t0);

                addTriangle(verts, uvs,
                    vx[r][c],    vy[r][c],    vz[r][c],    s0, t0,
                    vx[r+1][c],  vy[r+1][c],  vz[r+1][c],  s0, t1,
                    vx[r+1][c+1],vy[r+1][c+1],vz[r+1][c+1],s1, t1);
            }
        }
    }

    /**
     * Fade factor: 0 at edges, 1 at centre — keeps border vertices pinned to
     * room boundary so adjacent faces don't gap.
     */
    private float calcEdgeFactor(float s, float t) {
        float es = Math.min(s, 1f - s) * 2f / (1f / SUBDIVISIONS * 2f);
        float et = Math.min(t, 1f - t) * 2f / (1f / SUBDIVISIONS * 2f);
        es = Math.min(1f, es);
        et = Math.min(1f, et);
        return es * et;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private void addTriangle(ArrayList<Float> verts, ArrayList<Float> uvs,
                              float x0, float y0, float z0, float u0, float v0,
                              float x1, float y1, float z1, float u1, float v1,
                              float x2, float y2, float z2, float u2, float v2) {
        verts.add(x0); verts.add(y0); verts.add(z0);
        verts.add(x1); verts.add(y1); verts.add(z1);
        verts.add(x2); verts.add(y2); verts.add(z2);
        uvs.add(u0); uvs.add(v0);
        uvs.add(u1); uvs.add(v1);
        uvs.add(u2); uvs.add(v2);
    }

    private float[] toArray(ArrayList<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Draw
    // ─────────────────────────────────────────────────────────────────────────
    public void draw(int shaderProgram, float[] mvpMatrix, int textureId) {
        GLES20.glUseProgram(shaderProgram);

        int mvpHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);

        int alphaHandle = GLES20.glGetUniformLocation(shaderProgram, "uAlphaMultiplier");
        if (alphaHandle != -1) GLES20.glUniform1f(alphaHandle, 1.0f);

        int offsetHandle = GLES20.glGetUniformLocation(shaderProgram, "uOffset");
        if (offsetHandle != -1) GLES20.glUniform1f(offsetHandle, 0.0f);

        int texHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(texHandle, 0);

        int posHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition");
        GLES20.glEnableVertexAttribArray(posHandle);
        GLES20.glVertexAttribPointer(posHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false, COORDS_PER_VERTEX * BYTES_PER_FLOAT, vertexBuffer);

        int uvHandle = GLES20.glGetAttribLocation(shaderProgram, "aTexCoordinate");
        GLES20.glEnableVertexAttribArray(uvHandle);
        GLES20.glVertexAttribPointer(uvHandle, COORDS_PER_UV,
                GLES20.GL_FLOAT, false, COORDS_PER_UV * BYTES_PER_FLOAT, uvBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        GLES20.glDisableVertexAttribArray(posHandle);
        GLES20.glDisableVertexAttribArray(uvHandle);
    }
}
