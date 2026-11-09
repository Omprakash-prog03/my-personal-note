package com.archive.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "templates")
public class TemplateEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String name = "";
    @NonNull public String category = "GENERAL";
    public String formatKey = "A4_PORTRAIT";
    public String backgroundJson = "{\"type\":\"PLAIN\",\"color\":\"#FFFDF6E8\"}";
    public String elementsJson = "[]";
    public long createdAt = 0L;
}
