package com.archive.app.domain.model.elements;

import com.archive.app.domain.model.CanvasElement;
import com.archive.app.domain.model.ElementType;

import org.json.JSONObject;

public class StickyNoteElement extends CanvasElement {

    @Override public ElementType type() { return ElementType.STICKY_NOTE; }
    @Override public int defaultWidth() { return 280; }
    @Override public int defaultHeight() { return 280; }
    @Override public String label() { return "Sticky note"; }
    @Override public String previewText() { return getText(); }

    @Override protected void writeProps(JSONObject json) {
        try {
            json.put("text", getText());
            json.put("fill", getFillColour());
            json.put("textColour", getTextColour());
            json.put("folded", hasFoldedCorner());
            json.put("fontSize", getFontSize());
        } catch (Exception ignored) { }
    }

    @Override protected void readProps(JSONObject json) {
        setText(json.optString("text", ""));
        setFillColour(json.optInt("fill", 0xFFF6E7A8));
        setTextColour(json.optInt("textColour", 0xFF3A3126));
        setFoldedCorner(json.optBoolean("folded", true));
        setFontSize((float) json.optDouble("fontSize", 26));
    }

    public String getText() { return propString("text", ""); }
    public void setText(String value) { prop("text", value); }

    public int getFillColour() { return propInt("fill", 0xFFF6E7A8); }
    public void setFillColour(int value) { prop("fill", value); }

    public int getTextColour() { return propInt("textColour", 0xFF3A3126); }
    public void setTextColour(int value) { prop("textColour", value); }

    public boolean hasFoldedCorner() { return propBool("folded", true); }
    public void setFoldedCorner(boolean value) { prop("folded", value); }

    public float getFontSize() { return propFloat("fontSize", 26f); }
    public void setFontSize(float value) { prop("fontSize", value); }
}
