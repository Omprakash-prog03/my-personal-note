package com.nuzhat.app.canvas.commands;

import com.nuzhat.app.canvas.engine.CanvasDocument;
import com.nuzhat.app.domain.model.CanvasElement;

/** Any appearance change (colour, frame, text styling, envelope contents, stroke edits). */
public class PropertyCommand implements CanvasCommand {

    private final CanvasDocument document;
    private final String elementId;
    private final CanvasCommand.Snapshot before;
    private final CanvasCommand.Snapshot after;
    private final String label;

    public PropertyCommand(CanvasDocument document, CanvasElement element,
                           CanvasCommand.Snapshot before, String label) {
        this.document = document;
        this.elementId = element.id;
        this.before = before;
        this.after = CanvasCommand.Snapshot.of(element);
        this.label = label;
    }

    @Override
    public void execute() {
        apply(after);
    }

    @Override
    public void undo() {
        apply(before);
    }

    private void apply(CanvasCommand.Snapshot snapshot) {
        CanvasElement element = document.byId(elementId);
        if (element == null) return;
        snapshot.applyTo(element);
        document.markDirty(element);
        document.touch();
    }

    @Override
    public String label() { return label; }
}
