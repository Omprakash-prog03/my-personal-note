package com.archive.app.data.repository;

import android.graphics.Bitmap;
import android.os.Handler;

import com.archive.app.data.database.AppDatabase;
import com.archive.app.data.entity.CanvasElementEntity;
import com.archive.app.data.entity.PageEntity;
import com.archive.app.data.storage.FileStore;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Persistence for one editable canvas: element batches, page rows, thumbnails and crash-recovery
 * snapshots. Kept separate from {@link ArchiveRepository} because saving here is debounced and
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
        executor.execute(() -> {
            db.elementDao().saveBatch(pageId, updatedAt, upserts, deletes);
            db.pageDao().touch(pageId, updatedAt);
            if (done != null) main.post(done);
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
