package com.nuzhat.app.ui.common;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.nuzhat.app.R;
import com.nuzhat.app.data.dao.CollectionDao;
import com.nuzhat.app.data.entity.CollectionEntity;
import com.nuzhat.app.data.entity.MediaEntity;
import com.nuzhat.app.data.repository.NuzhatRepository;
import com.nuzhat.app.data.storage.ThumbnailLoader;
import com.nuzhat.app.utils.UiUtils;

/** Shared binding for the two collection card layouts (grid and carousel). */
public final class CollectionCardBinder {

    private CollectionCardBinder() {}

    public static void bind(View root, CollectionEntity collection, int pageCount,
                            MediaEntity cover, View.OnClickListener onClick) {
        TextView name = root.findViewById(R.id.collection_name);
        TextView meta = root.findViewById(R.id.collection_meta);
        ImageView coverImage = root.findViewById(R.id.cover_image);
        View coverFrame = root.findViewById(R.id.cover_frame);
        ImageView icon = root.findViewById(R.id.collection_icon);
        ImageView pin = root.findViewById(R.id.collection_pin);
        ImageView fav = root.findViewById(R.id.collection_fav);

        name.setText(collection.name);
        meta.setText(pageCount == 1
                ? "1 page" : pageCount + " pages");
        icon.setImageResource(UiUtils.iconForCategory(collection.icon));
        coverFrame.setBackgroundColor(collection.colour);
        if (pin != null) pin.setVisibility(collection.pinned ? View.VISIBLE : View.GONE);
        if (fav != null) fav.setVisibility(collection.favourite ? View.VISIBLE : View.GONE);

        if (cover != null && cover.relativePath != null) {
            coverImage.setVisibility(View.VISIBLE);
            ThumbnailLoader.loadMedia(coverImage, cover.relativePath, 480, 480);
        } else {
            coverImage.setVisibility(View.GONE);
            coverImage.setImageDrawable(null);
        }
        root.setOnClickListener(onClick);
    }

    public static void bindWithCount(View root, CollectionDao.CollectionWithCount item,
                                     MediaEntity cover, View.OnClickListener onClick) {
        bind(root, item.collection, item.pageCount, cover, onClick);
    }

    /**
     * Binds immediately with the collection colour, then fills the cover once the media row has been
     * read off the UI thread (Room forbids main-thread queries).
     */
    public static void bindWithCountAsync(View root, CollectionDao.CollectionWithCount item,
                                          NuzhatRepository repo, View.OnClickListener onClick) {
        CollectionEntity collection = item.collection;
        bind(root, collection, item.pageCount, null, onClick);
        final String mediaId = collection.coverMediaId;
        if (mediaId == null) return;
        repo.mediaOnce(mediaId, media -> {
            if (media == null || media.relativePath == null) return;
            ImageView image = root.findViewById(R.id.cover_image);
            if (image == null) return;
            image.setVisibility(View.VISIBLE);
            ThumbnailLoader.loadMedia(image, media.relativePath, 480, 480);
        });
    }
}
