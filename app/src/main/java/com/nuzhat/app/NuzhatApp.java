package com.nuzhat.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.room.Room;

import com.nuzhat.app.data.database.AppDatabase;
import com.nuzhat.app.data.repository.NuzhatRepository;
import com.nuzhat.app.data.repository.CanvasRepository;
import com.nuzhat.app.data.storage.FileStore;
import com.nuzhat.app.domain.model.CanvasElement;
import com.nuzhat.app.domain.model.ElementFactory;
import com.nuzhat.app.domain.model.PageFormat;
import com.nuzhat.app.utils.Ids;
import com.nuzhat.app.utils.SettingsStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Composition root: one database, one file store, one repository pair, one background executor.
 * Nothing in the app constructs its own Room instance or thread pool.
 */
public class NuzhatApp extends Application {

    private static NuzhatApp instance;

    private AppDatabase database;
    private NuzhatRepository repository;
    private CanvasRepository canvasRepository;
    private FileStore fileStore;
    private SettingsStore settings;
    private ExecutorService executor;
    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        // Installed before the database opens, so even a broken first launch leaves a readable trace.
        com.nuzhat.app.utils.CrashLog.install(this);
        instance = this;
        executor = Executors.newFixedThreadPool(4);
        mainHandler = new Handler(Looper.getMainLooper());
        settings = new SettingsStore(this);
        // A staged restore must be applied before Room opens its database file.
        com.nuzhat.app.data.storage.BackupManager.applyPendingRestore(this);
        fileStore = new FileStore(this);
        database = Room.databaseBuilder(this, AppDatabase.class, "nuzhat.db")
                .addMigrations(AppDatabase.MIGRATIONS)
                .build();
        repository = new NuzhatRepository(database, fileStore, executor, mainHandler);
        canvasRepository = new CanvasRepository(database, fileStore, executor, mainHandler);
        repository.setCollectionSort(settings.collectionSort());
        repository.activity().setRetentionDays(settings.activityRetentionDays());
        seedFirstRun();
    }

    public static NuzhatApp get(android.content.Context context) {
        return (NuzhatApp) context.getApplicationContext();
    }

    public static NuzhatApp self() {
        return instance;
    }

    public AppDatabase database() { return database; }
    public NuzhatRepository repo() { return repository; }
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
            com.nuzhat.app.data.entity.CollectionEntity collection = new com.nuzhat.app.data.entity.CollectionEntity();
            collection.id = Ids.newId();
            collection.name = "Getting started";
            collection.description = "A short tour of what NUZHAT can hold.";
            collection.colour = 0xFFB08968;
            collection.icon = "folder";
            collection.sortOrder = 0;
            collection.createdAt = now;
            collection.updatedAt = now;
            database.collectionDao().upsert(collection);

            com.nuzhat.app.data.entity.PageEntity page = new com.nuzhat.app.data.entity.PageEntity();
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

            CanvasElement heading = ElementFactory.create(com.nuzhat.app.domain.model.ElementType.TEXT);
            heading.x = 70;
            heading.y = 90;
            heading.width = 720;
            heading.height = 150;
            ((com.nuzhat.app.domain.model.elements.TextElement) heading).setText("Welcome to NUZHAT");
            ((com.nuzhat.app.domain.model.elements.TextElement) heading).setFontSize(56f);
            ((com.nuzhat.app.domain.model.elements.TextElement) heading).setColour(0xFF2C251C);
            ((com.nuzhat.app.domain.model.elements.TextElement) heading).setAlign("left");

            CanvasElement body = ElementFactory.create(com.nuzhat.app.domain.model.ElementType.TEXT);
            body.x = 70;
            body.y = 250;
            body.width = 720;
            body.height = 420;
            com.nuzhat.app.domain.model.elements.TextElement te =
                    (com.nuzhat.app.domain.model.elements.TextElement) body;
            te.setText("Tap \u201c+\u201d to add photographs, documents, drawings, shapes, stickers and envelopes.\n\n"
                    + "Drag an object to move it, use the corner handles to resize, or drag the round handle to rotate.\n\n"
                    + "Pinch with two fingers to zoom the canvas. Everything saves automatically — you can close the app at any time.");
            te.setFontSize(30f);
            te.setLineSpacing(1.35f);
            te.setColour(0xFF3A3126);
            te.setAlign("left");

            com.nuzhat.app.data.entity.CanvasElementEntity a = heading.toEntity(page.id);
            com.nuzhat.app.data.entity.CanvasElementEntity b = body.toEntity(page.id);
            database.elementDao().insertAll(java.util.Arrays.asList(a, b));
        });
    }
}
