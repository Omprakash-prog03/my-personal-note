package com.archive.app.domain.model;

import android.graphics.Matrix;
import android.graphics.RectF;

import com.archive.app.data.entity.CanvasElementEntity;
import com.archive.app.utils.Ids;

import org.json.JSONObject;

/**
 * Base of every canvas object.
 *
 * Geometry convention: (x, y, width, height) is the object's unrotated, unscaled rectangle in
 * document units. Scale and rotation are applied around its centre. {@link #renderMatrix()} and
 * {@link #hitTest} use the same maths, which is what keeps visual selection and touch targets in
 * agreement at any zoom.
 *
 * Type-specific fields live in a JSON property bag so that adding an object type never requires a
 * database migration, while geometry stays in real columns for cheap ordering and culling.
 */
public abstract class CanvasElement {

    public String id = Ids.newId();
    public float x = 0f;
    public float y = 0f;
    public float width = 300f;
    public float height = 300f;
    public float rotation = 0f;
    public float scale = 1f;
    public float opacity = 1f;
    public int zIndex = 0;
    public boolean visible = true;
    public boolean locked = false;
    public String groupId;
    public long createdAt = System.currentTimeMillis();
    public long updatedAt = System.currentTimeMillis();

    protected final JSONObject props = new JSONObject();

    public abstract ElementType type();

    protected abstract void writeProps(JSONObject json);

    protected abstract void readProps(JSONObject json);

    public abstract int defaultWidth();

    public abstract int defaultHeight();

    /** Human label for the layer list. */
    public abstract String label();

    /** Short text preview used by the layer list and search results. */
    public String previewText() { return ""; }

    // ---------------------------------------------------------------- property helpers

    protected String propString(String key, String fallback) {
        String value = props.optString(key, fallback);
        return value == null ? fallback : value;
    }

    protected void prop(String key, String value) {
        try {
            if (value == null) props.remove(key);
            else props.put(key, value);
        } catch (Exception ignored) { }
    }

    protected float propFloat(String key, float fallback) {
        return (float) props.optDouble(key, fallback);
    }

    protected void prop(String key, float value) {
        try { props.put(key, value); } catch (Exception ignored) { }
    }

    protected int propInt(String key, int fallback) {
        return props.optInt(key, fallback);
    }

    protected void prop(String key, int value) {
        try { props.put(key, value); } catch (Exception ignored) { }
    }

    protected boolean propBool(String key, boolean fallback) {
        return props.optBoolean(key, fallback);
    }

    protected void prop(String key, boolean value) {
        try { props.put(key, value); } catch (Exception ignored) { }
    }

    protected org.json.JSONArray propArray(String key) {
        org.json.JSONArray array = props.optJSONArray(key);
        return array == null ? new org.json.JSONArray() : array;
    }

    protected void prop(String key, org.json.JSONArray value) {
        try { props.put(key, value); } catch (Exception ignored) { }
    }

    public JSONObject propsCopy() {
        try {
            return new JSONObject(props.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    // ---------------------------------------------------------------- persistence

    public void loadFromEntity(CanvasElementEntity entity) {
        id = entity.id;
        x = entity.x;
        y = entity.y;
        width = entity.width <= 0 ? defaultWidth() : entity.width;
        height = entity.height <= 0 ? defaultHeight() : entity.height;
        rotation = entity.rotation;
        scale = entity.scale <= 0 ? 1f : entity.scale;
        opacity = entity.opacity <= 0 ? 1f : Math.min(1f, entity.opacity);
        zIndex = entity.zIndex;
        visible = entity.visible;
        locked = entity.locked;
        groupId = entity.groupId;
        createdAt = entity.createdAt;
        updatedAt = entity.updatedAt;
        try {
            readProps(entity.props == null || entity.props.isEmpty() ? new JSONObject() : new JSONObject(entity.props));
        } catch (Exception ignored) { }
    }

    /** Applies only the type-specific property bag (used by undo/redo snapshots). */
    public void applyPropsJson(String json) {
        try {
            readProps(json == null || json.isEmpty() ? new JSONObject() : new JSONObject(json));
        } catch (Exception ignored) { }
    }

    public CanvasElementEntity toEntity(String pageId) {
        CanvasElementEntity entity = new CanvasElementEntity();
        entity.id = id;
        entity.pageId = pageId;
        entity.type = type().name();
        entity.x = x;
        entity.y = y;
        entity.width = width;
        entity.height = height;
        entity.rotation = rotation;
        entity.scale = scale;
        entity.opacity = opacity;
        entity.zIndex = zIndex;
        entity.visible = visible;
        entity.locked = locked;
        entity.groupId = groupId;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        JSONObject json = new JSONObject();
        writeProps(json);
        entity.props = json.toString();
        return entity;
    }

    /** Deep copy with a fresh identity — used by duplicate and by copy/paste. */
    public CanvasElement duplicate() {
        CanvasElement copy = ElementFactory.create(type());
        copy.x = x + 28;
        copy.y = y + 28;
        copy.width = width;
        copy.height = height;
        copy.rotation = rotation;
        copy.scale = scale;
        copy.opacity = opacity;
        copy.zIndex = zIndex;
        copy.visible = visible;
        copy.locked = false;
        copy.groupId = null;
        copy.propsFrom(this);
        return copy;
    }

    protected void propsFrom(CanvasElement other) {
        JSONObject json = new JSONObject();
        other.writeProps(json);
        try {
            readProps(new JSONObject(json.toString()));
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- geometry

    public Matrix renderMatrix() {
        Matrix matrix = new Matrix();
        matrix.postTranslate(-width / 2f, -height / 2f);
        matrix.postScale(scale, scale);
        matrix.postRotate(rotation);
        matrix.postTranslate(x + width / 2f, y + height / 2f);
        return matrix;
    }

    public float halfScaledWidth() { return width * scale / 2f; }

    public float halfScaledHeight() { return height * scale / 2f; }

    /**
     * Rotated-rectangle hit test in document space. Slop is added in document units so tiny
     * objects remain tappable at low zoom.
     */
    public boolean hitTest(float px, float py, float slop) {
        float cx = x + width / 2f;
        float cy = y + height / 2f;
        float dx = px - cx;
        float dy = py - cy;
        double radians = Math.toRadians(-rotation);
        float rx = (float) (dx * Math.cos(radians) - dy * Math.sin(radians));
        float ry = (float) (dx * Math.sin(radians) + dy * Math.cos(radians));
        return Math.abs(rx) <= halfScaledWidth() + slop && Math.abs(ry) <= halfScaledHeight() + slop;
    }

    public RectF unscaledBounds() {
        return new RectF(x, y, x + width, y + height);
    }

    /** Axis-aligned bounding box of the rotated, scaled object (used for culling and marquee). */
    public void bounds(RectF out) {
        float cx = x + width / 2f;
        float cy = y + height / 2f;
        float hw = halfScaledWidth();
        float hh = halfScaledHeight();
        double radians = Math.toRadians(rotation);
        float cos = (float) Math.abs(Math.cos(radians));
        float sin = (float) Math.abs(Math.sin(radians));
        float ex = hw * cos + hh * sin;
        float ey = hw * sin + hh * cos;
        out.set(cx - ex, cy - ey, cx + ex, cy + ey);
    }

    /** Four corners of the object in document space, clockwise from top-left. */
    public void corners(float[] out) {
        float cx = x + width / 2f;
        float cy = y + height / 2f;
        float hw = halfScaledWidth();
        float hh = halfScaledHeight();
        double radians = Math.toRadians(rotation);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float[][] local = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};
        for (int i = 0; i < 4; i++) {
            out[i * 2] = cx + local[i][0] * cos - local[i][1] * sin;
            out[i * 2 + 1] = cy + local[i][0] * sin + local[i][1] * cos;
        }
    }

    public void centre(float[] out) {
        out[0] = x + width / 2f;
        out[1] = y + height / 2f;
    }

    public void moveBy(float dx, float dy) {
        x += dx;
        y += dy;
    }

    public boolean isEditable() {
        return visible && !locked;
    }
}
