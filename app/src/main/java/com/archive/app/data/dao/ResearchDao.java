package com.archive.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.archive.app.data.entity.ResearchProjectEntity;
import com.archive.app.data.entity.ResearchReferenceEntity;

import java.util.List;

@Dao
public interface ResearchDao {

    @Query("SELECT * FROM research_projects WHERE trashed = 0 ORDER BY updatedAt DESC")
    LiveData<List<ResearchProjectEntity>> observeProjects();

    @Query("SELECT * FROM research_projects WHERE id = :id")
    LiveData<ResearchProjectEntity> observeProject(String id);

    @Query("SELECT * FROM research_projects WHERE id = :id")
    ResearchProjectEntity findProjectSync(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertProject(ResearchProjectEntity project);

    @Query("UPDATE research_projects SET trashed = 1, trashedAt = :at WHERE id = :id")
    void trashProject(String id, long at);

    @Query("UPDATE research_projects SET trashed = 0, trashedAt = 0, updatedAt = :at WHERE id = :id")
    void restoreProject(String id, long at);

    @Query("DELETE FROM research_projects WHERE id = :id")
    void deleteProjectPermanently(String id);

    @Query("DELETE FROM research_projects WHERE trashed = 1")
    void deleteAllTrashedProjects();

    @Query("SELECT COUNT(*) FROM research_projects WHERE trashed = 1")
    int countTrashedProjects();

    @Query("SELECT * FROM research_references WHERE projectId = :projectId AND trashed = 0 ORDER BY dateCollected DESC")
    LiveData<List<ResearchReferenceEntity>> observeReferences(String projectId);

    @Query("SELECT * FROM research_references WHERE projectId = :projectId AND trashed = 0 ORDER BY dateCollected DESC")
    List<ResearchReferenceEntity> referencesSync(String projectId);

    @Query("SELECT * FROM research_references WHERE id = :id")
    ResearchReferenceEntity findReferenceSync(String id);

    @Query("SELECT COUNT(*) FROM research_references WHERE projectId = :projectId AND trashed = 0")
    int countReferences(String projectId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertReference(ResearchReferenceEntity reference);

    @Query("UPDATE research_references SET trashed = 1, trashedAt = :at WHERE id = :id")
    void trashReference(String id, long at);

    @Query("UPDATE research_references SET trashed = 0, trashedAt = 0, updatedAt = :at WHERE id = :id")
    void restoreReference(String id, long at);

    @Query("DELETE FROM research_references WHERE id = :id")
    void deleteReferencePermanently(String id);

    @Query("DELETE FROM research_references WHERE trashed = 1")
    void deleteAllTrashedReferences();

    @Query("SELECT COUNT(*) FROM research_references WHERE trashed = 1")
    int countTrashedReferences();
}
