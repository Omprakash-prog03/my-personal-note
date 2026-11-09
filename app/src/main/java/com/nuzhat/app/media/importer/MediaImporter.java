package com.nuzhat.app.media.importer;

import android.content.ContentResolver;
import android.net.Uri;

import com.nuzhat.app.data.entity.MediaEntity;
import com.nuzhat.app.data.repository.NuzhatRepository;
import com.nuzhat.app.data.storage.FileStore;
import com.nuzhat.app.utils.Ids;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Copies any picked/created content into private storage and returns the media row to persist.
 * Background thread only — every method here performs file I/O.
 */
public final class MediaImporter {

    private MediaImporter() {}

    public static MediaEntity importFromUri(NuzhatRepository repo, Uri uri) throws IOException {
        FileStore files = repo.files();
        ContentResolver resolver = files.context().getContentResolver();
        String mime = NuzhatRepository.mimeOf(resolver, uri, "application/octet-stream");
        String name = NuzhatRepository.displayName(resolver, uri, "import");
        String kind = kindFor(mime);
        String extension = files.extensionFor(mime, name);

        InputStream input = resolver.openInputStream(uri);
        if (input == null) throw new IOException("Could not open " + uri);
        File target = files.writeStream(input, kind, extension);
        return build(files, target, name, mime, kind);
    }

    public static MediaEntity importCameraPhoto(NuzhatRepository repo, File temporary) throws IOException {
        FileStore files = repo.files();
        File target = new File(files.mediaDirFor(MediaEntity.KIND_IMAGE),
                Ids.newId() + ".jpg");
        if (!temporary.renameTo(target)) {
            try (InputStream in = new java.io.FileInputStream(temporary);
                 OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            files.deleteQuietly(temporary);
        }
        return build(files, target, "Photograph " + System.currentTimeMillis() + ".jpg", "image/jpeg",
                MediaEntity.KIND_IMAGE);
    }

    public static MediaEntity importBytes(NuzhatRepository repo, byte[] data, String name, String mime,
                                          String kind) throws IOException {
        FileStore files = repo.files();
        File target = new File(files.mediaDirFor(kind),
                Ids.newId() + "." + files.extensionFor(mime, name));
        try (OutputStream out = new FileOutputStream(target)) {
            out.write(data);
        }
        return build(files, target, name, mime, kind);
    }

    public static MediaEntity importText(NuzhatRepository repo, String name, String text) throws IOException {
        FileStore files = repo.files();
        String safe = (name == null || name.trim().isEmpty()) ? "note" : name;
        File target = new File(files.mediaDirFor(MediaEntity.KIND_DOCUMENT),
                Ids.newId() + "." + (safe.toLowerCase().endsWith(".md") ? "md" : "txt"));
        try (OutputStream out = new FileOutputStream(target)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return build(files, target, safe, "text/plain", MediaEntity.KIND_DOCUMENT);
    }

    private static MediaEntity build(FileStore files, File target, String displayName, String mime,
                                     String kind) {
        MediaEntity media = new MediaEntity();
        media.id = Ids.newId();
        media.kind = kind;
        media.mime = mime;
        media.displayName = displayName;
        media.relativePath = files.relative(target);
        media.sizeBytes = target.length();
        media.createdAt = System.currentTimeMillis();
        if (MediaEntity.KIND_IMAGE.equals(kind) || MediaEntity.KIND_BACKGROUND.equals(kind)
                || MediaEntity.KIND_STICKER.equals(kind)) {
            int[] size = files.decodeBounds(target);
            media.width = size[0];
            media.height = size[1];
            File thumb = files.createThumbnail(media.id, target);
            if (thumb != null) media.thumbPath = files.relative(thumb);
        }
        return media;
    }

    public static String kindFor(String mime) {
        if (mime == null) return MediaEntity.KIND_DOCUMENT;
        if (mime.startsWith("image/")) return MediaEntity.KIND_IMAGE;
        if (mime.startsWith("audio/")) return MediaEntity.KIND_AUDIO;
        return MediaEntity.KIND_DOCUMENT;
    }
}
