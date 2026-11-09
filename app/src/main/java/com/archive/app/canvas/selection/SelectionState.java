package com.archive.app.canvas.selection;

import com.archive.app.canvas.engine.CanvasDocument;
import com.archive.app.domain.model.CanvasElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Ordered multi-selection. Kept separate from the document so undo never touches selection. */
public class SelectionState {

    private final LinkedHashSet<String> ids = new LinkedHashSet<>();
    private Runnable listener;

    public void setListener(Runnable listener) { this.listener = listener; }

    public boolean isEmpty() { return ids.isEmpty(); }

    public int size() { return ids.size(); }

    public Set<String> ids() { return Collections.unmodifiableSet(ids); }

    public List<String> idList() { return new ArrayList<>(ids); }

    public String single() { return ids.size() == 1 ? ids.iterator().next() : null; }

    public boolean contains(String id) { return ids.contains(id); }

    public void select(String id) {
        ids.clear();
        if (id != null) ids.add(id);
        notifyChanged();
    }

    public void add(String id) {
        if (id != null) ids.add(id);
        notifyChanged();
    }

    public void replaceWith(Set<String> newIds) {
        ids.clear();
        ids.addAll(newIds);
        notifyChanged();
    }

    public void toggle(String id) {
        if (id == null) return;
        if (!ids.remove(id)) ids.add(id);
        notifyChanged();
    }

    public void clear() {
        if (ids.isEmpty()) return;
        ids.clear();
        notifyChanged();
    }

    /** Drops ids whose objects no longer exist (after delete/undo). */
    public void retainExisting(CanvasDocument document) {
        boolean changed = ids.removeIf(id -> document.byId(id) == null);
        if (changed) notifyChanged();
    }

    public List<CanvasElement> elements(CanvasDocument document) {
        return document.byIds(ids);
    }

    private void notifyChanged() {
        if (listener != null) listener.run();
    }
}
