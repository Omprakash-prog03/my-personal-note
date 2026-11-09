package com.archive.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "research_projects", indices = {@Index("trashed"), @Index("updatedAt")})
public class ResearchProjectEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String title = "";
    public String description = "";
    public String questions = "";
    public String hypothesis = "";
    public String findings = "";
    public String conclusion = "";
    public int colour = 0xFF3F5B8C;
    public boolean favourite = false;
    public boolean trashed = false;
    public long trashedAt = 0L;
    public long createdAt = 0L;
    public long updatedAt = 0L;
}
