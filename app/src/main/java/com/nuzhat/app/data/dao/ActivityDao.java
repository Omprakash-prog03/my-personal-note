package com.nuzhat.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.nuzhat.app.data.entity.ActivityEntity;

import java.util.List;

@Dao
public interface ActivityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ActivityEntity activity);

    /** The timeline reads the most recent slice; filtering happens in the ViewModel. */
    @Query("SELECT * FROM activities ORDER BY timestamp DESC LIMIT 1000")
    LiveData<List<ActivityEntity>> recent();

    @Query("SELECT * FROM activities ORDER BY timestamp DESC LIMIT 1000")
    List<ActivityEntity> recentSync();

    @Query("SELECT COUNT(*) FROM activities")
    int countSync();

    /** Used by Settings when the user trims history. */
    @Query("DELETE FROM activities WHERE timestamp < :cutoff")
    int deleteOlderThan(long cutoff);

    /** Keeps the newest rows only, whatever the retention setting says. */
    @Query("DELETE FROM activities WHERE id NOT IN "
            + "(SELECT id FROM activities ORDER BY timestamp DESC LIMIT :keep)")
    int trimTo(int keep);

    @Query("DELETE FROM activities")
    void clearAll();
}
