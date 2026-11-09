package com.nuzhat.app.canvas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.nuzhat.app.domain.model.elements.ImageElement;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Crop maths behind the image property sheet: clamping and centre-cropping to a source aspect. */
@RunWith(AndroidJUnit4.class)
public class ImageElementCropTest {

    @Test
    public void cropDefaultsToWholeImage() {
        ImageElement image = new ImageElement();
        assertEquals(0f, image.getCropLeft(), 0.0001f);
        assertEquals(0f, image.getCropTop(), 0.0001f);
        assertEquals(1f, image.getCropRight(), 0.0001f);
        assertEquals(1f, image.getCropBottom(), 0.0001f);
    }

    @Test
    public void cropValuesAreClamped() {
        ImageElement image = new ImageElement();
        image.setCrop(-2f, -3f, 5f, 7f);
        assertEquals(0f, image.getCropLeft(), 0.0001f);
        assertEquals(0f, image.getCropTop(), 0.0001f);
        assertEquals(1f, image.getCropRight(), 0.0001f);
        assertEquals(1f, image.getCropBottom(), 0.0001f);
    }

    @Test
    public void cropEdgesCannotCross() {
        ImageElement image = new ImageElement();
        image.setCrop(0.8f, 0.9f, 0.1f, 0.2f);
        assertTrue(image.getCropRight() >= image.getCropLeft() + 0.049f);
        assertTrue(image.getCropBottom() >= image.getCropTop() + 0.049f);
    }

    @Test
    public void squareCropOfLandscapeSourceTakesTheMiddle() {
        ImageElement image = new ImageElement();
        // 4000x3000 source → a 1:1 crop keeps the middle 75% horizontally.
        image.cropToAspect(1f, 4000, 3000);
        assertEquals(0.125f, image.getCropLeft(), 0.01f);
        assertEquals(0.875f, image.getCropRight(), 0.01f);
        assertEquals(0f, image.getCropTop(), 0.001f);
        assertEquals(1f, image.getCropBottom(), 0.001f);
    }

    @Test
    public void wideCropOfPortraitSourceTrimmsVertically() {
        ImageElement image = new ImageElement();
        image.cropToAspect(16f / 9f, 1080, 1920);
        assertEquals(0f, image.getCropLeft(), 0.001f);
        assertEquals(1f, image.getCropRight(), 0.001f);
        assertTrue(image.getCropTop() > 0f);
        assertTrue(image.getCropBottom() < 1f);
    }

    @Test
    public void invalidSourceSizeLeavesCropUntouched() {
        ImageElement image = new ImageElement();
        image.setCrop(0.1f, 0.1f, 0.8f, 0.8f);
        image.cropToAspect(1f, 0, 0);
        assertEquals(0.1f, image.getCropLeft(), 0.0001f);
        assertEquals(0.8f, image.getCropRight(), 0.0001f);
    }
}
