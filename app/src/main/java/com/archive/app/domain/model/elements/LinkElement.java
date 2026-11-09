package com.archive.app.domain.model.elements;

import com.archive.app.domain.model.CanvasElement;
import com.archive.app.domain.model.ElementType;

import org.json.JSONObject;

/** Reference card for a URL, with a personal note. No network fetch is performed. */
public class LinkElement extends CanvasElement {

    @Override public ElementType type() { return ElementType.LINK_CARD; }
    @Override public int defaultWidth() { return 380; }
    @Override public int defaultHeight() { return 170; }
    @Override public String label() { return "Link"; }
    @Override public String previewText() { return getTitle().isEmpty() ? getUrl() : getTitle(); }

    @Override protected void writeProps(JSONObject json) {
        try {
            json.put("url", getUrl());
            json.put("title", getTitle());
            json.put("note", getNote());
        } catch (Exception ignored) { }
    }

    @Override protected void readProps(JSONObject json) {
        setUrl(json.optString("url", ""));
        setTitle(json.optString("title", ""));
        setNote(json.optString("note", ""));
    }

    public String getUrl() { return propString("url", ""); }
    public void setUrl(String value) { prop("url", value); }

    public String getTitle() { return propString("title", ""); }
    public void setTitle(String value) { prop("title", value); }

    public String getNote() { return propString("note", ""); }
    public void setNote(String value) { prop("note", value); }

    public String host() {
        try {
            String host = android.net.Uri.parse(getUrl()).getHost();
            return host == null ? getUrl() : host;
        } catch (Exception e) {
            return getUrl();
        }
    }
}
