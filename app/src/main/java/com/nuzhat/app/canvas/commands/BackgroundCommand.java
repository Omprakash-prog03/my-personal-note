package com.nuzhat.app.canvas.commands;

import com.nuzhat.app.canvas.engine.CanvasDocument;
import com.nuzhat.app.domain.model.BackgroundStyle;

public class BackgroundCommand implements CanvasCommand {

    private final CanvasDocument document;
    private final BackgroundStyle before;
    private final BackgroundStyle after;

    public BackgroundCommand(CanvasDocument document, BackgroundStyle before, BackgroundStyle after) {
        this.document = document;
        this.before = before.copy();
        this.after = after.copy();
    }

    @Override
    public void execute() {
        document.background = after.copy();
        document.touch();
    }

    @Override
    public void undo() {
        document.background = before.copy();
        document.touch();
    }

    @Override
    public String label() { return "Change background"; }
}
