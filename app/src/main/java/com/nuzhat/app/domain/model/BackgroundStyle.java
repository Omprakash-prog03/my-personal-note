package com.nuzhat.app.domain.model;

import org.json.JSONException;
import org.json.JSONObject;

/** Page background, independent of every object. Serialised as JSON on the page row. */
public class BackgroundStyle {

    public enum Type { PLAIN, GRID, RULED, DOTS, VINTAGE, KRAFT, GRADIENT, IMAGE, CHARCOAL }

    public Type type = Type.PLAIN;
    public int color = 0xFFFDF6E8;
    public int color2 = 0xFFEFE2C6;
    public String mediaId;
    public String relativePath;
    public float gridSize = 32f;

    public static BackgroundStyle defaultFor(boolean dark) {
        BackgroundStyle style = new BackgroundStyle();
        style.type = Type.PLAIN;
        style.color = dark ? 0xFF241F1A : 0xFFFDF6E8;
        return style;
    }

    public static BackgroundStyle of(Type type, int color) {
        BackgroundStyle style = new BackgroundStyle();
        style.type = type;
        style.color = color;
        style.color2 = UiColors.lighten(color, 0.08f);
        return style;
    }

    public BackgroundStyle copy() {
        BackgroundStyle style = new BackgroundStyle();
        style.type = type;
        style.color = color;
        style.color2 = color2;
        style.mediaId = mediaId;
        style.relativePath = relativePath;
        style.gridSize = gridSize;
        return style;
    }

    public String toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("type", type.name());
            json.put("color", "#" + String.format("%08X", color));
            json.put("color2", "#" + String.format("%08X", color2));
            json.put("gridSize", gridSize);
            if (mediaId != null) json.put("mediaId", mediaId);
            if (relativePath != null) json.put("relativePath", relativePath);
        } catch (JSONException ignored) { }
        return json.toString();
    }

    public static BackgroundStyle fromJson(String raw) {
        BackgroundStyle style = new BackgroundStyle();
        if (raw == null || raw.isEmpty()) return style;
        try {
            JSONObject json = new JSONObject(raw);
            style.type = Type.valueOf(json.optString("type", "PLAIN"));
            style.color = parseColor(json.optString("color", "#FFFDF6E8"));
            style.color2 = parseColor(json.optString("color2", "#FFEFE2C6"));
            style.gridSize = (float) json.optDouble("gridSize", 32);
            style.mediaId = json.optString("mediaId", null);
            style.relativePath = json.optString("relativePath", null);
        } catch (Exception ignored) { }
        return style;
    }

    private static int parseColor(String value) {
        try {
            return (int) Long.parseLong(value.replace("#", ""), 16);
        } catch (Exception e) {
            return 0xFFFDF6E8;
        }
    }

    /** Tiny helper so BackgroundStyle stays free of Android UI imports. */
    static final class UiColors {
        static int lighten(int color, float amount) {
            int r = (int) (android.graphics.Color.red(color) + (255 - android.graphics.Color.red(color)) * amount);
            int g = (int) (android.graphics.Color.green(color) + (255 - android.graphics.Color.green(color)) * amount);
            int b = (int) (android.graphics.Color.blue(color) + (255 - android.graphics.Color.blue(color)) * amount);
            return android.graphics.Color.rgb(Math.min(255, r), Math.min(255, g), Math.min(255, b));
        }
    }
}
