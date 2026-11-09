package com.archive.app.data.storage;

import android.content.Context;
import android.database.Cursor;

import com.archive.app.data.database.AppDatabase;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backups are a single zip: manifest + SQLite database + media/thumbnails/recovery files.
 *
 * Restore is deliberately two-phase. The zip is unpacked into pending_restore/ and a marker file is
 * written; the swap happens in {@link com.archive.app.ArchiveApp#onCreate()} *before* Room opens,
 * so a restore can never operate on a live database from a running UI.
 */
public final class BackupManager {

    public static final String MARKER = "restore.pending";
    public static final String PENDING_DIR = "pending_restore";

    private BackupManager() {}

    public static File createBackup(Context context, AppDatabase db, FileStore files) throws IOException {
        // Flush the WAL so archive.db is self-contained.
        try (Cursor cursor = db.getOpenHelper().getWritableDatabase()
                .query("PRAGMA wal_checkpoint(FULL)")) {
            if (cursor != null) cursor.moveToFirst();
        }

        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                .format(new java.util.Date());
        File target = new File(files.backupsDir(), "archive-" + stamp + ".zip");
        JSONObject manifest = new JSONObject();
        try {
            manifest.put("app", "ARCHIVE");
            manifest.put("format", 1);
            manifest.put("createdAt", System.currentTimeMillis());
            manifest.put("versionName", com.archive.app.BuildConfig.VERSION_NAME);
        } catch (Exception ignored) { }

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target)))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.toString().getBytes("UTF-8"));
            zip.closeEntry();

            File dbFile = context.getDatabasePath("archive.db");
            addFile(zip, dbFile, "database/archive.db");
            addFile(zip, new File(dbFile.getPath() + "-wal"), "database/archive.db-wal");
            addFile(zip, new File(dbFile.getPath() + "-shm"), "database/archive.db-shm");

            addTree(zip, new File(files.root(), "media"), "archive/media");
            addTree(zip, new File(files.root(), "thumbnails"), "archive/thumbnails");
        }
        return target;
    }

    private static void addTree(ZipOutputStream zip, File dir, String entryPrefix) throws IOException {
        if (!dir.exists()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                addTree(zip, child, entryPrefix + "/" + child.getName());
            } else {
                addFile(zip, child, entryPrefix + "/" + child.getName());
            }
        }
    }

    private static void addFile(ZipOutputStream zip, File file, String entryName) throws IOException {
        if (file == null || !file.exists()) return;
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) zip.write(buffer, 0, read);
        }
        zip.closeEntry();
    }

    /** Unpacks and validates a backup, then arms the marker consumed on next launch. */
    public static boolean stageRestore(Context context, File backup) throws IOException {
        File pending = new File(context.getFilesDir(), PENDING_DIR);
        if (pending.exists()) deleteTree(pending);
        //noinspection ResultOfMethodCallIgnored
        pending.mkdirs();

        boolean manifestFound = false;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(backup)))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.contains("..")) throw new IOException("Unsafe backup entry");
                File target = new File(pending, name);
                if (entry.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    target.mkdirs();
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null) {
                    //noinspection ResultOfMethodCallIgnored
                    parent.mkdirs();
                }
                try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) out.write(buffer, 0, read);
                }
                if ("manifest.json".equals(name)) manifestFound = true;
            }
        }
        if (!manifestFound || !new File(pending, "database/archive.db").exists()) {
            deleteTree(pending);
            return false;
        }
        //noinspection ResultOfMethodCallIgnored
        new File(context.getFilesDir(), MARKER).createNewFile();
        return true;
    }

    /** Called from Application.onCreate before Room is built. */
    public static void applyPendingRestore(Context context) {
        File marker = new File(context.getFilesDir(), MARKER);
        if (!marker.exists()) return;
        File pending = new File(context.getFilesDir(), PENDING_DIR);
        File dbFile = context.getDatabasePath("archive.db");
        File restoredDb = new File(pending, "database/archive.db");
        if (!restoredDb.exists()) {
            //noinspection ResultOfMethodCallIgnored
            marker.delete();
            deleteTree(pending);
            return;
        }
        deleteTree(new File(context.getFilesDir(), FileStore.ROOT));
        //noinspection ResultOfMethodCallIgnored
        dbFile.delete();
        //noinspection ResultOfMethodCallIgnored
        new File(dbFile.getPath() + "-wal").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(dbFile.getPath() + "-shm").delete();
        File dbParent = dbFile.getParentFile();
        if (dbParent != null) {
            //noinspection ResultOfMethodCallIgnored
            dbParent.mkdirs();
        }
        //noinspection ResultOfMethodCallIgnored
        restoredDb.renameTo(dbFile);
        File restoredArchive = new File(pending, FileStore.ROOT);
        if (restoredArchive.exists()) {
            File target = new File(context.getFilesDir(), FileStore.ROOT);
            copyTree(restoredArchive, target);
        }
        deleteTree(pending);
        //noinspection ResultOfMethodCallIgnored
        marker.delete();
    }

    private static void copyTree(File source, File target) {
        if (source.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            target.mkdirs();
            File[] children = source.listFiles();
            if (children == null) return;
            for (File child : children) copyTree(child, new File(target, child.getName()));
        } else {
            File parent = target.getParentFile();
            if (parent != null) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (InputStream in = new FileInputStream(source);
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            } catch (IOException ignored) { }
        }
    }

    public static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    public static File newestBackup(FileStore files) {
        File[] backups = files.backupsDir().listFiles();
        if (backups == null || backups.length == 0) return null;
        File newest = null;
        for (File file : backups) {
            if (newest == null || file.lastModified() > newest.lastModified()) newest = file;
        }
        return newest;
    }
}
