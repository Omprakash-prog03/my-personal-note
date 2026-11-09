package com.nuzhat.app.data.repository;

import android.graphics.Bitmap;
import android.os.Handler;

import com.nuzhat.app.canvas.engine.CanvasDocument;
import com.nuzhat.app.data.database.AppDatabase;
import com.nuzhat.app.data.entity.CanvasElementEntity;
import com.nuzhat.app.data.entity.PageEntity;
import com.nuzhat.app.data.storage.FileStore;
import com.nuzhat.app.domain.model.CanvasElement;
import com.nuzhat.app.utils.Ids;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Persistence for one editable canvas: element batches, page rows, thumbnails and crash-recovery
 * snapshots. Kept separate from {@link NuzhatRepository} because saving here is debounced and
 * happens constantly while a page is open.
 */
public class CanvasRepository {

    public static class LoadedPage {
        public PageEntity page;
        public List<CanvasElementEntity> elements;
    }

    private final AppDatabase db;
    private final FileStore files;
    private final ExecutorService executor;
    private final Handler main;

    public CanvasRepository(AppDatabase db, FileStore files, ExecutorService executor, Handler main) {
        this.db = db;
        this.files = files;
        this.executor = executor;
        this.main = main;
    }

    public void load(String pageId, Callback<LoadedPage> callback) {
        executor.execute(() -> {
            LoadedPage loaded = new LoadedPage();
            loaded.page = db.pageDao().findSync(pageId);
            loaded.elements = db.elementDao().loadSync(pageId);
            main.post(() -> callback.onResult(loaded));
        });
    }

    /** Single transaction for a save batch; called from the autosave scheduler. */
    public void save(String pageId, long updatedAt, List<CanvasElementEntity> upserts,
                     List<String> deletes, Runnable done) {
        save(pageId, updatedAt, upserts, deletes, done, null);
    }

    /**
     * Same batch, but reports failure so the view model can keep the edits dirty and retry instead of
     * showing "Saved" for a write that never happened.
     */
    public void save(String pageId, long updatedAt, List<CanvasElementEntity> upserts,
                     List<String> deletes, Runnable done, Runnable onFailure) {
        executor.execute(() -> {
            try {
                db.elementDao().saveBatch(pageId, updatedAt, upserts, deletes);
                db.pageDao().touch(pageId, updatedAt);
                if (done != null) main.post(done);
            } catch (Throwable error) {
                if (onFailure != null) main.post(onFailure);
            }
        });
    }

    /**
     * Copies a page and all of its objects into a new row in the same collection. The copy is a real
     * independent page: fresh ids for the page and every element, no thumbnail (it is regenerated the
     * first time the page is opened), and its own sort order at the end of the collection.
     */
    public void duplicatePage(String pageId, Callback<PageEntity> callback) {
        executor.execute(() -> {
            PageEntity source = db.pageDao().findSync(pageId);
            if (source == null) {
                main.post(() -> callback.onResult(null));
                return;
            }
            List<CanvasElementEntity> elements = db.elementDao().loadSync(pageId);
            PageEntity copy = new PageEntity();
            copy.id = Ids.newId();
            copy.collectionId = source.collectionId;
            copy.title = source.title + " copy";
            copy.formatKey = source.formatKey;
            copy.mode = source.mode;
            copy.width = source.width;
            copy.height = source.height;
            copy.backgroundJson = source.backgroundJson;
            copy.sortOrder = db.pageDao().maxSortOrder(source.collectionId) + 1;
            copy.createdAt = System.currentTimeMillis();
            copy.updatedAt = copy.createdAt;
            copy.thumbnailPath = null;

            List<CanvasElementEntity> copies = new ArrayList<>();
            CanvasDocument document = CanvasDocument.fromPage(source, elements);
            for (CanvasElement element : document.elements()) {
                copies.add(element.duplicate().toEntity(copy.id));
            }

            db.runInTransaction(() -> {
                db.pageDao().upsert(copy);
                if (!copies.isEmpty()) db.elementDao().insertAll(copies);
            });
            main.post(() -> callback.onResult(copy));
        });
    }

    /**
     * Moves a page one slot earlier or later inside its collection by swapping sort orders.
     *
     * @return the new index, or -1 when the page is already at that end.
     */
    public void movePage(String pageId, int direction, Callback<Integer> callback) {
        executor.execute(() -> {
            PageEntity page = db.pageDao().findSync(pageId);
            if (page == null) {
                main.post(() -> callback.onResult(-1));
                return;
            }
            List<PageEntity> siblings = db.pageDao().forCollectionSync(page.collectionId);
            int index = -1;
            for (int i = 0; i < siblings.size(); i++) {
                if (siblings.get(i).id.equals(pageId)) {
                    index = i;
                    break;
                }
            }
            int target = index + (direction < 0 ? -1 : 1);
            if (index < 0 || target < 0 || target >= siblings.size()) {
                main.post(() -> callback.onResult(-1));
                return;
            }
            PageEntity other = siblings.get(target);
            int mine = page.sortOrder;
            int theirs = other.sortOrder;
            if (mine == theirs) {
                // Legacy rows that never got an order: rebuild it from the current visual order.
                long now = System.currentTimeMillis();
                for (int i = 0; i < siblings.size(); i++) {
                    siblings.get(i).sortOrder = i;
                    siblings.get(i).updatedAt = now;
                }
                db.pageDao().insertAll(siblings);
                mine = index;
                theirs = target;
                page.sortOrder = mine;
                other.sortOrder = theirs;
            }
            page.sortOrder = theirs;
            other.sortOrder = mine;
            long now = System.currentTimeMillis();
            page.updatedAt = now;
            db.runInTransaction(() -> {
                db.pageDao().upsert(page);
                db.pageDao().upsert(other);
            });
            final int newIndex = target;
            main.post(() -> callback.onResult(newIndex));
        });
    }

    /** Inserts already-built elements (used by import-to-new-page flows). */
    public void addElements(String pageId, List<CanvasElementEntity> elements, Runnable done) {
        executor.execute(() -> {
            db.elementDao().insertAll(elements);
            db.pageDao().touch(pageId, System.currentTimeMillis());
            if (done != null) main.post(done);
        });
    }

    public void savePage(PageEntity page, Runnable done) {
        executor.execute(() -> {
            db.pageDao().upsert(page);
            if (done != null) main.post(done);
        });
    }

    public PageEntity pageSync(String pageId) {
        return db.pageDao().findSync(pageId);
    }

    /** Renders a small preview used by page cards; runs off the UI thread. */
    public void saveThumbnail(String pageId, Bitmap pageBitmap, Runnable done) {
        executor.execute(() -> {
            try {
                int targetWidth = 420;
                float ratio = pageBitmap.getHeight() / (float) pageBitmap.getWidth();
                Bitmap scaled = Bitmap.createScaledBitmap(pageBitmap, targetWidth,
                        Math.max(1, Math.round(targetWidth * ratio)), true);
                File file = new File(files.thumbnailsDir(), "page_" + pageId + ".png");
                try (java.io.OutputStream out = new java.io.FileOutputStream(file)) {
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                if (scaled != pageBitmap) scaled.recycle();
                db.pageDao().setThumbnail(pageId, files.relative(file), System.currentTimeMillis());
            } catch (Exception ignored) {
                // A missing thumbnail is cosmetic; never let it fail a save.
            } finally {
                if (!pageBitmap.isRecycled()) pageBitmap.recycle();
            }
            if (done != null) main.post(done);
        });
    }

    public void writeRecovery(String pageId, String json) {
        executor.execute(() -> files.writeTextAtomically(files.recoveryFile(pageId), json));
    }

    public String readRecovery(String pageId) {
        return files.readText(files.recoveryFile(pageId));
    }

    public void clearRecovery(String pageId) {
        executor.execute(() -> files.deleteQuietly(files.recoveryFile(pageId)));
    }

    public void deletePage(String pageId, boolean permanent, Runnable done) {
        executor.execute(() -> {
            if (permanent) db.pageDao().deletePermanently(pageId);
            else db.pageDao().trash(pageId, System.currentTimeMillis());
            if (done != null) main.post(done);
        });
    }
}
