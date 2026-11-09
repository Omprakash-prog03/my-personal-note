package com.nuzhat.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Books, films, music, articles, sites and quotes kept apart from the canvas. */
@Entity(tableName = "library_items", indices = {@Index("type"), @Index("trashed"), @Index("favourite")})
public class LibraryItemEntity {
    public static final String TYPE_BOOK = "BOOK";
    public static final String TYPE_MOVIE = "MOVIE";
    public static final String TYPE_MUSIC = "MUSIC";
    public static final String TYPE_ARTICLE = "ARTICLE";
    public static final String TYPE_WEBSITE = "WEBSITE";
    public static final String TYPE_QUOTE = "QUOTE";

    @PrimaryKey @NonNull public String id = "";
    @NonNull public String type = TYPE_BOOK;
    @NonNull public String title = "";
    public String creator = "";
    public String year = "";
    public String notes = "";
    public int rating = 0;
    public String tags = "";
    public String coverMediaId;
    public boolean favourite = false;
    public boolean trashed = false;
    public long trashedAt = 0L;
    public long createdAt = 0L;
    public long updatedAt = 0L;
}
