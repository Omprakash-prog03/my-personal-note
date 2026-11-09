package com.nuzhat.app.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One entry in the archive's history. Rows are written by {@code ActivityRecorder} from real
 * repository operations — nothing here is generated for display.
 */
@Entity(tableName = "activities",
        indices = {@Index("timestamp"), @Index("actionType"), @Index("collectionId"), @Index("pageId")})
public class ActivityEntity {

    // Collections -----------------------------------------------------------------
    public static final String COLLECTION_CREATED = "COLLECTION_CREATED";
    public static final String COLLECTION_RENAMED = "COLLECTION_RENAMED";
    public static final String COLLECTION_ARCHIVED = "COLLECTION_ARCHIVED";
    public static final String COLLECTION_RESTORED = "COLLECTION_RESTORED";
    public static final String COLLECTION_DELETED = "COLLECTION_DELETED";
    public static final String COLLECTION_COVER = "COLLECTION_COVER";

    // Pages -----------------------------------------------------------------------
    public static final String PAGE_CREATED = "PAGE_CREATED";
    public static final String PAGE_RENAMED = "PAGE_RENAMED";
    public static final String PAGE_DELETED = "PAGE_DELETED";
    public static final String PAGE_EDITED = "PAGE_EDITED";
    public static final String PAGE_BACKGROUND = "PAGE_BACKGROUND";

    // Media -----------------------------------------------------------------------
    public static final String MEDIA_IMPORTED = "MEDIA_IMPORTED";
    public static final String PHOTO_CAPTURED = "PHOTO_CAPTURED";
    public static final String MEDIA_DELETED = "MEDIA_DELETED";

    // Objects ---------------------------------------------------------------------
    public static final String OBJECT_ADDED = "OBJECT_ADDED";
    public static final String OBJECT_DELETED = "OBJECT_DELETED";
    public static final String OBJECT_MOVED = "OBJECT_MOVED";
    public static final String OBJECT_RESIZED = "OBJECT_RESIZED";
    public static final String OBJECT_ROTATED = "OBJECT_ROTATED";
    public static final String DRAWING_ADDED = "DRAWING_ADDED";

    // Exports ---------------------------------------------------------------------
    public static final String PAGE_EXPORTED = "PAGE_EXPORTED";
    public static final String COLLECTION_EXPORTED = "COLLECTION_EXPORTED";

    // Filter families used by the timeline chips.
    public static final String FAMILY_COLLECTION = "collection";
    public static final String FAMILY_PAGE = "page";
    public static final String FAMILY_MEDIA = "media";
    public static final String FAMILY_OBJECT = "object";
    public static final String FAMILY_EXPORT = "export";

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    public String actionType = "";

    /** Ready to show, e.g. “Added 3 photographs to Cinema”. */
    @NonNull
    public String description = "";

    @Nullable
    public String collectionId;

    @Nullable
    public String pageId;

    @Nullable
    public String objectId;

    public long timestamp;

    @Nullable
    public String metadataJson;

    /** Chip bucket: collection / page / media / object / export. */
    @NonNull
    public String family() {
        if (actionType.startsWith("COLLECTION_")) return FAMILY_COLLECTION;
        if (actionType.startsWith("PAGE_")) return FAMILY_PAGE;
        if (actionType.startsWith("MEDIA_") || actionType.startsWith("PHOTO_")) return FAMILY_MEDIA;
        if (actionType.startsWith("OBJECT_") || actionType.startsWith("DRAWING_")) return FAMILY_OBJECT;
        return FAMILY_EXPORT;
    }
}
