package com.nuzhat.app.data.storage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;

import com.nuzhat.app.data.entity.MediaEntity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * All archive bytes live under filesDir/archive. Database rows only ever store paths relative to
 * filesDir, which makes backups portable and keeps large blobs out of SQLite.
 */
public class FileStore {

    public static final String ROOT = "nuzhat";
    private final Context context;

    public FileStore(Context context) {
        this.context = context.getApplicationContext();
        ensureDirs();
    }

    private void ensureDirs() {
        //noinspection ResultOfMethodCallIgnored
        root().mkdirs();
        //noinspection ResultOfMethodCallIgnored
        new File(root(), "media/images").mkdirs();
        //noinspection ResultOfMethodCallIgnored
        new File(root(), "media/documents").mkdirs();
        //noinspection ResultOfMethodCallIgnored
        new File(root(), "media/audio").mkdirs();
        //noinspection ResultOfMethodCallIgnored
        new File(root(), "media/stickers").mkdirs();
        //noinspection ResultOfMethodCallIgnored
        new File(root(), "media/backgrounds").mkdirs();
        //noinspection ResultOfMethodCallIgnored
        new File(root(), "thumbnails").mkdirs();
        //noinspection ResultOfMethodCallIgnored
        new File(root(), "exports").mkdirs();
        //noinspection ResultOfMethodCallIgnored
        new File(root(), "backups").mkdirs();
        //noinspection ResultOfMethodCallIgnored
        new File(root(), "recovery").mkdirs();
    }

    public Context context() { return context; }

    public File root() { return new File(context.getFilesDir(), ROOT); }
    public File exportsDir() { return new File(root(), "exports"); }
    public File backupsDir() { return new File(root(), "backups"); }
    public File recoveryDir() { return new File(root(), "recovery"); }
    public File thumbnailsDir() { return new File(root(), "thumbnails"); }

    public File mediaDirFor(String kind) {
        switch (kind) {
            case MediaEntity.KIND_DOCUMENT: return new File(root(), "media/documents");
            case MediaEntity.KIND_AUDIO: return new File(root(), "media/audio");
            case MediaEntity.KIND_STICKER: return new File(root(), "media/stickers");
            case MediaEntity.KIND_BACKGROUND: return new File(root(), "media/backgrounds");
            case MediaEntity.KIND_IMAGE:
            default: return new File(root(), "media/images");
        }
    }

    public File fileFor(MediaEntity media) {
        return new File(context.getFilesDir(), media.relativePath);
    }

    public File fileForRelative(String relativePath) {
        return new File(context.getFilesDir(), relativePath);
    }

    public String relative(File file) {
        String base = context.getFilesDir().getAbsolutePath();
        String path = file.getAbsolutePath();
        if (path.startsWith(base)) {
            return path.substring(base.length() + 1);
        }
        return path;
    }

    public File thumbnailFile(String mediaId) {
        return new File(thumbnailsDir(), mediaId + ".jpg");
    }

    /** Copies an incoming document/photo stream into private storage under a unique name. */
    public File writeStream(InputStream in, String kind, String extension) throws IOException {
        String safeExt = extension == null || extension.isEmpty() ? "bin" : extension.replaceAll("[^A-Za-z0-9]", "");
        File target = new File(mediaDirFor(kind), java.util.UUID.randomUUID() + "." + safeExt);
        try (OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } finally {
            try { in.close(); } catch (IOException ignored) { }
        }
        return target;
    }

    public int[] decodeBounds(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        return new int[]{options.outWidth, options.outHeight};
    }

    /** 320 px thumbnail, JPEG 82 — used by every list, never the full-resolution image. */
    public File createThumbnail(String mediaId, File source) {
        int[] size = decodeBounds(source);
        if (size[0] <= 0 || size[1] <= 0) return null;
        Bitmap bitmap = decodeSampled(source, 320, 320);
        if (bitmap == null) return null;
        File target = thumbnailFile(mediaId);
        try (OutputStream out = new FileOutputStream(target)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out);
        } catch (IOException e) {
            return null;
        } finally {
            bitmap.recycle();
        }
        return target;
    }

    /** Memory-safe decode: never allocates a full-size bitmap for a large photo. */
    public static Bitmap decodeSampled(File file, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;

        int sample = 1;
        if (reqWidth > 0 && reqHeight > 0) {
            while ((options.outWidth / (sample * 2)) >= reqWidth
                    && (options.outHeight / (sample * 2)) >= reqHeight) {
                sample *= 2;
            }
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    public long totalBytes() {
        return sizeOf(root()) + sizeOf(context.getDatabasePath("nuzhat.db"));
    }

    private long sizeOf(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total += sizeOf(child);
        }
        return total;
    }

    public void clearThumbnailCache() {
        File[] files = thumbnailsDir().listFiles();
        if (files == null) return;
        for (File file : files) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    public void deleteQuietly(File file) {
        if (file == null || !file.exists()) return;
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    public void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /** Crash-recovery snapshots are written atomically so a half-written file is never restored. */
    public void writeTextAtomically(File target, String text) {
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(temp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            return;
        }
        if (target.exists()) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        temp.renameTo(target);
    }

    public String readText(File file) {
        if (file == null || !file.exists()) return null;
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int read = in.read(buffer);
            if (read <= 0) return "";
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public File recoveryFile(String pageId) {
        return new File(recoveryDir(), pageId + ".json");
    }

    public String extensionFor(String mime, String fallbackName) {
        String guess = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (guess != null && !guess.isEmpty()) return guess.toLowerCase(Locale.US);
        if (fallbackName != null && fallbackName.contains(".")) {
            return fallbackName.substring(fallbackName.lastIndexOf('.') + 1).toLowerCase(Locale.US);
        }
        if (mime != null && mime.startsWith("image/")) return "jpg";
        if (mime != null && mime.equals("application/pdf")) return "pdf";
        if (mime != null && mime.startsWith("text/")) return "txt";
        return "bin";
    }

    public boolean isExternalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    public static String sanitiseFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "nuzhat";
        return name.trim().replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", "_");
    }
}
