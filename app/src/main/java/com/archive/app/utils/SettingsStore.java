package com.archive.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.archive.app.domain.model.PageFormat;

/**
 * Lightweight app preferences. Room stores structural data (collections, pages, elements);
 * these flags are pure device presentation state, which is why SharedPreferences is used here
 * instead of DataStore (Java + DataStore would drag Coroutines into a Java-only app).
 */
public class SettingsStore {

    private static final String FILE = "archive_settings";

    public static final int THEME_WARM = 0;
    public static final int THEME_MINIMAL = 1;
    public static final int THEME_DARK = 2;

    public static final int ACCENT_OCHRE = 0;
    public static final int ACCENT_INDIGO = 1;
    public static final int ACCENT_SAGE = 2;
    public static final int ACCENT_CLAY = 3;
    public static final int ACCENT_ROSE = 4;
    public static final int ACCENT_SLATE = 5;

    public static final int HOME_BG_PAPER = 0;
    public static final int HOME_BG_PLAIN = 1;
    public static final int HOME_BG_RULED = 2;
    public static final int HOME_BG_DOTS = 3;
    public static final int HOME_BG_GRADIENT = 4;

    private final SharedPreferences prefs;

    public SettingsStore(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public int theme() { return prefs.getInt("theme", THEME_WARM); }
    public void setTheme(int value) { prefs.edit().putInt("theme", value).apply(); }

    public int accent() { return prefs.getInt("accent", ACCENT_OCHRE); }
    public void setAccent(int value) { prefs.edit().putInt("accent", value).apply(); }

    public int homeBackground() { return prefs.getInt("home_bg", HOME_BG_PAPER); }
    public void setHomeBackground(int value) { prefs.edit().putInt("home_bg", value).apply(); }

    public String defaultFormat() { return prefs.getString("page_format", PageFormat.A4_PORTRAIT); }
    public void setDefaultFormat(String value) { prefs.edit().putString("page_format", value).apply(); }

    public String defaultMode() { return prefs.getString("canvas_mode", PageFormat.MODE_PAGE); }
    public void setDefaultMode(String value) { prefs.edit().putString("canvas_mode", value).apply(); }

    public boolean snapToGrid() { return prefs.getBoolean("snap", false); }
    public void setSnapToGrid(boolean value) { prefs.edit().putBoolean("snap", value).apply(); }

    public boolean showGrid() { return prefs.getBoolean("grid", false); }
    public void setShowGrid(boolean value) { prefs.edit().putBoolean("grid", value).apply(); }

    /** Collection ordering: recent activity, alphabetical, or the manual sortOrder column. */
    public static final String SORT_RECENT = "RECENT";
    public static final String SORT_NAME = "NAME";
    public static final String SORT_CUSTOM = "CUSTOM";

    public String collectionSort() {
        return prefs.getString("collection_sort", SORT_CUSTOM);
    }

    public void setCollectionSort(String value) {
        prefs.edit().putString("collection_sort",
                value == null ? SORT_CUSTOM : value).apply();
    }

    public boolean appLock() { return prefs.getBoolean("app_lock", false); }
    public void setAppLock(boolean value) { prefs.edit().putBoolean("app_lock", value).apply(); }

    public boolean unlockedThisSession() { return prefs.getBoolean("session_unlocked", false); }
    public void setUnlockedThisSession(boolean value) { prefs.edit().putBoolean("session_unlocked", value).apply(); }

    // ------------------------------------------------------------------ export defaults

    public String exportFormat() {
        return prefs.getString("export_format", "PNG");
    }

    public void setExportFormat(String value) {
        prefs.edit().putString("export_format", value == null ? "PNG" : value).apply();
    }

    public int exportScale() {
        return Math.max(1, Math.min(3, prefs.getInt("export_scale", 2)));
    }

    public void setExportScale(int value) {
        prefs.edit().putInt("export_scale", Math.max(1, Math.min(3, value))).apply();
    }

    public boolean exportIncludeBackground() {
        return prefs.getBoolean("export_background", true);
    }

    public void setExportIncludeBackground(boolean value) {
        prefs.edit().putBoolean("export_background", value).apply();
    }

    public String deviceLabel() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }
}
