package com.nuzhat.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One canvas object. Geometry lives in real columns (cheap queries, culling, ordering);
 * type-specific attributes live in {@code props} as JSON so new object types need no migration.
 */
@Entity(tableName = "canvas_elements",
        foreignKeys = @ForeignKey(entity = PageEntity.class, parentColumns = "id",
                childColumns = "pageId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("pageId"), @Index(value = {"pageId", "zIndex"}), @Index("groupId")})
public class CanvasElementEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String pageId = "";
    @NonNull public String type = "TEXT";
    public float x = 0f;
    public float y = 0f;
    public float width = 100f;
    public float height = 100f;
    public float rotation = 0f;
    public float scale = 1f;
    public float opacity = 1f;
    public int zIndex = 0;
    public boolean visible = true;
    public boolean locked = false;
    public String groupId;
    @NonNull public String props = "{}";
    public long createdAt = 0L;
    public long updatedAt = 0L;
}
