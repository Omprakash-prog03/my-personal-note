package com.archive.app.data.entity;

import com.archive.app.domain.model.BackgroundStyle;

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
}
