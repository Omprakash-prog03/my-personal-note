package com.nuzhat.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import com.nuzhat.app.data.entity.CollectionEntity;
import com.nuzhat.app.data.entity.PageEntity;
import com.nuzhat.app.data.preferences.AuthStore;
import com.nuzhat.app.data.repository.NuzhatRepository;
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
        NuzhatRepository repo = NuzhatApp.self().repo();

        // Use the real write path: it also proves the Activity recorder does not block the caller.
        final String[] ids = new String[2];
        final java.util.concurrent.CountDownLatch collectionLatch = new java.util.concurrent.CountDownLatch(1);
        repo.createCollection("Smoke collection", "created by StartupSmokeTest", 0xFFB08968, "folder",
                id -> {
                    ids[0] = id;
                    collectionLatch.countDown();
                });
        awaitWithLooper(collectionLatch);

        final java.util.concurrent.CountDownLatch pageLatch = new java.util.concurrent.CountDownLatch(1);
        repo.createPage(ids[0], "Smoke page", PageFormat.A4_PORTRAIT, PageFormat.MODE_PAGE, null, page -> {
            ids[1] = page.id;
            pageLatch.countDown();
        });
        awaitWithLooper(pageLatch);
        assertNotNull("collection was not created", ids[0]);
        assertNotNull("page was not created", ids[1]);

        Intent collectionIntent = new Intent(app, CollectionActivity.class)
                .putExtra(CollectionActivity.EXTRA_COLLECTION_ID, ids[0]);
        ActivityController<CollectionActivity> collectionController =
                Robolectric.buildActivity(CollectionActivity.class, collectionIntent).setup();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertFalse("collection screen finished during creation",
                collectionController.get().isFinishing());

        Intent canvasIntent = new Intent(app, CanvasActivity.class)
                .putExtra(CanvasActivity.EXTRA_PAGE_ID, ids[1]);
        ActivityController<CanvasActivity> canvasController =
                Robolectric.buildActivity(CanvasActivity.class, canvasIntent).setup();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        // The canvas must end up holding the page it was asked to open.
        com.nuzhat.app.canvas.engine.CanvasView canvasView =
                canvasController.get().findViewById(R.id.canvas_view);
        long deadline = System.currentTimeMillis() + 5000;
        while (canvasView.document() == null && System.currentTimeMillis() < deadline
                && !canvasController.get().isFinishing()) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            Thread.sleep(10);
        }
        if (canvasController.get().isFinishing()) {
            throw new AssertionError("canvas quit while loading (toast="
                    + org.robolectric.shadows.ShadowToast.getTextOfLatestToast() + ")");
        }
        assertNotNull("canvas never received its document", canvasView.document());
    }

    private static void awaitWithLooper(java.util.concurrent.CountDownLatch latch) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            Thread.sleep(10);
        }
        assertEquals("caller callback never arrived", 0L, latch.getCount());
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
