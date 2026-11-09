package com.nuzhat.app.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.nuzhat.app.canvas.engine.CanvasDocument;
import com.nuzhat.app.canvas.engine.CanvasRenderer;
import com.nuzhat.app.data.entity.CanvasElementEntity;
import com.nuzhat.app.data.entity.PageEntity;
import com.nuzhat.app.domain.model.BackgroundStyle;
import com.nuzhat.app.domain.model.PageFormat;
import com.nuzhat.app.domain.model.elements.TextElement;
import com.nuzhat.app.media.exporter.PageExporter;
import com.nuzhat.app.utils.Ids;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * The exporter must paint the same content the editor shows, inside the size cap, with no chrome.
 * Assertions are coarse on purpose: pixel-exact typography would be brittle, while blank exports,
 * wrong aspect and leaked handles are exactly what these catch.
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

        List<CanvasElementEntity> entities = new ArrayList<>();
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
