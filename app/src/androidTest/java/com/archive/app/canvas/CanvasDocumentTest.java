package com.archive.app.canvas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.archive.app.canvas.commands.CanvasCommand;
import com.archive.app.canvas.engine.CanvasDocument;
import com.archive.app.data.entity.CanvasElementEntity;
import com.archive.app.data.entity.PageEntity;
import com.archive.app.domain.model.CanvasElement;
import com.archive.app.domain.model.elements.TextElement;
import com.archive.app.utils.Ids;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Document-level behaviour that does not need a screen: page hydration, z-order, grouping and the
 * property snapshot used by undo. Instrumented because element props use the platform JSON parser.
 */
@RunWith(AndroidJUnit4.class)
public class CanvasDocumentTest {

    private PageEntity page() {
        PageEntity page = new PageEntity();
        page.id = Ids.newId();
        page.collectionId = Ids.newId();
        page.title = "Test page";
        page.createdAt = System.currentTimeMillis();
        page.updatedAt = page.createdAt;
        return page;
    }

    private CanvasElementEntity textEntity(String pageId, int z, String text) {
        TextElement element = new TextElement();
        element.setText(text);
        element.zIndex = z;
        element.x = 40f * z;
        element.y = 20f * z;
        return element.toEntity(pageId);
    }

    @Test
    public void hydratesElementsAndNormalisesZOrder() {
        PageEntity page = page();
        List<CanvasElementEntity> entities = new ArrayList<>();
        entities.add(textEntity(page.id, 9, "nine"));
        entities.add(textEntity(page.id, 3, "three"));

        CanvasDocument document = CanvasDocument.fromPage(page, entities);

        assertEquals("Test page", document.title);
        assertEquals(2, document.elements().size());
        assertEquals(0, document.elements().get(0).zIndex);
        assertEquals(1, document.elements().get(1).zIndex);
        assertEquals("three", ((TextElement) document.elements().get(0)).getText());
    }

    @Test
    public void groupingAndUngroupingAffectMembersOnly() {
        PageEntity page = page();
        List<CanvasElementEntity> entities = new ArrayList<>();
        entities.add(textEntity(page.id, 0, "a"));
        entities.add(textEntity(page.id, 1, "b"));
        entities.add(textEntity(page.id, 2, "c"));
        CanvasDocument document = CanvasDocument.fromPage(page, entities);

        Set<String> pair = new LinkedHashSet<>(Arrays.asList(
                document.elements().get(0).id, document.elements().get(1).id));
        document.group(pair);

        CanvasElement first = document.elements().get(0);
        CanvasElement second = document.elements().get(1);
        CanvasElement third = document.elements().get(2);
        assertNotNull(first.groupId);
        assertEquals(first.groupId, second.groupId);
        assertFalse(first.groupId.equals(third.groupId));
        assertEquals(2, document.groupMembers(first.id).size());

        document.ungroup(pair);
        assertNull(first.groupId);
        assertNull(second.groupId);
    }

    @Test
    public void propertySnapshotRestoresGeometryAndProps() {
        TextElement element = new TextElement();
        element.x = 10f;
        element.y = 20f;
        element.width = 300f;
        element.height = 120f;
        element.setFontSize(30f);
        element.setText("original");

        CanvasCommand.Snapshot snapshot = CanvasCommand.Snapshot.of(element);
        element.x = 999f;
        element.setFontSize(64f);
        element.setText("changed");

        snapshot.applyTo(element);

        assertEquals(10f, element.x, 0.001f);
        assertEquals(20f, element.y, 0.001f);
        assertEquals(300f, element.width, 0.001f);
        assertEquals(30f, element.getFontSize(), 0.001f);
        assertEquals("original", element.getText());
    }

    @Test
    public void layerOrderingMovesSelection() {
        PageEntity page = page();
        List<CanvasElementEntity> entities = new ArrayList<>();
        for (int i = 0; i < 3; i++) entities.add(textEntity(page.id, i, "e" + i));
        CanvasDocument document = CanvasDocument.fromPage(page, entities);

        String bottomId = document.elements().get(0).id;
        document.bringToFront(java.util.Collections.singleton(bottomId));
        assertEquals(bottomId, document.elements().get(2).id);

        document.sendToBack(java.util.Collections.singleton(bottomId));
        assertEquals(bottomId, document.elements().get(0).id);
    }

    @Test
    public void completedWriteClearsOnlyPersistedChanges() {
        PageEntity page = page();
        CanvasDocument document = CanvasDocument.fromPage(page, new ArrayList<>());
        TextElement element = new TextElement();
        element.setText("first");
        document.add(element);
        document.markDirty(element);

        long stamp = element.updatedAt;
        java.util.Set<String> saved = new java.util.HashSet<>();
        saved.add(element.id);

        // An edit that lands after the write started must survive the clear.
        element.updatedAt = stamp + 5;

        document.clearDirty(saved, new ArrayList<>(), false, stamp);
        assertTrue(document.dirtyIds().contains(element.id));

        document.clearDirty(saved, new ArrayList<>(), false, element.updatedAt);
        assertFalse(document.dirtyIds().contains(element.id));
    }

    @Test
    public void duplicateKeepsGeometryButGetsNewId() {
        CanvasElementEntity entity = textEntity(Ids.newId(), 4, "copy me");
        CanvasElement element = com.archive.app.domain.model.ElementFactory.fromEntity(entity);
        element.x = 123f;
        element.y = 456f;

        CanvasElement copy = element.duplicate();

        assertNotEquals(element.id, copy.id);
        assertEquals(element.x, copy.x, 0.001f);
        assertEquals(element.y, copy.y, 0.001f);
        assertEquals("copy me", ((TextElement) copy).getText());
    }
}
