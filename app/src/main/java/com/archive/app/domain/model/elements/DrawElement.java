package com.archive.app.domain.model.elements;

import com.archive.app.domain.model.CanvasElement;
import com.archive.app.domain.model.ElementType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Handwriting / drawing. Individual strokes with their own colour, width, alpha and tool are kept as
 * vectors — nothing is flattened into a bitmap, so strokes stay editable and crisp at any zoom.
 */
public class DrawElement extends CanvasElement {

    public static final String TOOL_PEN = "PEN";
    public static final String TOOL_PENCIL = "PENCIL";
    public static final String TOOL_MARKER = "MARKER";
    public static final String TOOL_HIGHLIGHTER = "HIGHLIGHTER";
    public static final String TOOL_LINE = "LINE";
    public static final String TOOL_ARROW = "ARROW";
    public static final String TOOL_ERASER = "ERASER";

    private final List<Stroke> strokes = new ArrayList<>();

    @Override public ElementType type() { return ElementType.DRAWING; }
    @Override public int defaultWidth() { return 600; }
    @Override public int defaultHeight() { return 420; }
    @Override public String label() { return "Drawing"; }
    @Override public String previewText() { return strokes.size() + " strokes"; }

    @Override protected void writeProps(JSONObject json) {
        try {
            json.put("tool", getTool());
            JSONArray array = new JSONArray();
            for (Stroke stroke : strokes) array.put(stroke.toJson());
            json.put("strokes", array);
        } catch (Exception ignored) { }
    }

    @Override protected void readProps(JSONObject json) {
        setTool(json.optString("tool", TOOL_PEN));
        strokes.clear();
        JSONArray array = json.optJSONArray("strokes");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject strokeJson = array.optJSONObject(i);
                if (strokeJson != null) strokes.add(Stroke.fromJson(strokeJson));
            }
        }
    }

    public static class Stroke {
        public int colour = 0xFF2C251C;
        public float width = 4f;
        public int alpha = 255;
        public String tool = TOOL_PEN;
        public final List<Float> points = new ArrayList<>();

        public Stroke copy() {
            Stroke copy = new Stroke();
            copy.colour = colour;
            copy.width = width;
            copy.alpha = alpha;
            copy.tool = tool;
            copy.points.addAll(points);
            return copy;
        }

        public void add(float px, float py) {
            points.add(px);
            points.add(py);
        }

        public int pointCount() { return points.size() / 2; }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("colour", colour);
                json.put("width", width);
                json.put("alpha", alpha);
                json.put("tool", tool);
                JSONArray array = new JSONArray();
                for (float value : points) array.put(value);
                json.put("points", array);
            } catch (Exception ignored) { }
            return json;
        }

        public static Stroke fromJson(JSONObject json) {
            Stroke stroke = new Stroke();
            stroke.colour = json.optInt("colour", 0xFF2C251C);
            stroke.width = (float) json.optDouble("width", 4);
            stroke.alpha = json.optInt("alpha", 255);
            stroke.tool = json.optString("tool", TOOL_PEN);
            JSONArray array = json.optJSONArray("points");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    stroke.points.add((float) array.optDouble(i, 0));
                }
            }
            return stroke;
        }
    }

    public List<Stroke> getStrokes() { return strokes; }

    public void addStroke(Stroke stroke) { strokes.add(stroke); }

    public void clearStrokes() { strokes.clear(); }

    public String getTool() { return propString("tool", TOOL_PEN); }

    public void setTool(String value) { prop("tool", value == null ? TOOL_PEN : value); }

    public boolean isEmpty() { return strokes.isEmpty(); }
}
