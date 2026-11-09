package com.archive.app.domain.model.elements;

import com.archive.app.domain.model.CanvasElement;
import com.archive.app.domain.model.ElementType;

import org.json.JSONObject;

/** Audio recording/attachment card; playback is handled by the canvas activity. */
public class AudioElement extends CanvasElement {

    @Override public ElementType type() { return ElementType.AUDIO; }
    @Override public int defaultWidth() { return 360; }
    @Override public int defaultHeight() { return 130; }
    @Override public String label() { return "Audio"; }
    @Override public String previewText() { return getTitle(); }

    @Override protected void writeProps(JSONObject json) {
        try {
            json.put("mediaId", getMediaId());
            json.put("relativePath", getRelativePath());
            json.put("title", getTitle());
            json.put("durationMs", getDurationMs());
        } catch (Exception ignored) { }
    }

    @Override protected void readProps(JSONObject json) {
        setMediaId(json.optString("mediaId", null));
        setRelativePath(json.optString("relativePath", null));
        setTitle(json.optString("title", "Recording"));
        setDurationMs((long) json.optDouble("durationMs", 0));
    }

    public String getMediaId() { return propString("mediaId", null); }
    public void setMediaId(String value) { prop("mediaId", value); }

    public String getRelativePath() { return propString("relativePath", null); }
    public void setRelativePath(String value) { prop("relativePath", value); }

    public String getTitle() { return propString("title", "Recording"); }
    public void setTitle(String value) { prop("title", value); }

    public long getDurationMs() { return (long) propFloat("durationMs", 0f); }
    public void setDurationMs(long value) { prop("durationMs", (float) value); }

    public String durationLabel() {
        long ms = getDurationMs();
        if (ms <= 0) return "";
        long seconds = ms / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }
}
