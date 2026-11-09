package com.nuzhat.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

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

        // Room refuses main-thread writes, so the fixture is written on its own thread above.

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
        assertFalse("canvas screen finished during creation", canvasController.get().isFinishing());
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
