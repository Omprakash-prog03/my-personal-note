package com.archive.app.data.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;

import com.archive.app.data.dao.CollectionDao;
import com.archive.app.data.dao.ElementDao;
import com.archive.app.data.dao.JournalDao;
import com.archive.app.data.dao.LibraryDao;
import com.archive.app.data.dao.MediaDao;
import com.archive.app.data.dao.PageDao;
import com.archive.app.data.dao.PreferenceDao;
import com.archive.app.data.dao.ResearchDao;
import com.archive.app.data.dao.SearchDao;
import com.archive.app.data.dao.TagDao;
import com.archive.app.data.entity.AttachmentEntity;
import com.archive.app.data.entity.CanvasElementEntity;
import com.archive.app.data.entity.CollectionEntity;
import com.archive.app.data.entity.CollectionTagCrossRef;
import com.archive.app.data.entity.JournalEntryEntity;
import com.archive.app.data.entity.LibraryItemEntity;
import com.archive.app.data.entity.MediaEntity;
import com.archive.app.data.entity.PageEntity;
import com.archive.app.data.entity.PageTagCrossRef;
import com.archive.app.data.entity.ResearchProjectEntity;
import com.archive.app.data.entity.ResearchReferenceEntity;
import com.archive.app.data.entity.StickerEntity;
import com.archive.app.data.entity.TagEntity;
import com.archive.app.data.entity.TemplateEntity;
import com.archive.app.data.entity.UserPreferenceEntity;

/**
 * Version 1 schema. Schema JSON is exported to app/schemas so future upgrades can be written
 * with confidence; destructive migration fallback is deliberately NOT enabled.
 */
@Database(
        version = 1,
        exportSchema = true,
        entities = {
                CollectionEntity.class,
                PageEntity.class,
                CanvasElementEntity.class,
                MediaEntity.class,
                AttachmentEntity.class,
                JournalEntryEntity.class,
                ResearchProjectEntity.class,
                ResearchReferenceEntity.class,
                LibraryItemEntity.class,
                StickerEntity.class,
                TemplateEntity.class,
                TagEntity.class,
                CollectionTagCrossRef.class,
                PageTagCrossRef.class,
                UserPreferenceEntity.class
        })
public abstract class AppDatabase extends RoomDatabase {

    /** Populated as the schema evolves; kept here so migrations are impossible to forget. */
    public static final Migration[] MIGRATIONS = new Migration[]{};

    public abstract CollectionDao collectionDao();
    public abstract PageDao pageDao();
    public abstract ElementDao elementDao();
    public abstract MediaDao mediaDao();
    public abstract TagDao tagDao();
    public abstract JournalDao journalDao();
    public abstract ResearchDao researchDao();
    public abstract LibraryDao libraryDao();
    public abstract SearchDao searchDao();
    public abstract PreferenceDao preferenceDao();
}
