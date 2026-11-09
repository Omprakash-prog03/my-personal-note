package com.archive.app.canvas.engine;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import com.archive.app.canvas.paint.BackgroundRenderer;
import com.archive.app.canvas.paint.StickerArt;
import com.archive.app.canvas.selection.SelectionState;
import com.archive.app.domain.model.BackgroundStyle;
import com.archive.app.domain.model.CanvasElement;
import com.archive.app.domain.model.elements.AudioElement;
import com.archive.app.domain.model.elements.DocumentElement;
import com.archive.app.domain.model.elements.DrawElement;
import com.archive.app.domain.model.elements.EnvelopeElement;
import com.archive.app.domain.model.elements.ImageElement;
import com.archive.app.domain.model.elements.LinkElement;
import com.archive.app.domain.model.elements.ShapeElement;
import com.archive.app.domain.model.elements.StickerElement;
import com.archive.app.domain.model.elements.StickyNoteElement;
import com.archive.app.domain.model.elements.TextElement;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure drawing pass. Called both by the live CanvasView (chrome = true) and by the exporter
 * (chrome = false), which guarantees selection outlines can never appear in an exported image.
 */
public class CanvasRenderer {

    public static class Palette {
        public int backdrop = 0xFFE7DCC6;
        public int paper = 0xFFFDF7EA;
        public int ink = 0xFF2C251C;
        public int muted = 0xFF8C7B64;
        public int selection = 0xFF9C6B34;
        public int handleFill = 0xFFFFFBF3;
        public int accent = 0xFFA9713A;
    }

    public static class Options {
        public boolean chrome = true;
        public boolean includeBackground = true;
        public boolean showGrid = false;
        public float viewScale = 1f;
        public float density = 1f;
        public RectF visibleRect;
    }

    private static class TextCache {
        StaticLayout layout;
        float width;
        float fontSize;
        String text;
        int colour;
        boolean bold, italic, underline, strike, shadow;
        float letterSpacing, lineSpacing;
        String align;
        String font;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chromePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final Map<String, TextCache> textCaches = new HashMap<>();
    private final BitmapStore bitmaps;
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final RectF otherRect = new RectF(); // reused in helpers
    private final float[] corners = new float[8];

    public CanvasRenderer(BitmapStore bitmaps) {
        this.bitmaps = bitmaps;
    }

    public void draw(Canvas canvas, CanvasDocument document, SelectionState selection,
                     Palette palette, Options options, Runnable onBitmapReady) {
        if (document == null || palette == null || options == null) return;
        RectF visible = options.visibleRect;

        if (options.includeBackground) {
            drawBackdrop(canvas, document, palette, options);
        }

        RectF bounds = new RectF();
        for (CanvasElement element : document.elements()) {
            if (!element.visible) continue;
            element.bounds(bounds);
            if (visible != null && !RectF.intersects(bounds, visible)) continue;
            drawElement(canvas, element, palette, options, onBitmapReady);
        }

        if (options.chrome && selection != null && !selection.isEmpty()) {
            drawSelectionChrome(canvas, document, selection, palette, options);
        }
    }

    // ------------------------------------------------------------------ background

    private void drawBackdrop(Canvas canvas, CanvasDocument document, Palette palette, Options options) {
        RectF visible = options.visibleRect;
        if (!com.archive.app.domain.model.PageFormat.MODE_INFINITE.equals(document.mode)) {
            // Backdrop is painted by the view; here we draw the page itself with a soft edge.
            rect.set(0, 0, document.pageWidth, document.pageHeight);
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x33000000);
            RectF shadow = new RectF(rect);
            shadow.offset(3f, 4f);
            canvas.drawRect(shadow, paint);
            backgroundRenderer.draw(canvas, rect, document.background, loadBackgroundImage(document),
                    options.showGrid, palette.muted, document.background.gridSize);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(0x22000000);
            canvas.drawRect(rect, paint);
        } else {
            RectF area = visible == null ? new RectF(0, 0, document.pageWidth, document.pageHeight)
                    : new RectF(visible);
            backgroundRenderer.draw(canvas, area, document.background, loadBackgroundImage(document),
                    options.showGrid, palette.muted, document.background.gridSize);
        }
    }

    private Bitmap loadBackgroundImage(CanvasDocument document) {
        BackgroundStyle background = document.background;
        if (background.type != BackgroundStyle.Type.IMAGE) return null;
        return bitmaps.loadBlocking(background.mediaId, background.relativePath, 2048);
    }

    // ------------------------------------------------------------------ elements

    private void drawElement(Canvas canvas, CanvasElement element, Palette palette, Options options,
                             Runnable onBitmapReady) {
        int save = canvas.save();
        if (element.opacity < 1f) {
            int layer = canvas.saveLayerAlpha(null, Math.max(0, Math.min(255,
                    Math.round(element.opacity * 255f))));
            canvas.concat(element.renderMatrix());
            drawLocal(canvas, element, palette, options, onBitmapReady);
            canvas.restoreToCount(layer);
        } else {
            canvas.concat(element.renderMatrix());
            drawLocal(canvas, element, palette, options, onBitmapReady);
        }
        canvas.restoreToCount(save);
    }

    private void drawLocal(Canvas canvas, CanvasElement element, Palette palette, Options options,
                           Runnable onBitmapReady) {
        switch (element.type()) {
            case IMAGE: drawImage(canvas, (ImageElement) element, palette, options, onBitmapReady); break;
            case TEXT: drawText(canvas, (TextElement) element, palette); break;
            case SHAPE: drawShape(canvas, (ShapeElement) element); break;
            case DRAWING: drawStrokes(canvas, (DrawElement) element); break;
            case STICKER: drawSticker(canvas, (StickerElement) element, onBitmapReady); break;
            case STICKY_NOTE: drawStickyNote(canvas, (StickyNoteElement) element); break;
            case ENVELOPE: drawEnvelope(canvas, (EnvelopeElement) element, palette); break;
            case DOCUMENT: drawDocument(canvas, (DocumentElement) element, palette); break;
            case AUDIO: drawAudio(canvas, (AudioElement) element, palette); break;
            case LINK_CARD: drawLink(canvas, (LinkElement) element, palette); break;
            default: break;
        }
    }

    private void drawImage(Canvas canvas, ImageElement element, Palette palette, Options options,
                           Runnable onBitmapReady) {
        float w = element.width;
        float h = element.height;
        String frame = element.getFrame();
        float pad;
        int frameColour;
        float bottomExtra = 0f;
        switch (frame) {
            case ImageElement.FRAME_POLAROID:
                pad = Math.min(w, h) * 0.055f;
                bottomExtra = pad * 2.6f;
                frameColour = 0xFFFBF6EA;
                break;
            case ImageElement.FRAME_PAPER:
                pad = Math.min(w, h) * 0.035f;
                frameColour = palette.paper;
                break;
            case ImageElement.FRAME_FILM:
                pad = Math.min(w, h) * 0.075f;
                frameColour = 0xFF1B1B1B;
                break;
            case ImageElement.FRAME_BORDERLESS:
            case ImageElement.FRAME_ROUNDED:
            case ImageElement.FRAME_PLAIN:
            default:
                pad = 0f;
                frameColour = 0;
                break;
        }

        if (element.hasShadow()) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setColor(0x22000000);
            rect.set(pad * 0.5f + 4f, pad * 0.5f + 6f, w + 2f, h + 4f);
            canvas.drawRoundRect(rect, 6f, 6f, paint);
        }

        if (pad > 0f) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setColor(frameColour);
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, 4f, 4f, paint);
        }

        RectF imageRect = new RectF(pad, pad, w - pad, h - pad - bottomExtra);
        Bitmap bitmap = bitmaps.get(element.getMediaId(), element.getRelativePath(),
                Math.round(imageRect.width() * options.viewScale),
                Math.round(imageRect.height() * options.viewScale), onBitmapReady);

        int save = canvas.save();
        if (ImageElement.FRAME_ROUNDED.equals(frame) || element.getCornerRadius() > 0f) {
            path.reset();
            path.addRoundRect(imageRect, element.getCornerRadius(), element.getCornerRadius(), Path.Direction.CW);
            canvas.clipPath(path);
        } else if (!ImageElement.FRAME_BORDERLESS.equals(frame)) {
            canvas.clipRect(imageRect);
        }

        if (bitmap == null || bitmap.isRecycled()) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setColor(palette.muted);
            paint.setAlpha(30);
            canvas.drawRect(imageRect, paint);
            paint.setAlpha(255);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, Math.min(w, h) * 0.01f));
            paint.setColor(palette.muted);
            float icon = Math.min(imageRect.width(), imageRect.height()) * 0.12f;
            canvas.drawCircle(imageRect.centerX() - icon, imageRect.centerY() - icon * 0.4f, icon * 0.5f, paint);
            path.reset();
            path.moveTo(imageRect.left + icon, imageRect.bottom - icon);
            path.lineTo(imageRect.centerX(), imageRect.centerY() + icon * 0.8f);
            path.lineTo(imageRect.centerX() + icon, imageRect.bottom - icon * 1.6f);
            path.lineTo(imageRect.right - icon, imageRect.bottom - icon);
            canvas.drawPath(path, paint);
        } else {
            Matrix matrix = new Matrix();
            RectF source = new RectF(element.getCropLeft() * bitmap.getWidth(),
                    element.getCropTop() * bitmap.getHeight(),
                    element.getCropRight() * bitmap.getWidth(),
                    element.getCropBottom() * bitmap.getHeight());
            matrix.setRectToRect(source, imageRect, Matrix.ScaleToFit.FILL);
            if (element.isFlippedHorizontally()) {
                matrix.postScale(-1f, 1f, imageRect.centerX(), imageRect.centerY());
            }
            if (element.isFlippedVertically()) {
                matrix.postScale(1f, -1f, imageRect.centerX(), imageRect.centerY());
            }
            paint.reset();
            paint.setAntiAlias(true);
            paint.setFilterBitmap(true);
            canvas.drawBitmap(bitmap, matrix, paint);
        }
        canvas.restoreToCount(save);

        if (element.getBorderWidth() > 0f) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(element.getBorderWidth());
            paint.setColor(element.getBorderColour());
            rect.set(pad, pad, w - pad, h - pad - bottomExtra);
            canvas.drawRect(rect, paint);
        }
    }

    private void drawText(Canvas canvas, TextElement element, Palette palette) {
        float pad = element.getPadding();
        if (element.getBackgroundColour() != 0) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(element.getBackgroundColour());
            rect.set(0, 0, element.width, element.height);
            canvas.drawRoundRect(rect, 6f, 6f, paint);
        }
        if (element.getBorderWidth() > 0f) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(element.getBorderWidth());
            paint.setColor(element.getBorderColour());
            rect.set(0, 0, element.width, element.height);
            canvas.drawRect(rect, paint);
        }

        StaticLayout layout = layoutFor(element, Math.max(1, Math.round(element.width - pad * 2)));
        if (layout == null) return;

        if (element.autoHeight()) {
            element.height = layout.getHeight() + pad * 2;
        }

        int save = canvas.save();
        canvas.clipRect(0, 0, element.width, element.height);
        canvas.translate(pad, pad);

        if (element.getHighlightColour() != 0) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(element.getHighlightColour());
            for (int line = 0; line < layout.getLineCount(); line++) {
                float left = layout.getLineLeft(line);
                float right = layout.getLineRight(line);
                if (right <= left) continue;
                rect.set(left, layout.getLineTop(line) + layout.getHeight() * 0.06f,
                        right, layout.getLineBottom(line) - layout.getHeight() * 0.04f);
                canvas.drawRoundRect(rect, 3f, 3f, paint);
            }
        }
        layout.draw(canvas);
        canvas.restoreToCount(save);
    }

    private StaticLayout layoutFor(TextElement element, int width) {
        TextCache cache = textCaches.get(element.id);
        if (cache != null
                && cache.width == width
                && cache.fontSize == element.getFontSize()
                && cache.text.equals(element.getText())
                && cache.colour == element.getColour()
                && cache.bold == element.isBold()
                && cache.italic == element.isItalic()
                && cache.underline == element.isUnderline()
                && cache.strike == element.isStrike()
                && cache.shadow == element.hasShadow()
                && cache.letterSpacing == element.getLetterSpacing()
                && cache.lineSpacing == element.getLineSpacing()
                && cache.align.equals(element.getAlign())
                && cache.font.equals(element.getFontFamily())) {
            return cache.layout;
        }

        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(Math.max(4f, element.getFontSize()));
        tp.setColor(element.getColour());
        int style = Typeface.NORMAL;
        if (element.isBold() && element.isItalic()) style = Typeface.BOLD_ITALIC;
        else if (element.isBold()) style = Typeface.BOLD;
        else if (element.isItalic()) style = Typeface.ITALIC;
        tp.setTypeface(Typeface.create(
                TextElement.FONT_SERIF.equals(element.getFontFamily()) ? Typeface.SERIF : Typeface.SANS_SERIF,
                style));
        tp.setUnderlineText(element.isUnderline());
        tp.setStrikeThruText(element.isStrike());
        tp.setLetterSpacing(element.getLetterSpacing());
        if (element.hasShadow()) tp.setShadowLayer(4f, 1.5f, 2f, 0x55000000);

        Layout.Alignment alignment = Layout.Alignment.ALIGN_NORMAL;
        if (TextElement.ALIGN_CENTER.equals(element.getAlign())) alignment = Layout.Alignment.ALIGN_CENTER;
        else if (TextElement.ALIGN_RIGHT.equals(element.getAlign())) alignment = Layout.Alignment.ALIGN_OPPOSITE;

        StaticLayout layout = StaticLayout.Builder
                .obtain(element.getText().isEmpty() ? " " : element.getText(), 0,
                        Math.max(1, element.getText().isEmpty() ? 1 : element.getText().length()), tp, width)
                .setAlignment(alignment)
                .setLineSpacing(0f, Math.max(0.7f, element.getLineSpacing()))
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .build();

        cache = new TextCache();
        cache.layout = layout;
        cache.width = width;
        cache.fontSize = element.getFontSize();
        cache.text = element.getText();
        cache.colour = element.getColour();
        cache.bold = element.isBold();
        cache.italic = element.isItalic();
        cache.underline = element.isUnderline();
        cache.strike = element.isStrike();
        cache.shadow = element.hasShadow();
        cache.letterSpacing = element.getLetterSpacing();
        cache.lineSpacing = element.getLineSpacing();
        cache.align = element.getAlign();
        cache.font = element.getFontFamily();
        textCaches.put(element.id, cache);
        return layout;
    }

    private void drawShape(Canvas canvas, ShapeElement element) {
        float w = element.width;
        float h = element.height;
        String shape = element.getShape();
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Math.max(0f, element.getStrokeWidth()));
        paint.setColor(element.getStrokeColour());

        if (ShapeElement.LINE.equals(shape) || ShapeElement.ARROW.equals(shape)
                || ShapeElement.DOUBLE_ARROW.equals(shape)) {
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(0, h / 2f, w, h / 2f, paint);
            float head = Math.min(h * 0.5f, w * 0.25f);
            paint.setStyle(Paint.Style.FILL);
            if (!ShapeElement.LINE.equals(shape)) {
                path.reset();
                path.moveTo(w, h / 2f);
                path.lineTo(w - head, h / 2f - head * 0.55f);
                path.lineTo(w - head, h / 2f + head * 0.55f);
                path.close();
                canvas.drawPath(path, paint);
            }
            if (ShapeElement.DOUBLE_ARROW.equals(shape)) {
                path.reset();
                path.moveTo(0, h / 2f);
                path.lineTo(head, h / 2f - head * 0.55f);
                path.lineTo(head, h / 2f + head * 0.55f);
                path.close();
                canvas.drawPath(path, paint);
            }
            return;
        }

        buildShapePath(shape, w, h, element.getCornerRadius());
        if (element.getFillColour() != 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(element.getFillColour());
            canvas.drawPath(path, paint);
        }
        if (element.getStrokeWidth() > 0f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(element.getStrokeColour());
            canvas.drawPath(path, paint);
        }
    }

    private void buildShapePath(String shape, float w, float h, float radius) {
        path.reset();
        switch (shape) {
            case ShapeElement.ELLIPSE:
            case ShapeElement.CIRCLE: {
                float left = 0, top = 0, right = w, bottom = h;
                if (ShapeElement.CIRCLE.equals(shape)) {
                    float size = Math.min(w, h);
                    left = (w - size) / 2f;
                    top = (h - size) / 2f;
                    right = left + size;
                    bottom = top + size;
                }
                path.addOval(new RectF(left, top, right, bottom), Path.Direction.CW);
                break;
            }
            case ShapeElement.TRIANGLE:
                path.moveTo(w / 2f, 0);
                path.lineTo(w, h);
                path.lineTo(0, h);
                path.close();
                break;
            case ShapeElement.STAR: {
                for (int i = 0; i < 10; i++) {
                    double angle = -Math.PI / 2 + Math.PI * i / 5;
                    float rx = w / 2f;
                    float ry = h / 2f;
                    float factor = (i % 2 == 0) ? 1f : 0.45f;
                    float px = w / 2f + (float) Math.cos(angle) * rx * factor;
                    float py = h / 2f + (float) Math.sin(angle) * ry * factor;
                    if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
                }
                path.close();
                break;
            }
            case ShapeElement.BURST: {
                int spikes = 16;
                for (int i = 0; i < spikes * 2; i++) {
                    double angle = Math.PI * i / spikes;
                    float factor = (i % 2 == 0) ? 1f : 0.72f;
                    float px = w / 2f + (float) Math.cos(angle) * (w / 2f) * factor;
                    float py = h / 2f + (float) Math.sin(angle) * (h / 2f) * factor;
                    if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
                }
                path.close();
                break;
            }
            case ShapeElement.ROUND_RECT:
                path.addRoundRect(new RectF(0, 0, w, h), Math.min(radius, Math.min(w, h) / 2f),
                        Math.min(radius, Math.min(w, h) / 2f), Path.Direction.CW);
                break;
            case ShapeElement.RECT:
            default:
                path.addRect(new RectF(0, 0, w, h), Path.Direction.CW);
                break;
        }
    }

    private void drawStrokes(Canvas canvas, DrawElement element) {
        for (DrawElement.Stroke stroke : element.getStrokes()) {
            if (stroke.pointCount() < 1) continue;
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(stroke.colour);
            float width = stroke.width;
            if (DrawElement.TOOL_HIGHLIGHTER.equals(stroke.tool)) {
                width = Math.max(width, 18f);
                paint.setAlpha(110);
                paint.setStrokeCap(Paint.Cap.SQUARE);
            } else if (DrawElement.TOOL_MARKER.equals(stroke.tool)) {
                paint.setAlpha(200);
            } else if (DrawElement.TOOL_PENCIL.equals(stroke.tool)) {
                paint.setAlpha(180);
            } else {
                paint.setAlpha(stroke.alpha);
            }
            paint.setStrokeWidth(width);

            if (stroke.pointCount() == 1) {
                canvas.drawPoint(stroke.points.get(0), stroke.points.get(1), paint);
                continue;
            }
            path.reset();
            path.moveTo(stroke.points.get(0), stroke.points.get(1));
            for (int i = 2; i < stroke.points.size() - 2; i += 2) {
                float midX = (stroke.points.get(i) + stroke.points.get(i + 2)) / 2f;
                float midY = (stroke.points.get(i + 1) + stroke.points.get(i + 3)) / 2f;
                path.quadTo(stroke.points.get(i), stroke.points.get(i + 1), midX, midY);
            }
            int last = stroke.points.size();
            path.lineTo(stroke.points.get(last - 2), stroke.points.get(last - 1));
            canvas.drawPath(path, paint);
        }
    }

    private void drawSticker(Canvas canvas, StickerElement element, Runnable onBitmapReady) {
        if (element.getRelativePath() != null) {
            Bitmap bitmap = bitmaps.get(element.getMediaId(), element.getRelativePath(),
                    Math.round(element.width), Math.round(element.height), onBitmapReady);
            if (bitmap != null) {
                paint.reset();
                paint.setAntiAlias(true);
                paint.setFilterBitmap(true);
                rect.set(0, 0, element.width, element.height);
                canvas.drawBitmap(bitmap, null, rect, paint);
                return;
            }
        }
        StickerArt.draw(canvas, element.getStickerKey(), element.width, element.height,
                element.getTint(), paint);
    }

    private void drawStickyNote(Canvas canvas, StickyNoteElement element) {
        float w = element.width;
        float h = element.height;
        float fold = element.hasFoldedCorner() ? Math.min(w, h) * 0.16f : 0f;

        paint.reset();
        paint.setAntiAlias(true);
        paint.setColor(0x22000000);
        rect.set(4, 6, w + 2, h + 2);
        canvas.drawRoundRect(rect, 4f, 4f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(element.getFillColour());
        path.reset();
        path.moveTo(0, 0);
        path.lineTo(w, 0);
        path.lineTo(w, h - fold);
        path.lineTo(w - fold, h);
        path.lineTo(0, h);
        path.close();
        canvas.drawPath(path, paint);

        if (fold > 0f) {
            paint.setColor(0x33000000);
            path.reset();
            path.moveTo(w - fold, h);
            path.lineTo(w, h - fold);
            path.lineTo(w - fold, h - fold);
            path.close();
            canvas.drawPath(path, paint);
        }

        buildNoteLayout(canvas, element.getText(), 18f, 22f, w - 36f,
                element.getFontSize(), element.getTextColour());
    }

    private void buildNoteLayout(Canvas canvas, String text, float x, float y, float width,
                                 float fontSize, int colour) {
        if (text == null || text.isEmpty() || width <= 4f) return;
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setAntiAlias(true);
        tp.setTextSize(Math.max(4f, fontSize));
        tp.setColor(colour);
        tp.setTypeface(Typeface.SANS_SERIF);
        StaticLayout layout = StaticLayout.Builder
                .obtain(text, 0, text.length(), tp, Math.max(1, Math.round(width)))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .build();
        int save = canvas.save();
        canvas.translate(x, y);
        layout.draw(canvas);
        canvas.restoreToCount(save);
    }

    private void drawEnvelope(Canvas canvas, EnvelopeElement element, Palette palette) {
        float w = element.width;
        float h = element.height;
        int fill = element.getFillColour();

        paint.reset();
        paint.setAntiAlias(true);
        paint.setColor(0x22000000);
        rect.set(3, 5, w + 2, h + 3);
        canvas.drawRoundRect(rect, 6f, 6f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, 6f, 6f, paint);

        // Inner letter peeking out when the envelope has been opened.
        if (element.isOpened()) {
            paint.setColor(0xFFFDF7EA);
            rect.set(w * 0.08f, h * 0.06f, w * 0.92f, h * 0.72f);
            canvas.drawRoundRect(rect, 4f, 4f, paint);
            paint.setColor(0x338A7A63);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            for (int i = 0; i < 3; i++) {
                float y = h * (0.24f + i * 0.14f);
                canvas.drawLine(w * 0.18f, y, w * 0.82f, y, paint);
            }
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, w * 0.008f));
        paint.setColor(darken(fill));
        path.reset();
        if (element.isOpened()) {
            path.moveTo(0, h * 0.30f);
            path.lineTo(w / 2f, h * 0.02f);
            path.lineTo(w, h * 0.30f);
        } else {
            path.moveTo(0, 0);
            path.lineTo(w / 2f, h * 0.44f);
            path.lineTo(w, 0);
        }
        canvas.drawPath(path, paint);
        path.reset();
        path.moveTo(0, h);
        path.lineTo(w * 0.38f, h * 0.46f);
        path.moveTo(w, h);
        path.lineTo(w * 0.62f, h * 0.46f);
        canvas.drawPath(path, paint);

        if (element.isSealed() && !element.isOpened()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF9B3B33);
            canvas.drawCircle(w / 2f, h * 0.44f, Math.min(w, h) * 0.075f, paint);
        }

        String label = element.getLabel();
        if (label != null && !label.isEmpty()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF3A3126);
            paint.setTextSize(Math.max(9f, h * 0.105f));
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            String text = ellipsize(label, paint, w * 0.8f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(text, w / 2f, h * (element.isOpened() ? 0.90f : 0.82f), paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawDocument(Canvas canvas, DocumentElement element, Palette palette) {
        float w = element.width;
        float h = element.height;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setColor(0x1F000000);
        rect.set(2, 4, w + 1, h + 2);
        canvas.drawRoundRect(rect, 8f, 8f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFDF7EA);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, 8f, 8f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x338A7A63);
        canvas.drawRoundRect(rect, 8f, 8f, paint);

        float iconW = Math.min(w, h) * 0.26f;
        float left = (w - iconW) / 2f;
        float top = h * 0.14f;
        rect.set(left, top, left + iconW, top + iconW * 1.25f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFEFE4D0);
        canvas.drawRoundRect(rect, 4f, 4f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(palette.muted);
        canvas.drawRoundRect(rect, 4f, 4f, paint);
        canvas.drawLine(left + iconW * 0.24f, top + iconW * 0.42f,
                left + iconW * 0.76f, top + iconW * 0.42f, paint);
        canvas.drawLine(left + iconW * 0.24f, top + iconW * 0.62f,
                left + iconW * 0.64f, top + iconW * 0.62f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(palette.ink);
        paint.setTextSize(Math.max(10f, Math.min(w, h) * 0.085f));
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(ellipsize(element.getTitle(), paint, w * 0.84f), w / 2f, h * 0.72f, paint);
        paint.setTypeface(Typeface.SANS_SERIF);
        paint.setColor(palette.muted);
        paint.setTextSize(Math.max(8f, Math.min(w, h) * 0.062f));
        String meta = element.getPageCount() > 0
                ? element.getPageCount() + " pages" : element.getMime();
        canvas.drawText(ellipsize(meta, paint, w * 0.84f), w / 2f, h * 0.84f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawAudio(Canvas canvas, AudioElement element, Palette palette) {
        float w = element.width;
        float h = element.height;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFDF7EA);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, 14f, 14f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x338A7A63);
        canvas.drawRoundRect(rect, 14f, 14f, paint);

        float radius = Math.min(h * 0.28f, w * 0.14f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(palette.accent);
        canvas.drawCircle(radius * 1.8f, h / 2f, radius, paint);
        paint.setColor(0xFFFDF7EA);
        path.reset();
        path.moveTo(radius * 1.55f, h / 2f - radius * 0.45f);
        path.lineTo(radius * 1.55f + radius * 0.7f, h / 2f);
        path.lineTo(radius * 1.55f, h / 2f + radius * 0.45f);
        path.close();
        canvas.drawPath(path, paint);

        paint.setColor(palette.ink);
        paint.setTextSize(Math.max(10f, h * 0.16f));
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        canvas.drawText(ellipsize(element.getTitle(), paint, w - radius * 3.6f),
                radius * 3.2f, h * 0.46f, paint);
        paint.setTypeface(Typeface.SANS_SERIF);
        paint.setColor(palette.muted);
        paint.setTextSize(Math.max(8f, h * 0.13f));
        canvas.drawText(element.durationLabel().isEmpty() ? "Audio" : element.durationLabel(),
                radius * 3.2f, h * 0.68f, paint);
    }

    private void drawLink(Canvas canvas, LinkElement element, Palette palette) {
        float w = element.width;
        float h = element.height;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFDF7EA);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, 10f, 10f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x338A7A63);
        canvas.drawRoundRect(rect, 10f, 10f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(palette.accent);
        rect.set(0, 0, w * 0.035f, h);
        canvas.drawRoundRect(rect, 3f, 3f, paint);

        paint.setColor(palette.ink);
        paint.setTextSize(Math.max(10f, h * 0.13f));
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        String title = element.getTitle().isEmpty() ? element.host() : element.getTitle();
        canvas.drawText(ellipsize(title, paint, w * 0.86f), w * 0.08f, h * 0.30f, paint);

        paint.setTypeface(Typeface.SANS_SERIF);
        paint.setColor(palette.muted);
        paint.setTextSize(Math.max(8f, h * 0.105f));
        canvas.drawText(ellipsize(element.host(), paint, w * 0.86f), w * 0.08f, h * 0.48f, paint);

        String note = element.getNote();
        if (note != null && !note.isEmpty()) {
            paint.setTextSize(Math.max(8f, h * 0.10f));
            canvas.drawText(ellipsize(note, paint, w * 0.86f), w * 0.08f, h * 0.66f, paint);
        }
    }

    private static String ellipsize(String text, Paint paint, float maxWidth) {
        if (text == null) return "";
        if (paint.measureText(text) <= maxWidth) return text;
        String ellipsis = "…";
        int length = text.length();
        while (length > 1 && paint.measureText(text, 0, length) + paint.measureText(ellipsis) > maxWidth) {
            length--;
        }
        return text.substring(0, Math.max(1, length)) + ellipsis;
    }

    // ------------------------------------------------------------------ chrome

    private void drawSelectionChrome(Canvas canvas, CanvasDocument document, SelectionState selection,
                                     Palette palette, Options options) {
        float handleRadius = 10f * options.density / Math.max(0.15f, options.viewScale);
        float rotateOffset = 46f * options.density / Math.max(0.15f, options.viewScale);

        chromePaint.reset();
        chromePaint.setAntiAlias(true);
        chromePaint.setStyle(Paint.Style.STROKE);
        chromePaint.setStrokeWidth(1.6f * options.density / Math.max(0.15f, options.viewScale));
        chromePaint.setColor(palette.selection);
        chromePaint.setPathEffect(new DashPathEffect(new float[]{6f * options.density, 4f * options.density}, 0f));

        for (CanvasElement element : selection.elements(document)) {
            element.corners(corners);
            path.reset();
            path.moveTo(corners[0], corners[1]);
            for (int i = 1; i < 4; i++) path.lineTo(corners[i * 2], corners[i * 2 + 1]);
            path.close();
            canvas.drawPath(path, chromePaint);
        }

        String singleId = selection.single();
        if (singleId == null) return;
        CanvasElement element = document.byId(singleId);
        if (element == null || element.locked) return;

        element.corners(corners);
        chromePaint.setPathEffect(null);
        chromePaint.setStyle(Paint.Style.FILL);
        chromePaint.setColor(palette.handleFill);
        chromePaint.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        for (int i = 0; i < 4; i++) {
            canvas.drawCircle(corners[i * 2], corners[i * 2 + 1], handleRadius, chromePaint);
        }
        chromePaint.setStyle(Paint.Style.STROKE);
        chromePaint.setStrokeWidth(2f * options.density / Math.max(0.15f, options.viewScale));
        chromePaint.setColor(palette.selection);
        for (int i = 0; i < 4; i++) {
            canvas.drawCircle(corners[i * 2], corners[i * 2 + 1], handleRadius, chromePaint);
        }

        // Rotation handle sits above the top edge, along the object's own outward normal.
        float topMidX = (corners[0] + corners[2]) / 2f;
        float topMidY = (corners[1] + corners[3]) / 2f;
        float edgeX = corners[2] - corners[0];
        float edgeY = corners[3] - corners[1];
        float edgeLength = (float) Math.hypot(edgeX, edgeY);
        if (edgeLength <= 0.001f) return;
        float upX = edgeY / edgeLength;
        float upY = -edgeX / edgeLength;
        float handleX = topMidX + upX * rotateOffset;
        float handleY = topMidY + upY * rotateOffset;

        chromePaint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(topMidX, topMidY, handleX, handleY, chromePaint);
        chromePaint.setStyle(Paint.Style.FILL);
        chromePaint.setColor(palette.handleFill);
        canvas.drawCircle(handleX, handleY, handleRadius, chromePaint);
        chromePaint.setStyle(Paint.Style.STROKE);
        chromePaint.setColor(palette.selection);
        canvas.drawCircle(handleX, handleY, handleRadius, chromePaint);
        chromePaint.setStrokeWidth(1.4f * options.density / Math.max(0.15f, options.viewScale));
        RectF arc = new RectF(handleX - handleRadius * 0.48f, handleY - handleRadius * 0.48f,
                handleX + handleRadius * 0.48f, handleY + handleRadius * 0.48f);
        canvas.drawArc(arc, 40f, 280f, false, chromePaint);
    }

    private static int darken(int colour) {
        int r = (int) (Color.red(colour) * 0.72f);
        int g = (int) (Color.green(colour) * 0.72f);
        int b = (int) (Color.blue(colour) * 0.72f);
        return Color.argb(Color.alpha(colour), r, g, b);
    }
}
