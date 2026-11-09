package com.nuzhat.app.domain.model.elements;

import com.nuzhat.app.domain.model.CanvasElement;
import com.nuzhat.app.domain.model.ElementType;

import org.json.JSONObject;

/** Decorative sticker drawn from the offline vector catalogue (or a user PNG). */
public class StickerElement extends CanvasElement {

    @Override public ElementType type() { return ElementType.STICKER; }
    @Override public int defaultWidth() { return 170; }
    @Override public int defaultHeight() { return 170; }
    @Override public String label() { return "Sticker"; }
    @Override public String previewText() { return getStickerKey(); }

    @Override protected void writeProps(JSONObject json) {
        try {
            json.put("key", getStickerKey());
            json.put("tint", getTint());
            json.put("mediaId", getMediaId());
            json.put("relativePath", getRelativePath());
        } catch (Exception ignored) { }
    }

    @Override protected void readProps(JSONObject json) {
        setStickerKey(json.optString("key", "tape"));
        setTint(json.optInt("tint", 0));
        setMediaId(json.optString("mediaId", null));
        setRelativePath(json.optString("relativePath", null));
    }

    public String getStickerKey() { return propString("key", "tape"); }
    public void setStickerKey(String value) { prop("key", value); }

    public int getTint() { return propInt("tint", 0); }
    public void setTint(int value) { prop("tint", value); }

    /** Optional user-imported PNG sticker; when set it is drawn instead of the vector art. */
    public String getMediaId() { return propString("mediaId", null); }
    public void setMediaId(String value) { prop("mediaId", value); }

    public String getRelativePath() { return propString("relativePath", null); }
    public void setRelativePath(String value) { prop("relativePath", value); }
}
