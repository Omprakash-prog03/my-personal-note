package com.archive.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.archive.app.data.entity.UserPreferenceEntity;

import java.util.List;

@Dao
public interface PreferenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(UserPreferenceEntity preference);

    @Query("SELECT value FROM user_preferences WHERE `key` = :key")
    String get(String key);

    @Query("SELECT * FROM user_preferences")
    List<UserPreferenceEntity> all();
}
