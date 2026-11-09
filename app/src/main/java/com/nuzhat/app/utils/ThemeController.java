package com.nuzhat.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.annotation.StyleRes;

import com.nuzhat.app.R;

/**
 * Applies the selected theme and accent overlay. Accents are real ThemeOverlays rather than
 * runtime colour patching, so every Material component derives correct container/on-colour values.
 * Must be called before setContentView().
 */
public final class ThemeController {
    private ThemeController() {}

    public static void apply(Activity activity, SettingsStore settings) {
        activity.setTheme(themeRes(settings.theme()));
        activity.getTheme().applyStyle(accentOverlay(settings.accent()), true);
    }

    public static void apply(Context themedContext, SettingsStore settings) {
        if (themedContext instanceof Activity) {
            apply((Activity) themedContext, settings);
        }
    }

    @StyleRes
    public static int themeRes(int theme) {
        switch (theme) {
            case SettingsStore.THEME_MINIMAL: return R.style.Theme_Nuzhat_Minimal;
            case SettingsStore.THEME_DARK: return R.style.Theme_Nuzhat_Dark;
            case SettingsStore.THEME_WARM:
            default: return R.style.Theme_Nuzhat_Warm;
        }
    }

    @StyleRes
    public static int accentOverlay(int accent) {
        switch (accent) {
            case SettingsStore.ACCENT_INDIGO: return R.style.ThemeOverlay_Nuzhat_Accent_Indigo;
            case SettingsStore.ACCENT_SAGE: return R.style.ThemeOverlay_Nuzhat_Accent_Sage;
            case SettingsStore.ACCENT_CLAY: return R.style.ThemeOverlay_Nuzhat_Accent_Clay;
            case SettingsStore.ACCENT_ROSE: return R.style.ThemeOverlay_Nuzhat_Accent_Rose;
            case SettingsStore.ACCENT_SLATE: return R.style.ThemeOverlay_Nuzhat_Accent_Slate;
            case SettingsStore.ACCENT_OCHRE:
            default: return R.style.ThemeOverlay_Nuzhat_Accent_Ochre;
        }
    }

    public static int[] accentSwatches() {
        return new int[]{
                R.color.accent_ochre, R.color.accent_indigo, R.color.accent_sage,
                R.color.accent_clay, R.color.accent_rose, R.color.accent_slate
        };
    }

    /** Resolves a theme attribute (used for canvas colours so rendering follows the theme). */
    @ColorInt
    public static int themeColor(Context context, int attr) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
            TypedArray array = context.obtainStyledAttributes(new int[]{attr});
            try {
                return array.getColor(0, 0xFF000000);
            } finally {
                array.recycle();
            }
        }
        return 0xFF888888;
    }
}
