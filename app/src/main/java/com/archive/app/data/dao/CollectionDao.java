package com.archive.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.archive.app.data.entity.CollectionEntity;

import java.util.List;

@Dao
public interface CollectionDao {

    /** A collection row plus its live page count, for card subtitles. */
    class CollectionWithCount {
        @Embedded public CollectionEntity collection;
        public int pageCount;
    }

    @Query("SELECT * FROM collections WHERE trashed = 0 ORDER BY pinned DESC, sortOrder ASC, updatedAt DESC")
    LiveData<List<CollectionEntity>> observeAll();

    @Query("SELECT * FROM collections WHERE trashed = 0 AND pinned = 1 ORDER BY updatedAt DESC")
    LiveData<List<CollectionEntity>> observePinned();

    @Query("SELECT c.*, (SELECT COUNT(*) FROM pages p WHERE p.collectionId = c.id AND p.trashed = 0) AS pageCount "
            + "FROM collections c WHERE c.trashed = 0 ORDER BY c.pinned DESC, c.sortOrder ASC, c.updatedAt DESC")
    LiveData<List<CollectionWithCount>> observeWithCounts();

    @Query("SELECT c.*, (SELECT COUNT(*) FROM pages p WHERE p.collectionId = c.id AND p.trashed = 0) AS pageCount "
            + "FROM collections c WHERE c.trashed = 0 AND c.pinned = 1 ORDER BY c.updatedAt DESC")
    LiveData<List<CollectionWithCount>> observePinnedWithCounts();

    @Query("SELECT * FROM collections WHERE id = :id")
    LiveData<CollectionEntity> observe(String id);

    @Query("SELECT * FROM collections WHERE id = :id")
    CollectionEntity findSync(String id);

    @Query("SELECT * FROM collections WHERE trashed = 0 ORDER BY pinned DESC, sortOrder ASC, updatedAt DESC")
    List<CollectionEntity> allSync();

    @Query("SELECT COUNT(*) FROM collections")
    int countAllSync();

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM collections WHERE trashed = 0")
    int maxSortOrder();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CollectionEntity collection);

    @Query("UPDATE collections SET trashed = 1, trashedAt = :at, updatedAt = :at WHERE id = :id")
    void trash(String id, long at);

    @Query("UPDATE collections SET trashed = 0, trashedAt = 0, updatedAt = :at WHERE id = :id")
    void restore(String id, long at);

    @Query("UPDATE collections SET coverMediaId = :mediaId, updatedAt = :at WHERE id = :id")
    void setCover(String id, String mediaId, long at);

    @Query("DELETE FROM collections WHERE id = :id")
    void deletePermanently(String id);

    @Query("DELETE FROM collections WHERE trashed = 1")
    void deleteAllTrashed();

    @Query("SELECT COUNT(*) FROM collections WHERE trashed = 1")
    int countTrashed();

    @Query("UPDATE collections SET updatedAt = :at WHERE id = :id")
    void touch(String id, long at);

    @Query("SELECT coverMediaId FROM collections WHERE coverMediaId IS NOT NULL")
    List<String> coverMediaIds();

    @Query("UPDATE collections SET sortOrder = :order, updatedAt = :at WHERE id = :id")
    void updateOrder(String id, int order, long at);
}
