package com.archive.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(tableName = "page_tag_cross_ref",
        primaryKeys = {"pageId", "tagId"},
        foreignKeys = {
                @ForeignKey(entity = PageEntity.class, parentColumns = "id",
                        childColumns = "pageId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = TagEntity.class, parentColumns = "id",
                        childColumns = "tagId", onDelete = ForeignKey.CASCADE)},
        indices = {@Index("tagId")})
public class PageTagCrossRef {
    @NonNull public String pageId = "";
    @NonNull public String tagId = "";
}
