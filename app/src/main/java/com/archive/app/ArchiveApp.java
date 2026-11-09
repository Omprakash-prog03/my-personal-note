package com.archive.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.room.Room;

import com.archive.app.data.database.AppDatabase;
import com.archive.app.data.repository.ArchiveRepository;
import com.archive.app.data.repository.CanvasRepository;
import com.archive.app.data.storage.FileStore;
import com.archive.app.domain.model.CanvasElement;
import com.archive.app.domain.model.ElementFactory;
import com.archive.app.domain.model.PageFormat;
import com.archive.app.utils.Ids;
import com.archive.app.utils.SettingsStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Composition root: one database, one file store, one repository pair, one background executor.
 * Nothing in the app constructs its own Room instance or thread pool.
 */
public class ArchiveApp extends Application {

    private static ArchiveApp instance;

    private AppDatabase database;
    private ArchiveRepository repository;
    private CanvasRepository canvasRepository;
    private FileStore fileStore;
    private SettingsStore settings;
    private ExecutorService executor;
    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        executor = Executors.newFixedThreadPool(4);
        mainHandler = new Handler(Looper.getMainLooper());
        settings = new SettingsStore(this);
        // A staged restore must be applied before Room opens its database file.
        com.archive.app.data.storage.BackupManager.applyPendingRestore(this);
        fileStore = new FileStore(this);
        database = Room.databaseBuilder(this, AppDatabase.class, "archive.db")
                .addMigrations(AppDatabase.MIGRATIONS)
                .build();
        repository = new ArchiveRepository(database, fileStore, executor, mainHandler);
        canvasRepository = new CanvasRepository(database, fileStore, executor, mainHandler);
        seedFirstRun();
    }

    public static ArchiveApp get(android.content.Context context) {
        return (ArchiveApp) context.getApplicationContext();
    }

    public static ArchiveApp self() {
        return instance;
    }

    public AppDatabase database() { return database; }
    public ArchiveRepository repo() { return repository; }
    public CanvasRepository canvasRepo() { return canvasRepository; }
    public FileStore files() { return fileStore; }
    public SettingsStore settings() { return settings; }
    public ExecutorService executor() { return executor; }
    public Handler main() { return mainHandler; }

    /** First launch gets an empty but explainable archive: one collection, one guided page. */
    private void seedFirstRun() {
        executor.execute(() -> {
            if (database.collectionDao().countAllSync() > 0) return;

            long now = System.currentTimeMillis();
            com.archive.app.data.entity.CollectionEntity collection = new com.archive.app.data.entity.CollectionEntity();
            collection.id = Ids.newId();
            collection.name = "Getting started";
            collection.description = "A short tour of what ARCHIVE can hold.";
            collection.colour = 0xFFB08968;
            collection.icon = "folder";
            collection.sortOrder = 0;
            collection.createdAt = now;
            collection.updatedAt = now;
            database.collectionDao().upsert(collection);

            com.archive.app.data.entity.PageEntity page = new com.archive.app.data.entity.PageEntity();
            page.id = Ids.newId();
            page.collectionId = collection.id;
            page.title = "How this page works";
            page.formatKey = PageFormat.A4_PORTRAIT;
            page.mode = PageFormat.MODE_PAGE;
            page.width = PageFormat.widthOf(PageFormat.A4_PORTRAIT);
            page.height = PageFormat.heightOf(PageFormat.A4_PORTRAIT);
            page.backgroundJson = "{\"type\":\"RULED\",\"color\":\"#FFFDF6E8\"}";
            page.createdAt = now;
            page.updatedAt = now;
            database.pageDao().upsert(page);

            CanvasElement heading = ElementFactory.create(com.archive.app.domain.model.ElementType.TEXT);
            heading.x = 70;
            heading.y = 90;
            heading.width = 720;
            heading.height = 150;
            ((com.archive.app.domain.model.elements.TextElement) heading).setText("Welcome to ARCHIVE");
            ((com.archive.app.domain.model.elements.TextElement) heading).setFontSize(56f);
            ((com.archive.app.domain.model.elements.TextElement) heading).setColour(0xFF2C251C);
            ((com.archive.app.domain.model.elements.TextElement) heading).setAlign("left");

            CanvasElement body = ElementFactory.create(com.archive.app.domain.model.ElementType.TEXT);
            body.x = 70;
            body.y = 250;
            body.width = 720;
            body.height = 420;
            com.archive.app.domain.model.elements.TextElement te =
                    (com.archive.app.domain.model.elements.TextElement) body;
            te.setText("Tap \u201c+\u201d to add photographs, documents, drawings, shapes, stickers and envelopes.\n\n"
                    + "Drag an object to move it, use the corner handles to resize, or drag the round handle to rotate.\n\n"
                    + "Pinch with two fingers to zoom the canvas. Everything saves automatically — you can close the app at any time.");
            te.setFontSize(30f);
            te.setLineSpacing(1.35f);
            te.setColour(0xFF3A3126);
            te.setAlign("left");

            com.archive.app.data.entity.CanvasElementEntity a = heading.toEntity(page.id);
            com.archive.app.data.entity.CanvasElementEntity b = body.toEntity(page.id);
            database.elementDao().insertAll(java.util.Arrays.asList(a, b));
        });
    }
}
