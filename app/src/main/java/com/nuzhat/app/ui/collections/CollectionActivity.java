package com.nuzhat.app.ui.collections;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.nuzhat.app.R;
import com.nuzhat.app.utils.PickerRequest;
import com.nuzhat.app.canvas.engine.CanvasDocument;
import com.nuzhat.app.canvas.engine.CanvasRenderer;
import com.nuzhat.app.data.entity.CollectionEntity;
import com.nuzhat.app.data.entity.MediaEntity;
import com.nuzhat.app.data.entity.PageEntity;
import com.nuzhat.app.data.storage.ThumbnailLoader;
import com.nuzhat.app.domain.model.PageFormat;
import com.nuzhat.app.media.exporter.PageExporter;
import com.nuzhat.app.ui.canvas.CanvasActivity;
import com.nuzhat.app.ui.common.BaseActivity;
import com.nuzhat.app.ui.common.CreateSheet;
import com.nuzhat.app.ui.common.ItemDialogs;
import com.nuzhat.app.ui.common.UiKit;
import com.nuzhat.app.utils.DateFormats;
import com.nuzhat.app.utils.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** One collection: its pages, and every collection-level action. */
public class CollectionActivity extends BaseActivity {

    public static final String EXTRA_COLLECTION_ID = "extra_collection_id";

    private String collectionId;
    private CollectionEntity collection;
    private MaterialToolbar toolbar;
    private TextView description;
    private View emptyState;
    private View recycler;
    private ViewPager2 pager;
    private final PageAdapter adapter = new PageAdapter();
    private ActivityResultLauncher<PickVisualMediaRequest> coverPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collection);
        collectionId = getIntent().getStringExtra(EXTRA_COLLECTION_ID);
        if (collectionId == null) {
            finish();
            return;
        }

        toolbar = findViewById(R.id.toolbar);
        description = findViewById(R.id.collection_description);
        emptyState = findViewById(R.id.empty_state);
        recycler = findViewById(R.id.pages_pager);
        pager = (ViewPager2) recycler;
        ExtendedFloatingActionButton fab = findViewById(R.id.fab_add_page);

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_collection);
        toolbar.setOnMenuItemClickListener(this::onMenuItem);

        pager.setOffscreenPageLimit(3);
        pager.setAdapter(adapter);
        pager.setPageTransformer(new FanPageTransformer());
        adapter.listener = this::onPageLongPress;

        emptyState.findViewById(R.id.empty_title).setVisibility(View.GONE);
        ((TextView) emptyState.findViewById(R.id.empty_body)).setText(R.string.no_pages_yet);

        fab.setOnClickListener(v -> chooseFormatAndCreate());

        coverPicker = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri == null) return;
            setCover(uri);
        });

        app.repo().collection(collectionId).observe(this, entity -> {
            if (entity == null) return;
            collection = entity;
            toolbar.setTitle(entity.name);
            if (entity.description == null || entity.description.trim().isEmpty()) {
                description.setVisibility(View.GONE);
            } else {
                description.setVisibility(View.VISIBLE);
                description.setText(entity.description);
            }
            updateMenuState();
        });

        app.repo().pages(collectionId).observe(this, pages -> {
            List<PageEntity> list = pages == null ? new ArrayList<>() : pages;
            adapter.submit(list);
            emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void updateMenuState() {
        if (collection == null || toolbar.getMenu() == null) return;
        MenuItem fav = toolbar.getMenu().findItem(R.id.action_favourite);
        if (fav != null) fav.setTitle(collection.favourite ? R.string.unfavourite : R.string.favourite);
        MenuItem pin = toolbar.getMenu().findItem(R.id.action_pin);
        if (pin != null) pin.setTitle(collection.pinned ? R.string.unpin : R.string.pin);
    }

    private boolean onMenuItem(MenuItem item) {
        if (collection == null) return false;
        int id = item.getItemId();
        if (id == R.id.action_rename) {
            ItemDialogs.editCollection(this, collection, () -> app.repo().saveCollection(collection, null));
            return true;
        }
        if (id == R.id.action_cover) {
            coverPicker.launch(PickerRequest.images());
            return true;
        }
        if (id == R.id.action_favourite) {
            collection.favourite = !collection.favourite;
            app.repo().saveCollection(collection, this::updateMenuState);
            return true;
        }
        if (id == R.id.action_pin) {
            collection.pinned = !collection.pinned;
            app.repo().saveCollection(collection, this::updateMenuState);
            return true;
        }
        if (id == R.id.action_duplicate) {
            app.repo().duplicateCollection(collectionId, newId -> {
                UiUtils.toast(this, getString(R.string.duplicate) + " ✓");
                if (newId != null) {
                    Intent intent = new Intent(this, CollectionActivity.class);
                    intent.putExtra(EXTRA_COLLECTION_ID, newId);
                    startActivity(intent);
                }
            });
            return true;
        }
        if (id == R.id.action_export_collection) {
            exportCollection();
            return true;
        }
        if (id == R.id.action_export_pages) {
            exportSelectedPages();
            return true;
        }
        if (id == R.id.action_delete) {
            UiUtils.confirm(this, getString(R.string.delete_collection_title),
                    getString(R.string.delete_collection_body, collection.name),
                    getString(R.string.move_to_trash), () -> app.repo().trashCollection(collectionId, () -> {
                        UiUtils.toast(this, getString(R.string.collection_moved_to_trash));
                        finish();
                    }));
            return true;
        }
        return false;
    }

    private void setCover(Uri uri) {
        app.repo().importMedia(uri, media -> {
            if (media == null) return;
            app.repo().setCollectionCover(collectionId, media.id,
                    () -> UiUtils.snack(recycler, getString(R.string.change_cover)));
        });
    }

    private void chooseFormatAndCreate() {
        ItemDialogs.choosePageFormat(this, settings.defaultFormat(), format ->
                CreateSheet.chooseBackgroundAndCreate(this, collectionId, format, null));
    }

    private void onPageLongPress(PageEntity page) {
        String[] actions = {getString(R.string.rename), getString(R.string.tags_label),
                getString(R.string.duplicate), getString(R.string.move_earlier),
                getString(R.string.move_later), getString(R.string.move_to_trash)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(page.title)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        UiUtils.textInputDialog(this, getString(R.string.rename), page.title,
                                getString(R.string.page_title), value -> {
                                    page.title = value.isEmpty()
                                            ? getString(R.string.untitled_page) : value;
                                    page.updatedAt = System.currentTimeMillis();
                                    app.repo().savePage(page, null);
                                });
                    } else if (which == 1) {
                        app.repo().pageTags(page.id, tags -> {
                            StringBuilder builder = new StringBuilder();
                            for (int i = 0; i < tags.size(); i++) {
                                if (i > 0) builder.append(", ");
                                builder.append(tags.get(i).name);
                            }
                            UiUtils.textInputDialog(this, getString(R.string.tags_label),
                                    builder.toString(), getString(R.string.tags_hint), value -> {
                                        List<String> names = new ArrayList<>();
                                        for (String part : value.split(",")) names.add(part.trim());
                                        app.repo().setPageTags(page.id, names, null);
                                    });
                        });
                    } else if (which == 2) {
                        app.canvasRepo().duplicatePage(page.id, copy -> {
                            if (copy == null) {
                                UiUtils.toast(this, getString(R.string.duplicate_failed));
                            } else {
                                UiUtils.snack(recycler, getString(R.string.page_duplicated));
                            }
                        });
                    } else if (which == 3 || which == 4) {
                        app.canvasRepo().movePage(page.id, which == 3 ? -1 : 1, index -> {
                            if (index < 0) UiUtils.snack(recycler, getString(R.string.page_order_edge));
                        });
                    } else {
                        UiUtils.confirm(this, getString(R.string.delete_page_title),
                                getString(R.string.delete_page_body), getString(R.string.move_to_trash),
                                () -> app.repo().trashPage(page.id,
                                        () -> UiUtils.toast(this, getString(R.string.page_moved_to_trash))));
                    }
                })
                .show();
    }

    /** Renders every page and writes one PDF — the "organized export" for a collection. */
    private void exportCollection() {
        app.repo().pagesSync(collectionId, pages -> {
            if (pages == null || pages.isEmpty()) {
                UiUtils.snack(recycler, getString(R.string.export_no_pages));
                return;
            }
            exportPages(pages);
        });
    }

    /**
     * Multi-page export with thumbnails, so the owner ticks pages by sight rather than by title.
     * The picker is a custom dialog because a plain items dialog cannot show previews.
     */
    private void exportSelectedPages() {
        app.repo().pagesSync(collectionId, pages -> {
            if (pages == null || pages.isEmpty()) {
                UiUtils.snack(recycler, getString(R.string.export_no_pages));
                return;
            }
            View content = LayoutInflater.from(this).inflate(R.layout.dialog_page_picker, null);
            RecyclerView list = content.findViewById(R.id.picker_recycler);
            list.setLayoutManager(new LinearLayoutManager(this));
            final boolean[] checked = new boolean[pages.size()];
            PagePickAdapter adapter = new PagePickAdapter(pages, checked);
            list.setAdapter(adapter);
            content.findViewById(R.id.picker_select_all).setOnClickListener(v -> adapter.selectAll());

            androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.export_pages)
                    .setView(content)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.export, (d, which) -> {
                        List<PageEntity> chosen = new ArrayList<>();
                        for (int i = 0; i < pages.size(); i++) {
                            if (checked[i]) chosen.add(pages.get(i));
                        }
                        if (chosen.isEmpty()) {
                            UiUtils.snack(recycler, getString(R.string.no_pages_selected));
                            return;
                        }
                        exportPages(chosen);
                    })
                    .create();
            dialog.show();
        });
    }

    private void exportPages(List<PageEntity> pages) {
        UiUtils.snack(recycler, getString(R.string.export_working));
        final List<CanvasDocument> documents = new ArrayList<>();
        final CanvasDocument[] ordered = new CanvasDocument[pages.size()];
        final int[] remaining = {pages.size()};
        final CanvasRenderer.Palette palette = UiKit.palette(this);
        for (int i = 0; i < pages.size(); i++) {
            final int index = i;
            PageEntity page = pages.get(i);
            app.canvasRepo().load(page.id, loaded -> {
                if (loaded.page != null) {
                    // Keep the visual page order even though loads finish out of order.
                    ordered[index] = CanvasDocument.fromPage(loaded.page, loaded.elements);
                }
                if (--remaining[0] == 0) {
                    for (CanvasDocument document : ordered) {
                        if (document != null) documents.add(document);
                    }
                    if (documents.isEmpty()) {
                        UiUtils.snack(recycler, getString(R.string.export_no_pages));
                    } else {
                        writeCollectionPdf(documents, palette);
                    }
                }
            });
        }
    }

    private void writeCollectionPdf(List<CanvasDocument> documents, CanvasRenderer.Palette palette) {
        PageExporter.Settings exportSettings = new PageExporter.Settings();
        exportSettings.format = PageExporter.Format.PDF;
        exportSettings.scale = 2f;
        exportSettings.fileName = collection != null ? collection.name : "archive-collection";
        final float density = getResources().getDisplayMetrics().density;
        app.executor().execute(() -> {
            try {
                File file = PageExporter.exportCollectionPdf(app.files(), documents, palette,
                        exportSettings, density);
                app.repo().activity().record(com.nuzhat.app.data.entity.ActivityEntity.COLLECTION_EXPORTED,
                        "Exported " + documents.size() + (documents.size() == 1 ? " page from " : " pages from ")
                                + "\"" + (collection != null ? collection.name : "collection")
                                + "\" as a PDF",
                        collection != null ? collection.id : null, null, null);
                runOnUiThread(() -> UiUtils.snack(recycler,
                        getString(R.string.collection_export_done, file.getName())));
            } catch (Exception e) {
                runOnUiThread(() -> UiUtils.snack(recycler,
                        getString(R.string.export_failed, String.valueOf(e.getMessage()))));
            }
        });
    }

    // ------------------------------------------------------------------ adapter

    /**
     * Layered deck: the active page sits square in front while its neighbours tilt away and fall
     * back, which is what makes the collection read as a physical scrapbook instead of a grid.
     */
    private static class FanPageTransformer implements ViewPager2.PageTransformer {
        @Override
        public void transformPage(@NonNull View page, float position) {
            float distance = Math.min(1f, Math.abs(position));
            page.setTranslationX(-page.getWidth() * position * 0.34f);
            page.setPivotY(page.getHeight() * 0.5f);
            page.setPivotX(position < 0 ? page.getWidth() : 0f);
            page.setRotationY(position * -9f);
            float scale = 1f - 0.16f * distance;
            page.setScaleX(scale);
            page.setScaleY(scale);
            page.setAlpha(1f - 0.35f * distance);
        }
    }

    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.Holder> {

        private final List<PageEntity> items = new ArrayList<>();
        private PageLongPress listener;

        void submit(List<PageEntity> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_page_carousel, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            PageEntity page = items.get(position);
            holder.title.setText(page.title);
            holder.meta.setText(PageFormat.labelOf(page.formatKey) + " \u00b7 " + DateFormats.relative(page.updatedAt));
            if (holder.badge != null) {
                holder.badge.setText(String.format(java.util.Locale.US, "PAGE %02d", position + 1));
            }
            if (page.thumbnailPath != null) {
                holder.thumb.setVisibility(View.VISIBLE);
                ThumbnailLoader.loadMedia(holder.thumb, page.thumbnailPath, 480, 640);
            } else {
                holder.thumb.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(v -> CreateSheet.openPage(CollectionActivity.this, page));
            holder.itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onLongPress(page);
                return true;
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class Holder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView meta;
            final TextView badge;
            final ImageView thumb;

            Holder(View view) {
                super(view);
                title = view.findViewById(R.id.page_title);
                meta = view.findViewById(R.id.page_meta);
                badge = view.findViewById(R.id.page_number);
                thumb = view.findViewById(R.id.page_thumb);
            }
        }
    }

    /** Row in the export picker: thumbnail, title, and a tick box driven by row taps. */
    private class PagePickAdapter extends RecyclerView.Adapter<PagePickAdapter.Holder> {

        private final List<PageEntity> pages;
        private final boolean[] checked;

        PagePickAdapter(List<PageEntity> pages, boolean[] checked) {
            this.pages = pages;
            this.checked = checked;
        }

        void selectAll() {
            java.util.Arrays.fill(checked, true);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_page_select, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            PageEntity page = pages.get(position);
            holder.title.setText((position + 1) + ". " + (page.title.trim().isEmpty()
                    ? getString(R.string.untitled_page) : page.title));
            holder.meta.setText(PageFormat.labelOf(page.formatKey) + " · "
                    + DateFormats.relative(page.updatedAt));
            holder.check.setChecked(checked[position]);
            if (page.thumbnailPath != null) {
                ThumbnailLoader.loadMedia(holder.thumb, page.thumbnailPath, 160, 220);
            } else {
                holder.thumb.setImageResource(R.drawable.ic_pages);
            }
            holder.itemView.setOnClickListener(v -> {
                int index = holder.getBindingAdapterPosition();
                if (index == RecyclerView.NO_POSITION) return;
                checked[index] = !checked[index];
                notifyItemChanged(index);
            });
        }

        @Override
        public int getItemCount() { return pages.size(); }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView title;
            final TextView meta;
            final android.widget.CheckBox check;

            Holder(View view) {
                super(view);
                thumb = view.findViewById(R.id.select_thumb);
                title = view.findViewById(R.id.select_title);
                meta = view.findViewById(R.id.select_meta);
                check = view.findViewById(R.id.select_check);
            }
        }
    }

    private interface PageLongPress {
        void onLongPress(PageEntity page);
    }
}
