package com.archive.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "stickers", indices = {@Index(value = "stickerKey", unique = true)})
public class StickerEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String stickerKey = "";
    @NonNull public String label = "";
    @NonNull public String category = "DECORATIVE";
    public boolean favourite = false;
    public String customMediaId;
    public long createdAt = 0L;
}
