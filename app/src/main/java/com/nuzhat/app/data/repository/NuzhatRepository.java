package com.nuzhat.app.data.repository;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.OpenableColumns;

import androidx.lifecycle.LiveData;

import com.nuzhat.app.data.dao.CollectionDao;
import com.nuzhat.app.data.dao.model.SearchResult;
import com.nuzhat.app.data.dao.model.TrashItem;
import com.nuzhat.app.data.database.AppDatabase;
import com.nuzhat.app.data.entity.CanvasElementEntity;
import com.nuzhat.app.data.entity.ActivityEntity;
import com.nuzhat.app.data.entity.CollectionEntity;
import com.nuzhat.app.data.entity.CollectionTagCrossRef;
import com.nuzhat.app.data.entity.JournalEntryEntity;
import com.nuzhat.app.data.entity.LibraryItemEntity;
import com.nuzhat.app.data.entity.MediaEntity;
import com.nuzhat.app.data.entity.PageEntity;
import com.nuzhat.app.data.entity.PageTagCrossRef;
import com.nuzhat.app.data.entity.ResearchProjectEntity;
import com.nuzhat.app.data.entity.ResearchReferenceEntity;
import com.nuzhat.app.data.entity.TagEntity;
import com.nuzhat.app.data.storage.FileStore;
import com.nuzhat.app.domain.model.ElementType;
import com.nuzhat.app.domain.model.PageFormat;
import com.nuzhat.app.media.importer.MediaImporter;
import com.nuzhat.app.utils.Ids;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Single write path for everything except the canvas (which has its own repository because its
 * autosave semantics are different). All DB/file work happens on the shared executor.
 */
public class NuzhatRepository {

    protected final AppDatabase db;
    protected final FileStore files;
    protected final ExecutorService executor;
    protected final Handler main;
    protected final ActivityRecorder activity;

    public NuzhatRepository(AppDatabase db, FileStore files, ExecutorService executor, Handler main) {
        this.db = db;
        this.files = files;
        this.executor = executor;
        this.main = main;
        this.activity = new ActivityRecorder(db.activityDao(), executor);
    }

    /** History writer shared by every operation below and by the canvas. */
    public ActivityRecorder activity() { return activity; }

    public ExecutorService executor() { return executor; }
    public Handler main() { return main; }
    public FileStore files() { return files; }

    // ------------------------------------------------------------------ collections

    /** Re-sorted whenever the user changes the ordering in the library. */
    private final androidx.lifecycle.MutableLiveData<String> collectionSort =
            new androidx.lifecycle.MutableLiveData<>(com.nuzhat.app.utils.SettingsStore.SORT_CUSTOM);

    public void setCollectionSort(String mode) {
        if (mode == null) return;
        if (!mode.equals(collectionSort.getValue())) collectionSort.setValue(mode);
    }

    /** Newest-first history feed for the Activity tab. */
    public LiveData<List<ActivityEntity>> recentActivity() {
        return db.activityDao().recent();
    }

    public LiveData<String> collectionSort() { return collectionSort; }

    public LiveData<List<CollectionDao.CollectionWithCount>> collections() {
        return androidx.lifecycle.Transformations.switchMap(collectionSort, mode -> {
            if (com.nuzhat.app.utils.SettingsStore.SORT_RECENT.equals(mode)) {
                return db.collectionDao().observeWithCountsRecent();
            }
            if (com.nuzhat.app.utils.SettingsStore.SORT_NAME.equals(mode)) {
                return db.collectionDao().observeWithCountsName();
            }
            return db.collectionDao().observeWithCounts();
        });
    }

    public LiveData<List<CollectionDao.CollectionWithCount>> pinnedCollections() {
        return db.collectionDao().observePinnedWithCounts();
    }

    public LiveData<List<CollectionEntity>> collectionsPlain() {
        return db.collectionDao().observeAll();
    }

    public LiveData<CollectionEntity> collection(String id) {
        return db.collectionDao().observe(id);
    }

    public void collectionsSync(Callback<List<CollectionEntity>> callback) {
        executor.execute(() -> {
            List<CollectionEntity> items = db.collectionDao().allSync();
            main.post(() -> callback.onResult(items));
        });
    }

    public void collectionOnce(String id, Callback<CollectionEntity> callback) {
        executor.execute(() -> {
            CollectionEntity entity = db.collectionDao().findSync(id);
            main.post(() -> callback.onResult(entity));
        });
    }

    public void createCollection(String name, String description, int colour, String icon,
                                 Callback<String> callback) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            CollectionEntity entity = new CollectionEntity();
            entity.id = Ids.newId();
            entity.name = name == null || name.trim().isEmpty() ? "Untitled collection" : name.trim();
            entity.description = description == null ? "" : description;
            entity.colour = colour;
            entity.icon = icon;
            entity.sortOrder = db.collectionDao().maxSortOrder() + 1;
            entity.createdAt = now;
            entity.updatedAt = now;
            db.collectionDao().upsert(entity);
            activity.record(ActivityEntity.COLLECTION_CREATED,
                    "Created collection \u201c" + entity.name + "\u201d", entity.id, null, null);
            main.post(() -> callback.onResult(entity.id));
        });
    }

    public void saveCollection(CollectionEntity entity, Runnable done) {
        executor.execute(() -> {
            CollectionEntity before = db.collectionDao().findSync(entity.id);
            entity.updatedAt = System.currentTimeMillis();
            db.collectionDao().upsert(entity);
            if (before != null && !before.name.equals(entity.name)) {
                activity.record(ActivityEntity.COLLECTION_RENAMED,
                        "Renamed collection \u201c" + before.name + "\u201d to \u201c" + entity.name + "\u201d",
                        entity.id, null, null);
            } else if (before != null && before.archived != entity.archived) {
                activity.record(entity.archived ? ActivityEntity.COLLECTION_ARCHIVED
                                : ActivityEntity.COLLECTION_RESTORED,
                        (entity.archived ? "Archived collection \u201c" : "Restored collection \u201c")
                                + entity.name + "\u201d", entity.id, null, null);
            }
            if (done != null) main.post(done);
        });
    }

    public void trashCollection(String id, Runnable done) {
        executor.execute(() -> {
            CollectionEntity before = db.collectionDao().findSync(id);
            long now = System.currentTimeMillis();
            db.collectionDao().trash(id, now);
            if (before != null) {
                activity.record(ActivityEntity.COLLECTION_DELETED,
                        "Moved collection \u201c" + before.name + "\u201d to trash", id, null, null);
            }
            if (done != null) main.post(done);
        });
    }

    public void setCollectionCover(String collectionId, String mediaId, Runnable done) {
        executor.execute(() -> {
            db.collectionDao().setCover(collectionId, mediaId, System.currentTimeMillis());
            CollectionEntity owner = db.collectionDao().findSync(collectionId);
            if (owner != null) {
                activity.record(ActivityEntity.COLLECTION_COVER,
                        "Changed the cover of \u201c" + owner.name + "\u201d", collectionId, null, null);
            }
            if (done != null) main.post(done);
        });
    }

    /** Deep copy: collection row, its pages and every canvas object. Media stays shared (no byte copy). */
    public void duplicateCollection(String id, Callback<String> callback) {
        executor.execute(() -> {
            CollectionEntity source = db.collectionDao().findSync(id);
            if (source == null) {
                main.post(() -> callback.onResult(null));
                return;
            }
            long now = System.currentTimeMillis();
            CollectionEntity copy = new CollectionEntity();
            copy.id = Ids.newId();
            copy.name = source.name + " (copy)";
            copy.description = source.description;
            copy.coverMediaId = source.coverMediaId;
            copy.colour = source.colour;
            copy.icon = source.icon;
            copy.category = source.category;
            copy.sortOrder = db.collectionDao().maxSortOrder() + 1;
            copy.createdAt = now;
            copy.updatedAt = now;
            db.collectionDao().upsert(copy);

            for (PageEntity page : db.pageDao().forCollectionSync(id)) {
                PageEntity pageCopy = new PageEntity();
                pageCopy.id = Ids.newId();
                pageCopy.collectionId = copy.id;
                pageCopy.title = page.title;
                pageCopy.formatKey = page.formatKey;
                pageCopy.mode = page.mode;
                pageCopy.width = page.width;
                pageCopy.height = page.height;
                pageCopy.backgroundJson = page.backgroundJson;
                pageCopy.sortOrder = page.sortOrder;
                pageCopy.thumbnailPath = page.thumbnailPath;
                pageCopy.createdAt = now;
                pageCopy.updatedAt = now;
                db.pageDao().upsert(pageCopy);

                List<CanvasElementEntity> copies = new ArrayList<>();
                for (CanvasElementEntity element : db.elementDao().loadSync(page.id)) {
                    CanvasElementEntity elementCopy = element;
                    elementCopy.id = Ids.newId();
                    elementCopy.pageId = pageCopy.id;
                    copies.add(elementCopy);
                }
                if (!copies.isEmpty()) db.elementDao().insertAll(copies);
            }
            main.post(() -> callback.onResult(copy.id));
        });
    }

    // ------------------------------------------------------------------ pages

    public LiveData<List<PageEntity>> pages(String collectionId) {
        return db.pageDao().observeForCollection(collectionId);
    }

    public LiveData<List<PageEntity>> recentPages(int limit) {
        return db.pageDao().observeRecent(limit);
    }

    public LiveData<PageEntity> page(String id) {
        return db.pageDao().observe(id);
    }

    public PageEntity pageBlocking(String id) {
        return db.pageDao().findSync(id);
    }

    public void pageOnce(String id, Callback<PageEntity> callback) {
        executor.execute(() -> {
            PageEntity page = db.pageDao().findSync(id);
            main.post(() -> callback.onResult(page));
        });
    }

    public void pagesSync(String collectionId, Callback<List<PageEntity>> callback) {
        executor.execute(() -> {
            List<PageEntity> pages = db.pageDao().forCollectionSync(collectionId);
            main.post(() -> callback.onResult(pages));
        });
    }

    public void createPage(String collectionId, String title, String formatKey, String mode,
                           String backgroundJson, Callback<PageEntity> callback) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            PageEntity page = new PageEntity();
            page.id = Ids.newId();
            page.collectionId = collectionId;
            page.title = title == null || title.trim().isEmpty() ? "Untitled page" : title.trim();
            page.formatKey = PageFormat.normalise(formatKey);
            page.mode = PageFormat.MODE_INFINITE.equals(mode) ? PageFormat.MODE_INFINITE : PageFormat.MODE_PAGE;
            page.width = PageFormat.widthOf(page.formatKey);
            page.height = PageFormat.heightOf(page.formatKey);
            page.backgroundJson = backgroundJson;
            page.sortOrder = db.pageDao().maxSortOrder(collectionId) + 1;
            page.createdAt = now;
            page.updatedAt = now;
            db.pageDao().upsert(page);
            db.collectionDao().touch(collectionId, now);
            CollectionEntity owner = db.collectionDao().findSync(collectionId);
            activity.record(ActivityEntity.PAGE_CREATED,
                    "Created page \u201c" + page.title + "\u201d"
                            + (owner == null ? "" : " in \u201c" + owner.name + "\u201d"),
                    collectionId, page.id, null);
            main.post(() -> callback.onResult(page));
        });
    }

    public void savePage(PageEntity page, Runnable done) {
        executor.execute(() -> {
            PageEntity before = db.pageDao().findSync(page.id);
            page.updatedAt = System.currentTimeMillis();
            db.pageDao().upsert(page);
            if (before != null && !before.title.equals(page.title)) {
                activity.record(ActivityEntity.PAGE_RENAMED,
                        "Renamed page \u201c" + before.title + "\u201d to \u201c" + page.title + "\u201d",
                        page.collectionId, page.id, null);
            }
            if (done != null) main.post(done);
        });
    }

    public void touchPage(String pageId) {
        executor.execute(() -> db.pageDao().touch(pageId, System.currentTimeMillis()));
    }

    public void trashPage(String id, Runnable done) {
        executor.execute(() -> {
            PageEntity before = db.pageDao().findSync(id);
            db.pageDao().trash(id, System.currentTimeMillis());
            if (before != null) {
                activity.record(ActivityEntity.PAGE_DELETED,
                        "Moved page \u201c" + before.title + "\u201d to trash",
                        before.collectionId, id, null);
            }
            if (done != null) main.post(done);
        });
    }

    // ------------------------------------------------------------------ media

    public LiveData<List<MediaEntity>> recentMedia() {
        return db.mediaDao().observeRecent(24);
    }

    public LiveData<List<MediaEntity>> mediaOfKind(String kind) {
        return db.mediaDao().observeKind(kind);
    }

    public MediaEntity mediaBlocking(String id) {
        return db.mediaDao().findSync(id);
    }

    public LiveData<MediaEntity> media(String id) {
        return db.mediaDao().observe(id);
    }

    public void mediaOnce(String id, Callback<MediaEntity> callback) {
        executor.execute(() -> {
            MediaEntity media = db.mediaDao().findSync(id);
            main.post(() -> callback.onResult(media));
        });
    }

    public void saveMediaBlocking(MediaEntity media) {
        db.mediaDao().upsert(media);
    }

    public void saveMedia(MediaEntity media) {
        executor.execute(() -> db.mediaDao().upsert(media));
    }

    /** Import is always a copy into private storage; the source URI is never retained. */
    public void importMedia(Uri uri, Callback<MediaEntity> callback) {
        executor.execute(() -> {
            try {
                MediaEntity media = MediaImporter.importFromUri(this, uri);
                if (media != null) db.mediaDao().upsert(media);
                main.post(() -> callback.onResult(media));
            } catch (Exception e) {
                main.post(() -> callback.onResult(null));
            }
        });
    }

    public void importTextAsDocument(String name, String text, Callback<MediaEntity> callback) {
        executor.execute(() -> {
            try {
                MediaEntity media = MediaImporter.importText(this, name, text);
                db.mediaDao().upsert(media);
                main.post(() -> callback.onResult(media));
            } catch (Exception e) {
                main.post(() -> callback.onResult(null));
            }
        });
    }

    public long storageUsageBlocking() {
        return files.totalBytes();
    }

    public void storageUsage(Callback<Long> callback) {
        executor.execute(() -> {
            long bytes = files.totalBytes() + db.mediaDao().totalBytes();
            main.post(() -> callback.onResult(bytes));
        });
    }

    // ------------------------------------------------------------------ journal

    public LiveData<List<JournalEntryEntity>> journalEntries() {
        return db.journalDao().observeAll();
    }

    public LiveData<JournalEntryEntity> latestJournalEntry() {
        return db.journalDao().observeLatest();
    }

    public JournalEntryEntity journalBlocking(String id) {
        return db.journalDao().findSync(id);
    }

    public void journalOnce(String id, Callback<JournalEntryEntity> callback) {
        executor.execute(() -> {
            JournalEntryEntity entry = db.journalDao().findSync(id);
            main.post(() -> callback.onResult(entry));
        });
    }

    public void saveJournal(JournalEntryEntity entry, Runnable done) {
        executor.execute(() -> {
            entry.updatedAt = System.currentTimeMillis();
            db.journalDao().upsert(entry);
            if (done != null) main.post(done);
        });
    }

    public void newJournalEntry(Callback<JournalEntryEntity> callback) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            JournalEntryEntity entry = new JournalEntryEntity();
            entry.id = Ids.newId();
            entry.date = now;
            entry.createdAt = now;
            entry.updatedAt = now;
            db.journalDao().upsert(entry);
            main.post(() -> callback.onResult(entry));
        });
    }

    // ------------------------------------------------------------------ research

    public LiveData<List<ResearchProjectEntity>> researchProjects() {
        return db.researchDao().observeProjects();
    }

    public LiveData<ResearchProjectEntity> researchProject(String id) {
        return db.researchDao().observeProject(id);
    }

    public ResearchProjectEntity researchProjectBlocking(String id) {
        return db.researchDao().findProjectSync(id);
    }

    public LiveData<List<ResearchReferenceEntity>> references(String projectId) {
        return db.researchDao().observeReferences(projectId);
    }

    public void saveResearchProject(ResearchProjectEntity project, Runnable done) {
        executor.execute(() -> {
            project.updatedAt = System.currentTimeMillis();
            db.researchDao().upsertProject(project);
            if (done != null) main.post(done);
        });
    }

    public void newResearchProject(Callback<ResearchProjectEntity> callback) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            ResearchProjectEntity project = new ResearchProjectEntity();
            project.id = Ids.newId();
            project.title = "Untitled research";
            project.createdAt = now;
            project.updatedAt = now;
            db.researchDao().upsertProject(project);
            main.post(() -> callback.onResult(project));
        });
    }

    public void saveReference(ResearchReferenceEntity reference, Runnable done) {
        executor.execute(() -> {
            reference.updatedAt = System.currentTimeMillis();
            if (reference.dateCollected == 0L) reference.dateCollected = System.currentTimeMillis();
            db.researchDao().upsertReference(reference);
            if (done != null) main.post(done);
        });
    }

    public void referenceOnce(String id, Callback<ResearchReferenceEntity> callback) {
        executor.execute(() -> {
            ResearchReferenceEntity ref = db.researchDao().findReferenceSync(id);
            main.post(() -> callback.onResult(ref));
        });
    }

    public void trashJournal(String id, Runnable done) {
        executor.execute(() -> {
            db.journalDao().trash(id, System.currentTimeMillis());
            if (done != null) main.post(done);
        });
    }

    public void trashResearchProject(String id, Runnable done) {
        executor.execute(() -> {
            db.researchDao().trashProject(id, System.currentTimeMillis());
            if (done != null) main.post(done);
        });
    }

    public void trashReference(String id, Runnable done) {
        executor.execute(() -> {
            db.researchDao().trashReference(id, System.currentTimeMillis());
            if (done != null) main.post(done);
        });
    }

    // ------------------------------------------------------------------ library

    public LiveData<List<LibraryItemEntity>> libraryItems() {
        return db.libraryDao().observeAll();
    }

    public LibraryItemEntity libraryItemBlocking(String id) {
        return db.libraryDao().findSync(id);
    }

    public void saveLibraryItem(LibraryItemEntity item, Runnable done) {
        executor.execute(() -> {
            item.updatedAt = System.currentTimeMillis();
            db.libraryDao().upsert(item);
            if (done != null) main.post(done);
        });
    }

    public void newLibraryItem(Callback<LibraryItemEntity> callback) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            LibraryItemEntity item = new LibraryItemEntity();
            item.id = Ids.newId();
            item.createdAt = now;
            item.updatedAt = now;
            db.libraryDao().upsert(item);
            main.post(() -> callback.onResult(item));
        });
    }

    // ------------------------------------------------------------------ search + trash

    public LiveData<List<SearchResult>> search(String query) {
        return db.searchDao().search("%" + (query == null ? "" : query.trim()) + "%");
    }

    public LiveData<List<TrashItem>> trash() {
        return db.searchDao().observeTrash();
    }

    public void trashCount(Callback<Integer> callback) {
        executor.execute(() -> {
            int count = db.collectionDao().countTrashed()
                    + db.pageDao().countTrashed()
                    + db.journalDao().countTrashed()
                    + db.researchDao().countTrashedProjects()
                    + db.researchDao().countTrashedReferences()
                    + db.libraryDao().countTrashed();
            main.post(() -> callback.onResult(count));
        });
    }

    /** Restores any soft-deleted entity; type strings come from {@link TrashItem}. */
    public void restoreTrashItem(String type, String id, Runnable done) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            switch (type == null ? "" : type) {
                case "COLLECTION": db.collectionDao().restore(id, now); break;
                case "PAGE": db.pageDao().restore(id, now); break;
                case "JOURNAL": db.journalDao().restore(id, now); break;
                case "RESEARCH": db.researchDao().restoreProject(id, now); break;
                case "REFERENCE": db.researchDao().restoreReference(id, now); break;
                case "LIBRARY": db.libraryDao().restore(id, now); break;
                default: break;
            }
            if (done != null) main.post(done);
        });
    }

    public void deleteTrashItemForever(String type, String id, Runnable done) {
        executor.execute(() -> {
            switch (type == null ? "" : type) {
                case "COLLECTION": db.collectionDao().deletePermanently(id); break;
                case "PAGE": db.pageDao().deletePermanently(id); break;
                case "JOURNAL": db.journalDao().deletePermanently(id); break;
                case "RESEARCH": db.researchDao().deleteProjectPermanently(id); break;
                case "REFERENCE": db.researchDao().deleteReferencePermanently(id); break;
                case "LIBRARY": db.libraryDao().deletePermanently(id); break;
                default: break;
            }
            if (done != null) main.post(done);
        });
    }

    public void emptyTrash(Runnable done) {
        executor.execute(() -> {
            db.collectionDao().deleteAllTrashed();
            db.pageDao().deleteAllTrashed();
            db.journalDao().deleteAllTrashed();
            db.researchDao().deleteAllTrashedProjects();
            db.researchDao().deleteAllTrashedReferences();
            db.libraryDao().deleteAllTrashed();
            if (done != null) main.post(done);
        });
    }

    // ------------------------------------------------------------------ tags

    public void setPageTags(String pageId, List<String> names, Runnable done) {
        executor.execute(() -> {
            db.tagDao().clearPageTags(pageId);
            long now = System.currentTimeMillis();
            for (String raw : names) {
                String name = raw == null ? "" : raw.trim();
                if (name.isEmpty()) continue;
                TagEntity tag = db.tagDao().findByName(name);
                if (tag == null) {
                    tag = new TagEntity();
                    tag.id = Ids.newId();
                    tag.name = name;
                    tag.createdAt = now;
                    db.tagDao().upsertTag(tag);
                }
                PageTagCrossRef ref = new PageTagCrossRef();
                ref.pageId = pageId;
                ref.tagId = tag.id;
                db.tagDao().upsertPageRef(ref);
            }
            if (done != null) main.post(done);
        });
    }

    public void pageTags(String pageId, Callback<List<TagEntity>> callback) {
        executor.execute(() -> {
            List<TagEntity> tags = db.tagDao().tagsForPage(pageId);
            main.post(() -> callback.onResult(tags));
        });
    }

    public void setCollectionTags(String collectionId, List<String> names, Runnable done) {
        executor.execute(() -> {
            db.tagDao().clearCollectionTags(collectionId);
            long now = System.currentTimeMillis();
            for (String raw : names) {
                String name = raw == null ? "" : raw.trim();
                if (name.isEmpty()) continue;
                TagEntity tag = db.tagDao().findByName(name);
                if (tag == null) {
                    tag = new TagEntity();
                    tag.id = Ids.newId();
                    tag.name = name;
                    tag.createdAt = now;
                    db.tagDao().upsertTag(tag);
                }
                CollectionTagCrossRef ref = new CollectionTagCrossRef();
                ref.collectionId = collectionId;
                ref.tagId = tag.id;
                db.tagDao().upsertCollectionRef(ref);
            }
            if (done != null) main.post(done);
        });
    }

    // ------------------------------------------------------------------ housekeeping

    /**
     * Reference-counting GC for media. A file is only removed when no canvas element, page
     * background, collection cover or library cover mentions it — which is what makes deleting an
     * object safely undoable within a session.
     */
    public void collectOrphanMedia(Runnable done) {
        executor.execute(() -> {
            List<MediaEntity> all = db.mediaDao().allSync();
            if (all.isEmpty()) {
                if (done != null) main.post(done);
                return;
            }
            java.util.HashSet<String> used = new java.util.HashSet<>();
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "\"(mediaId|imageMediaId)\"\\s*:\\s*\"([^\"]+)\"");
            for (String props : db.elementDao().allProps()) {
                if (props == null) continue;
                java.util.regex.Matcher matcher = pattern.matcher(props);
                while (matcher.find()) used.add(matcher.group(2));
            }
            for (String background : db.pageDao().allBackgrounds()) {
                if (background == null) continue;
                java.util.regex.Matcher matcher = pattern.matcher(background);
                while (matcher.find()) used.add(matcher.group(2));
            }
            used.addAll(db.collectionDao().coverMediaIds());
            used.addAll(db.libraryDao().coverMediaIds());
            for (MediaEntity media : all) {
                if (used.contains(media.id)) continue;
                files.deleteQuietly(files.fileFor(media));
                files.deleteQuietly(files.thumbnailFile(media.id));
                db.mediaDao().delete(media.id);
            }
            if (done != null) main.post(done);
        });
    }

    public String extensionForMime(String mime) {
        return files.extensionFor(mime, null);
    }

    public static String displayName(ContentResolver resolver, Uri uri, String fallback) {
        if (uri == null) return fallback;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        String name = cursor.getString(index);
                        if (name != null && !name.isEmpty()) return name;
                    }
                }
            } catch (Exception ignored) {
                // fall through to the path-based name
            }
        }
        String last = uri.getLastPathSegment();
        return last == null ? fallback : last;
    }

    public static String mimeOf(ContentResolver resolver, Uri uri, String fallback) {
        String mime = resolver.getType(uri);
        return mime == null ? fallback : mime;
    }

    public static ElementType typeOf(String typeName) {
        return ElementType.valueOf(typeName);
    }
}
