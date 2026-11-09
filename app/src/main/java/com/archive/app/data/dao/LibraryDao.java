package com.archive.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.archive.app.data.entity.LibraryItemEntity;

import java.util.List;

@Dao
public interface LibraryDao {

    @Query("SELECT * FROM library_items WHERE trashed = 0 ORDER BY updatedAt DESC")
    LiveData<List<LibraryItemEntity>> observeAll();

    @Query("SELECT * FROM library_items WHERE id = :id")
    LibraryItemEntity findSync(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(LibraryItemEntity item);

    @Query("UPDATE library_items SET trashed = 1, trashedAt = :at WHERE id = :id")
    void trash(String id, long at);

    @Query("UPDATE library_items SET trashed = 0, trashedAt = 0, updatedAt = :at WHERE id = :id")
    void restore(String id, long at);

    @Query("DELETE FROM library_items WHERE id = :id")
    void deletePermanently(String id);

    @Query("DELETE FROM library_items WHERE trashed = 1")
    void deleteAllTrashed();

    @Query("SELECT coverMediaId FROM library_items WHERE coverMediaId IS NOT NULL")
    List<String> coverMediaIds();

    @Query("SELECT COUNT(*) FROM library_items WHERE trashed = 1")
    int countTrashed();
}
