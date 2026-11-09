package com.archive.app.canvas.paint;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline sticker catalogue drawn with vector paths. No network, no bundled bitmaps, crisp at any
 * zoom, and every sticker can be tinted. Users can still import PNG stickers.
 */
public final class StickerArt {

    public static class Spec {
        public final String key;
        public final String label;
        public final String category;

        Spec(String key, String label, String category) {
            this.key = key;
            this.label = label;
            this.category = category;
        }
    }

    public static final List<Spec> CATALOG = new ArrayList<>();

    static {
        CATALOG.add(new Spec("tape", "Washi tape", "DECORATIVE"));
        CATALOG.add(new Spec("tape_dots", "Dotted tape", "DECORATIVE"));
        CATALOG.add(new Spec("stamp", "Postage stamp", "VINTAGE"));
        CATALOG.add(new Spec("seal", "Wax seal", "VINTAGE"));
        CATALOG.add(new Spec("pin", "Push pin", "VINTAGE"));
        CATALOG.add(new Spec("paperclip", "Paper clip", "DECORATIVE"));
        CATALOG.add(new Spec("label", "Label", "LABELS"));
        CATALOG.add(new Spec("arrow", "Arrow", "ARROWS"));
        CATALOG.add(new Spec("arrow_curve", "Curved arrow", "ARROWS"));
        CATALOG.add(new Spec("star", "Star", "SYMBOLS"));
        CATALOG.add(new Spec("burst", "Burst", "SYMBOLS"));
        CATALOG.add(new Spec("quote", "Quotation", "LITERATURE"));
        CATALOG.add(new Spec("underline", "Ink stroke", "DECORATIVE"));
        CATALOG.add(new Spec("flower", "Botanical", "NATURE"));
        CATALOG.add(new Spec("leaf", "Leaf", "NATURE"));
        CATALOG.add(new Spec("sun", "Sun", "NATURE"));
        CATALOG.add(new Spec("moon", "Crescent", "NATURE"));
        CATALOG.add(new Spec("flask", "Flask", "SCIENTIFIC"));
        CATALOG.add(new Spec("cross", "Plus mark", "ACADEMIC"));
        CATALOG.add(new Spec("frame", "Corner frame", "ACADEMIC"));
    }

    private StickerArt() {}

    public static Spec spec(String key) {
        for (Spec spec : CATALOG) if (spec.key.equals(key)) return spec;
        return CATALOG.get(0);
    }

    /** Draws into the 0..1 unit box mapped to (w, h). */
    public static void draw(Canvas canvas, String key, float width, float height, int tint, Paint paint) {
        if (width <= 0 || height <= 0) return;
        int save = canvas.save();
        canvas.scale(width, height);
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        int colour = tint == 0 ? 0xFF8A6A45 : tint;
        paint.setColor(colour);
        paint.setStrokeWidth(0.028f);
        paint.setAlpha(255);
        paint.setShadowLayer(0, 0, 0, Color.TRANSPARENT);

        switch (key == null ? "" : key) {
            case "tape": drawTape(canvas, paint, false); break;
            case "tape_dots": drawTape(canvas, paint, true); break;
            case "stamp": drawStamp(canvas, paint, colour); break;
            case "seal": drawSeal(canvas, paint, colour); break;
            case "pin": drawPin(canvas, paint, colour); break;
            case "paperclip": drawPaperclip(canvas, paint); break;
            case "label": drawLabel(canvas, paint, colour); break;
            case "arrow": drawArrow(canvas, paint, false); break;
            case "arrow_curve": drawArrow(canvas, paint, true); break;
            case "star": drawStar(canvas, paint, colour); break;
            case "burst": drawBurst(canvas, paint); break;
            case "quote": drawQuote(canvas, paint, colour); break;
            case "underline": drawUnderline(canvas, paint); break;
            case "flower": drawFlower(canvas, paint, colour); break;
            case "leaf": drawLeaf(canvas, paint, colour); break;
            case "sun": drawSun(canvas, paint); break;
            case "moon": drawMoon(canvas, paint, colour); break;
            case "flask": drawFlask(canvas, paint); break;
            case "cross": drawCross(canvas, paint); break;
            case "frame": drawFrame(canvas, paint); break;
            default: drawTape(canvas, paint, false); break;
        }
        canvas.restoreToCount(save);
    }

    private static void drawTape(Canvas canvas, Paint paint, boolean dotted) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(dotted ? 0xFFEFE6D2 : 0xCCF3E8CE);
        Path path = new Path();
        path.moveTo(0.02f, 0.24f);
        path.lineTo(0.98f, 0.10f);
        path.lineTo(0.99f, 0.78f);
        path.lineTo(0.01f, 0.92f);
        path.close();
        canvas.drawPath(path, paint);

        paint.setColor(0x558A7A63);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.012f);
        if (dotted) {
            for (int i = 0; i < 7; i++) {
                canvas.drawCircle(0.12f + i * 0.13f, 0.52f, 0.028f, paint);
            }
        } else {
            canvas.drawLine(0.03f, 0.26f, 0.03f, 0.90f, paint);
            canvas.drawLine(0.97f, 0.12f, 0.97f, 0.76f, paint);
        }
    }

    private static void drawStamp(Canvas canvas, Paint paint, int colour) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF6EFE0);
        Path path = new Path();
        int teeth = 9;
        for (int i = 0; i < teeth; i++) {
            float x = i / (float) teeth;
            path.addCircle(x + 0.5f / teeth, 0.06f, 0.035f, Path.Direction.CW);
            path.addCircle(x + 0.5f / teeth, 0.94f, 0.035f, Path.Direction.CW);
        }
        for (int i = 0; i < teeth; i++) {
            float y = i / (float) teeth;
            path.addCircle(0.06f, y + 0.5f / teeth, 0.035f, Path.Direction.CW);
            path.addCircle(0.94f, y + 0.5f / teeth, 0.035f, Path.Direction.CW);
        }
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.012f);
        paint.setColor(colour);
        canvas.drawRect(0.16f, 0.18f, 0.84f, 0.82f, paint);
        canvas.drawLine(0.20f, 0.60f, 0.80f, 0.60f, paint);
        canvas.drawLine(0.20f, 0.70f, 0.62f, 0.70f, paint);
        canvas.drawCircle(0.50f, 0.40f, 0.12f, paint);
    }

    private static void drawSeal(Canvas canvas, Paint paint, int colour) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF9B3B33);
        Path path = new Path();
        int spikes = 14;
        for (int i = 0; i < spikes * 2; i++) {
            double angle = Math.PI * i / spikes;
            float radius = (i % 2 == 0) ? 0.46f : 0.38f;
            float x = 0.5f + (float) Math.cos(angle) * radius;
            float y = 0.5f + (float) Math.sin(angle) * radius;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(0x33FFFFFF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.02f);
        canvas.drawCircle(0.5f, 0.5f, 0.24f, paint);
        paint.setColor(Color.alpha(colour) == 0 ? 0xFF9B3B33 : colour);
    }

    private static void drawPin(Canvas canvas, Paint paint, int colour) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFC8503F);
        canvas.drawCircle(0.40f, 0.34f, 0.16f, paint);
        paint.setColor(0x66C8503F);
        canvas.drawCircle(0.44f, 0.30f, 0.05f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFF8A7A63);
        paint.setStrokeWidth(0.03f);
        canvas.drawLine(0.46f, 0.44f, 0.66f, 0.80f, paint);
        paint.setColor(colour);
    }

    private static void drawPaperclip(Canvas canvas, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.05f);
        RectF outer = new RectF(0.32f, 0.14f, 0.70f, 0.72f);
        canvas.drawArc(outer, -20, 300, false, paint);
        RectF inner = new RectF(0.40f, 0.28f, 0.62f, 0.86f);
        canvas.drawArc(inner, 160, 220, false, paint);
        canvas.drawLine(0.62f, 0.28f, 0.62f, 0.70f, paint);
    }

    private static void drawLabel(Canvas canvas, Paint paint, int colour) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFBF3E2);
        Path path = new Path();
        path.moveTo(0.06f, 0.30f);
        path.lineTo(0.78f, 0.30f);
        path.lineTo(0.94f, 0.50f);
        path.lineTo(0.78f, 0.70f);
        path.lineTo(0.06f, 0.70f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.014f);
        paint.setColor(colour);
        canvas.drawPath(path, paint);
        canvas.drawCircle(0.78f, 0.50f, 0.05f, paint);
        canvas.drawLine(0.14f, 0.42f, 0.62f, 0.42f, paint);
        canvas.drawLine(0.14f, 0.58f, 0.52f, 0.58f, paint);
    }

    private static void drawArrow(Canvas canvas, Paint paint, boolean curved) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.035f);
        Path path = new Path();
        if (curved) {
            path.moveTo(0.10f, 0.80f);
            path.quadTo(0.44f, 0.16f, 0.84f, 0.40f);
        } else {
            path.moveTo(0.08f, 0.72f);
            path.lineTo(0.84f, 0.30f);
        }
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        Path head = new Path();
        head.moveTo(0.92f, 0.26f);
        head.lineTo(0.74f, 0.22f);
        head.lineTo(0.82f, 0.42f);
        head.close();
        canvas.drawPath(head, paint);
    }

    private static void drawStar(Canvas canvas, Paint paint, int colour) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
        Path path = new Path();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + Math.PI * i / 5;
            float radius = (i % 2 == 0) ? 0.46f : 0.20f;
            float x = 0.5f + (float) Math.cos(angle) * radius;
            float y = 0.52f + (float) Math.sin(angle) * radius;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        canvas.drawPath(path, paint);
    }

    private static void drawBurst(Canvas canvas, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.025f);
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2 * i / 12;
            canvas.drawLine(0.5f + (float) Math.cos(angle) * 0.20f,
                    0.5f + (float) Math.sin(angle) * 0.20f,
                    0.5f + (float) Math.cos(angle) * 0.46f,
                    0.5f + (float) Math.sin(angle) * 0.46f, paint);
        }
    }

    private static void drawQuote(Canvas canvas, Paint paint, int colour) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
        for (int i = 0; i < 2; i++) {
            float offset = i * 0.30f;
            Path path = new Path();
            path.moveTo(0.18f + offset, 0.24f);
            path.quadTo(0.02f + offset, 0.44f, 0.16f + offset, 0.66f);
            path.quadTo(0.34f + offset, 0.62f, 0.30f + offset, 0.44f);
            path.quadTo(0.28f + offset, 0.32f, 0.18f + offset, 0.24f);
            path.close();
            canvas.drawPath(path, paint);
        }
    }

    private static void drawUnderline(Canvas canvas, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.06f);
        Path path = new Path();
        path.moveTo(0.06f, 0.55f);
        path.cubicTo(0.30f, 0.42f, 0.55f, 0.66f, 0.94f, 0.46f);
        canvas.drawPath(path, paint);
    }

    private static void drawFlower(Canvas canvas, Paint paint, int colour) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI * 2 * i / 6;
            float cx = 0.5f + (float) Math.cos(angle) * 0.20f;
            float cy = 0.5f + (float) Math.sin(angle) * 0.20f;
            RectF petal = new RectF(cx - 0.13f, cy - 0.13f, cx + 0.13f, cy + 0.13f);
            canvas.drawOval(petal, paint);
        }
        paint.setColor(0xFFF0D99A);
        canvas.drawCircle(0.5f, 0.5f, 0.10f, paint);
    }

    private static void drawLeaf(Canvas canvas, Paint paint, int colour) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
        Path path = new Path();
        path.moveTo(0.20f, 0.80f);
        path.quadTo(0.16f, 0.24f, 0.80f, 0.20f);
        path.quadTo(0.86f, 0.72f, 0.20f, 0.80f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.014f);
        paint.setColor(0x88000000);
        canvas.drawLine(0.22f, 0.78f, 0.72f, 0.28f, paint);
    }

    private static void drawSun(Canvas canvas, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.03f);
        canvas.drawCircle(0.5f, 0.5f, 0.22f, paint);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            canvas.drawLine(0.5f + (float) Math.cos(angle) * 0.30f,
                    0.5f + (float) Math.sin(angle) * 0.30f,
                    0.5f + (float) Math.cos(angle) * 0.46f,
                    0.5f + (float) Math.sin(angle) * 0.46f, paint);
        }
    }

    private static void drawMoon(Canvas canvas, Paint paint, int colour) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
        Path path = new Path();
        path.addCircle(0.5f, 0.5f, 0.40f, Path.Direction.CW);
        path.addCircle(0.66f, 0.42f, 0.34f, Path.Direction.CCW);
        canvas.drawPath(path, paint);
    }

    private static void drawFlask(Canvas canvas, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.028f);
        Path path = new Path();
        path.moveTo(0.40f, 0.16f);
        path.lineTo(0.40f, 0.42f);
        path.lineTo(0.20f, 0.82f);
        path.lineTo(0.80f, 0.82f);
        path.lineTo(0.60f, 0.42f);
        path.lineTo(0.60f, 0.16f);
        canvas.drawPath(path, paint);
        canvas.drawLine(0.32f, 0.16f, 0.68f, 0.16f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x55A9713A);
        Path liquid = new Path();
        liquid.moveTo(0.30f, 0.64f);
        liquid.lineTo(0.70f, 0.64f);
        liquid.lineTo(0.80f, 0.82f);
        liquid.lineTo(0.20f, 0.82f);
        liquid.close();
        canvas.drawPath(liquid, paint);
    }

    private static void drawCross(Canvas canvas, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.05f);
        canvas.drawLine(0.5f, 0.18f, 0.5f, 0.82f, paint);
        canvas.drawLine(0.18f, 0.5f, 0.82f, 0.5f, paint);
    }

    private static void drawFrame(Canvas canvas, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.03f);
        float s = 0.24f;
        canvas.drawLine(0f, s, 0f, 0f, paint);
        canvas.drawLine(0f, 0f, s, 0f, paint);
        canvas.drawLine(1f - s, 0f, 1f, 0f, paint);
        canvas.drawLine(1f, 0f, 1f, s, paint);
        canvas.drawLine(0f, 1f - s, 0f, 1f, paint);
        canvas.drawLine(0f, 1f, s, 1f, paint);
        canvas.drawLine(1f - s, 1f, 1f, 1f, paint);
        canvas.drawLine(1f, 1f - s, 1f, 1f, paint);
    }
}
