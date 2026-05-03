package com.example.vrdesert.shapes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLUtils;

public class TextTextureHelper {
    public static int createTextTexture(String text) {
        // Create a wider bitmap for the text to prevent overlapping/clipping
        int width = 1024;
        int height = 128;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        bitmap.eraseColor(Color.TRANSPARENT);

        // Draw background (semi-transparent dark)
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.argb(180, 0, 0, 0)); // Darker for better contrast
        canvas.drawRect(0, 0, width, height, bgPaint);

        // Draw text
        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(60); // Larger font
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Vertical center
        float xPos = width / 2f;
        float yPos = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f);
        
        canvas.drawText(text, xPos, yPos, textPaint);

        // Upload to OpenGL
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int textureId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();

        return textureId;
    }
}
