package com.nuzhat.app.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The settings that outlive a process: defaults must be real defaults, not "unset". */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SettingsStoreTest {

    @Test
    public void historyRetentionDefaultsToNinetyDays() {
        SettingsStore store = new SettingsStore(RuntimeEnvironment.getApplication());
        assertEquals(90, store.activityRetentionDays());
    }

    @Test
    public void historyRetentionSurvivesAndClampsNegatives() {
        SettingsStore store = new SettingsStore(RuntimeEnvironment.getApplication());
        store.setActivityRetentionDays(0);
        assertEquals(0, store.activityRetentionDays());
        store.setActivityRetentionDays(30);
        assertEquals(30, new SettingsStore(RuntimeEnvironment.getApplication())
                .activityRetentionDays());
        store.setActivityRetentionDays(-5);
        assertEquals(0, store.activityRetentionDays());
    }

    @Test
    public void darkIsTheDefaultTheme() {
        SettingsStore store = new SettingsStore(RuntimeEnvironment.getApplication());
        assertEquals(SettingsStore.THEME_DARK, store.theme());
    }
}
