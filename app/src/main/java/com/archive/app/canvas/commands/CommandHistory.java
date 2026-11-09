package com.archive.app.canvas.commands;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bounded undo/redo stack. Gestures push commands as "already applied" because they mutate the live
 * document while the finger moves; discrete actions execute through {@link #execute(CanvasCommand)}.
 * Commands store deltas, never bitmaps, so a deep history stays small.
 */
public class CommandHistory {

    private static final int MAX_ENTRIES = 60;

    private final Deque<CanvasCommand> undoStack = new ArrayDeque<>();
    private final Deque<CanvasCommand> redoStack = new ArrayDeque<>();
    private Runnable listener;

    public void setListener(Runnable listener) { this.listener = listener; }

    public void execute(CanvasCommand command) {
        command.execute();
        record(command);
    }

    /** Records a command whose effect has already been applied to the live document. */
    public void record(CanvasCommand command) {
        undoStack.push(command);
        redoStack.clear();
        while (undoStack.size() > MAX_ENTRIES) undoStack.removeLast();
        notifyChanged();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        CanvasCommand command = undoStack.pop();
        command.undo();
        redoStack.push(command);
        notifyChanged();
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        CanvasCommand command = redoStack.pop();
        command.execute();
        undoStack.push(command);
        notifyChanged();
        return true;
    }

    @Nullable
    public String peekUndoLabel() { return undoStack.isEmpty() ? null : undoStack.peek().label(); }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        notifyChanged();
    }

    public void addListener(Runnable listener) { this.listener = listener; }

    private void notifyChanged() {
        if (listener != null) listener.run();
    }
}
