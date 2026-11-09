package com.archive.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(tableName = "collection_tag_cross_ref",
        primaryKeys = {"collectionId", "tagId"},
        foreignKeys = {
                @ForeignKey(entity = CollectionEntity.class, parentColumns = "id",
                        childColumns = "collectionId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = TagEntity.class, parentColumns = "id",
                        childColumns = "tagId", onDelete = ForeignKey.CASCADE)},
        indices = {@Index("tagId")})
public class CollectionTagCrossRef {
    @NonNull public String collectionId = "";
    @NonNull public String tagId = "";
}
