package com.nuzhat.app.canvas.commands;

import java.util.ArrayList;
import java.util.List;

/** Groups several mutations into one undo step (e.g. paste of multiple objects). */
public class CompositeCommand implements CanvasCommand {

    private final List<CanvasCommand> children = new ArrayList<>();
    private final String label;

    public CompositeCommand(String label) {
        this.label = label;
    }

    public CompositeCommand add(CanvasCommand command) {
        children.add(command);
        return this;
    }

    public boolean isEmpty() { return children.isEmpty(); }

    @Override
    public void execute() {
        for (CanvasCommand child : children) child.execute();
    }

    @Override
    public void undo() {
        for (int i = children.size() - 1; i >= 0; i--) children.get(i).undo();
    }

    @Override
    public String label() { return label; }
}
