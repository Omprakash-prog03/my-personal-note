package com.archive.app.domain.model.elements;

import com.archive.app.domain.model.CanvasElement;
import com.archive.app.domain.model.ElementType;

import org.json.JSONObject;

/** Rectangles, ellipses, triangles, lines, arrows, stars — the annotation vocabulary. */
public class ShapeElement extends CanvasElement {

    public static final String RECT = "RECT";
    public static final String ROUND_RECT = "ROUND_RECT";
    public static final String ELLIPSE = "ELLIPSE";
    public static final String CIRCLE = "CIRCLE";
    public static final String TRIANGLE = "TRIANGLE";
    public static final String LINE = "LINE";
    public static final String ARROW = "ARROW";
    public static final String DOUBLE_ARROW = "DOUBLE_ARROW";
    public static final String STAR = "STAR";
    public static final String BURST = "BURST";

    @Override public ElementType type() { return ElementType.SHAPE; }
    @Override public int defaultWidth() { return 320; }
    @Override public int defaultHeight() { return 220; }
    @Override public String label() { return "Shape"; }
    @Override public String previewText() { return getShape(); }

    @Override protected void writeProps(JSONObject json) {
        try {
            json.put("shape", getShape());
            json.put("fill", getFillColour());
            json.put("stroke", getStrokeColour());
            json.put("strokeWidth", getStrokeWidth());
            json.put("radius", getCornerRadius());
        } catch (Exception ignored) { }
    }

    @Override protected void readProps(JSONObject json) {
        setShape(json.optString("shape", RECT));
        setFillColour(json.optInt("fill", 0));
        setStrokeColour(json.optInt("stroke", 0xFF2C251C));
        setStrokeWidth((float) json.optDouble("strokeWidth", 3));
        setCornerRadius((float) json.optDouble("radius", 24));
    }

    public String getShape() { return propString("shape", RECT); }
    public void setShape(String value) { prop("shape", value == null ? RECT : value); }

    public int getFillColour() { return propInt("fill", 0); }
    public void setFillColour(int value) { prop("fill", value); }

    public int getStrokeColour() { return propInt("stroke", 0xFF2C251C); }
    public void setStrokeColour(int value) { prop("stroke", value); }

    public float getStrokeWidth() { return propFloat("strokeWidth", 3f); }
    public void setStrokeWidth(float value) { prop("strokeWidth", Math.max(0f, value)); }

    public float getCornerRadius() { return propFloat("radius", 24f); }
    public void setCornerRadius(float value) { prop("radius", Math.max(0f, value)); }
}
