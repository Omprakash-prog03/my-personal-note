package com.archive.app.canvas.engine;

import com.archive.app.canvas.commands.CommandHistory;
import com.archive.app.data.entity.CanvasElementEntity;
import com.archive.app.data.entity.PageEntity;
import com.archive.app.domain.model.BackgroundStyle;
import com.archive.app.domain.model.CanvasElement;
import com.archive.app.domain.model.ElementFactory;
import com.archive.app.domain.model.PageFormat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The live, in-memory page. It owns objects, background, dirty tracking and the undo stack.
 * It knows nothing about Android views, files or Room — which is what makes it unit-testable.
 */
public class CanvasDocument {

    private static final Comparator<CanvasElement> BY_Z = new Comparator<CanvasElement>() {
        @Override
        public int compare(CanvasElement a, CanvasElement b) {
            return Integer.compare(a.zIndex, b.zIndex);
        }
    };

    public String pageId = "";
    public String collectionId = "";
    public String title = "Untitled page";
    public String formatKey = PageFormat.A4_PORTRAIT;
    public String mode = PageFormat.MODE_PAGE;
    public int pageWidth = PageFormat.widthOf(PageFormat.A4_PORTRAIT);
    public int pageHeight = PageFormat.heightOf(PageFormat.A4_PORTRAIT);
    public BackgroundStyle background = BackgroundStyle.defaultFor(false);
    public boolean favourite = false;
    public long createdAt = 0L;
    public long updatedAt = 0L;

    private final List<CanvasElement> elements = new ArrayList<>();
    private final Set<String> dirtyIds = new LinkedHashSet<>();
    private final Set<String> deletedIds = new LinkedHashSet<>();
    private boolean backgroundDirty = false;

    public final CommandHistory history = new CommandHistory();

    public static CanvasDocument fromPage(PageEntity page, List<CanvasElementEntity> entities) {
        CanvasDocument document = new CanvasDocument();
        document.pageId = page.id;
        document.collectionId = page.collectionId;
        document.title = page.title;
        document.formatKey = PageFormat.normalise(page.formatKey);
        document.mode = PageFormat.MODE_INFINITE.equals(page.mode) ? PageFormat.MODE_INFINITE : PageFormat.MODE_PAGE;
        document.pageWidth = page.width <= 0 ? PageFormat.widthOf(document.formatKey) : page.width;
        document.pageHeight = page.height <= 0 ? PageFormat.heightOf(document.formatKey) : page.height;
        document.background = BackgroundStyle.fromJson(page.backgroundJson);
        document.favourite = page.favourite;
        document.createdAt = page.createdAt;
        document.updatedAt = page.updatedAt;
        for (CanvasElementEntity entity : entities) {
            document.elements.add(ElementFactory.fromEntity(entity));
        }
        Collections.sort(document.elements, BY_Z);
        document.normaliseZ();
        return document;
    }

    public List<CanvasElement> elements() { return elements; }

    public CanvasElement byId(String id) {
        for (CanvasElement element : elements) {
            if (element.id.equals(id)) return element;
        }
        return null;
    }

    public List<CanvasElement> byIds(Set<String> ids) {
        List<CanvasElement> result = new ArrayList<>();
        for (CanvasElement element : elements) {
            if (ids.contains(element.id)) result.add(element);
        }
        return result;
    }

    public int nextZIndex() {
        int max = 0;
        for (CanvasElement element : elements) max = Math.max(max, element.zIndex);
        return max + 1;
    }

    public void add(CanvasElement element) {
        if (element.zIndex <= 0) element.zIndex = nextZIndex();
        if (byId(element.id) == null) elements.add(element);
        sort();
        markDirty(element);
        touch();
    }

    public void remove(CanvasElement element) {
        elements.remove(element);
        deletedIds.add(element.id);
        dirtyIds.remove(element.id);
        touch();
    }

    public void removeByIds(List<String> ids) {
        for (String id : ids) {
            CanvasElement element = byId(id);
            if (element != null) remove(element);
        }
    }

    public void sort() { Collections.sort(elements, BY_Z); }

    /** Keeps z-index contiguous so bring-to-front / send-to-back stay simple. */
    public void normaliseZ() {
        sort();
        int index = 1;
        for (CanvasElement element : elements) element.zIndex = index++;
    }

    public void markDirty(CanvasElement element) {
        if (element == null) return;
        element.updatedAt = System.currentTimeMillis();
        dirtyIds.add(element.id);
    }

    public void markAllDirty() {
        for (CanvasElement element : elements) {
            element.updatedAt = System.currentTimeMillis();
            dirtyIds.add(element.id);
        }
    }

    public void markBackgroundDirty() { backgroundDirty = true; }

    public Set<String> dirtyIds() { return new HashSet<>(dirtyIds); }

    public List<String> deletedIds() { return new ArrayList<>(deletedIds); }

    public boolean isBackgroundDirty() { return backgroundDirty; }

    public void clearDirty() {
        dirtyIds.clear();
        deletedIds.clear();
        backgroundDirty = false;
    }

    public boolean hasPendingChanges() {
        return !dirtyIds.isEmpty() || !deletedIds.isEmpty() || backgroundDirty;
    }

    public void touch() { updatedAt = System.currentTimeMillis(); }

    public List<String> orderedIds() {
        List<String> ids = new ArrayList<>();
        for (CanvasElement element : elements) ids.add(element.id);
        return ids;
    }

    /** Restores a recorded stacking order verbatim. */
    public void applyOrder(List<String> ids) {
        List<CanvasElement> reordered = new ArrayList<>();
        for (String id : ids) {
            CanvasElement element = byId(id);
            if (element != null) reordered.add(element);
        }
        for (CanvasElement element : elements) {
            if (!reordered.contains(element)) reordered.add(element);
        }
        elements.clear();
        elements.addAll(reordered);
        int z = 1;
        for (CanvasElement element : elements) {
            element.zIndex = z++;
            markDirty(element);
        }
        touch();
    }

    public void bringToFront(Set<String> ids) {
        List<CanvasElement> selected = byIds(ids);
        elements.removeAll(selected);
        elements.addAll(selected);
        normaliseZ();
        for (CanvasElement element : selected) markDirty(element);
        touch();
    }

    public void sendToBack(Set<String> ids) {
        List<CanvasElement> selected = byIds(ids);
        elements.removeAll(selected);
        elements.addAll(0, selected);
        normaliseZ();
        for (CanvasElement element : selected) markDirty(element);
        touch();
    }

    public void moveForward(Set<String> ids) {
        List<CanvasElement> ordered = new ArrayList<>(elements);
        for (int i = ordered.size() - 2; i >= 0; i--) {
            if (ids.contains(ordered.get(i).id) && !ids.contains(ordered.get(i + 1).id)) {
                Collections.swap(ordered, i, i + 1);
            }
        }
        elements.clear();
        elements.addAll(ordered);
        normaliseZ();
        markAllDirty();
        touch();
    }

    public void moveBackward(Set<String> ids) {
        List<CanvasElement> ordered = new ArrayList<>(elements);
        for (int i = 1; i < ordered.size(); i++) {
            if (ids.contains(ordered.get(i).id) && !ids.contains(ordered.get(i - 1).id)) {
                Collections.swap(ordered, i, i - 1);
            }
        }
        elements.clear();
        elements.addAll(ordered);
        normaliseZ();
        markAllDirty();
        touch();
    }

    public void setLocks(Set<String> ids, boolean locked) {
        for (CanvasElement element : byIds(ids)) {
            element.locked = locked;
            markDirty(element);
        }
        touch();
    }

    public void setVisibility(Set<String> ids, boolean visible) {
        for (CanvasElement element : byIds(ids)) {
            element.visible = visible;
            markDirty(element);
        }
        touch();
    }

    public void group(Set<String> ids) {
        String groupId = com.archive.app.utils.Ids.newId();
        for (CanvasElement element : byIds(ids)) {
            element.groupId = groupId;
            markDirty(element);
        }
        touch();
    }

    public void ungroup(Set<String> ids) {
        for (CanvasElement element : byIds(ids)) {
            element.groupId = null;
            markDirty(element);
        }
        touch();
    }

    public Set<String> groupMembers(String elementId) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(elementId);
        CanvasElement element = byId(elementId);
        if (element == null || element.groupId == null) return ids;
        for (CanvasElement other : elements) {
            if (element.groupId.equals(other.groupId)) ids.add(other.id);
        }
        return ids;
    }

    // ---------------------------------------------------------------- persistence helpers

    public PageEntity toPageEntity() {
        PageEntity page = new PageEntity();
        page.id = pageId;
        page.collectionId = collectionId;
        page.title = title;
        page.formatKey = formatKey;
        page.mode = mode;
        page.width = pageWidth;
        page.height = pageHeight;
        page.backgroundJson = background.toJson();
        page.favourite = favourite;
        page.createdAt = createdAt;
        page.updatedAt = updatedAt;
        return page;
    }

    public List<CanvasElementEntity> dirtyEntities() {
        List<CanvasElementEntity> entities = new ArrayList<>();
        for (String id : dirtyIds) {
            CanvasElement element = byId(id);
            if (element != null) entities.add(element.toEntity(pageId));
        }
        return entities;
    }

    // ---------------------------------------------------------------- crash recovery

    public String toRecoveryJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("pageId", pageId);
            root.put("collectionId", collectionId);
            root.put("title", title);
            root.put("formatKey", formatKey);
            root.put("mode", mode);
            root.put("width", pageWidth);
            root.put("height", pageHeight);
            root.put("background", background.toJson());
            root.put("favourite", favourite);
            root.put("createdAt", createdAt);
            root.put("updatedAt", updatedAt);
            root.put("savedAt", System.currentTimeMillis());
            JSONArray array = new JSONArray();
            for (CanvasElement element : elements) {
                CanvasElementEntity entity = element.toEntity(pageId);
                JSONObject json = new JSONObject();
                json.put("id", entity.id);
                json.put("type", entity.type);
                json.put("x", entity.x);
                json.put("y", entity.y);
                json.put("width", entity.width);
                json.put("height", entity.height);
                json.put("rotation", entity.rotation);
                json.put("scale", entity.scale);
                json.put("opacity", entity.opacity);
                json.put("zIndex", entity.zIndex);
                json.put("visible", entity.visible);
                json.put("locked", entity.locked);
                json.put("groupId", entity.groupId == null ? JSONObject.NULL : entity.groupId);
                json.put("props", new JSONObject(entity.props));
                array.put(json);
            }
            root.put("elements", array);
        } catch (Exception ignored) { }
        return root.toString();
    }

    public static class Recovery {
        public CanvasDocument document;
        public long savedAt;
        public boolean valid;
    }

    /** Reads a recovery snapshot without touching the database. */
    public static Recovery readRecovery(String raw, PageEntity page) {
        Recovery recovery = new Recovery();
        if (raw == null || raw.isEmpty()) return recovery;
        try {
            JSONObject root = new JSONObject(raw);
            if (!page.id.equals(root.optString("pageId"))) return recovery;
            CanvasDocument document = new CanvasDocument();
            document.pageId = page.id;
            document.collectionId = page.collectionId;
            document.title = root.optString("title", page.title);
            document.formatKey = root.optString("formatKey", page.formatKey);
            document.mode = root.optString("mode", page.mode);
            document.pageWidth = root.optInt("width", page.width);
            document.pageHeight = root.optInt("height", page.height);
            document.background = BackgroundStyle.fromJson(root.optString("background", page.backgroundJson));
            document.createdAt = page.createdAt;
            JSONArray array = root.optJSONArray("elements");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject json = array.optJSONObject(i);
                    if (json == null) continue;
                    CanvasElementEntity entity = new CanvasElementEntity();
                    entity.id = json.optString("id");
                    entity.pageId = page.id;
                    entity.type = json.optString("type", "TEXT");
                    entity.x = (float) json.optDouble("x", 0);
                    entity.y = (float) json.optDouble("y", 0);
                    entity.width = (float) json.optDouble("width", 100);
                    entity.height = (float) json.optDouble("height", 100);
                    entity.rotation = (float) json.optDouble("rotation", 0);
                    entity.scale = (float) json.optDouble("scale", 1);
                    entity.opacity = (float) json.optDouble("opacity", 1);
                    entity.zIndex = json.optInt("zIndex", i);
                    entity.visible = json.optBoolean("visible", true);
                    entity.locked = json.optBoolean("locked", false);
                    entity.groupId = json.isNull("groupId") ? null : json.optString("groupId", null);
                    entity.props = json.optJSONObject("props") == null ? "{}" : json.optJSONObject("props").toString();
                    document.elements.add(ElementFactory.fromEntity(entity));
                }
            }
            document.sort();
            recovery.document = document;
            recovery.savedAt = root.optLong("savedAt", 0L);
            recovery.valid = true;
        } catch (Exception ignored) { }
        return recovery;
    }
}
