package com.nuzhat.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "collections", indices = {
        @Index("trashed"), @Index("pinned"), @Index("favourite"), @Index("updatedAt")
})
public class CollectionEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String name = "";
    public String description = "";
    public String coverMediaId;
    public int colour = 0xFFB08968;
    public String icon = "folder";
    public int sortOrder = 0;
    public boolean favourite = false;
    public boolean pinned = false;
    public boolean archived = false;
    public boolean trashed = false;
    public long trashedAt = 0L;
    public long createdAt = 0L;
    public long updatedAt = 0L;
    public String category = "Unsorted";
}
