package com.example.vrdesert.shapes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
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
            options.inScaled = false; // No pre-scaling
            final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options);
            
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

            // Draw emoji exactly in center
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
     * Procedurally generates a 512×512 brownish stone tile texture using Android Canvas.
     *
     * Layers applied (bottom → top):
     *  1. Warm brown base fill
     *  2. Random stone-grain pixel noise (darker and lighter flecks)
     *  3. Brick mortar lines (horizontal + vertical grid in grey)
     *  4. Per-brick shading variation (slight brightness offset per cell)
     *  5. Corner radial vignette (darkens edges for cave depth)
     *
     * The texture is uploaded with GL_REPEAT so it tiles on all cave surfaces.
     */
    public static int generateCaveStoneTexture() {
        final int SIZE = 512;
        // How many bricks per tile row and column
        final int BRICK_COLS = 4;
        final int BRICK_ROWS = 4;
        final int MORTAR_PX  = 6;   // mortar line width in pixels

        Bitmap bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Random rng = new Random(42); // fixed seed for deterministic look

        // ── 1. Base warm-brown fill ──────────────────────────────────────────
        Paint basePaint = new Paint();
        basePaint.setColor(Color.rgb(107, 66, 38));  // #6B4226 earthy brown
        canvas.drawRect(0, 0, SIZE, SIZE, basePaint);

        // ── 2. Stone grain noise ─────────────────────────────────────────────
        Paint noisePaint = new Paint();
        noisePaint.setStrokeWidth(1f);
        for (int i = 0; i < SIZE * SIZE / 8; i++) {
            int px = rng.nextInt(SIZE);
            int py = rng.nextInt(SIZE);
            // Randomly shift toward dark or light
            int shift = rng.nextInt(3); // 0=dark, 1=mid, 2=light
            if (shift == 0) {
                noisePaint.setColor(Color.argb(80, 50, 28, 12));   // dark fleck
            } else if (shift == 1) {
                noisePaint.setColor(Color.argb(50, 90, 55, 30));   // mid grain
            } else {
                noisePaint.setColor(Color.argb(60, 160, 110, 70)); // highlight
            }
            canvas.drawPoint(px, py, noisePaint);
        }

        // ── 3. Per-brick slight colour variation ─────────────────────────────
        int brickW = SIZE / BRICK_COLS;
        int brickH = SIZE / BRICK_ROWS;
        Paint brickPaint = new Paint();
        brickPaint.setStyle(Paint.Style.FILL);
        for (int row = 0; row < BRICK_ROWS; row++) {
            // Offset every other row for a staggered brick pattern
            int colOffset = (row % 2 == 0) ? 0 : brickW / 2;
            for (int col = -1; col <= BRICK_COLS; col++) {
                int left  = col * brickW + colOffset + MORTAR_PX / 2;
                int top   = row * brickH              + MORTAR_PX / 2;
                int right = left + brickW             - MORTAR_PX;
                int bot   = top  + brickH             - MORTAR_PX;

                // Small random brightness variation per brick
                int var = rng.nextInt(30) - 15;  // ±15
                int r = Math.max(0, Math.min(255, 107 + var));
                int g = Math.max(0, Math.min(255,  66 + var));
                int b = Math.max(0, Math.min(255,  38 + var));
                brickPaint.setColor(Color.argb(120, r, g, b)); // semi-transparent overlay
                canvas.drawRect(new RectF(left, top, right, bot), brickPaint);

                // Subtle inner-edge darkening (simulate depth in brick joints)
                Paint edgePaint = new Paint();
                edgePaint.setStyle(Paint.Style.STROKE);
                edgePaint.setStrokeWidth(2f);
                edgePaint.setColor(Color.argb(60, 30, 16, 6));
                canvas.drawRect(new RectF(left, top, right, bot), edgePaint);
            }
        }

        // ── 4. Mortar lines (horizontal + vertical grid) ─────────────────────
        Paint mortarPaint = new Paint();
        mortarPaint.setColor(Color.rgb(72, 65, 58));  // dark warm grey
        mortarPaint.setStrokeWidth(MORTAR_PX);
        mortarPaint.setStyle(Paint.Style.FILL);

        // Horizontal lines
        for (int row = 0; row <= BRICK_ROWS; row++) {
            int y = row * brickH;
            canvas.drawRect(0, y - MORTAR_PX / 2f, SIZE, y + MORTAR_PX / 2f, mortarPaint);
        }
        // Vertical lines (staggered per row)
        for (int row = 0; row < BRICK_ROWS; row++) {
            int colOffset = (row % 2 == 0) ? 0 : brickW / 2;
            for (int col = -1; col <= BRICK_COLS + 1; col++) {
                int x = col * brickW + colOffset;
                int top  = row * brickH;
                int bot  = top + brickH;
                canvas.drawRect(x - MORTAR_PX / 2f, top, x + MORTAR_PX / 2f, bot, mortarPaint);
            }
        }

        // ── 5. Radial vignette — darkens corners to simulate cave depth ───────
        Paint vignettePaint = new Paint();
        RadialGradient vignette = new RadialGradient(
            SIZE / 2f, SIZE / 2f,            // center
            SIZE * 0.75f,                    // radius at which darkening starts
            Color.TRANSPARENT,               // center: transparent
            Color.argb(160, 20, 10, 4),      // edge: dark earthy shadow
            Shader.TileMode.CLAMP);
        vignettePaint.setShader(vignette);
        canvas.drawRect(0, 0, SIZE, SIZE, vignettePaint);

        // ── Upload to OpenGL with GL_REPEAT for seamless tiling ───────────────
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
