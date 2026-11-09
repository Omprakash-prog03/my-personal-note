package com.nuzhat.app.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nuzhat.app.NuzhatApp;
import com.nuzhat.app.R;
import com.nuzhat.app.data.dao.CollectionDao;
import com.nuzhat.app.data.entity.CollectionEntity;
import com.nuzhat.app.data.entity.JournalEntryEntity;
import com.nuzhat.app.data.entity.MediaEntity;
import com.nuzhat.app.data.entity.PageEntity;
import com.nuzhat.app.data.storage.ThumbnailLoader;
import com.nuzhat.app.data.repository.NuzhatRepository;
import com.nuzhat.app.ui.canvas.CanvasActivity;
import com.nuzhat.app.ui.collections.CollectionActivity;
import com.nuzhat.app.ui.common.CollectionCardBinder;
import com.nuzhat.app.ui.common.CreateSheet;
import com.nuzhat.app.ui.common.HomeBackdropView;
import com.nuzhat.app.ui.common.UiKit;
import com.nuzhat.app.ui.journal.JournalEditorActivity;
import com.nuzhat.app.ui.library.LibraryFragment;
import com.nuzhat.app.utils.DateFormats;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The archive dashboard: greeting, search, pinned collections, recent pages, collections, latest
 * journal entry and recently imported media. Sections hide themselves when empty so the page never
 * looks crowded.
 */
public class HomeFragment extends Fragment {

    public interface Host {
        void openSearch();

        void openLibraryTab(int index);

        void openCreate();
    }

    private Host host;
    private final NuzhatRepository repo = NuzhatApp.self().repo();

    private final CollectionAdapter pinnedAdapter = new CollectionAdapter(true);
    private final ChapterAdapter chapterAdapter = new ChapterAdapter();
    private final PageAdapter pageAdapter = new PageAdapter();
    private final MediaAdapter mediaAdapter = new MediaAdapter();

    /** Collections picked by long press; the bar under the page acts on this set. */
    private final java.util.LinkedHashSet<String> selectedIds = new java.util.LinkedHashSet<>();
    private List<CollectionDao.CollectionWithCount> chapterItems = new ArrayList<>();
    private View selectionBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        host = (Host) requireActivity();

        int greeting = DateFormats.greeting().equals("morning") ? R.string.greeting_morning
                : DateFormats.greeting().equals("afternoon") ? R.string.greeting_afternoon
                : DateFormats.greeting().equals("evening") ? R.string.greeting_evening
                : R.string.greeting_night;
        ((TextView) root.findViewById(R.id.greeting)).setText(greeting);
        ((TextView) root.findViewById(R.id.home_date)).setText(DateFormats.fullDate());

        root.findViewById(R.id.search_bar).setOnClickListener(v -> host.openSearch());

        RecyclerView pinned = root.findViewById(R.id.pinned_recycler);
        pinned.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        pinned.setAdapter(pinnedAdapter);

        RecyclerView pages = root.findViewById(R.id.recent_pages_recycler);
        pages.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        pages.setAdapter(pageAdapter);

        RecyclerView collections = root.findViewById(R.id.collections_recycler);
        collections.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        collections.setAdapter(chapterAdapter);

        RecyclerView media = root.findViewById(R.id.media_recycler);
        media.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        media.setAdapter(mediaAdapter);

        root.findViewById(R.id.see_all_collections).setOnClickListener(v -> host.openLibraryTab(0));
        root.findViewById(R.id.see_all_pages).setOnClickListener(v -> host.openLibraryTab(0));
        root.findViewById(R.id.quick_collection).setOnClickListener(v -> CreateSheet.newCollection(requireActivity(), () -> { }));
        root.findViewById(R.id.quick_page).setOnClickListener(v -> CreateSheet.newPage(requireActivity(), () -> { }));

        selectionBar = root.findViewById(R.id.selection_bar);
        ((TextView) root.findViewById(R.id.selection_count)).setText("");
        root.findViewById(R.id.selection_close).setOnClickListener(v -> exitSelection());
        root.findViewById(R.id.selection_actions).setOnClickListener(this::showSelectionActions);

        TextView journalTitle = root.findViewById(R.id.journal_title);
        TextView journalSnippet = root.findViewById(R.id.journal_snippet);
        TextView journalDate = root.findViewById(R.id.journal_date);
        root.findViewById(R.id.journal_card).setOnClickListener(v -> {
            JournalEntryEntity entry = (JournalEntryEntity) v.getTag();
            if (entry == null) {
                repo.newJournalEntry(created -> openJournal(created.id));
            } else {
                openJournal(entry.id);
            }
        });

        repo.pinnedCollections().observe(getViewLifecycleOwner(), items -> {
            List<CollectionDao.CollectionWithCount> list = items == null ? new ArrayList<>() : items;
            pinnedAdapter.submit(list);
            root.findViewById(R.id.section_pinned).setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        });

        repo.recentPages(10).observe(getViewLifecycleOwner(), pagesList -> {
            List<PageEntity> list = pagesList == null ? new ArrayList<>() : pagesList;
            pageAdapter.submit(list);
            root.findViewById(R.id.section_recent_pages).setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        });

        repo.collections().observe(getViewLifecycleOwner(), collectionsList -> {
            List<CollectionDao.CollectionWithCount> list = collectionsList == null
                    ? new ArrayList<>() : collectionsList;
            chapterItems = list;
            chapterAdapter.submit(list);
            root.findViewById(R.id.section_collections).setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
            // A collection can vanish while selected (trashed elsewhere); drop those ids.
            if (!selectedIds.isEmpty()) {
                java.util.LinkedHashSet<String> live = new java.util.LinkedHashSet<>();
                for (CollectionDao.CollectionWithCount item : list) {
                    if (selectedIds.contains(item.collection.id)) live.add(item.collection.id);
                }
                if (live.size() != selectedIds.size()) {
                    selectedIds.clear();
                    selectedIds.addAll(live);
                    if (selectedIds.isEmpty()) exitSelection(); else updateSelectionBar();
                }
            }
        });

        repo.latestJournalEntry().observe(getViewLifecycleOwner(), entry -> {
            if (entry == null) {
                journalDate.setText(R.string.journal_today);
                journalTitle.setText(R.string.journal_empty);
                journalSnippet.setText(R.string.create_journal_desc);
                root.findViewById(R.id.journal_card).setTag(null);
            } else {
                journalDate.setText(DateFormats.relative(entry.date));
                journalTitle.setText(entry.title == null || entry.title.trim().isEmpty()
                        ? getString(R.string.journal_title) : entry.title);
                journalSnippet.setText(entry.body);
                root.findViewById(R.id.journal_card).setTag(entry);
            }
        });

        repo.recentMedia().observe(getViewLifecycleOwner(), mediaList -> {
            List<MediaEntity> list = mediaList == null ? new ArrayList<>() : mediaList;
            mediaAdapter.submit(list);
            root.findViewById(R.id.media_empty).setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            root.findViewById(R.id.section_media).setVisibility(View.VISIBLE);
        });

        return root;
    }

    // ------------------------------------------------------------------ selection

    private boolean inSelection() {
        return !selectedIds.isEmpty();
    }

    private void toggleSelection(String id) {
        if (!selectedIds.remove(id)) selectedIds.add(id);
        if (selectedIds.isEmpty()) {
            exitSelection();
            return;
        }
        updateSelectionBar();
        chapterAdapter.notifyDataSetChanged();
    }

    private void exitSelection() {
        selectedIds.clear();
        if (selectionBar != null) selectionBar.setVisibility(View.GONE);
        chapterAdapter.notifyDataSetChanged();
    }

    private void updateSelectionBar() {
        if (selectionBar == null) return;
        selectionBar.setVisibility(View.VISIBLE);
        ((TextView) selectionBar.findViewById(R.id.selection_count))
                .setText(getString(R.string.selection_count, selectedIds.size()));
    }

    /** Single-selection actions stay disabled for multi-selection rather than guessing a target. */
    private void showSelectionActions(View anchor) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.rename);
        menu.getMenu().add(0, 2, 1, R.string.duplicate);
        menu.getMenu().add(0, 3, 2, R.string.move_up);
        menu.getMenu().add(0, 4, 3, R.string.move_down);
        menu.getMenu().add(0, 5, 4, R.string.archive);
        menu.getMenu().add(0, 6, 5, R.string.delete);
        boolean single = selectedIds.size() == 1;
        menu.getMenu().findItem(1).setEnabled(single);
        menu.getMenu().findItem(3).setEnabled(single);
        menu.getMenu().findItem(4).setEnabled(single);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) renameSelected();
            else if (id == 2) duplicateSelected();
            else if (id == 3) moveSelected(-1);
            else if (id == 4) moveSelected(1);
            else if (id == 5) archiveSelected();
            else if (id == 6) deleteSelected();
            return true;
        });
        menu.show();
    }

    private String onlySelected() {
        return selectedIds.isEmpty() ? null : selectedIds.iterator().next();
    }

    private void renameSelected() {
        String id = onlySelected();
        if (id == null) return;
        repo.collectionOnce(id, collection -> {
            if (collection == null) return;
            android.widget.EditText field = new android.widget.EditText(requireContext());
            field.setText(collection.name);
            field.setSelection(collection.name.length());
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.rename)
                    .setView(padDialogView(field))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.rename, (d, w) -> {
                        String name = field.getText().toString().trim();
                        if (name.isEmpty() || name.equals(collection.name)) return;
                        collection.name = name;
                        repo.saveCollection(collection, () -> {
                            toast(R.string.renamed_toast);
                            exitSelection();
                        });
                    })
                    .show();
        });
    }

    private void duplicateSelected() {
        final List<String> ids = new ArrayList<>(selectedIds);
        final int[] remaining = {ids.size()};
        final boolean[] failed = {false};
        for (String id : ids) {
            repo.duplicateCollection(id, copyId -> {
                if (copyId == null) failed[0] = true;
                if (--remaining[0] == 0) {
                    toast(failed[0] ? R.string.copy_failed : R.string.duplicated_toast);
                    exitSelection();
                }
            });
        }
    }

    /** Neighbour swap in the ordered list, written back so it survives a restart. */
    private void moveSelected(int delta) {
        String id = onlySelected();
        if (id == null) return;
        int index = -1;
        for (int i = 0; i < chapterItems.size(); i++) {
            if (chapterItems.get(i).collection.id.equals(id)) { index = i; break; }
        }
        int target = index + delta;
        if (index < 0 || target < 0 || target >= chapterItems.size()) {
            toast(R.string.reordered_toast);
            return;
        }
        CollectionEntity a = chapterItems.get(index).collection;
        CollectionEntity b = chapterItems.get(target).collection;
        int sortA = a.sortOrder;
        int sortB = b.sortOrder;
        if (sortA == sortB) {
            // Equal sort orders are common on freshly created collections: give them real values.
            sortA = index;
            sortB = target;
        }
        a.sortOrder = sortB;
        b.sortOrder = sortA;
        final int pending = 2;
        final int[] done = {0};
        Runnable finished = () -> {
            if (++done[0] == pending) {
                repo.activity().record(
                        com.nuzhat.app.data.entity.ActivityEntity.COLLECTION_RENAMED,
                        "Reordered \"" + a.name + "\"", a.id, null, null);
                exitSelection();
                toast(R.string.reordered_toast);
            }
        };
        repo.saveCollection(a, finished);
        repo.saveCollection(b, finished);
    }

    private void archiveSelected() {
        final List<String> ids = new ArrayList<>(selectedIds);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.archive_collection_title)
                .setMessage(getString(R.string.archive_collection_body, ids.size()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.archive, (d, w) -> {
                    final int[] remaining = {ids.size()};
                    for (String id : ids) {
                        repo.collectionOnce(id, collection -> {
                            if (collection != null) {
                                collection.archived = true;
                                repo.saveCollection(collection, () -> { });
                            }
                            if (--remaining[0] == 0) {
                                toast(R.string.archived_toast);
                                exitSelection();
                            }
                        });
                    }
                })
                .show();
    }

    private void deleteSelected() {
        final List<String> ids = new ArrayList<>(selectedIds);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(ids.size() == 1 ? R.string.delete_collection_title
                        : R.string.delete_collection_title)
                .setMessage(getString(R.string.delete_collection_body,
                        ids.size() == 1 ? nameOf(ids.get(0)) : ids.size() + " collections"))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    final int[] remaining = {ids.size()};
                    for (String id : ids) {
                        repo.trashCollection(id, () -> {
                            if (--remaining[0] == 0) {
                                toast(R.string.collection_moved_to_trash);
                                exitSelection();
                            }
                        });
                    }
                })
                .show();
    }

    private String nameOf(String id) {
        for (CollectionDao.CollectionWithCount item : chapterItems) {
            if (item.collection.id.equals(id)) return item.collection.name;
        }
        return "";
    }

    private View padDialogView(View field) {
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        field.setPadding(pad, pad / 2, pad, pad / 2);
        return field;
    }

    private void toast(int res) {
        android.widget.Toast.makeText(requireContext(), res, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void openJournal(String id) {
        Intent intent = new Intent(requireActivity(), JournalEditorActivity.class);
        intent.putExtra(JournalEditorActivity.EXTRA_ENTRY_ID, id);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        View root = getView();
        if (root != null) {
            View backdrop = root.findViewById(R.id.home_backdrop);
            if (backdrop instanceof HomeBackdropView) ((HomeBackdropView) backdrop).refresh();
        }
    }

    // ------------------------------------------------------------------ adapters

    private class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.Holder> {

        private final boolean compact;
        private final List<CollectionDao.CollectionWithCount> items = new ArrayList<>();

        CollectionAdapter(boolean compact) { this.compact = compact; }

        void submit(List<CollectionDao.CollectionWithCount> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(
                    compact ? R.layout.item_collection_compact : R.layout.item_collection, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            CollectionDao.CollectionWithCount item = items.get(position);
            CollectionEntity collection = item.collection;
            CollectionCardBinder.bindWithCountAsync(holder.itemView, item, repo, v -> {
                Intent intent = new Intent(requireActivity(), CollectionActivity.class);
                intent.putExtra(CollectionActivity.EXTRA_COLLECTION_ID, collection.id);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class Holder extends RecyclerView.ViewHolder {
            Holder(View itemView) { super(itemView); }
        }
    }

    /**
     * Editorial chapter rail: tall cover cards with a chapter number, like the plates of an
     * illustrated book. Covers are loaded lazily through the repository's cached media lookup.
     */
    private class ChapterAdapter extends RecyclerView.Adapter<ChapterAdapter.Holder> {

        private final List<CollectionDao.CollectionWithCount> items = new ArrayList<>();

        void submit(List<CollectionDao.CollectionWithCount> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chapter, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            CollectionDao.CollectionWithCount item = items.get(position);
            CollectionEntity collection = item.collection;
            holder.number.setText(String.format(Locale.US, "CHAPTER %02d", position + 1));
            holder.title.setText(collection.name);
            holder.description.setText(collection.description == null || collection.description.isEmpty()
                    ? collection.category : collection.description);
            holder.meta.setText(item.pageCount == 1 ? "1 page" : item.pageCount + " pages");
            holder.cover.setImageDrawable(null);
            holder.cover.setBackgroundColor(collection.colour);
            if (collection.coverMediaId != null) {
                repo.mediaOnce(collection.coverMediaId, media -> {
                    if (media != null && media.relativePath != null) {
                        ThumbnailLoader.loadMedia(holder.cover, media.relativePath, 480, 960);
                    }
                });
            }
            boolean selected = selectedIds.contains(collection.id);
            holder.itemView.setAlpha(inSelection() && !selected ? 0.45f : 1f);
            holder.itemView.setScaleX(selected ? 0.97f : 1f);
            holder.itemView.setScaleY(selected ? 0.97f : 1f);
            holder.itemView.setOnClickListener(v -> {
                if (inSelection()) {
                    toggleSelection(collection.id);
                    return;
                }
                Intent intent = new Intent(requireActivity(), CollectionActivity.class);
                intent.putExtra(CollectionActivity.EXTRA_COLLECTION_ID, collection.id);
                startActivity(intent);
            });
            holder.itemView.setOnLongClickListener(v -> {
                toggleSelection(collection.id);
                return true;
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView number;
            final TextView title;
            final TextView description;
            final TextView meta;

            Holder(View itemView) {
                super(itemView);
                cover = itemView.findViewById(R.id.chapter_cover);
                number = itemView.findViewById(R.id.chapter_number);
                title = itemView.findViewById(R.id.chapter_title);
                description = itemView.findViewById(R.id.chapter_description);
                meta = itemView.findViewById(R.id.chapter_meta);
            }
        }
    }

    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.Holder> {

        private final List<PageEntity> items = new ArrayList<>();

        void submit(List<PageEntity> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_page_compact, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            PageEntity page = items.get(position);
            holder.title.setText(page.title);
            holder.meta.setText(DateFormats.relative(page.updatedAt));
            holder.badge.setText(com.nuzhat.app.domain.model.PageFormat.labelOf(page.formatKey));
            if (page.thumbnailPath != null) {
                holder.thumb.setVisibility(View.VISIBLE);
                ThumbnailLoader.loadMedia(holder.thumb, page.thumbnailPath, 360, 480);
            } else {
                holder.thumb.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(v -> CreateSheet.openPage(requireActivity(), page));
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
                badge = view.findViewById(R.id.page_format_badge);
                thumb = view.findViewById(R.id.page_thumb);
            }
        }
    }

    private class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.Holder> {

        private final List<MediaEntity> items = new ArrayList<>();

        void submit(List<MediaEntity> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_media, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            MediaEntity media = items.get(position);
            ThumbnailLoader.loadMedia(holder.image, media.thumbPath != null ? media.thumbPath
                    : media.relativePath, 320, 320);
            holder.image.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(media.displayName)
                    .setMessage(media.mime + " · " + UiUtilsHuman.bytes(media.sizeBytes))
                    .setPositiveButton(R.string.close, null)
                    .show());
        }

        @Override
        public int getItemCount() { return items.size(); }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView image;

            Holder(View view) {
                super(view);
                image = view.findViewById(R.id.media_image);
            }
        }
    }

    private static final class UiUtilsHuman {
        static String bytes(long value) { return com.nuzhat.app.utils.UiUtils.humanBytes(value); }
    }
}
