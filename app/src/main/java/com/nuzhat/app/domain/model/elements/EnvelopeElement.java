package com.nuzhat.app.domain.model.elements;

import com.nuzhat.app.domain.model.CanvasElement;
import com.nuzhat.app.domain.model.ElementType;

import org.json.JSONObject;

/**
 * A meaningful interactive paper object: tapping opens the envelope and reveals the message
 * and/or image stored inside it.
 */
public class EnvelopeElement extends CanvasElement {

    @Override public ElementType type() { return ElementType.ENVELOPE; }
    @Override public int defaultWidth() { return 340; }
    @Override public int defaultHeight() { return 230; }
    @Override public String label() { return "Envelope"; }
    @Override public String previewText() { return getLabel(); }

    @Override protected void writeProps(JSONObject json) {
        try {
            json.put("label", getLabel());
            json.put("message", getMessage());
            json.put("fill", getFillColour());
            json.put("opened", isOpened());
            json.put("imageMediaId", getImageMediaId());
            json.put("imageRelativePath", getImageRelativePath());
            json.put("sealed", isSealed());
        } catch (Exception ignored) { }
    }

    @Override protected void readProps(JSONObject json) {
        setLabel(json.optString("label", "A note"));
        setMessage(json.optString("message", ""));
        setFillColour(json.optInt("fill", 0xFFD8C0A0));
        setOpened(json.optBoolean("opened", false));
        setImageMediaId(json.optString("imageMediaId", null));
        setImageRelativePath(json.optString("imageRelativePath", null));
        setSealed(json.optBoolean("sealed", true));
    }

    public String getLabel() { return propString("label", "A note"); }
    public void setLabel(String value) { prop("label", value); }

    public String getMessage() { return propString("message", ""); }
    public void setMessage(String value) { prop("message", value); }

    public int getFillColour() { return propInt("fill", 0xFFD8C0A0); }
    public void setFillColour(int value) { prop("fill", value); }

    public boolean isOpened() { return propBool("opened", false); }
    public void setOpened(boolean value) { prop("opened", value); }

    public boolean isSealed() { return propBool("sealed", true); }
    public void setSealed(boolean value) { prop("sealed", value); }

    public String getImageMediaId() { return propString("imageMediaId", null); }
    public void setImageMediaId(String value) { prop("imageMediaId", value); }

    public String getImageRelativePath() { return propString("imageRelativePath", null); }
    public void setImageRelativePath(String value) { prop("imageRelativePath", value); }

    public boolean hasContents() { return !getMessage().isEmpty() || getImageMediaId() != null; }
}
