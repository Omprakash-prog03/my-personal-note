package com.archive.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.archive.app.data.database.AppDatabase;
import com.archive.app.data.entity.CanvasElementEntity;
import com.archive.app.data.entity.CollectionEntity;
import com.archive.app.data.entity.PageEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Instrumented Room tests against real SQLite (foreign keys enforced).
 * Run with {@code ./gradlew connectedDebugAndroidTest}.
 */
@RunWith(AndroidJUnit4.class)
public class ArchiveDatabaseTest {

    private AppDatabase db;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        db.close();
    }

    private CollectionEntity collection(String name) {
        CollectionEntity collection = new CollectionEntity();
        collection.id = UUID.randomUUID().toString();
        collection.name = name;
        collection.createdAt = System.currentTimeMillis();
        collection.updatedAt = collection.createdAt;
        db.collectionDao().upsert(collection);
        return collection;
    }

    private PageEntity page(String collectionId, String title) {
        PageEntity page = new PageEntity();
        page.id = UUID.randomUUID().toString();
        page.collectionId = collectionId;
        page.title = title;
        page.createdAt = System.currentTimeMillis();
        page.updatedAt = page.createdAt;
        db.pageDao().upsert(page);
        return page;
    }

    private CanvasElementEntity element(String pageId, int zIndex, String text) {
        CanvasElementEntity element = new CanvasElementEntity();
        element.id = UUID.randomUUID().toString();
        element.pageId = pageId;
        element.type = "TEXT";
        element.zIndex = zIndex;
        element.props = "{\"text\":\"" + text + "\"}";
        element.createdAt = System.currentTimeMillis();
        element.updatedAt = element.createdAt;
        db.elementDao().insertAll(Collections.singletonList(element));
        return element;
    }

    @Test
    public void collectionsAndPagesPersist() {
        CollectionEntity collection = collection("Research");
        PageEntity page = page(collection.id, "First page");

        CollectionEntity reloadedCollection = db.collectionDao().findSync(collection.id);
        PageEntity reloadedPage = db.pageDao().findSync(page.id);

        assertNotNull(reloadedCollection);
        assertEquals("Research", reloadedCollection.name);
        assertNotNull(reloadedPage);
        assertEquals("First page", reloadedPage.title);
        assertEquals(collection.id, reloadedPage.collectionId);
        assertEquals(1, db.pageDao().countForCollection(collection.id));
    }

    @Test
    public void elementsLoadInZOrder() {
        CollectionEntity collection = collection("Ordering");
        PageEntity page = page(collection.id, "Layers");
        element(page.id, 5, "top");
        element(page.id, 1, "bottom");
        element(page.id, 3, "middle");

        List<CanvasElementEntity> loaded = db.elementDao().loadSync(page.id);
        assertEquals(3, loaded.size());
        assertEquals(1, loaded.get(0).zIndex);
        assertEquals(3, loaded.get(1).zIndex);
        assertEquals(5, loaded.get(2).zIndex);
        assertTrue(loaded.get(0).props.contains("bottom"));
    }

    @Test
    public void deletingPageCascadesToElements() {
        CollectionEntity collection = collection("Cascade");
        PageEntity page = page(collection.id, "Doomed");
        element(page.id, 0, "a");
        element(page.id, 1, "b");

        db.pageDao().deletePermanently(page.id);

        assertNull(db.pageDao().findSync(page.id));
        assertTrue(db.elementDao().loadSync(page.id).isEmpty());
    }

    @Test
    public void trashedPagesAreHiddenFromCollectionQueries() {
        CollectionEntity collection = collection("Trash filtering");
        PageEntity keep = page(collection.id, "Keep");
        PageEntity drop = page(collection.id, "Drop");

        db.pageDao().trash(drop.id, System.currentTimeMillis());

        List<PageEntity> visible = db.pageDao().forCollectionSync(collection.id);
        assertEquals(1, visible.size());
        assertEquals(keep.id, visible.get(0).id);
        assertEquals(1, db.pageDao().countTrashed());
    }

    @Test
    public void thumbnailUpdateSurvivesReload() {
        CollectionEntity collection = collection("Updates");
        PageEntity page = page(collection.id, "Before");

        db.pageDao().setThumbnail(page.id, "archive/thumbnails/page_" + page.id + ".png",
                System.currentTimeMillis());

        PageEntity reloaded = db.pageDao().findSync(page.id);
        assertNotNull(reloaded.thumbnailPath);
        assertTrue(reloaded.thumbnailPath.endsWith(".png"));
    }

    @Test
    public void transactionRollsBackOnFailure() {
        CollectionEntity collection = collection("Transactions");
        PageEntity page = page(collection.id, "Atomic");
        element(page.id, 0, "committed");

        try {
            db.runInTransaction(() -> {
                db.pageDao().upsert(page(collection.id, "rolled back"));
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // Room rolls the transaction back for us.
        }

        assertEquals(1, db.elementDao().loadSync(page.id).size());
        assertEquals(1, db.pageDao().countForCollection(collection.id));
    }
}
