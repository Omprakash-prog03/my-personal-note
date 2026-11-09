package com.nuzhat.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "journal_entries", indices = {@Index("date"), @Index("trashed"), @Index("favourite")})
public class JournalEntryEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String title = "";
    @NonNull public String body = "";
    public String mood = "";
    public int moodColour = 0xFFA9713A;
    public long date = 0L;
    public boolean favourite = false;
    public boolean trashed = false;
    public long trashedAt = 0L;
    public long createdAt = 0L;
    public long updatedAt = 0L;
}
