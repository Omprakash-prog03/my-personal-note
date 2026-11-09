package com.archive.app.canvas.engine;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import com.archive.app.data.storage.FileStore;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bitmap cache for canvas images. Decodes are sampled to one of a few buckets sized for the display,
 * happen off the UI thread, and never block a frame — the renderer draws a placeholder until ready.
 */
public class BitmapStore {

    private static final int[] BUCKETS = {256, 512, 1024, 2048};

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private final Set<String> loading = new HashSet<>();

    public Bitmap get(String mediaId, String relativePath, int requiredWidth, int requiredHeight,
                      Runnable onReady) {
        if (relativePath == null || mediaId == null) return null;
        File file = com.archive.app.ArchiveApp.self().files().fileForRelative(relativePath);
        if (!file.exists()) return null;

        int bucket = pickBucket(Math.max(requiredWidth, requiredHeight));
        final String key = mediaId + ":" + bucket;
        Bitmap cached = cache.get(key);
        if (cached != null && !cached.isRecycled()) return cached;

        if (!loading.contains(key)) {
            loading.add(key);
            IO.execute(() -> {
                Bitmap bitmap = FileStore.decodeSampled(file, bucket, bucket);
                if (bitmap != null) cache.put(key, bitmap);
                MAIN.post(() -> {
                    loading.remove(key);
                    if (onReady != null) onReady.run();
                });
            });
        }
        return null;
    }

    /** Pre-warms the cache for export, where placeholders are unacceptable. */
    public Bitmap loadBlocking(String mediaId, String relativePath, int requiredSize) {
        if (relativePath == null) return null;
        File file = com.archive.app.ArchiveApp.self().files().fileForRelative(relativePath);
        if (!file.exists()) return null;
        int bucket = pickBucket(requiredSize);
        Bitmap bitmap = cache.get(mediaId + ":" + bucket);
        if (bitmap == null) {
            bitmap = FileStore.decodeSampled(file, bucket, bucket);
            if (bitmap != null) cache.put(mediaId + ":" + bucket, bitmap);
        }
        return bitmap;
    }

    private int pickBucket(int required) {
        for (int bucket : BUCKETS) {
            if (required <= bucket) return bucket;
        }
        return BUCKETS[BUCKETS.length - 1];
    }

    public void evictAll() {
        cache.evictAll();
    }
}
