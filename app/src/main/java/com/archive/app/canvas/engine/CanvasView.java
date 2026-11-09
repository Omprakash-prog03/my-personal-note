package com.archive.app.canvas.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.archive.app.R;
import com.archive.app.canvas.commands.CommandHistory;
import com.archive.app.canvas.gestures.CanvasTouchHandler;
import com.archive.app.canvas.paint.BackgroundRenderer;
import com.archive.app.canvas.selection.SelectionState;
import com.archive.app.domain.model.CanvasElement;
import com.archive.app.domain.model.elements.AudioElement;
import com.archive.app.domain.model.elements.DocumentElement;
import com.archive.app.domain.model.elements.EnvelopeElement;
import com.archive.app.domain.model.elements.LinkElement;
import com.archive.app.domain.model.elements.TextElement;
import com.archive.app.utils.ThemeController;

/**
 * Hardware-accelerated canvas surface. Owns the viewport, selection, renderer and touch state
 * machine, but no persistence: the activity/view model decides when to save.
 */
public class CanvasView extends View {

    public interface Listener {
        void onSelectionChanged();

        void onDocumentChanged();

        void onEditText(TextElement element);

        void onOpenEnvelope(EnvelopeElement element);

        void onOpenDocument(DocumentElement element);

        void onOpenLink(LinkElement element);

        void onOpenAudio(AudioElement element);

        /** Single tap or long press on an object — the activity shows its contextual toolbar. */
        void onObjectTapped(CanvasElement element, boolean longPress);

        /** Tap on empty paper with the text tool. */
        void onEmptyTextPlacement(float docX, float docY);

        void onZoomChanged(int percent);
    }

    private CanvasDocument document;
    private final CanvasViewport viewport = new CanvasViewport();
    private final SelectionState selection = new SelectionState();
    private final BitmapStore bitmaps = new BitmapStore();
    private final CanvasRenderer renderer = new CanvasRenderer(bitmaps);
    private final BackgroundRenderer backdropRenderer = new BackgroundRenderer();
    private final CanvasTouchHandler touchHandler;
    private final Paint backdropPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dirty = new RectF();

    private CanvasRenderer.Palette palette = new CanvasRenderer.Palette();
    private Listener listener;
    private boolean showGrid = false;
    private boolean snapToGrid = false;
    private boolean fittedOnce = false;

    public CanvasView(Context context) {
        this(context, null);
    }

    public CanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        refreshPalette();
        touchHandler = new CanvasTouchHandler(this, viewport, selection, new CanvasTouchHandler.Listener() {
            @Override public void onSelectionChanged() {
                invalidate();
                if (listener != null) listener.onSelectionChanged();
            }

            @Override public void onDocumentChanged() {
                if (listener != null) listener.onDocumentChanged();
            }

            @Override public void onEditElement(CanvasElement element) {
                if (element instanceof TextElement) {
                    if (listener != null) listener.onEditText((TextElement) element);
                } else if (element instanceof EnvelopeElement) {
                    if (listener != null) listener.onOpenEnvelope((EnvelopeElement) element);
                } else if (element instanceof DocumentElement) {
                    if (listener != null) listener.onOpenDocument((DocumentElement) element);
                } else if (element instanceof LinkElement) {
                    if (listener != null) listener.onOpenLink((LinkElement) element);
                } else if (element instanceof AudioElement) {
                    if (listener != null) listener.onOpenAudio((AudioElement) element);
                } else {
                    if (listener != null) listener.onObjectTapped(element, false);
                }
            }

            @Override public void onObjectTapped(CanvasElement element, boolean longPress) {
                if (listener != null) listener.onObjectTapped(element, longPress);
            }

            @Override public void onEmptyTextPlacement(float docX, float docY) {
                if (listener != null) listener.onEmptyTextPlacement(docX, docY);
            }

            @Override public void onZoomChanged() {
                notifyZoom();
            }

            @Override public void onRequestInvalidate() {
                invalidate();
            }

            @Override public void onInvalidateRegion(float left, float top, float right, float bottom) {
                invalidateDocumentRect(left, top, right, bottom);
            }
        });
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void refreshPalette() {
        Context context = getContext();
        CanvasRenderer.Palette fresh = new CanvasRenderer.Palette();
        fresh.backdrop = ThemeController.themeColor(context, R.attr.archiveBackdrop);
        fresh.paper = ThemeController.themeColor(context, R.attr.archivePaper);
        fresh.ink = ThemeController.themeColor(context, R.attr.archivePaperInk);
        fresh.muted = ThemeController.themeColor(context, R.attr.archiveMuted);
        fresh.selection = ThemeController.themeColor(context, R.attr.archiveSelection);
        fresh.handleFill = ThemeController.themeColor(context, R.attr.archiveHandleFill);
        fresh.accent = ThemeController.themeColor(context, androidx.appcompat.R.attr.colorPrimary);
        palette = fresh;
        invalidate();
    }

    public CanvasRenderer.Palette palette() { return palette; }

    public void setDocument(CanvasDocument newDocument) {
        this.document = newDocument;
        selection.clear();
        viewport.setDocument(newDocument.pageWidth, newDocument.pageHeight,
                !com.archive.app.domain.model.PageFormat.MODE_INFINITE.equals(newDocument.mode));
        newDocument.history.setListener(() -> {
            invalidate();
            if (listener != null) listener.onDocumentChanged();
        });
        fittedOnce = false;
        requestFit();
        invalidate();
    }

    public CanvasDocument document() { return document; }

    public CanvasViewport viewport() { return viewport; }

    public SelectionState selection() { return selection; }

    public BitmapStore bitmaps() { return bitmaps; }

    public void setShowGrid(boolean value) {
        showGrid = value;
        invalidate();
    }

    public void setSnapToGrid(boolean value) {
        snapToGrid = value;
        touchHandler.setSnapToGrid(value);
    }

    public boolean snapToGrid() { return snapToGrid; }

    public void setTool(CanvasTouchHandler.Tool tool) {
        touchHandler.setTool(tool);
        if (tool != CanvasTouchHandler.Tool.SELECT) selection.clear();
        invalidate();
    }

    public CanvasTouchHandler.Tool tool() { return touchHandler.getTool(); }

    public void setDrawStyle(int colour, float width, String brush) {
        touchHandler.setDrawStyle(colour, width, brush);
    }

    public void requestFit() {
        viewport.fit();
        invalidate();
        notifyZoom();
    }

    public void zoomIn() {
        viewport.zoomBy(1.25f, getWidth() / 2f, getHeight() / 2f);
        invalidate();
        notifyZoom();
    }

    public void zoomOut() {
        viewport.zoomBy(0.8f, getWidth() / 2f, getHeight() / 2f);
        invalidate();
        notifyZoom();
    }

    public void undo() {
        if (document == null) return;
        if (document.history.undo()) {
            selection.retainExisting(document);
            invalidate();
            if (listener != null) listener.onDocumentChanged();
        }
    }

    public void redo() {
        if (document == null) return;
        if (document.history.redo()) {
            selection.retainExisting(document);
            invalidate();
            if (listener != null) listener.onDocumentChanged();
        }
    }

    public CommandHistory history() {
        return document == null ? new CommandHistory() : document.history;
    }

    private void notifyZoom() {
        if (listener != null) listener.onZoomChanged(Math.round(viewport.scale * 100));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewport.setViewportSize(w, h);
        if (!fittedOnce && document != null) {
            fittedOnce = true;
            viewport.fit();
            notifyZoom();
        } else {
            viewport.clamp();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        backdropPaint.setColor(palette.backdrop);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backdropPaint);

        if (document == null) return;

        canvas.save();
        canvas.concat(viewport.matrix());

        CanvasRenderer.Options options = new CanvasRenderer.Options();
        options.chrome = true;
        options.includeBackground = true;
        options.showGrid = showGrid;
        options.viewScale = viewport.scale;
        options.density = getResources().getDisplayMetrics().density;
        options.visibleRect = viewport.visibleDocumentRect();

        renderer.draw(canvas, document, selection, palette, options, this::invalidate);
        canvas.restore();

        RectF marquee = touchHandler.marqueeRect();
        if (marquee != null) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
            paint.setColor(palette.selection);
            paint.setAlpha(180);
            float[] topLeft = {marquee.left, marquee.top};
            float[] bottomRight = {marquee.right, marquee.bottom};
            viewport.docToScreen(topLeft);
            viewport.docToScreen(bottomRight);
            canvas.drawRect(topLeft[0], topLeft[1], bottomRight[0], bottomRight[1], paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (document == null) return false;
        getParent().requestDisallowInterceptTouchEvent(true);
        return touchHandler.onTouchEvent(event);
    }

    private void invalidateDocumentRect(float left, float top, float right, float bottom) {
        float[] a = {left, top};
        float[] b = {right, bottom};
        viewport.docToScreen(a);
        viewport.docToScreen(b);
        dirty.set(a[0] - 24, a[1] - 24, b[0] + 24, b[1] + 24);
        invalidate((int) Math.floor(dirty.left), (int) Math.floor(dirty.top),
                (int) Math.ceil(dirty.right), (int) Math.ceil(dirty.bottom));
    }

    /** Renders the page to a bitmap without any editing chrome — used by export, PDF and thumbnails. */
    public Bitmap renderToBitmap(float scale, boolean includeBackground) {
        if (document == null) return null;
        int width = Math.round(document.pageWidth * scale);
        int height = Math.round(document.pageHeight * scale);
        if (width <= 0 || height <= 0) return null;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (includeBackground) {
            canvas.drawColor(palette.backdrop);
        } else {
            canvas.drawColor(Color.TRANSPARENT);
        }
        canvas.scale(scale, scale);

        CanvasRenderer.Options options = new CanvasRenderer.Options();
        options.chrome = false;
        options.includeBackground = true;
        options.showGrid = false;
        options.viewScale = scale;
        options.density = getResources().getDisplayMetrics().density;
        options.visibleRect = new RectF(0, 0, document.pageWidth, document.pageHeight);

        // Work on a copy: autosave must not see export-only mutations.
        CanvasDocument exportDocument = document;
        renderer.draw(canvas, exportDocument, new SelectionState(), palette, options, null);
        return bitmap;
    }

    public void clearSelection() {
        selection.clear();
    }

    public void selectAll() {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        for (CanvasElement element : document.elements()) ids.add(element.id);
        selection.replaceWith(ids);
        invalidate();
    }
}
