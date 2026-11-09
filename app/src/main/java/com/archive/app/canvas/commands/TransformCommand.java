package com.archive.app.canvas.commands;

import com.archive.app.canvas.engine.CanvasDocument;
import com.archive.app.domain.model.CanvasElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Geometry change for one or more objects (move, resize, rotate). Captured once per gesture. */
public class TransformCommand implements CanvasCommand {

    public static class Geometry {
        public final float x, y, width, height, rotation, scale;

        public Geometry(CanvasElement element) {
            this.x = element.x;
            this.y = element.y;
            this.width = element.width;
            this.height = element.height;
            this.rotation = element.rotation;
            this.scale = element.scale;
        }
    }

    private final CanvasDocument document;
    private final Map<String, Geometry> before = new LinkedHashMap<>();
    private final Map<String, Geometry> after = new LinkedHashMap<>();
    private final String label;

    public TransformCommand(CanvasDocument document, List<CanvasElement> elements,
                            Map<String, Geometry> before, String label) {
        this.document = document;
        this.label = label;
        for (CanvasElement element : new ArrayList<>(elements)) {
            Geometry start = before.get(element.id);
            if (start == null) continue;
            this.before.put(element.id, start);
            this.after.put(element.id, new Geometry(element));
        }
    }

    public static Map<String, Geometry> capture(List<CanvasElement> elements) {
        Map<String, Geometry> map = new LinkedHashMap<>();
        for (CanvasElement element : elements) map.put(element.id, new Geometry(element));
        return map;
    }

    @Override
    public void execute() {
        apply(after);
    }

    @Override
    public void undo() {
        apply(before);
    }

    private void apply(Map<String, Geometry> values) {
        for (Map.Entry<String, Geometry> entry : values.entrySet()) {
            CanvasElement element = document.byId(entry.getKey());
            if (element == null) continue;
            Geometry g = entry.getValue();
            element.x = g.x;
            element.y = g.y;
            element.width = g.width;
            element.height = g.height;
            element.rotation = g.rotation;
            element.scale = g.scale;
            document.markDirty(element);
        }
        document.touch();
    }

    @Override
    public String label() { return label; }
}
