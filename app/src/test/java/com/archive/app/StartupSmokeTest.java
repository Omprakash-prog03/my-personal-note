package com.archive.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.archive.app.data.preferences.AuthStore;
import com.archive.app.ui.auth.LoginActivity;

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
    public void mainScreenStartsAfterSignIn() {
        new AuthStore(RuntimeEnvironment.getApplication()).signIn(true);

        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        assertFalse("main screen finished during creation", activity.isFinishing());
        assertNotNull("main screen has no bottom navigation", activity.findViewById(R.id.bottom_nav));
    }
}
