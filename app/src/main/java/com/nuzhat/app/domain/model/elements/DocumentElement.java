package com.nuzhat.app.domain.model.elements;

import com.nuzhat.app.domain.model.CanvasElement;
import com.nuzhat.app.domain.model.ElementType;

import org.json.JSONObject;

/** A visual card for an attached original document; the file itself is never converted. */
public class DocumentElement extends CanvasElement {

    @Override public ElementType type() { return ElementType.DOCUMENT; }
    @Override public int defaultWidth() { return 340; }
    @Override public int defaultHeight() { return 380; }
    @Override public String label() { return "Document"; }
    @Override public String previewText() { return getTitle(); }

    @Override protected void writeProps(JSONObject json) {
        try {
            json.put("mediaId", getMediaId());
            json.put("relativePath", getRelativePath());
            json.put("title", getTitle());
            json.put("mime", getMime());
            json.put("pageCount", getPageCount());
        } catch (Exception ignored) { }
    }

    @Override protected void readProps(JSONObject json) {
        setMediaId(json.optString("mediaId", null));
        setRelativePath(json.optString("relativePath", null));
        setTitle(json.optString("title", "Document"));
        setMime(json.optString("mime", "application/pdf"));
        setPageCount(json.optInt("pageCount", 0));
    }

    public String getMediaId() { return propString("mediaId", null); }
    public void setMediaId(String value) { prop("mediaId", value); }

    public String getRelativePath() { return propString("relativePath", null); }
    public void setRelativePath(String value) { prop("relativePath", value); }

    public String getTitle() { return propString("title", "Document"); }
    public void setTitle(String value) { prop("title", value); }

    public String getMime() { return propString("mime", "application/pdf"); }
    public void setMime(String value) { prop("mime", value); }

    public int getPageCount() { return propInt("pageCount", 0); }
    public void setPageCount(int value) { prop("pageCount", value); }
}
