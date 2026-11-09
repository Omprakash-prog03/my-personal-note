package com.nuzhat.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A scrapbook page (fixed size) or an infinite canvas belonging to one collection. */
@Entity(tableName = "pages",
        foreignKeys = @ForeignKey(entity = CollectionEntity.class, parentColumns = "id",
                childColumns = "collectionId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("collectionId"), @Index("trashed"), @Index("updatedAt")})
public class PageEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String collectionId = "";
    @NonNull public String title = "";
    /** A4_PORTRAIT | A4_LANDSCAPE | SQUARE | JOURNAL | CUSTOM */
    public String formatKey = "A4_PORTRAIT";
    /** PAGE | INFINITE */
    public String mode = "PAGE";
    public int width = 1240;
    public int height = 1754;
    /** Background is stored on the page, never baked into the objects. */
    public String backgroundJson = "{\"type\":\"PLAIN\",\"color\":\"#FFFDF6E8\"}";
    public int sortOrder = 0;
    public boolean favourite = false;
    public boolean trashed = false;
    public long trashedAt = 0L;
    public String thumbnailPath;
    public long createdAt = 0L;
    public long updatedAt = 0L;
}
