package com.archive.app.canvas.commands;

import com.archive.app.canvas.engine.CanvasDocument;
import com.archive.app.domain.model.CanvasElement;

import java.util.ArrayList;
import java.util.List;

/** Deleting one or more objects; re-insertion restores their original stacking order. */
public class ElementRemoveCommand implements CanvasCommand {

    private final CanvasDocument document;
    private final List<CanvasElement> elements = new ArrayList<>();

    public ElementRemoveCommand(CanvasDocument document, List<CanvasElement> elements) {
        this.document = document;
        this.elements.addAll(elements);
    }

    @Override
    public void execute() {
        for (CanvasElement element : elements) document.remove(element);
    }

    @Override
    public void undo() {
        for (CanvasElement element : elements) document.add(element);
    }

    @Override
    public String label() { return elements.size() == 1 ? "Delete object" : "Delete objects"; }
}
