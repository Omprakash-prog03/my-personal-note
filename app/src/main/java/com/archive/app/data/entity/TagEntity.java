package com.archive.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "tags", indices = {@Index(value = "name", unique = true)})
public class TagEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String name = "";
    public int colour = 0xFF8A7A63;
    public long createdAt = 0L;
}
