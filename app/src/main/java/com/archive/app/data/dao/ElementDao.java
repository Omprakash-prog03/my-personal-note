package com.archive.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.archive.app.data.entity.CanvasElementEntity;

import java.util.List;

@Dao
public interface ElementDao {

    @Query("SELECT * FROM canvas_elements WHERE pageId = :pageId ORDER BY zIndex ASC")
    List<CanvasElementEntity> loadSync(String pageId);

    @Query("SELECT COUNT(*) FROM canvas_elements WHERE pageId = :pageId")
    int countForPage(String pageId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CanvasElementEntity> elements);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CanvasElementEntity element);

    @Query("DELETE FROM canvas_elements WHERE id IN (:ids)")
    void deleteByIds(List<String> ids);

    @Query("DELETE FROM canvas_elements WHERE pageId = :pageId")
    void deleteForPage(String pageId);

    @Query("SELECT props FROM canvas_elements")
    List<String> allProps();

    @Query("SELECT * FROM canvas_elements WHERE pageId = :pageId AND type = :type ORDER BY zIndex ASC")
    List<CanvasElementEntity> loadByType(String pageId, String type);

    /**
     * One transaction per autosave batch: deletes first (so a duplicate id can be re-inserted safely),
     * then upserts. Either the whole canvas change lands or none of it does.
     */
    @Transaction
    default void saveBatch(String pageId, long updatedAt, List<CanvasElementEntity> upserts, List<String> deletes) {
        if (deletes != null && !deletes.isEmpty()) {
            deleteByIds(deletes);
        }
        if (upserts != null && !upserts.isEmpty()) {
            insertAll(upserts);
        }
    }
}
