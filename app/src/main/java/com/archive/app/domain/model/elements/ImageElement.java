package com.archive.app.domain.model.elements;

import com.archive.app.domain.model.CanvasElement;
import com.archive.app.domain.model.ElementType;

import org.json.JSONObject;

/**
 * Photograph or screenshot placed on the page. Cropping is stored as a normalised source rectangle,
 * so the original file is never re-encoded or overwritten.
 */
public class ImageElement extends CanvasElement {

    public static final String FRAME_PLAIN = "PLAIN";
    public static final String FRAME_ROUNDED = "ROUNDED";
    public static final String FRAME_POLAROID = "POLAROID";
    public static final String FRAME_PAPER = "PAPER";
    public static final String FRAME_FILM = "FILM";
    public static final String FRAME_BORDERLESS = "BORDERLESS";

    @Override public ElementType type() { return ElementType.IMAGE; }
    @Override public int defaultWidth() { return 520; }
    @Override public int defaultHeight() { return 390; }
    @Override public String label() { return "Image"; }
    @Override public String previewText() { return getDisplayName(); }

    @Override protected void writeProps(JSONObject json) {
        try {
            json.put("mediaId", getMediaId());
            json.put("relativePath", getRelativePath());
            json.put("displayName", getDisplayName());
            json.put("frame", getFrame());
            json.put("flipH", isFlippedHorizontally());
            json.put("flipV", isFlippedVertically());
            json.put("radius", getCornerRadius());
            json.put("borderWidth", getBorderWidth());
            json.put("borderColour", getBorderColour());
            json.put("shadow", hasShadow());
            json.put("cropLeft", getCropLeft());
            json.put("cropTop", getCropTop());
            json.put("cropRight", getCropRight());
            json.put("cropBottom", getCropBottom());
        } catch (Exception ignored) { }
    }

    @Override protected void readProps(JSONObject json) {
        setMediaId(json.optString("mediaId", null));
        setRelativePath(json.optString("relativePath", null));
        setDisplayName(json.optString("displayName", ""));
        setFrame(json.optString("frame", FRAME_PAPER));
        setFlippedHorizontally(json.optBoolean("flipH", false));
        setFlippedVertically(json.optBoolean("flipV", false));
        setCornerRadius((float) json.optDouble("radius", 4));
        setBorderWidth((float) json.optDouble("borderWidth", 0));
        setBorderColour(json.optInt("borderColour", 0xFFFDF7EA));
        setShadow(json.optBoolean("shadow", true));
        setCrop((float) json.optDouble("cropLeft", 0), (float) json.optDouble("cropTop", 0),
                (float) json.optDouble("cropRight", 1), (float) json.optDouble("cropBottom", 1));
    }

    public String getMediaId() { return propString("mediaId", null); }
    public void setMediaId(String value) { prop("mediaId", value); }

    public String getRelativePath() { return propString("relativePath", null); }
    public void setRelativePath(String value) { prop("relativePath", value); }

    public String getDisplayName() { return propString("displayName", ""); }
    public void setDisplayName(String value) { prop("displayName", value); }

    public String getFrame() { return propString("frame", FRAME_PAPER); }
    public void setFrame(String value) { prop("frame", value == null ? FRAME_PAPER : value); }

    public boolean isFlippedHorizontally() { return propBool("flipH", false); }
    public void setFlippedHorizontally(boolean value) { prop("flipH", value); }

    public boolean isFlippedVertically() { return propBool("flipV", false); }
    public void setFlippedVertically(boolean value) { prop("flipV", value); }

    public float getCornerRadius() { return propFloat("radius", 4f); }
    public void setCornerRadius(float value) { prop("radius", Math.max(0f, value)); }

    public float getBorderWidth() { return propFloat("borderWidth", 0f); }
    public void setBorderWidth(float value) { prop("borderWidth", Math.max(0f, value)); }

    public int getBorderColour() { return propInt("borderColour", 0xFFFDF7EA); }
    public void setBorderColour(int value) { prop("borderColour", value); }

    public boolean hasShadow() { return propBool("shadow", true); }
    public void setShadow(boolean value) { prop("shadow", value); }

    public float getCropLeft() { return propFloat("cropLeft", 0f); }
    public float getCropTop() { return propFloat("cropTop", 0f); }
    public float getCropRight() { return propFloat("cropRight", 1f); }
    public float getCropBottom() { return propFloat("cropBottom", 1f); }

    public void setCrop(float left, float top, float right, float bottom) {
        prop("cropLeft", clamp01(left));
        prop("cropTop", clamp01(top));
        prop("cropRight", Math.max(clamp01(left) + 0.05f, clamp01(right)));
        prop("cropBottom", Math.max(clamp01(top) + 0.05f, clamp01(bottom)));
    }

    /** Centre-crops the visible region to the requested aspect ratio without touching the file. */
    public void cropToAspect(float targetAspect, int sourceWidth, int sourceHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0) return;
        float sourceAspect = sourceWidth / (float) sourceHeight;
        if (sourceAspect > targetAspect) {
            float visible = targetAspect / sourceAspect;
            float half = visible / 2f;
            setCrop(0.5f - half, 0f, 0.5f + half, 1f);
        } else {
            float visible = sourceAspect / targetAspect;
            float half = visible / 2f;
            setCrop(0f, 0.5f - half, 1f, 0.5f + half);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
