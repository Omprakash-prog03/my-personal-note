package com.archive.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Links an imported document to a page object so originals stay addressable. */
@Entity(tableName = "attachments", indices = {@Index("mediaId"), @Index("pageId")})
public class AttachmentEntity {
    @PrimaryKey @NonNull public String id = "";
    public String pageId;
    public String elementId;
    @NonNull public String mediaId = "";
    @NonNull public String title = "";
    @NonNull public String mime = "";
    public long createdAt = 0L;
}
