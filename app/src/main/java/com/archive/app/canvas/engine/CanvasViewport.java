package com.archive.app.canvas.engine;

import android.graphics.Matrix;
import android.graphics.RectF;

/**
 * Maps document space -> view space with a single matrix: screen = doc * scale + translate.
 * Page mode keeps the page centred and visible; infinite mode allows free roaming.
 */
public class CanvasViewport {

    private static final float MIN_SCALE = 0.08f;
    private static final float MAX_SCALE = 8f;

    public float scale = 1f;
    public float translateX = 0f;
    public float translateY = 0f;

    public int viewWidth = 0;
    public int viewHeight = 0;
    public float docWidth = 1240f;
    public float docHeight = 1754f;
    public boolean pageMode = true;

    private final Matrix matrix = new Matrix();
    private final RectF visible = new RectF();

    public void setViewportSize(int width, int height) {
        this.viewWidth = width;
        this.viewHeight = height;
    }

    public void setDocument(float width, float height, boolean pageMode) {
        this.docWidth = Math.max(1f, width);
        this.docHeight = Math.max(1f, height);
        this.pageMode = pageMode;
    }

    public Matrix matrix() {
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(translateX, translateY);
        return matrix;
    }

    public void set(Matrix value) {
        matrix.set(value);
        float[] values = new float[9];
        matrix.getValues(values);
        scale = values[Matrix.MSCALE_X];
        translateX = values[Matrix.MTRANS_X];
        translateY = values[Matrix.MTRANS_Y];
    }

    /** Fits the page (or a pleasant default area in infinite mode) into the current view. */
    public void fit() {
        if (viewWidth <= 0 || viewHeight <= 0) return;
        float targetWidth = pageMode ? docWidth : Math.max(docWidth, viewWidth * 1.6f);
        float targetHeight = pageMode ? docHeight : Math.max(docHeight, viewHeight * 1.6f);
        float margin = pageMode ? 0.92f : 1f;
        float fitScale = Math.min(viewWidth / targetWidth, viewHeight / targetHeight) * margin;
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, fitScale));
        translateX = (viewWidth - docWidth * scale) / 2f;
        translateY = (viewHeight - docHeight * scale) / 2f;
        clamp();
    }

    /** Zooms around a screen-space pivot (pinch focus or double-tap point). */
    public void zoomBy(float factor, float pivotX, float pivotY) {
        float newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
        float docX = (pivotX - translateX) / scale;
        float docY = (pivotY - translateY) / scale;
        scale = newScale;
        translateX = pivotX - docX * scale;
        translateY = pivotY - docY * scale;
        clamp();
    }

    public void zoomTo(float newScale, float pivotX, float pivotY) {
        zoomBy(newScale / scale, pivotX, pivotY);
    }

    public void panBy(float dx, float dy) {
        translateX += dx;
        translateY += dy;
        clamp();
    }

    /** Keeps the page on screen in page mode; allows generous roaming in infinite mode. */
    public void clamp() {
        if (viewWidth <= 0 || viewHeight <= 0) return;
        float contentWidth = docWidth * scale;
        float contentHeight = docHeight * scale;

        if (pageMode && contentWidth <= viewWidth) {
            translateX = (viewWidth - contentWidth) / 2f;
        } else {
            float marginX = pageMode ? 0f : viewWidth * 0.9f;
            float minX = viewWidth - contentWidth - marginX;
            float maxX = marginX;
            translateX = Math.min(maxX, Math.max(minX, translateX));
        }

        if (pageMode && contentHeight <= viewHeight) {
            translateY = (viewHeight - contentHeight) / 2f;
        } else {
            float marginY = pageMode ? 0f : viewHeight * 0.9f;
            float minY = viewHeight - contentHeight - marginY;
            float maxY = marginY;
            translateY = Math.min(maxY, Math.max(minY, translateY));
        }
    }

    public void screenToDoc(float[] point) {
        point[0] = (point[0] - translateX) / scale;
        point[1] = (point[1] - translateY) / scale;
    }

    public void docToScreen(float[] point) {
        point[0] = point[0] * scale + translateX;
        point[1] = point[1] * scale + translateY;
    }

    public float toScreen(float docLength) { return docLength * scale; }

    public float toDocument(float screenLength) { return screenLength / scale; }

    public RectF visibleDocumentRect() {
        visible.set(-translateX / scale, -translateY / scale,
                (viewWidth - translateX) / scale, (viewHeight - translateY) / scale);
        return visible;
    }

    /** Document-space rect of the page itself (used for the drop shadow and export bounds). */
    public RectF pageRect() {
        return new RectF(0, 0, docWidth, docHeight);
    }
}
