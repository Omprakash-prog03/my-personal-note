package com.nuzhat.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Structural preferences that should travel with a backup (home layout, pinned sections). */
@Entity(tableName = "user_preferences")
public class UserPreferenceEntity {
    @PrimaryKey @NonNull public String key = "";
    @NonNull public String value = "";
}
