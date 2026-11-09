package com.archive.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.archive.app.data.entity.PageEntity;

import java.util.List;

@Dao
public interface PageDao {

    @Query("SELECT * FROM pages WHERE collectionId = :collectionId AND trashed = 0 "
            + "ORDER BY sortOrder ASC, updatedAt DESC")
    LiveData<List<PageEntity>> observeForCollection(String collectionId);

    @Query("SELECT p.* FROM pages p JOIN collections c ON c.id = p.collectionId "
            + "WHERE p.trashed = 0 AND c.trashed = 0 ORDER BY p.updatedAt DESC LIMIT :limit")
    LiveData<List<PageEntity>> observeRecent(int limit);

    @Query("SELECT * FROM pages WHERE id = :id")
    LiveData<PageEntity> observe(String id);

    @Query("SELECT * FROM pages WHERE id = :id")
    PageEntity findSync(String id);

    @Query("SELECT * FROM pages WHERE collectionId = :collectionId AND trashed = 0 ORDER BY sortOrder ASC, updatedAt DESC")
    List<PageEntity> forCollectionSync(String collectionId);

    @Query("SELECT COUNT(*) FROM pages WHERE collectionId = :collectionId AND trashed = 0")
    int countForCollection(String collectionId);

    @Query("SELECT COUNT(*) FROM pages WHERE trashed = 1")
    int countTrashed();

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM pages WHERE collectionId = :collectionId")
    int maxSortOrder(String collectionId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PageEntity page);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PageEntity> pages);

    @Query("UPDATE pages SET updatedAt = :at WHERE id = :id")
    void touch(String id, long at);

    @Query("UPDATE pages SET thumbnailPath = :path, updatedAt = :at WHERE id = :id")
    void setThumbnail(String id, String path, long at);

    @Query("UPDATE pages SET trashed = 1, trashedAt = :at WHERE id = :id")
    void trash(String id, long at);

    @Query("UPDATE pages SET trashed = 0, trashedAt = 0, updatedAt = :at WHERE id = :id")
    void restore(String id, long at);

    @Query("DELETE FROM pages WHERE id = :id")
    void deletePermanently(String id);

    @Query("SELECT backgroundJson FROM pages")
    List<String> allBackgrounds();

    @Query("DELETE FROM pages WHERE trashed = 1")
    void deleteAllTrashed();
}
