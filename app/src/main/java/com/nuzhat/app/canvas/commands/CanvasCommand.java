package com.nuzhat.app.canvas.commands;

import com.nuzhat.app.domain.model.CanvasElement;

import org.json.JSONObject;

/** Command-pattern contract: every canvas mutation is undoable and replayable. */
public interface CanvasCommand {
    void execute();

    void undo();

    String label();

    /** Helper used by property commands to snapshot an object without copying bitmaps. */
    final class Snapshot {
        public float x, y, width, height, rotation, scale;
        public JSONObject props = new JSONObject();

        public static Snapshot of(CanvasElement element) {
            Snapshot snapshot = new Snapshot();
            snapshot.x = element.x;
            snapshot.y = element.y;
            snapshot.width = element.width;
            snapshot.height = element.height;
            snapshot.rotation = element.rotation;
            snapshot.scale = element.scale;
            snapshot.props = element.propsCopy();
            return snapshot;
        }

        public void applyTo(CanvasElement element) {
            element.x = x;
            element.y = y;
            element.width = width;
            element.height = height;
            element.rotation = rotation;
            element.scale = scale;
            element.applyPropsJson(props.toString());
        }
    }
}
