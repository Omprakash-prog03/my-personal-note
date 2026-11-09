package com.nuzhat.app.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Page geometry is shared by the editor, thumbnails and PDF export, so it is worth pinning down. */
public class PageFormatTest {

    @Test
    public void unknownFormatsFallBackToA4Portrait() {
        assertEquals(PageFormat.A4_PORTRAIT, PageFormat.normalise(null));
        assertEquals(PageFormat.A4_PORTRAIT, PageFormat.normalise("nonsense"));
        assertEquals(PageFormat.SQUARE, PageFormat.normalise(PageFormat.SQUARE));
    }

    @Test
    public void dimensionsMatchOrientation() {
        int portraitWidth = PageFormat.widthOf(PageFormat.A4_PORTRAIT);
        int portraitHeight = PageFormat.heightOf(PageFormat.A4_PORTRAIT);
        assertTrue(portraitHeight > portraitWidth);

        assertEquals(portraitHeight, PageFormat.widthOf(PageFormat.A4_LANDSCAPE));
        assertEquals(portraitWidth, PageFormat.heightOf(PageFormat.A4_LANDSCAPE));

        assertEquals(PageFormat.widthOf(PageFormat.SQUARE), PageFormat.heightOf(PageFormat.SQUARE));
    }

    @Test
    public void aspectRatioIsPositive() {
        for (String key : PageFormat.keys()) {
            float aspect = PageFormat.aspect(key);
            assertTrue(key, aspect > 0.4f && aspect < 2.6f);
        }
        assertEquals(1f, PageFormat.aspect(PageFormat.SQUARE), 0.0001f);
        assertNotEquals(PageFormat.aspect(PageFormat.A4_PORTRAIT),
                PageFormat.aspect(PageFormat.A4_LANDSCAPE), 0.01f);
    }

    @Test
    public void keysAndLabelsStayInSync() {
        String[] keys = PageFormat.keys();
        String[] labels = PageFormat.labels();
        assertEquals(keys.length, labels.length);
        for (int i = 0; i < keys.length; i++) {
            assertEquals(i, PageFormat.indexOf(keys[i]));
            assertEquals(PageFormat.labelOf(keys[i]), labels[i]);
            assertTrue(labels[i].trim().length() > 0);
        }
        assertEquals(0, PageFormat.indexOf("unknown"));
    }

    @Test
    public void exportScaleStaysUnderThePixelCap() {
        // The exporter caps the long edge at 4096 px: A4 must still fit at 2x, and 3x is the point
        // where the cap has to kick in.
        int at2x = Math.max(PageFormat.widthOf(PageFormat.A4_PORTRAIT) * 2,
                PageFormat.heightOf(PageFormat.A4_PORTRAIT) * 2);
        int at3x = Math.max(PageFormat.widthOf(PageFormat.A4_PORTRAIT) * 3,
                PageFormat.heightOf(PageFormat.A4_PORTRAIT) * 3);
        assertTrue("2x A4 should fit under the 4096 px cap", at2x <= 4096);
        assertTrue("3x A4 should exceed the cap", at3x > 4096);
    }
}
