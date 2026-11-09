package com.archive.app.ui.canvas;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.archive.app.ArchiveApp;
import com.archive.app.canvas.engine.CanvasDocument;
import com.archive.app.data.entity.CanvasElementEntity;
import com.archive.app.data.entity.PageEntity;
import com.archive.app.data.repository.CanvasRepository;

import java.util.List;

/**
 * Owns the live canvas document and all autosave behaviour.
 *
 * Saving is debounced (600 ms) so continuous dragging does not hammer SQLite, but it is also forced
 * on stop, on back, before export and before closing. A recovery snapshot is written with every save
 * batch, which is what makes "the app was killed" survivable rather than merely unlikely.
 */
public class CanvasViewModel extends AndroidViewModel {

    public static final int STATE_SAVED = 0;
    public static final int STATE_SAVING = 1;

    private final CanvasRepository repo;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveTask = this::save;

    private final MutableLiveData<CanvasDocument> document = new MutableLiveData<>();
    private final MutableLiveData<Integer> saveState = new MutableLiveData<>(STATE_SAVED);
    private final MutableLiveData<Boolean> pageMissing = new MutableLiveData<>(false);
    private final MutableLiveData<RecoveryInfo> recovery = new MutableLiveData<>();

    private CanvasDocument current;
    private boolean loaded;

    public static class RecoveryInfo {
        public CanvasDocument document;
        public long savedAt;
    }

    public CanvasViewModel(@NonNull Application application) {
        super(application);
        repo = ArchiveApp.get(application).canvasRepo();
    }

    public LiveData<CanvasDocument> document() { return document; }
    public LiveData<Integer> saveState() { return saveState; }
    public LiveData<Boolean> pageMissing() { return pageMissing; }
    public LiveData<RecoveryInfo> recovery() { return recovery; }

    public CanvasDocument current() { return current; }

    public void load(final String pageId) {
        if (loaded) return;
        loaded = true;
        repo.load(pageId, loadedPage -> {
            if (loadedPage.page == null) {
                pageMissing.setValue(true);
                return;
            }
            current = CanvasDocument.fromPage(loadedPage.page, loadedPage.elements);
            document.setValue(current);

            // A recovery snapshot newer than the stored page means the app died with unsaved work.
            String raw = repo.readRecovery(pageId);
            if (raw != null && !raw.isEmpty()) {
                CanvasDocument.Recovery recovered = CanvasDocument.readRecovery(raw, loadedPage.page);
                if (recovered.valid && recovered.savedAt > loadedPage.page.updatedAt + 500) {
                    RecoveryInfo info = new RecoveryInfo();
                    info.document = recovered.document;
                    info.savedAt = recovered.savedAt;
                    recovery.setValue(info);
                }
            }
        });
    }

    public void acceptRecovery() {
        RecoveryInfo info = recovery.getValue();
        if (info == null || info.document == null) return;
        current = info.document;
        current.markAllDirty();
        current.markBackgroundDirty();
        current.touch();
        document.setValue(current);
        recovery.setValue(null);
        scheduleSave();
    }

    public void discardRecovery(String pageId) {
        recovery.setValue(null);
        repo.clearRecovery(pageId);
    }

    /** Called by the view after any mutation; coalesces rapid edits into one write. */
    public void scheduleSave() {
        handler.removeCallbacks(saveTask);
        handler.postDelayed(saveTask, 600);
    }

    public void saveNow() {
        handler.removeCallbacks(saveTask);
        save();
    }

    public void saveNow(Runnable after) {
        handler.removeCallbacks(saveTask);
        save();
        if (after != null) handler.postDelayed(after, 250);
    }

    private void save() {
        final CanvasDocument doc = current;
        if (doc == null) return;
        List<CanvasElementEntity> upserts = doc.dirtyEntities();
        List<String> deletes = doc.deletedIds();
        boolean backgroundDirty = doc.isBackgroundDirty();
        if (upserts.isEmpty() && deletes.isEmpty() && !backgroundDirty) return;

        doc.clearDirty();
        saveState.setValue(STATE_SAVING);
        PageEntity page = doc.toPageEntity();
        long stamp = System.currentTimeMillis();
        doc.pageId = page.id;

        repo.save(doc.pageId, stamp, upserts, deletes, () -> saveState.setValue(STATE_SAVED));
        repo.savePage(page, null);
        repo.writeRecovery(doc.pageId, doc.toRecoveryJson());
    }

    public void saveThumbnail(String pageId, Bitmap bitmap) {
        repo.saveThumbnail(pageId, bitmap, null);
    }

    public void setTitle(String pageId, String title) {
        if (current == null) return;
        current.title = title;
        current.touch();
        repo.savePage(current.toPageEntity(), null);
    }

    @Override
    protected void onCleared() {
        handler.removeCallbacks(saveTask);
        save();
        super.onCleared();
    }
}
