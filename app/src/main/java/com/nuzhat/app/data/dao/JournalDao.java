package com.nuzhat.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.nuzhat.app.data.entity.JournalEntryEntity;

import java.util.List;

@Dao
public interface JournalDao {

    @Query("SELECT * FROM journal_entries WHERE trashed = 0 ORDER BY date DESC, updatedAt DESC")
    LiveData<List<JournalEntryEntity>> observeAll();

    @Query("SELECT * FROM journal_entries WHERE trashed = 0 ORDER BY date DESC, updatedAt DESC LIMIT 1")
    LiveData<JournalEntryEntity> observeLatest();

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    JournalEntryEntity findSync(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(JournalEntryEntity entry);

    @Query("UPDATE journal_entries SET trashed = 1, trashedAt = :at WHERE id = :id")
    void trash(String id, long at);

    @Query("UPDATE journal_entries SET trashed = 0, trashedAt = 0, updatedAt = :at WHERE id = :id")
    void restore(String id, long at);

    @Query("DELETE FROM journal_entries WHERE id = :id")
    void deletePermanently(String id);

    @Query("DELETE FROM journal_entries WHERE trashed = 1")
    void deleteAllTrashed();

    @Query("SELECT COUNT(*) FROM journal_entries WHERE trashed = 1")
    int countTrashed();
}
