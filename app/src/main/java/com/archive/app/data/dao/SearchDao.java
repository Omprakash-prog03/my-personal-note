package com.archive.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;

import com.archive.app.data.dao.model.SearchResult;
import com.archive.app.data.dao.model.TrashItem;

import java.util.List;

/**
 * Global search and trash listing. SQLite LIKE keeps this dependency-free and works offline;
 * the searched columns are indexed where they are used as filters, and result sets are capped.
 */
@Dao
public interface SearchDao {

    @Query("SELECT * FROM ("
            + "SELECT 'COLLECTION' AS type, c.id AS id, c.name AS title, c.description AS subtitle, c.updatedAt AS updatedAt "
            + "FROM collections c WHERE c.trashed = 0 AND (c.name LIKE :q OR c.description LIKE :q) "
            + "UNION ALL SELECT 'PAGE', p.id, p.title, c2.name, p.updatedAt "
            + "FROM pages p JOIN collections c2 ON c2.id = p.collectionId "
            + "WHERE p.trashed = 0 AND c2.trashed = 0 AND p.title LIKE :q "
            + "UNION ALL SELECT 'TEXT', p2.id, TRIM(SUBSTR(e.props, 1, 120)), p2.title, e.updatedAt "
            + "FROM canvas_elements e JOIN pages p2 ON p2.id = e.pageId "
            + "WHERE e.type = 'TEXT' AND p2.trashed = 0 AND e.props LIKE :q "
            + "UNION ALL SELECT 'JOURNAL', j.id, j.title, SUBSTR(j.body, 1, 140), j.updatedAt "
            + "FROM journal_entries j WHERE j.trashed = 0 AND (j.title LIKE :q OR j.body LIKE :q) "
            + "UNION ALL SELECT 'REFERENCE', r.id, r.title, (r.author || ' ' || r.publisher), r.updatedAt "
            + "FROM research_references r JOIN research_projects rp ON rp.id = r.projectId "
            + "WHERE r.trashed = 0 AND rp.trashed = 0 "
            + "AND (r.title LIKE :q OR r.author LIKE :q OR r.notes LIKE :q OR r.sourceUrl LIKE :q OR r.publisher LIKE :q) "
            + "UNION ALL SELECT 'LIBRARY', l.id, l.title, (COALESCE(l.creator, '') || ' ' || COALESCE(l.notes, '')), l.updatedAt "
            + "FROM library_items l WHERE l.trashed = 0 "
            + "AND (l.title LIKE :q OR l.creator LIKE :q OR l.notes LIKE :q OR l.tags LIKE :q) "
            + "UNION ALL SELECT 'PAGE', p3.id, p3.title, ('#' || t.name), p3.updatedAt "
            + "FROM pages p3 JOIN page_tag_cross_ref ptr ON ptr.pageId = p3.id JOIN tags t ON t.id = ptr.tagId "
            + "WHERE p3.trashed = 0 AND t.name LIKE :q"
            + ") ORDER BY updatedAt DESC LIMIT 200")
    LiveData<List<SearchResult>> search(String q);

    @Query("SELECT * FROM ("
            + "SELECT 'COLLECTION' AS type, id AS id, name AS name, trashedAt AS trashedAt FROM collections WHERE trashed = 1 "
            + "UNION ALL SELECT 'PAGE', id, title, trashedAt FROM pages WHERE trashed = 1 "
            + "UNION ALL SELECT 'JOURNAL', id, title, trashedAt FROM journal_entries WHERE trashed = 1 "
            + "UNION ALL SELECT 'RESEARCH', id, title, trashedAt FROM research_projects WHERE trashed = 1 "
            + "UNION ALL SELECT 'REFERENCE', id, title, trashedAt FROM research_references WHERE trashed = 1 "
            + "UNION ALL SELECT 'LIBRARY', id, title, trashedAt FROM library_items WHERE trashed = 1"
            + ") ORDER BY trashedAt DESC LIMIT 400")
    LiveData<List<TrashItem>> observeTrash();
}
