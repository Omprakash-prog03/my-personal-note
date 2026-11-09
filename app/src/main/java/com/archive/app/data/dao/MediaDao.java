package com.archive.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.archive.app.data.entity.MediaEntity;

import java.util.List;

@Dao
public interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(MediaEntity media);

    @Query("SELECT * FROM media WHERE id = :id")
    MediaEntity findSync(String id);

    @Query("SELECT * FROM media WHERE id = :id")
    LiveData<MediaEntity> observe(String id);

    @Query("SELECT * FROM media ORDER BY createdAt DESC LIMIT :limit")
    LiveData<List<MediaEntity>> observeRecent(int limit);

    @Query("SELECT * FROM media WHERE kind = :kind ORDER BY createdAt DESC")
    LiveData<List<MediaEntity>> observeKind(String kind);

    @Query("SELECT * FROM media ORDER BY createdAt DESC")
    List<MediaEntity> allSync();

    @Query("DELETE FROM media WHERE id = :id")
    void delete(String id);

    @Query("SELECT COUNT(*) FROM media")
    int count();

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM media")
    long totalBytes();
}
