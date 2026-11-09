package com.archive.app.ui.common;

import android.content.Context;

import com.archive.app.R;
import com.archive.app.domain.model.ElementType;
import com.archive.app.utils.ThemeController;

/** Theme-resolved colours and type icons shared by programmatic UI. */
public final class UiKit {
    private UiKit() {}

    public static int ink(Context context) {
        return ThemeController.themeColor(context, com.google.android.material.R.attr.colorOnSurface);
    }

    public static int surface(Context context) {
        return ThemeController.themeColor(context, com.google.android.material.R.attr.colorSurface);
    }

    public static int muted(Context context) {
        return ThemeController.themeColor(context, R.attr.archiveMuted);
    }

    public static int accent(Context context) {
        return ThemeController.themeColor(context, androidx.appcompat.R.attr.colorPrimary);
    }

    public static int outline(Context context) {
        return ThemeController.themeColor(context, com.google.android.material.R.attr.colorOutline);
    }

    /** Canvas palette for screens that render pages without a live CanvasView (exports, PDFs). */
    public static com.archive.app.canvas.engine.CanvasRenderer.Palette palette(Context context) {
        com.archive.app.canvas.engine.CanvasRenderer.Palette palette =
                new com.archive.app.canvas.engine.CanvasRenderer.Palette();
        palette.backdrop = ThemeController.themeColor(context, R.attr.archiveBackdrop);
        palette.paper = ThemeController.themeColor(context, R.attr.archivePaper);
        palette.ink = ThemeController.themeColor(context, R.attr.archivePaperInk);
        palette.muted = ThemeController.themeColor(context, R.attr.archiveMuted);
        palette.selection = ThemeController.themeColor(context, R.attr.archiveSelection);
        palette.handleFill = ThemeController.themeColor(context, R.attr.archiveHandleFill);
        palette.accent = ThemeController.themeColor(context, androidx.appcompat.R.attr.colorPrimary);
        return palette;
    }

    public static int iconForElement(ElementType type) {
        switch (type) {
            case IMAGE: return R.drawable.ic_image;
            case TEXT: return R.drawable.ic_text;
            case SHAPE: return R.drawable.ic_shape;
            case DRAWING: return R.drawable.ic_draw;
            case STICKER: return R.drawable.ic_sticker;
            case STICKY_NOTE: return R.drawable.ic_note;
            case ENVELOPE: return R.drawable.ic_envelope;
            case DOCUMENT: return R.drawable.ic_pdf;
            case AUDIO: return R.drawable.ic_audio;
            case LINK_CARD: return R.drawable.ic_link;
            default: return R.drawable.ic_layers;
        }
    }
}
