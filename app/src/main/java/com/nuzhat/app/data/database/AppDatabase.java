package com.nuzhat.app.data.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;

import com.nuzhat.app.data.dao.ActivityDao;
import com.nuzhat.app.data.dao.CollectionDao;
import com.nuzhat.app.data.dao.ElementDao;
import com.nuzhat.app.data.dao.JournalDao;
import com.nuzhat.app.data.dao.LibraryDao;
import com.nuzhat.app.data.dao.MediaDao;
import com.nuzhat.app.data.dao.PageDao;
import com.nuzhat.app.data.dao.PreferenceDao;
import com.nuzhat.app.data.dao.ResearchDao;
import com.nuzhat.app.data.dao.SearchDao;
import com.nuzhat.app.data.dao.TagDao;
import com.nuzhat.app.data.entity.ActivityEntity;
import com.nuzhat.app.data.entity.AttachmentEntity;
import com.nuzhat.app.data.entity.CanvasElementEntity;
import com.nuzhat.app.data.entity.CollectionEntity;
import com.nuzhat.app.data.entity.CollectionTagCrossRef;
import com.nuzhat.app.data.entity.JournalEntryEntity;
import com.nuzhat.app.data.entity.LibraryItemEntity;
import com.nuzhat.app.data.entity.MediaEntity;
import com.nuzhat.app.data.entity.PageEntity;
import com.nuzhat.app.data.entity.PageTagCrossRef;
import com.nuzhat.app.data.entity.ResearchProjectEntity;
import com.nuzhat.app.data.entity.ResearchReferenceEntity;
import com.nuzhat.app.data.entity.StickerEntity;
import com.nuzhat.app.data.entity.TagEntity;
import com.nuzhat.app.data.entity.TemplateEntity;
import com.nuzhat.app.data.entity.UserPreferenceEntity;

/**
 * Version 1 schema. Schema JSON is exported to app/schemas so future upgrades can be written
 * with confidence; destructive migration fallback is deliberately NOT enabled.
 */
@Database(
        version = 1,
        exportSchema = true,
        entities = {
                ActivityEntity.class,
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

    public abstract ActivityDao activityDao();
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
