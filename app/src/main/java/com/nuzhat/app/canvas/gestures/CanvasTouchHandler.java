package com.nuzhat.app.canvas.gestures;

import android.content.Context;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;

import com.nuzhat.app.canvas.commands.ElementAddCommand;
import com.nuzhat.app.canvas.commands.TransformCommand;
import com.nuzhat.app.canvas.engine.CanvasDocument;
import com.nuzhat.app.canvas.engine.CanvasView;
import com.nuzhat.app.canvas.engine.CanvasViewport;
import com.nuzhat.app.canvas.selection.SelectionState;
import com.nuzhat.app.domain.model.CanvasElement;
import com.nuzhat.app.domain.model.elements.DrawElement;
import com.nuzhat.app.domain.model.elements.TextElement;
import com.nuzhat.app.utils.Ids;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Touch state machine for the canvas.
 *
 * Key decisions:
 *  - Two fingers always mean "navigate"; objects are resized with handles, never by pinch, so a
 *    zoom can't accidentally deform artwork.
 *  - A drag starts only after the system touch slop, so tap-to-select never moves an object.
 *  - Every completed gesture becomes exactly one undo entry (captured at ACTION_DOWN), which keeps
 *    history small and autosave quiet during the drag itself.
 */
public class CanvasTouchHandler {

    public enum Tool { SELECT, TEXT, DRAW }

    public interface Listener {
        void onSelectionChanged();

        void onDocumentChanged();

        void onEditElement(CanvasElement element);

        void onObjectTapped(CanvasElement element, boolean longPress);

        void onEmptyTextPlacement(float docX, float docY);

        void onZoomChanged();

        void onRequestInvalidate();

        void onInvalidateRegion(float left, float top, float right, float bottom);

        /** A drag, resize or rotate gesture finished and became one undoable command. */
        void onTransformCommitted(String label);
    }

    private static final int MODE_NONE = 0;
    private static final int MODE_PAN_PENDING = 1;
    private static final int MODE_PAN = 2;
    private static final int MODE_MOVE_PENDING = 3;
    private static final int MODE_MOVE = 4;
    private static final int MODE_SCALE = 5;
    private static final int MODE_ROTATE = 6;
    private static final int MODE_PINCH = 7;
    private static final int MODE_DRAW = 8;
    private static final int MODE_MARQUEE = 9;

    private static final int HANDLE_ROTATE = 4;
    private static final float MIN_SIZE_UNITS = 18f;
    private static final float GRID_STEP = 20f;

    private final CanvasView view;
    private final CanvasViewport viewport;
    private final SelectionState selection;
    private final Listener listener;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private final int touchSlop;
    private final float density;

    private Tool tool = Tool.SELECT;
    private boolean snapToGrid = false;
    private int drawColour = 0xFF2C251C;
    private float drawWidth = 5f;
    private String brush = DrawElement.TOOL_PEN;

    private int mode = MODE_NONE;
    private int handleIndex = -1;
    private float lastScreenX, lastScreenY;
    private float downScreenX, downScreenY;
    private float lastDocX, lastDocY;
    private float downDocX, downDocY;

    private final List<CanvasElement> transformTargets = new ArrayList<>();
    private Map<String, TransformCommand.Geometry> transformBefore;
    private float fixedCornerX, fixedCornerY;
    private float startAngle;
    private float startRotation;
    private float startWidth, startHeight;

    private DrawElement inkLayer;
    private DrawElement.Stroke activeStroke;

    private RectF marquee;
    private boolean tapCandidate = true;

    public CanvasTouchHandler(CanvasView view, CanvasViewport viewport, SelectionState selection,
                              Listener listener) {
        this.view = view;
        this.viewport = viewport;
        this.selection = selection;
        this.listener = listener;
        Context context = view.getContext();
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.density = context.getResources().getDisplayMetrics().density;

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                if (mode == MODE_MOVE || mode == MODE_SCALE || mode == MODE_ROTATE) commitTransform();
                mode = MODE_PINCH;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                viewport.zoomBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                listener.onRequestInvalidate();
                listener.onZoomChanged();
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                mode = MODE_NONE;
                listener.onZoomChanged();
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent event) {
                CanvasDocument document = view.document();
                if (document == null) return false;
                if (!selection.isEmpty()) {
                    for (CanvasElement element : selection.elements(document)) {
                        listener.onEditElement(element);
                        break;
                    }
                } else {
                    viewport.zoomBy(1.6f, event.getX(), event.getY());
                    listener.onZoomChanged();
                    listener.onRequestInvalidate();
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent event) {
                CanvasDocument document = view.document();
                if (document == null) return;
                if (mode == MODE_MOVE_PENDING) {
                    CanvasElement element = document.byId(selection.single());
                    if (element != null) listener.onObjectTapped(element, true);
                    mode = MODE_NONE;
                } else if (mode == MODE_PAN_PENDING) {
                    mode = MODE_MARQUEE;
                    marquee = new RectF(downDocX, downDocY, downDocX, downDocY);
                    listener.onRequestInvalidate();
                }
            }
        });
        gestureDetector.setIsLongpressEnabled(true);
    }

    public Tool getTool() { return tool; }

    public void setTool(Tool value) {
        this.tool = value == null ? Tool.SELECT : value;
    }

    public void setSnapToGrid(boolean value) { snapToGrid = value; }

    public void setDrawStyle(int colour, float width, String brush) {
        this.drawColour = colour;
        this.drawWidth = Math.max(1f, width);
        this.brush = brush;
    }

    public String brush() { return brush; }

    public float drawWidth() { return drawWidth; }

    public int drawColour() { return drawColour; }

    public RectF marqueeRect() { return marquee; }

    // ------------------------------------------------------------------ events

    public boolean onTouchEvent(MotionEvent event) {
        CanvasDocument document = view.document();
        if (document == null) return false;
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return handleDown(document, event);
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mode == MODE_MOVE || mode == MODE_SCALE || mode == MODE_ROTATE
                        || mode == MODE_DRAW) {
                    commitTransform();
                }
                mode = MODE_PINCH;
                return true;
            case MotionEvent.ACTION_MOVE:
                return handleMove(document, event);
            case MotionEvent.ACTION_POINTER_UP:
                return true;
            case MotionEvent.ACTION_UP:
                return handleUp(document, event);
            case MotionEvent.ACTION_CANCEL:
                cancel();
                return true;
            default:
                return true;
        }
    }

    private boolean handleDown(CanvasDocument document, MotionEvent event) {
        downScreenX = event.getX();
        downScreenY = event.getY();
        lastScreenX = downScreenX;
        lastScreenY = downScreenY;
        float[] doc = {downScreenX, downScreenY};
        viewport.screenToDoc(doc);
        downDocX = doc[0];
        downDocY = doc[1];
        lastDocX = doc[0];
        lastDocY = doc[1];
        tapCandidate = true;
        marquee = null;

        if (tool == Tool.DRAW) {
            startInk(document, doc[0], doc[1]);
            return true;
        }

        CanvasElement selected = selection.single() == null ? null : document.byId(selection.single());
        if (selected != null && selected.isEditable()) {
            int handle = hitHandle(selected, event.getX(), event.getY());
            if (handle == HANDLE_ROTATE) {
                startRotation(selected, doc[0], doc[1]);
                return true;
            }
            if (handle >= 0) {
                startScale(selected, handle);
                return true;
            }
        }

        CanvasElement hit = topmostAt(document, doc[0], doc[1]);
        if (hit != null) {
            if (!selection.contains(hit.id)) {
                if (hit.groupId != null) {
                    selection.replaceWith(document.groupMembers(hit.id));
                } else {
                    selection.select(hit.id);
                }
                listener.onSelectionChanged();
            }
            transformTargets.clear();
            transformTargets.addAll(selection.elements(document));
            transformBefore = TransformCommand.capture(transformTargets);
            mode = MODE_MOVE_PENDING;
            return true;
        }

        selection.clear();
        listener.onSelectionChanged();
        mode = MODE_PAN_PENDING;
        return true;
    }

    private boolean handleMove(CanvasDocument document, MotionEvent event) {
        if (mode == MODE_PINCH || event.getPointerCount() > 1) return true;

        float x = event.getX();
        float y = event.getY();
        float[] doc = {x, y};
        viewport.screenToDoc(doc);

        switch (mode) {
            case MODE_PAN_PENDING:
                if (Math.hypot(x - downScreenX, y - downScreenY) > touchSlop) mode = MODE_PAN;
                break;
            case MODE_MOVE_PENDING:
                if (Math.hypot(x - downScreenX, y - downScreenY) > touchSlop) mode = MODE_MOVE;
                break;
            case MODE_PAN:
                viewport.panBy(x - lastScreenX, y - lastScreenY);
                listener.onRequestInvalidate();
                break;
            case MODE_MOVE:
                moveSelection(doc[0] - lastDocX, doc[1] - lastDocY);
                break;
            case MODE_SCALE:
                resizeSelection(document, doc[0], doc[1]);
                break;
            case MODE_ROTATE:
                rotateSelection(document, doc[0], doc[1]);
                break;
            case MODE_MARQUEE:
                if (marquee != null) {
                    marquee.set(Math.min(downDocX, doc[0]), Math.min(downDocY, doc[1]),
                            Math.max(downDocX, doc[0]), Math.max(downDocY, doc[1]));
                    listener.onRequestInvalidate();
                }
                break;
            case MODE_DRAW:
                appendInk(document, doc[0], doc[1]);
                break;
            case MODE_NONE:
            default:
                break;
        }

        lastScreenX = x;
        lastScreenY = y;
        lastDocX = doc[0];
        lastDocY = doc[1];
        return true;
    }

    private boolean handleUp(CanvasDocument document, MotionEvent event) {
        switch (mode) {
            case MODE_DRAW:
                finishInk(document);
                break;
            case MODE_MOVE:
            case MODE_SCALE:
            case MODE_ROTATE:
                commitTransform();
                break;
            case MODE_MARQUEE:
                finishMarquee(document);
                break;
            case MODE_MOVE_PENDING:
                if (tapCandidate && !selection.isEmpty()) {
                    CanvasElement element = document.byId(selection.single());
                    if (element != null) {
                        if (tool == Tool.TEXT && element instanceof TextElement) {
                            listener.onEditElement(element);
                        } else {
                            listener.onObjectTapped(element, false);
                        }
                    }
                }
                break;
            case MODE_PAN_PENDING:
                if (tool == Tool.TEXT) {
                    listener.onEmptyTextPlacement(downDocX, downDocY);
                }
                break;
            default:
                break;
        }
        mode = MODE_NONE;
        handleIndex = -1;
        marquee = null;
        listener.onRequestInvalidate();
        return true;
    }

    private void cancel() {
        if ((mode == MODE_MOVE || mode == MODE_SCALE || mode == MODE_ROTATE) && transformBefore != null) {
            CanvasDocument document = view.document();
            if (document != null) {
                for (Map.Entry<String, TransformCommand.Geometry> entry : transformBefore.entrySet()) {
                    CanvasElement element = document.byId(entry.getKey());
                    if (element == null) continue;
                    TransformCommand.Geometry g = entry.getValue();
                    element.x = g.x;
                    element.y = g.y;
                    element.width = g.width;
                    element.height = g.height;
                    element.rotation = g.rotation;
                    element.scale = g.scale;
                }
            }
        }
        if (mode == MODE_DRAW && inkLayer != null) {
            CanvasDocument document = view.document();
            if (document != null) document.remove(inkLayer);
        }
        inkLayer = null;
        activeStroke = null;
        marquee = null;
        mode = MODE_NONE;
        listener.onRequestInvalidate();
    }

    // ------------------------------------------------------------------ transform

    private void moveSelection(float dx, float dy) {
        if (transformTargets.isEmpty()) return;
        CanvasElement primary = transformTargets.get(0);
        for (CanvasElement element : transformTargets) element.moveBy(dx, dy);
        if (snapToGrid) {
            float snappedX = Math.round(primary.x / GRID_STEP) * GRID_STEP;
            float snappedY = Math.round(primary.y / GRID_STEP) * GRID_STEP;
            float offsetX = snappedX - primary.x;
            float offsetY = snappedY - primary.y;
            if (Math.abs(offsetX) < 1f && Math.abs(offsetY) < 1f) {
                for (CanvasElement element : transformTargets) element.moveBy(offsetX, offsetY);
            }
        }
        RectF dirty = new RectF();
        for (CanvasElement element : transformTargets) {
            element.bounds(dirty);
            listener.onInvalidateRegion(dirty.left - 8, dirty.top - 8, dirty.right + 8, dirty.bottom + 8);
        }
    }

    private void startScale(CanvasElement element, int handle) {
        mode = MODE_SCALE;
        handleIndex = handle;
        startWidth = element.width;
        startHeight = element.height;
        startRotation = element.rotation;
        float[] corners = new float[8];
        element.corners(corners);
        int fixed = (handle + 2) % 4;
        fixedCornerX = corners[fixed * 2];
        fixedCornerY = corners[fixed * 2 + 1];
        transformTargets.clear();
        transformTargets.add(element);
        transformBefore = TransformCommand.capture(transformTargets);
    }

    private void resizeSelection(CanvasDocument document, float px, float py) {
        if (transformTargets.isEmpty()) return;
        CanvasElement element = transformTargets.get(0);
        double radians = Math.toRadians(startRotation);
        float ux = (float) Math.cos(radians);
        float uy = (float) Math.sin(radians);
        float vx = -uy;
        float vy = ux;

        float dx = px - fixedCornerX;
        float dy = py - fixedCornerY;
        float du = dx * ux + dy * uy;
        float dv = dx * vx + dy * vy;

        float signU = (handleIndex == 1 || handleIndex == 2) ? 1f : -1f;
        float signV = (handleIndex == 2 || handleIndex == 3) ? 1f : -1f;

        float scaleFactor = element.scale <= 0.01f ? 1f : element.scale;
        float scaledW = Math.max(MIN_SIZE_UNITS * scaleFactor, du * signU);
        float scaledH = Math.max(MIN_SIZE_UNITS * scaleFactor, dv * signV);

        float newWidth = scaledW / scaleFactor;
        float newHeight = scaledH / scaleFactor;
        if (snapToGrid) {
            newWidth = Math.max(MIN_SIZE_UNITS, Math.round(newWidth / GRID_STEP) * GRID_STEP);
            newHeight = Math.max(MIN_SIZE_UNITS, Math.round(newHeight / GRID_STEP) * GRID_STEP);
        }

        float centreX = fixedCornerX + ux * (scaledW / 2f) * signU + vx * (scaledH / 2f) * signV;
        float centreY = fixedCornerY + uy * (scaledW / 2f) * signU + vy * (scaledH / 2f) * signV;

        float minW = newWidth * scaleFactor;
        float minH = newHeight * scaleFactor;
        element.width = newWidth;
        element.height = newHeight;
        element.x = centreX - minW / 2f;
        element.y = centreY - minH / 2f;

        RectF dirty = new RectF();
        element.bounds(dirty);
        listener.onInvalidateRegion(dirty.left - 16, dirty.top - 16, dirty.right + 16, dirty.bottom + 16);
        document.markDirty(element);
    }

    private void startRotation(CanvasElement element, float px, float py) {
        mode = MODE_ROTATE;
        startRotation = element.rotation;
        float cx = element.x + element.width / 2f;
        float cy = element.y + element.height / 2f;
        startAngle = (float) Math.toDegrees(Math.atan2(py - cy, px - cx));
        transformTargets.clear();
        transformTargets.add(element);
        transformBefore = TransformCommand.capture(transformTargets);
    }

    private void rotateSelection(CanvasDocument document, float px, float py) {
        if (transformTargets.isEmpty()) return;
        CanvasElement element = transformTargets.get(0);
        float cx = element.x + element.width / 2f;
        float cy = element.y + element.height / 2f;
        float angle = (float) Math.toDegrees(Math.atan2(py - cy, px - cx));
        float rotation = startRotation + (angle - startAngle);
        float snapped = Math.round(rotation / 15f) * 15f;
        if (Math.abs(rotation - snapped) < 4f) rotation = snapped;
        while (rotation < 0) rotation += 360f;
        while (rotation >= 360f) rotation -= 360f;
        element.rotation = rotation;
        document.markDirty(element);
        RectF dirty = new RectF();
        element.bounds(dirty);
        listener.onInvalidateRegion(dirty.left - 24, dirty.top - 24, dirty.right + 24, dirty.bottom + 24);
    }

    private void commitTransform() {
        CanvasDocument document = view.document();
        if (document == null || transformBefore == null || transformTargets.isEmpty()) {
            mode = MODE_NONE;
            return;
        }
        for (CanvasElement element : transformTargets) document.markDirty(element);
        document.touch();
        String label = mode == MODE_SCALE ? "Resize object"
                : mode == MODE_ROTATE ? "Rotate object" : "Move object";
        TransformCommand command = new TransformCommand(document, transformTargets, transformBefore, label);
        document.history.record(command);
        listener.onDocumentChanged();
        // Only now, at the end of the gesture, is it a completed action worth a history row.
        listener.onTransformCommitted(label);
        mode = MODE_NONE;
        transformBefore = null;
        transformTargets.clear();
    }

    // ------------------------------------------------------------------ drawing

    private void startInk(CanvasDocument document, float px, float py) {
        inkLayer = new DrawElement();
        inkLayer.id = Ids.newId();
        inkLayer.width = Math.max(1, document.pageWidth);
        inkLayer.height = Math.max(1, document.pageHeight);
        inkLayer.x = 0;
        inkLayer.y = 0;
        inkLayer.setTool(brush);
        inkLayer.zIndex = document.nextZIndex();
        document.add(inkLayer);

        activeStroke = new DrawElement.Stroke();
        activeStroke.colour = drawColour;
        activeStroke.width = drawWidth;
        activeStroke.tool = brush;
        activeStroke.add(px, py);
        inkLayer.addStroke(activeStroke);
        mode = MODE_DRAW;
        listener.onRequestInvalidate();
    }

    private void appendInk(CanvasDocument document, float px, float py) {
        if (activeStroke == null) return;
        int size = activeStroke.points.size();
        if (size >= 2) {
            float lastX = activeStroke.points.get(size - 2);
            float lastY = activeStroke.points.get(size - 1);
            if (Math.hypot(px - lastX, py - lastY) < 1.2f) return;
        }
        activeStroke.add(px, py);
        document.markDirty(inkLayer);
        listener.onInvalidateRegion(px - drawWidth * 3, py - drawWidth * 3,
                px + drawWidth * 3, py + drawWidth * 3);
    }

    private void finishInk(CanvasDocument document) {
        if (inkLayer == null) {
            mode = MODE_NONE;
            return;
        }
        if (activeStroke != null && activeStroke.pointCount() < 1) {
            document.remove(inkLayer);
        } else {
            document.markDirty(inkLayer);
            document.history.record(new ElementAddCommand(document, inkLayer, "Draw strokes"));
            listener.onDocumentChanged();
            selection.select(inkLayer.id);
            listener.onSelectionChanged();
        }
        inkLayer = null;
        activeStroke = null;
        mode = MODE_NONE;
        listener.onRequestInvalidate();
    }

    // ------------------------------------------------------------------ helpers

    private void finishMarquee(CanvasDocument document) {
        if (marquee == null || marquee.width() < 6 || marquee.height() < 6) {
            marquee = null;
            return;
        }
        Set<String> hits = new LinkedHashSet<>();
        RectF bounds = new RectF();
        for (CanvasElement element : document.elements()) {
            if (!element.visible || element.locked) continue;
            element.bounds(bounds);
            if (RectF.intersects(bounds, marquee)) hits.add(element.id);
        }
        selection.replaceWith(hits);
        listener.onSelectionChanged();
        marquee = null;
        listener.onRequestInvalidate();
    }

    private CanvasElement topmostAt(CanvasDocument document, float docX, float docY) {
        List<CanvasElement> elements = document.elements();
        float slop = viewport.toDocument(6f * density);
        for (int i = elements.size() - 1; i >= 0; i--) {
            CanvasElement element = elements.get(i);
            if (!element.visible) continue;
            if (element.hitTest(docX, docY, slop)) return element;
        }
        return null;
    }

    private int hitHandle(CanvasElement element, float screenX, float screenY) {
        float[] corners = new float[8];
        element.corners(corners);
        float radius = 22f * density;
        for (int i = 0; i < 4; i++) {
            float[] point = {corners[i * 2], corners[i * 2 + 1]};
            viewport.docToScreen(point);
            if (Math.hypot(point[0] - screenX, point[1] - screenY) <= radius) return i;
        }
        // Rotate handle above the top edge.
        float topMidX = (corners[0] + corners[2]) / 2f;
        float topMidY = (corners[1] + corners[3]) / 2f;
        float edgeX = corners[2] - corners[0];
        float edgeY = corners[3] - corners[1];
        float length = (float) Math.hypot(edgeX, edgeY);
        if (length > 0.001f) {
            float upX = edgeY / length;
            float upY = -edgeX / length;
            float offset = viewport.toDocument(46f * density);
            float[] point = {topMidX + upX * offset, topMidY + upY * offset};
            viewport.docToScreen(point);
            if (Math.hypot(point[0] - screenX, point[1] - screenY) <= radius * 1.2f) {
                return HANDLE_ROTATE;
            }
        }
        return -1;
    }
}
