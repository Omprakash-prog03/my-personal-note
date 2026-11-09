package com.archive.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.archive.app.data.entity.CollectionTagCrossRef;
import com.archive.app.data.entity.PageTagCrossRef;
import com.archive.app.data.entity.TagEntity;

import java.util.List;

@Dao
public interface TagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertTag(TagEntity tag);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertPageRef(PageTagCrossRef ref);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertCollectionRef(CollectionTagCrossRef ref);

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    TagEntity findByName(String name);

    @Query("SELECT t.* FROM tags t JOIN page_tag_cross_ref r ON r.tagId = t.id WHERE r.pageId = :pageId ORDER BY t.name")
    List<TagEntity> tagsForPage(String pageId);

    @Query("SELECT t.* FROM tags t JOIN collection_tag_cross_ref r ON r.tagId = t.id WHERE r.collectionId = :collectionId ORDER BY t.name")
    List<TagEntity> tagsForCollection(String collectionId);

    @Query("DELETE FROM page_tag_cross_ref WHERE pageId = :pageId")
    void clearPageTags(String pageId);

    @Query("DELETE FROM collection_tag_cross_ref WHERE collectionId = :collectionId")
    void clearCollectionTags(String collectionId);

    @Query("SELECT * FROM tags ORDER BY name")
    List<TagEntity> allTags();
}
