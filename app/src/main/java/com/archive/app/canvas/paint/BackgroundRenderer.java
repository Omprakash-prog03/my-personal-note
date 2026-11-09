package com.archive.app.canvas.paint;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import com.archive.app.domain.model.BackgroundStyle;

import java.util.Random;

/**
 * Draws paper: plain, grid, ruled, dotted, vintage, kraft, gradient, charcoal or a user image.
 * Background rendering is completely separate from objects, so changing paper never disturbs content.
 */
public final class BackgroundRenderer {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public void draw(Canvas canvas, RectF area, BackgroundStyle style, Bitmap image,
                     boolean showGrid, int gridColor, float gridSize) {
        if (style == null || area == null || area.isEmpty()) return;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        switch (style.type) {
            case GRID:
                paint.setColor(style.color);
                canvas.drawRect(area, paint);
                drawGrid(canvas, area, gridColor, gridSize);
                break;
            case RULED:
                paint.setColor(style.color);
                canvas.drawRect(area, paint);
                drawRuled(canvas, area, gridColor, gridSize);
                break;
            case DOTS:
                paint.setColor(style.color);
                canvas.drawRect(area, paint);
                drawDots(canvas, area, gridColor, gridSize);
                break;
            case VINTAGE:
                paint.setColor(style.color);
                canvas.drawRect(area, paint);
                drawVintage(canvas, area);
                if (showGrid) drawGrid(canvas, area, gridColor, gridSize);
                break;
            case KRAFT:
                paint.setColor(style.color);
                canvas.drawRect(area, paint);
                drawGrain(canvas, area, 0x22000000L, 900);
                if (showGrid) drawGrid(canvas, area, gridColor, gridSize);
                break;
            case GRADIENT:
                paint.setShader(new LinearGradient(area.left, area.top, area.right, area.bottom,
                        style.color, style.color2, Shader.TileMode.CLAMP));
                canvas.drawRect(area, paint);
                paint.setShader(null);
                break;
            case IMAGE:
                paint.setColor(style.color);
                canvas.drawRect(area, paint);
                if (image != null && !image.isRecycled()) {
                    drawCenterCrop(canvas, area, image);
                }
                break;
            case CHARCOAL:
                paint.setColor(style.color);
                canvas.drawRect(area, paint);
                drawGrain(canvas, area, 0x33FFFFFFL, 500);
                break;
            case PLAIN:
            default:
                paint.setColor(style.color);
                canvas.drawRect(area, paint);
                if (showGrid) drawGrid(canvas, area, gridColor, gridSize);
                break;
        }
    }

    private void drawGrid(Canvas canvas, RectF area, int color, float step) {
        if (step < 4) step = 4;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(adjustAlpha(color, 0.35f));
        for (float x = area.left + step; x < area.right; x += step) {
            canvas.drawLine(x, area.top, x, area.bottom, paint);
        }
        for (float y = area.top + step; y < area.bottom; y += step) {
            canvas.drawLine(area.left, y, area.right, y, paint);
        }
    }

    private void drawRuled(Canvas canvas, RectF area, int color, float step) {
        if (step < 8) step = 8;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(adjustAlpha(color, 0.6f));
        for (float y = area.top + step; y < area.bottom; y += step) {
            canvas.drawLine(area.left, y, area.right, y, paint);
        }
        paint.setColor(adjustAlpha(color, 0.5f));
        canvas.drawLine(area.left + step * 1.6f, area.top, area.left + step * 1.6f, area.bottom, paint);
    }

    private void drawDots(Canvas canvas, RectF area, int color, float step) {
        if (step < 8) step = 8;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(adjustAlpha(color, 0.55f));
        float radius = Math.max(1.2f, step * 0.055f);
        for (float x = area.left + step; x < area.right; x += step) {
            for (float y = area.top + step; y < area.bottom; y += step) {
                canvas.drawCircle(x, y, radius, paint);
            }
        }
    }

    private void drawVintage(Canvas canvas, RectF area) {
        grainPaint.reset();
        grainPaint.setAntiAlias(true);
        grainPaint.setColor(0x1A6B4E2E);
        Random random = new Random(0x5EEDL);
        int count = (int) Math.min(1200, area.width() * area.height() / 2600f);
        for (int i = 0; i < count; i++) {
            float x = area.left + random.nextFloat() * area.width();
            float y = area.top + random.nextFloat() * area.height();
            grainPaint.setColor(random.nextBoolean() ? 0x146B4E2E : 0x16FFFFFF);
            canvas.drawCircle(x, y, 1.2f + random.nextFloat() * 2.4f, grainPaint);
        }
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x228A6A45);
        RectF inset = new RectF(area.left + 8, area.top + 8, area.right - 8, area.bottom - 8);
        canvas.drawRect(inset, paint);
    }

    private void drawGrain(Canvas canvas, RectF area, long colour, int count) {
        Random random = new Random(0xC0FFEEL);
        grainPaint.reset();
        grainPaint.setAntiAlias(true);
        grainPaint.setStyle(Paint.Style.FILL);
        grainPaint.setColor((int) colour);
        int dots = (int) Math.min(count, Math.max(60, area.width() * area.height() / 3200f));
        for (int i = 0; i < dots; i++) {
            float x = area.left + random.nextFloat() * area.width();
            float y = area.top + random.nextFloat() * area.height();
            canvas.drawCircle(x, y, 1f + random.nextFloat() * 2f, grainPaint);
        }
    }

    private void drawCenterCrop(Canvas canvas, RectF area, Bitmap image) {
        float scale = Math.max(area.width() / image.getWidth(), area.height() / image.getHeight());
        float dx = area.left + (area.width() - image.getWidth() * scale) / 2f;
        float dy = area.top + (area.height() - image.getHeight() * scale) / 2f;
        int save = canvas.save();
        canvas.clipRect(area);
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        canvas.drawBitmap(image, 0, 0, null);
        canvas.restoreToCount(save);
    }

    private static int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color),
                Color.green(color), Color.blue(color));
    }
}
