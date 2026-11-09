package com.nuzhat.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import com.nuzhat.app.data.entity.CollectionEntity;
import com.nuzhat.app.data.entity.PageEntity;
import com.nuzhat.app.data.preferences.AuthStore;
import com.nuzhat.app.domain.model.PageFormat;
import com.nuzhat.app.ui.auth.LoginActivity;
import com.nuzhat.app.ui.canvas.CanvasActivity;
import com.nuzhat.app.ui.collections.CollectionActivity;
import com.nuzhat.app.utils.Ids;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Cold starts on the JVM with real Android resources, so a launch crash shows up here instead of on
 * the phone. Robolectric inflates the actual theme and layouts, which is what the plain JVM tests
 * (returnDefaultValues) cannot do.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StartupSmokeTest {

    @Test
    public void loginScreenInflates() {
        // A previous test may have left a persisted session behind; the login screen only stays up
        // when nobody is signed in.
        new AuthStore(RuntimeEnvironment.getApplication()).signOut();
        ActivityController<LoginActivity> controller = Robolectric.buildActivity(LoginActivity.class).setup();
        LoginActivity activity = controller.get();
        assertFalse("login screen finished during creation", activity.isFinishing());
        assertNotNull("login screen has no sign-in button", activity.findViewById(R.id.login_button));
    }

    @Test
    public void collectionAndCanvasScreensOpen() throws Exception {
        new AuthStore(RuntimeEnvironment.getApplication()).signIn(true);
        NuzhatApp app = (NuzhatApp) RuntimeEnvironment.getApplication();

        final String[] ids = new String[2];
        Thread writer = new Thread(() -> {
            long now = System.currentTimeMillis();
            CollectionEntity collection = new CollectionEntity();
            collection.id = Ids.newId();
            collection.name = "Smoke collection";
            collection.description = "";
            collection.colour = 0xFFB08968;
            collection.icon = "folder";
            collection.sortOrder = 0;
            collection.createdAt = now;
            collection.updatedAt = now;
            app.database().collectionDao().upsert(collection);

            PageEntity page = new PageEntity();
            page.id = Ids.newId();
            page.collectionId = collection.id;
            page.title = "Smoke page";
            page.formatKey = PageFormat.A4_PORTRAIT;
            page.mode = PageFormat.MODE_PAGE;
            page.width = PageFormat.widthOf(PageFormat.A4_PORTRAIT);
            page.height = PageFormat.heightOf(PageFormat.A4_PORTRAIT);
            page.createdAt = now;
            page.updatedAt = now;
            app.database().pageDao().upsert(page);

            ids[0] = collection.id;
            ids[1] = page.id;
        });
        writer.start();
        writer.join();

        // Wait until the row is visible from a different connection. Without this the canvas can
        // legitimately report "page missing" and finish, which would hide a real regression.
        PageEntity persisted = null;
        long deadline = System.currentTimeMillis() + 4000;
        while (persisted == null && System.currentTimeMillis() < deadline) {
            final PageEntity[] box = new PageEntity[1];
            Thread reader = new Thread(() -> box[0] = app.database().pageDao().findSync(ids[1]));
            reader.start();
            reader.join();
            persisted = box[0];
            if (persisted == null) Thread.sleep(20);
        }
        assertNotNull("fixture page was never persisted", persisted);

        Intent collectionIntent = new Intent(app, CollectionActivity.class)
                .putExtra(CollectionActivity.EXTRA_COLLECTION_ID, ids[0]);
        ActivityController<CollectionActivity> collectionController =
                Robolectric.buildActivity(CollectionActivity.class, collectionIntent).setup();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertFalse("collection screen finished during creation",
                collectionController.get().isFinishing());

        // Probe the same repository path the canvas uses before blaming the activity.
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final com.nuzhat.app.data.repository.CanvasRepository.LoadedPage[] probe =
                new com.nuzhat.app.data.repository.CanvasRepository.LoadedPage[1];
        NuzhatApp.self().canvasRepo().load(ids[1], loaded -> {
            probe[0] = loaded;
            latch.countDown();
        });
        // main.post() needs the paused Robolectric looper pumped for the callback to run.
        long latchDeadline = System.currentTimeMillis() + 5000;
        while (latch.getCount() > 0 && System.currentTimeMillis() < latchDeadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            Thread.sleep(10);
        }
        assertEquals("repository never answered the canvas load", 0L, latch.getCount());
        assertNotNull("canvas repository could not read the page it was given", probe[0].page);

        Intent canvasIntent = new Intent(app, CanvasActivity.class)
                .putExtra(CanvasActivity.EXTRA_PAGE_ID, ids[1]);
        ActivityController<CanvasActivity> canvasController =
                Robolectric.buildActivity(CanvasActivity.class, canvasIntent).setup();
        String seenExtra = canvasController.get().getIntent() == null ? "<no intent>"
                : String.valueOf(canvasController.get().getIntent()
                        .getStringExtra(CanvasActivity.EXTRA_PAGE_ID));
        String toast = org.robolectric.shadows.ShadowToast.getTextOfLatestToast();
        boolean sameDb = NuzhatApp.self().database() == app.database();
        boolean sameApp = canvasController.get().getApplication() == app;
        boolean sameRepo = NuzhatApp.get(canvasController.get()).canvasRepo()
                == NuzhatApp.self().canvasRepo();
        assertFalse("canvas quit during creation (extra=" + seenExtra + ", id=" + ids[1]
                        + ", toast=" + toast + ", sameDb=" + sameDb + ", sameApp=" + sameApp
                        + ", sameRepo=" + sameRepo + ")",
                canvasController.get().isFinishing());

        // Let the page load resolve, then insist the canvas actually holds the document.
        long canvasDeadline = System.currentTimeMillis() + 5000;
        com.nuzhat.app.canvas.engine.CanvasView canvasView =
                canvasController.get().findViewById(R.id.canvas_view);
        while (canvasView.document() == null && System.currentTimeMillis() < canvasDeadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            Thread.sleep(10);
        }
        assertNotNull("canvas never received its document", canvasView.document());
        assertFalse("canvas closed after loading its page", canvasController.get().isFinishing());
    }

    @Test
    public void everyTabInflates() {
        new AuthStore(RuntimeEnvironment.getApplication()).signIn(true);

        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                activity.findViewById(R.id.bottom_nav);
        assertNotNull(nav);
        // Home, Search, Library and Settings are all added lazily; each one inflates its own layout.
        for (int tabId : new int[]{R.id.tab_home, R.id.tab_collections, R.id.tab_activity, R.id.tab_settings}) {
            nav.setSelectedItemId(tabId);
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertFalse("activity finished while showing tab " + tabId, activity.isFinishing());
        }
    }

    @Test
    public void mainScreenStartsAfterSignIn() {
        new AuthStore(RuntimeEnvironment.getApplication()).signIn(true);

        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        assertFalse("main screen finished during creation", activity.isFinishing());
        assertNotNull("main screen has no bottom navigation", activity.findViewById(R.id.bottom_nav));
    }
}
