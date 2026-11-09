package com.archive.app.domain.model;

/** Page geometry in canvas document units (1 unit ~ 1 px at 100% zoom). */
public final class PageFormat {

    public static final String A4_PORTRAIT = "A4_PORTRAIT";
    public static final String A4_LANDSCAPE = "A4_LANDSCAPE";
    public static final String SQUARE = "SQUARE";
    public static final String JOURNAL = "JOURNAL";
    public static final String CUSTOM = "CUSTOM";

    public static final String MODE_PAGE = "PAGE";
    public static final String MODE_INFINITE = "INFINITE";

    private PageFormat() {}

    public static String normalise(String key) {
        if (key == null) return A4_PORTRAIT;
        switch (key) {
            case A4_LANDSCAPE:
            case SQUARE:
            case JOURNAL:
            case CUSTOM:
            case A4_PORTRAIT:
                return key;
            default:
                return A4_PORTRAIT;
        }
    }

    public static int widthOf(String key) {
        switch (normalise(key)) {
            case A4_LANDSCAPE: return 1754;
            case SQUARE: return 1200;
            case JOURNAL: return 1080;
            default: return 1240;
        }
    }

    public static int heightOf(String key) {
        switch (normalise(key)) {
            case A4_LANDSCAPE: return 1240;
            case SQUARE: return 1200;
            case JOURNAL: return 1528;
            default: return 1754;
        }
    }

    public static String labelOf(String key) {
        switch (normalise(key)) {
            case A4_LANDSCAPE: return "A4 landscape";
            case SQUARE: return "Square";
            case JOURNAL: return "Journal";
            case CUSTOM: return "Custom";
            default: return "A4 portrait";
        }
    }

    public static String[] keys() {
        return new String[]{A4_PORTRAIT, A4_LANDSCAPE, SQUARE, JOURNAL};
    }

    public static String[] labels() {
        String[] labels = new String[keys().length];
        for (int i = 0; i < labels.length; i++) labels[i] = labelOf(keys()[i]);
        return labels;
    }

    public static int indexOf(String key) {
        String[] all = keys();
        for (int i = 0; i < all.length; i++) {
            if (all[i].equals(normalise(key))) return i;
        }
        return 0;
    }

    public static float aspect(String key) {
        return widthOf(key) / (float) heightOf(key);
    }
}
