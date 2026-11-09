package com.nuzhat.app.domain.model.elements;

import com.nuzhat.app.domain.model.CanvasElement;
import com.nuzhat.app.domain.model.ElementType;

import org.json.JSONObject;

/** First-class text object: multiline, styleable, never merged into the background. */
public class TextElement extends CanvasElement {

    public static final String ALIGN_LEFT = "left";
    public static final String ALIGN_CENTER = "center";
    public static final String ALIGN_RIGHT = "right";
    public static final String FONT_SERIF = "serif";
    public static final String FONT_SANS = "sans";

    @Override public ElementType type() { return ElementType.TEXT; }
    @Override public int defaultWidth() { return 520; }
    @Override public int defaultHeight() { return 170; }
    @Override public String label() { return "Text"; }
    @Override public String previewText() { return getText(); }

    @Override protected void writeProps(JSONObject json) {
        try {
            json.put("text", getText());
            json.put("fontSize", getFontSize());
            json.put("colour", getColour());
            json.put("bold", isBold());
            json.put("italic", isItalic());
            json.put("underline", isUnderline());
            json.put("strike", isStrike());
            json.put("align", getAlign());
            json.put("lineSpacing", getLineSpacing());
            json.put("letterSpacing", getLetterSpacing());
            json.put("padding", getPadding());
            json.put("bgColour", getBackgroundColour());
            json.put("highlight", getHighlightColour());
            json.put("borderColour", getBorderColour());
            json.put("borderWidth", getBorderWidth());
            json.put("shadow", hasShadow());
            json.put("font", getFontFamily());
            json.put("autoHeight", autoHeight());
        } catch (Exception ignored) { }
    }

    @Override protected void readProps(JSONObject json) {
        setText(json.optString("text", ""));
        setFontSize((float) json.optDouble("fontSize", 32));
        setColour(json.optInt("colour", 0xFF2C251C));
        setBold(json.optBoolean("bold", false));
        setItalic(json.optBoolean("italic", false));
        setUnderline(json.optBoolean("underline", false));
        setStrike(json.optBoolean("strike", false));
        setAlign(json.optString("align", ALIGN_LEFT));
        setLineSpacing((float) json.optDouble("lineSpacing", 1.2));
        setLetterSpacing((float) json.optDouble("letterSpacing", 0f));
        setPadding((float) json.optDouble("padding", 10));
        setBackgroundColour(json.optInt("bgColour", 0));
        setHighlightColour(json.optInt("highlight", 0));
        setBorderColour(json.optInt("borderColour", 0));
        setBorderWidth((float) json.optDouble("borderWidth", 0));
        setShadow(json.optBoolean("shadow", false));
        setFontFamily(json.optString("font", FONT_SERIF));
        setAutoHeight(json.optBoolean("autoHeight", false));
    }

    public String getText() { return propString("text", ""); }
    public void setText(String value) { prop("text", value); }

    public float getFontSize() { return propFloat("fontSize", 32f); }
    public void setFontSize(float value) { prop("fontSize", Math.max(6f, value)); }

    public int getColour() { return propInt("colour", 0xFF2C251C); }
    public void setColour(int value) { prop("colour", value); }

    public boolean isBold() { return propBool("bold", false); }
    public void setBold(boolean value) { prop("bold", value); }

    public boolean isItalic() { return propBool("italic", false); }
    public void setItalic(boolean value) { prop("italic", value); }

    public boolean isUnderline() { return propBool("underline", false); }
    public void setUnderline(boolean value) { prop("underline", value); }

    public boolean isStrike() { return propBool("strike", false); }
    public void setStrike(boolean value) { prop("strike", value); }

    public String getAlign() { return propString("align", ALIGN_LEFT); }
    public void setAlign(String value) { prop("align", value == null ? ALIGN_LEFT : value); }

    public float getLineSpacing() { return propFloat("lineSpacing", 1.2f); }
    public void setLineSpacing(float value) { prop("lineSpacing", Math.max(0.6f, value)); }

    public float getLetterSpacing() { return propFloat("letterSpacing", 0f); }
    public void setLetterSpacing(float value) { prop("letterSpacing", value); }

    public float getPadding() { return propFloat("padding", 10f); }
    public void setPadding(float value) { prop("padding", Math.max(0f, value)); }

    public int getBackgroundColour() { return propInt("bgColour", 0); }
    public void setBackgroundColour(int value) { prop("bgColour", value); }

    public int getHighlightColour() { return propInt("highlight", 0); }
    public void setHighlightColour(int value) { prop("highlight", value); }

    public int getBorderColour() { return propInt("borderColour", 0); }
    public void setBorderColour(int value) { prop("borderColour", value); }

    public float getBorderWidth() { return propFloat("borderWidth", 0f); }
    public void setBorderWidth(float value) { prop("borderWidth", Math.max(0f, value)); }

    public boolean hasShadow() { return propBool("shadow", false); }
    public void setShadow(boolean value) { prop("shadow", value); }

    public String getFontFamily() { return propString("font", FONT_SERIF); }
    public void setFontFamily(String value) { prop("font", FONT_SERIF.equals(value) ? FONT_SERIF : FONT_SANS); }

    public boolean autoHeight() { return propBool("autoHeight", false); }
    public void setAutoHeight(boolean value) { prop("autoHeight", value); }
}
