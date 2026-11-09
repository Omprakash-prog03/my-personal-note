package com.archive.app.media.exporter;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;

import com.archive.app.canvas.engine.BitmapStore;
import com.archive.app.canvas.engine.CanvasDocument;
import com.archive.app.canvas.engine.CanvasRenderer;
import com.archive.app.canvas.selection.SelectionState;
import com.archive.app.data.storage.FileStore;
import com.archive.app.domain.model.CanvasElement;
import com.archive.app.domain.model.elements.AudioElement;
import com.archive.app.domain.model.elements.DocumentElement;
import com.archive.app.domain.model.elements.ImageElement;
import com.archive.app.domain.model.elements.StickerElement;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders pages off-screen for PNG/JPEG/PDF. Chrome is disabled by construction, and image bitmaps
 * are pre-loaded so an export never contains placeholder rectangles.
 */
public final class PageExporter {

    public enum Format { PNG, JPEG, PDF }

    public static class Settings {
        public Format format = Format.PNG;
        public float scale = 2f;
        public boolean includeBackground = true;
        public String fileName = "archive-page";
        public int jpegQuality = 92;
    }

    private PageExporter() {}

    public static Bitmap renderPage(CanvasDocument document, CanvasRenderer.Palette palette,
                                    float scale, boolean includeBackground, float density) {
        int width = Math.max(1, Math.round(document.pageWidth * scale));
        int height = Math.max(1, Math.round(document.pageHeight * scale));
        // Guard against pathological page sizes.
        float cap = Math.min(1f, 4096f / Math.max(width, height));
        if (cap < 1f) {
            scale *= cap;
            width = Math.max(1, Math.round(document.pageWidth * scale));
            height = Math.max(1, Math.round(document.pageHeight * scale));
        }

        BitmapStore store = new BitmapStore();
        preload(store, document, scale);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(includeBackground ? palette.backdrop : Color.TRANSPARENT);
        canvas.scale(scale, scale);

        CanvasRenderer.Options options = new CanvasRenderer.Options();
        options.chrome = false;
        options.includeBackground = includeBackground;
        options.showGrid = false;
        options.viewScale = scale;
        options.density = density;
        options.visibleRect = new RectF(0, 0, document.pageWidth, document.pageHeight);

        new CanvasRenderer(store).draw(canvas, document, new SelectionState(), palette, options, null);
        return bitmap;
    }

    private static void preload(BitmapStore store, CanvasDocument document, float scale) {
        if (document.background.relativePath != null) {
            store.loadBlocking(document.background.mediaId, document.background.relativePath, 2048);
        }
        for (CanvasElement element : document.elements()) {
            if (element instanceof ImageElement) {
                ImageElement image = (ImageElement) element;
                int size = Math.round(Math.max(image.width, image.height) * scale * 1.2f);
                store.loadBlocking(image.getMediaId(), image.getRelativePath(), size);
            } else if (element instanceof StickerElement) {
                StickerElement sticker = (StickerElement) element;
                store.loadBlocking(sticker.getMediaId(), sticker.getRelativePath(),
                        Math.round(Math.max(sticker.width, sticker.height) * scale));
            } else if (element instanceof DocumentElement || element instanceof AudioElement) {
                // Cards are drawn, not rasterised; nothing to preload.
            }
        }
    }

    public static File writeImage(FileStore files, Bitmap bitmap, Settings settings) throws IOException {
        String extension = settings.format == Format.JPEG ? ".jpg" : ".png";
        File target = new File(files.exportsDir(),
                FileStore.sanitiseFileName(settings.fileName) + "-" + System.currentTimeMillis() + extension);
        try (FileOutputStream out = new FileOutputStream(target)) {
            bitmap.compress(settings.format == Format.JPEG ? Bitmap.CompressFormat.JPEG
                    : Bitmap.CompressFormat.PNG, settings.jpegQuality, out);
        }
        return target;
    }

    public static File exportPage(FileStore files, CanvasDocument document,
                                  CanvasRenderer.Palette palette, Settings settings,
                                  float density) throws IOException {
        if (settings.format == Format.PDF) {
            List<CanvasDocument> pages = new ArrayList<>();
            pages.add(document);
            List<CanvasRenderer.Palette> palettes = new ArrayList<>();
            palettes.add(palette);
            return writePdf(files, pages, palettes, settings, density);
        }
        Bitmap bitmap = renderPage(document, palette, settings.scale, settings.includeBackground, density);
        try {
            return writeImage(files, bitmap, settings);
        } finally {
            bitmap.recycle();
        }
    }

    /** One PDF page per canvas page, keeps aspect ratio, never taller than a PDF page allows. */
    public static File writePdf(FileStore files, List<CanvasDocument> documents,
                                List<CanvasRenderer.Palette> palettes, Settings settings,
                                float density) throws IOException {
        PdfDocument pdf = new PdfDocument();
        try {
            for (int i = 0; i < documents.size(); i++) {
                CanvasDocument document = documents.get(i);
                CanvasRenderer.Palette palette = palettes.get(Math.min(i, palettes.size() - 1));
                float targetWidth = 1240f;
                float ratio = document.pageHeight / (float) Math.max(1, document.pageWidth);
                float targetHeight = Math.min(1754f, targetWidth * ratio);
                float renderScale = Math.max(0.5f, settings.scale);
                Bitmap bitmap = renderPage(document, palette, renderScale, settings.includeBackground, density);
                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                        Math.round(targetWidth), Math.round(targetHeight), i + 1).create();
                PdfDocument.Page page = pdf.startPage(info);
                Canvas canvas = page.getCanvas();
                RectF destination = new RectF(0, 0, targetWidth, targetHeight);
                canvas.drawBitmap(bitmap, null, destination, null);
                pdf.finishPage(page);
                bitmap.recycle();
            }
            File target = new File(files.exportsDir(), FileStore.sanitiseFileName(settings.fileName)
                    + "-" + System.currentTimeMillis() + ".pdf");
            try (FileOutputStream out = new FileOutputStream(target)) {
                pdf.writeTo(out);
            }
            return target;
        } finally {
            pdf.close();
        }
    }

    public static File exportCollectionPdf(FileStore files, List<CanvasDocument> documents,
                                           CanvasRenderer.Palette palette, Settings settings,
                                           float density) throws IOException {
        List<CanvasRenderer.Palette> palettes = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) palettes.add(palette);
        return writePdf(files, documents, palettes, settings, density);
    }
}
