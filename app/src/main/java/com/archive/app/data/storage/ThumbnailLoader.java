package com.archive.app.data.storage;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.archive.app.ArchiveApp;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small LRU + background decode for list thumbnails. Views are tagged with the requested path so a
 * recycled row never shows another row's image.
 */
public final class ThumbnailLoader {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final int TAG_KEY = "thumbnail_request".hashCode();

    private ThumbnailLoader() {}

    public static void load(ImageView view, File file, int reqW, int reqH) {
        if (view == null) return;
        if (file == null || !file.exists()) {
            view.setImageDrawable(null);
            view.setTag(TAG_KEY, null);
            return;
        }
        final String path = file.getAbsolutePath();
        Bitmap cached = CACHE.get(path);
        if (cached != null && !cached.isRecycled()) {
            view.setImageBitmap(cached);
            return;
        }
        view.setTag(TAG_KEY, path);
        final String requested = path;
        IO.execute(() -> {
            Bitmap bitmap = FileStore.decodeSampled(file, reqW, reqH);
            if (bitmap == null) return;
            CACHE.put(requested, bitmap);
            MAIN.post(() -> {
                Object tag = view.getTag(TAG_KEY);
                if (requested.equals(tag)) {
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    public static void clear() {
        CACHE.evictAll();
    }

    public static void loadMedia(ImageView view, String relativePath, int reqW, int reqH) {
        if (relativePath == null) {
            if (view != null) view.setImageDrawable(null);
            return;
        }
        load(view, ArchiveApp.self().files().fileForRelative(relativePath), reqW, reqH);
    }
}
