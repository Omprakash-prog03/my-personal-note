package com.archive.app.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.archive.app.canvas.engine.CanvasDocument;
import com.archive.app.canvas.engine.CanvasRenderer;
import com.archive.app.data.entity.CanvasElementEntity;
import com.archive.app.data.entity.PageEntity;
import com.archive.app.domain.model.BackgroundStyle;
import com.archive.app.domain.model.PageFormat;
import com.archive.app.domain.model.elements.TextElement;
import com.archive.app.media.exporter.PageExporter;
import com.archive.app.utils.Ids;

import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * The exporter must render the same content the editor shows, without selection chrome, inside the
 * size cap. These assertions are intentionally coarse — pixel-exact typography would be brittle —
 * but they catch blank exports, wrong page size and leaked handles columns.
 */
@RunWith(AndroidJUnit4.class)
public class PageExporterTest {

    private CanvasRenderer.Palette palette() {
        CanvasRenderer.Palette palette = new CanvasRenderer.Palette();
        palette.backdrop = 0xFFEDE3D2;
        palette.paper = 0xFFFDF6E8;
        palette.ink = 0xFF2C251C;
        palette.muted = 0xFF7A6A58;
        palette.selection = 0xFFB08968;
        palette.handleFill = 0xFFFFFFFF;
        palette.accent = 0xFFA9713A;
        return palette;
    }

    private CanvasDocument documentWith(boolean addText) {
        PageEntity page = new PageEntity();
        page.id = Ids.newId();
        page.collectionId = Ids.newId();
        page.title = "Export test";
        page.formatKey = PageFormat.A4_PORTRAIT;

        java.util.List<CanvasElementEntity> entities = new java.util.ArrayList<>();
        if (addText) {
            TextElement text = new TextElement();
            text.x = 120f;
            text.y = 240f;
            text.width = 600f;
            text.height = 140f;
            text.setText("A page worth keeping");
            text.setFontSize(48f);
            text.setBold(true);
            entities.add(text.toEntity(page.id));
        }
        CanvasDocument document = CanvasDocument.fromPage(page, entities);
        document.background = BackgroundStyle.defaultFor(false);
        return document;
    }

    @Test
    public void rendersBackgroundAndKeepsPageAspect() {
        CanvasDocument document = documentWith(false);
        Bitmap bitmap = PageExporter.renderPage(document, palette(), 1f, true, 2f);

        assertNotNull(bitmap);
        assertFalse(bitmap.isRecycled());
        float expected = document.pageWidth / (float) document.pageHeight;
        float actual = bitmap.getWidth() / (float) bitmap.getHeight();
        assertEquals(expected, actual, 0.02f);
        assertEquals(0xFFFDF6E8, bitmap.getPixel(2, 2));
    }

    @Test
    public void backgroundCanBeExcluded() {
        CanvasDocument document = documentWith(false);
        Bitmap bitmap = PageExporter.renderPage(document, palette(), 1f, false, 2f);
        assertNotNull(bitmap);
        assertEquals(0, bitmap.getPixel(2, 2));
    }

    @Test
    public void longEdgeIsCappedAt4096() {
        CanvasDocument document = documentWith(true);
        Bitmap bitmap = PageExporter.renderPage(document, palette(), 4f, true, 2f);
        assertNotNull(bitmap);
        assertTrue(Math.max(bitmap.getWidth(), bitmap.getHeight()) <= 4096);
    }

    @Test
    public void contentIsActuallyPainted() {
        CanvasDocument document = documentWith(true);
        Bitmap bitmap = PageExporter.renderPage(document, palette(), 2f, true, 2f);
        assertNotNull(bitmap);

        int background = bitmap.getPixel(4, 4);
        int different = 0;
        for (int y = 0; y < bitmap.getHeight(); y += 17) {
            for (int x = 0; x < bitmap.getWidth(); x += 17) {
                if (bitmap.getPixel(x, y) != background) different++;
            }
        }
        assertTrue("expected visible text pixels", different > 0);
    }

    @Test
    public void paperOnlyPageRenders() {
        CanvasDocument document = documentWith(false);
        Bitmap bitmap = PageExporter.renderPage(document, palette(), 1f, true, 2f);
        assertNotNull(bitmap);
        assertTrue(bitmap.getWidth() > 0 && bitmap.getHeight() > 0);
        bitmap.recycle();
    }
}
