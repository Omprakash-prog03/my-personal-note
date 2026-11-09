package com.nuzhat.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Metadata only — bytes live in app-private storage, never in a database row. */
@Entity(tableName = "media", indices = {@Index("kind"), @Index("createdAt")})
public class MediaEntity {
    public static final String KIND_IMAGE = "IMAGE";
    public static final String KIND_DOCUMENT = "DOCUMENT";
    public static final String KIND_AUDIO = "AUDIO";
    public static final String KIND_STICKER = "STICKER";
    public static final String KIND_BACKGROUND = "BACKGROUND";

    @PrimaryKey @NonNull public String id = "";
    @NonNull public String kind = KIND_IMAGE;
    @NonNull public String mime = "image/jpeg";
    @NonNull public String displayName = "";
    /** Path relative to filesDir, so backups stay portable across installs. */
    @NonNull public String relativePath = "";
    public String thumbPath;
    public int width = 0;
    public int height = 0;
    public long sizeBytes = 0L;
    public long createdAt = 0L;
}
