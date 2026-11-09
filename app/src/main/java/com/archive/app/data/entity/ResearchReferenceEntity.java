package com.archive.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Manually entered source metadata. ARCHIVE never claims a source is verified or auto-cites it. */
@Entity(tableName = "research_references",
        foreignKeys = @ForeignKey(entity = ResearchProjectEntity.class, parentColumns = "id",
                childColumns = "projectId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("projectId"), @Index("trashed")})
public class ResearchReferenceEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String projectId = "";
    @NonNull public String title = "";
    public String author = "";
    public String publisher = "";
    public String publishedDate = "";
    public String sourceUrl = "";
    public String notes = "";
    public String sourceType = "ARTICLE";
    public long dateCollected = 0L;
    public boolean trashed = false;
    public long trashedAt = 0L;
    public long createdAt = 0L;
    public long updatedAt = 0L;
}
