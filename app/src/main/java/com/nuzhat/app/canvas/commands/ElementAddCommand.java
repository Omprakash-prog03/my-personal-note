package com.nuzhat.app.canvas.commands;

import com.nuzhat.app.canvas.engine.CanvasDocument;
import com.nuzhat.app.domain.model.CanvasElement;

/** Adding an object (image, text, stroke batch, sticker, envelope…). */
public class ElementAddCommand implements CanvasCommand {

    private final CanvasDocument document;
    private final CanvasElement element;
    private final String label;

    public ElementAddCommand(CanvasDocument document, CanvasElement element, String label) {
        this.document = document;
        this.element = element;
        this.label = label;
    }

    @Override
    public void execute() {
        document.add(element);
    }

    @Override
    public void undo() {
        document.remove(element);
    }

    @Override
    public String label() { return label; }
}
