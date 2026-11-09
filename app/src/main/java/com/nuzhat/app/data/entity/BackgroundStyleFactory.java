package com.nuzhat.app.data.entity;

import com.nuzhat.app.domain.model.BackgroundStyle;

/** Builds the default page background JSON for a theme. Lives in data because it is a column value. */
public final class BackgroundStyleFactory {
    private BackgroundStyleFactory() {}

    public static String defaultJson(boolean dark) {
        BackgroundStyle style = BackgroundStyle.defaultFor(dark);
        style.type = BackgroundStyle.Type.RULED;
        return style.toJson();
    }

    public static String plainJson(boolean dark) {
        return BackgroundStyle.defaultFor(dark).toJson();
    }

    /** Presets offered when a page is created. Keys match {@link #presetJson(String)}. */
    public static final String[] PRESET_KEYS = {"PLAIN", "RULED", "GRID", "DOTS", "VINTAGE",
            "KRAFT", "CHARCOAL", "GRADIENT"};

    /**
     * Builds the background JSON for a creation-time preset. Colours are fixed (not theme-derived)
     * so a page keeps the paper the user picked even after switching themes.
     */
    public static String presetJson(String key) {
        BackgroundStyle style;
        if (key == null) {
            style = BackgroundStyle.defaultFor(false);
        } else {
            switch (key) {
                case "RULED":
                    style = BackgroundStyle.of(BackgroundStyle.Type.RULED, 0xFFFDF6E8);
                    break;
                case "GRID":
                    style = BackgroundStyle.of(BackgroundStyle.Type.GRID, 0xFFFDF6E8);
                    break;
                case "DOTS":
                    style = BackgroundStyle.of(BackgroundStyle.Type.DOTS, 0xFFFDF6E8);
                    break;
                case "VINTAGE":
                    style = BackgroundStyle.of(BackgroundStyle.Type.VINTAGE, 0xFFF3E6C9);
                    break;
                case "KRAFT":
                    style = BackgroundStyle.of(BackgroundStyle.Type.KRAFT, 0xFFD8C0A0);
                    break;
                case "CHARCOAL":
                    style = BackgroundStyle.of(BackgroundStyle.Type.CHARCOAL, 0xFF23232A);
                    break;
                case "GRADIENT":
                    style = BackgroundStyle.of(BackgroundStyle.Type.GRADIENT, 0xFFF7E7D0);
                    style.color2 = 0xFFE4D2F0;
                    break;
                case "PLAIN":
                default:
                    style = BackgroundStyle.of(BackgroundStyle.Type.PLAIN, 0xFFFDF6E8);
                    break;
            }
        }
        return style.toJson();
    }
}
