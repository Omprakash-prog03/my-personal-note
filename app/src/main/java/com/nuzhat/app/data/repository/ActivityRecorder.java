package com.nuzhat.app.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nuzhat.app.data.dao.ActivityDao;
import com.nuzhat.app.data.entity.ActivityEntity;
import com.nuzhat.app.utils.Ids;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Writes real history rows off the main thread.
 *
 * <p>Continuous gestures are collapsed: a drag, a slider or an autosave produces one row because
 * repeat events for the same object inside a window are dropped. That is the difference between a
 * readable timeline and hundreds of “moved object” entries.</p>
 */
public class ActivityRecorder {

    /** Two quick taps on the same object are still one intent; sub-second repeats are noise. */
    private static final long DEFAULT_WINDOW_MS = 1500L;
    /** Editing is expected to be continuous: one row per page per five minutes. */
    public static final long EDIT_WINDOW_MS = 5L * 60L * 1000L;
    /** Trimmed here so a runaway gesture cannot create an unbounded table. */
    private static final int KEEP_LIMIT = 4000;

    private final ActivityDao dao;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, Long> lastKeys = new ConcurrentHashMap<>();

    public ActivityRecorder(ActivityDao dao, ExecutorService executor) {
        this.dao = dao;
        this.executor = executor;
    }

    public void record(@NonNull String type, @NonNull String description,
                       @Nullable String collectionId, @Nullable String pageId,
                       @Nullable String objectId) {
        record(type, description, collectionId, pageId, objectId, DEFAULT_WINDOW_MS);
    }

    public synchronized void record(@NonNull String type, @NonNull String description,
                                    @Nullable String collectionId, @Nullable String pageId,
                                    @Nullable String objectId, long windowMs) {
        String target = objectId != null ? objectId : pageId != null ? pageId : collectionId;
        String key = type + '|' + (target == null ? "" : target);
        long now = System.currentTimeMillis();
        Long previous = lastKeys.get(key);
        if (windowMs > 0 && previous != null && now - previous < windowMs) {
            return;
        }
        lastKeys.put(key, now);

        ActivityEntity entity = new ActivityEntity();
        entity.id = Ids.newId();
        entity.actionType = type;
        entity.description = description;
        entity.collectionId = collectionId;
        entity.pageId = pageId;
        entity.objectId = objectId;
        entity.timestamp = now;
        executor.execute(() -> {
            dao.insert(entity);
            if (dao.countSync() > KEEP_LIMIT) {
                dao.deleteOlderThan(now - 90L * 24L * 60L * 60L * 1000L);
            }
        });
    }
}
