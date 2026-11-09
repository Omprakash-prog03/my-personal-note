package com.archive.app.canvas.commands;

import com.archive.app.canvas.engine.CanvasDocument;

import java.util.ArrayList;
import java.util.List;

/** Records the whole z-order before and after a stacking change (simple and always exact). */
public class LayerOrderCommand implements CanvasCommand {

    private final CanvasDocument document;
    private final List<String> before;
    private final List<String> after;

    public LayerOrderCommand(CanvasDocument document, List<String> before) {
        this.document = document;
        this.before = new ArrayList<>(before);
        this.after = document.orderedIds();
    }

    @Override
    public void execute() {
        document.applyOrder(after);
    }

    @Override
    public void undo() {
        document.applyOrder(before);
    }

    @Override
    public String label() { return "Change layers"; }
}
