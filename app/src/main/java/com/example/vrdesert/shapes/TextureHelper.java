package com.example.vrdesert.shapes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import java.util.Random;

public class TextureHelper {
    public static int loadTexture(final Context context, final int resourceId) {
        final int[] textureHandle = new int[1];
        GLES20.glGenTextures(1, textureHandle, 0);
        if (textureHandle[0] != 0) {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options);

            // Fallback: if the image file is missing, use a 1x1 icy-blue placeholder
            if (bitmap == null) {
                bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                bitmap.setPixel(0, 0, 0xFF1A3A5A); // dark ice blue
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            bitmap.recycle();
        }
        return textureHandle[0];
    }

    public static int generateEmojiTexture(String emoji) {
        int[] textureHandle = new int[1];
        GLES20.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] != 0) {
            int size = 256;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT);

            android.graphics.Paint textPaint = new android.graphics.Paint();
            textPaint.setTextSize(size * 0.8f);
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);

            int xPos = (canvas.getWidth() / 2);
            int yPos = (int) ((canvas.getHeight() / 2) - ((textPaint.descent() + textPaint.ascent()) / 2));
            canvas.drawText(emoji, xPos, yPos, textPaint);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            bitmap.recycle();
        }
        return textureHandle[0];
    }

    /**
     * Generates an organic 512×512 cave rock texture using layered procedural noise.
     *
     * Layers (bottom → top):
     *  1. Very dark base (near-black charcoal rock)
     *  2. Large Voronoi-like rock panel patches (grey/brown variation)
     *  3. Medium noise blobs to break up regularity
     *  4. Fine pixel-level grain scatter
     *  5. Natural crack lines (thin dark paths across the surface)
     *  6. Moisture/mineral streak highlights (faint cool-toned lines)
     *  7. Stalactite-drop shadow patches
     *  8. Radial vignette to push edges darker
     */
    public static int generateCaveStoneTexture() {
        final int SIZE = 512;
        Bitmap bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Random rng = new Random(7331); // deterministic seed

        // ── 1. Dark charcoal rock base ──────────────────────────────────────
        Paint basePaint = new Paint();
        basePaint.setColor(Color.rgb(28, 22, 18)); // near-black dark rock
        canvas.drawRect(0, 0, SIZE, SIZE, basePaint);

        // ── 2. Large rock panel patches (Voronoi-style blobs) ───────────────
        // Simulate natural rock facets: large irregular oval regions with
        // slightly different grey/brown tones
        Paint panelPaint = new Paint();
        panelPaint.setAntiAlias(true);
        panelPaint.setStyle(Paint.Style.FILL);
        int NUM_PANELS = 28;
        for (int i = 0; i < NUM_PANELS; i++) {
            float cx = rng.nextFloat() * SIZE;
            float cy = rng.nextFloat() * SIZE;
            float rx = 30f + rng.nextFloat() * 90f;
            float ry = 20f + rng.nextFloat() * 70f;
            float angle = rng.nextFloat() * 360f;

            // Rock panel colour: dark grey-brown variants
            int rv = 38 + rng.nextInt(40);   // 38–78
            int gv = 30 + rng.nextInt(28);   // 30–58
            int bv = 22 + rng.nextInt(20);   // 22–42
            panelPaint.setColor(Color.argb(90 + rng.nextInt(60), rv, gv, bv));

            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(angle);
            canvas.drawOval(new RectF(-rx, -ry, rx, ry), panelPaint);
            canvas.restore();
        }

        // ── 3. Medium noise blobs (break up flatness) ───────────────────────
        Paint blobPaint = new Paint();
        blobPaint.setAntiAlias(true);
        blobPaint.setStyle(Paint.Style.FILL);
        int NUM_BLOBS = 80;
        for (int i = 0; i < NUM_BLOBS; i++) {
            float cx = rng.nextFloat() * SIZE;
            float cy = rng.nextFloat() * SIZE;
            float r = 5f + rng.nextFloat() * 22f;
            int bright = rng.nextInt(3);
            if (bright == 0) {
                // lighter stone highlight
                blobPaint.setColor(Color.argb(50, 90, 72, 55));
            } else if (bright == 1) {
                // dark shadow pocket
                blobPaint.setColor(Color.argb(70, 12, 8, 5));
            } else {
                // mid rock
                blobPaint.setColor(Color.argb(45, 55, 42, 30));
            }
            canvas.drawCircle(cx, cy, r, blobPaint);
        }

        // ── 4. Fine grain pixel scatter ──────────────────────────────────────
        Paint grainPaint = new Paint();
        grainPaint.setStrokeWidth(1.5f);
        grainPaint.setStrokeCap(Paint.Cap.ROUND);
        int NUM_GRAINS = SIZE * SIZE / 6;
        for (int i = 0; i < NUM_GRAINS; i++) {
            float px = rng.nextFloat() * SIZE;
            float py = rng.nextFloat() * SIZE;
            int type = rng.nextInt(4);
            if (type == 0) {
                grainPaint.setColor(Color.argb(55, 8,  5,  3));   // very dark speck
            } else if (type == 1) {
                grainPaint.setColor(Color.argb(35, 75, 58, 40));  // medium stone
            } else if (type == 2) {
                grainPaint.setColor(Color.argb(25, 110, 88, 62)); // warm highlight
            } else {
                grainPaint.setColor(Color.argb(20, 50, 55, 60));  // cool mineral
            }
            canvas.drawPoint(px, py, grainPaint);
        }

        // ── 5. Natural crack lines ───────────────────────────────────────────
        // Draw thin, jagged crack paths across the surface
        Paint crackPaint = new Paint();
        crackPaint.setAntiAlias(true);
        crackPaint.setStyle(Paint.Style.STROKE);
        crackPaint.setStrokeCap(Paint.Cap.ROUND);
        int NUM_CRACKS = 18;
        for (int c = 0; c < NUM_CRACKS; c++) {
            float sx = rng.nextFloat() * SIZE;
            float sy = rng.nextFloat() * SIZE;
            // crack width: thin (1–2 px) with dark shadow
            crackPaint.setStrokeWidth(1f + rng.nextFloat() * 1.5f);
            crackPaint.setColor(Color.argb(130 + rng.nextInt(80), 6, 4, 3));

            Path crackPath = new Path();
            crackPath.moveTo(sx, sy);
            float cx = sx, cy = sy;
            int steps = 8 + rng.nextInt(10);
            for (int s = 0; s < steps; s++) {
                cx += (rng.nextFloat() - 0.5f) * 50f;
                cy += (rng.nextFloat() - 0.5f) * 40f;
                // Use quadratic bezier for organic look
                float cpx = (cx + (rng.nextFloat() - 0.5f) * 20f);
                float cpy = (cy + (rng.nextFloat() - 0.5f) * 20f);
                crackPath.quadTo(cpx, cpy, cx, cy);
            }
            canvas.drawPath(crackPath, crackPaint);

            // Faint brighter edge alongside crack (mineral vein effect)
            crackPaint.setStrokeWidth(0.5f);
            crackPaint.setColor(Color.argb(30, 120, 100, 80));
            canvas.drawPath(crackPath, crackPaint);
        }

        // ── 6. Moisture / mineral streaks (vertical drip lines) ─────────────
        Paint streakPaint = new Paint();
        streakPaint.setAntiAlias(true);
        streakPaint.setStyle(Paint.Style.STROKE);
        streakPaint.setStrokeCap(Paint.Cap.ROUND);
        int NUM_STREAKS = 12;
        for (int s = 0; s < NUM_STREAKS; s++) {
            float sx = rng.nextFloat() * SIZE;
            float length = 30f + rng.nextFloat() * 120f;
            streakPaint.setStrokeWidth(1f + rng.nextFloat() * 2f);

            // Either a dark damp streak or a faint white mineral deposit
            if (rng.nextBoolean()) {
                streakPaint.setColor(Color.argb(40, 20, 30, 40)); // damp dark streak
            } else {
                streakPaint.setColor(Color.argb(25, 200, 195, 185)); // mineral white
            }

            Path streak = new Path();
            streak.moveTo(sx, rng.nextFloat() * SIZE);
            float cx2 = sx + (rng.nextFloat() - 0.5f) * 15f;
            streak.lineTo(cx2, rng.nextFloat() * SIZE);
            canvas.drawPath(streak, streakPaint);
        }

        // ── 7. Stalactite-drip dark base shadows (top area darkened splotches)
        Paint dripPaint = new Paint();
        dripPaint.setAntiAlias(true);
        dripPaint.setStyle(Paint.Style.FILL);
        int NUM_DRIPS = 12;
        for (int d = 0; d < NUM_DRIPS; d++) {
            float cx3 = rng.nextFloat() * SIZE;
            // Drip shadow is near the top quarter of texture
            float cy3 = rng.nextFloat() * (SIZE * 0.3f);
            float w = 15f + rng.nextFloat() * 40f;
            float h = 20f + rng.nextFloat() * 60f;
            int alpha = 50 + rng.nextInt(60);
            dripPaint.setColor(Color.argb(alpha, 5, 3, 2));
            canvas.drawOval(new RectF(cx3 - w/2f, cy3, cx3 + w/2f, cy3 + h), dripPaint);
        }

        // ── 8. Radial vignette — pushes edges very dark like interior shadow ─
        RadialGradient vignette = new RadialGradient(
            SIZE / 2f, SIZE / 2f,
            SIZE * 0.65f,
            Color.TRANSPARENT,
            Color.argb(180, 5, 3, 2),
            Shader.TileMode.CLAMP);
        Paint vignettePaint = new Paint();
        vignettePaint.setShader(vignette);
        canvas.drawRect(0, 0, SIZE, SIZE, vignettePaint);

        // ── Upload to OpenGL ─────────────────────────────────────────────────
        int[] handle = new int[1];
        GLES20.glGenTextures(1, handle, 0);
        if (handle[0] != 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, handle[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,     GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,     GLES20.GL_REPEAT);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            bmp.recycle();
        }
        return handle[0];
    }
}
