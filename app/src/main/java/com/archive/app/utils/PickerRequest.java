package com.archive.app.utils;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;

/**
 * One place that builds the photo-picker request.
 *
 * The media-type constants on {@code ActivityResultContracts.PickVisualMedia} have moved between
 * AndroidX releases, so every screen asks here instead of repeating the builder call.
 */
public final class PickerRequest {
    private PickerRequest() {}

    /** Photo picker limited to images. */
    public static PickVisualMediaRequest images() {
        return new PickVisualMediaRequest.Builder()
                .setMediaType(new ActivityResultContracts.PickVisualMedia.SingleMimeType("image/*"))
                .build();
    }
}
