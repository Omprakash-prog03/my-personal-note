package com.archive.app.ui.home;

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

import com.archive.app.ArchiveApp;
import com.archive.app.R;
import com.archive.app.data.dao.CollectionDao;
import com.archive.app.data.entity.CollectionEntity;
import com.archive.app.data.entity.JournalEntryEntity;
import com.archive.app.data.entity.MediaEntity;
import com.archive.app.data.entity.PageEntity;
import com.archive.app.data.storage.ThumbnailLoader;
import com.archive.app.data.repository.ArchiveRepository;
import com.archive.app.ui.canvas.CanvasActivity;
import com.archive.app.ui.collections.CollectionActivity;
import com.archive.app.ui.common.CollectionCardBinder;
import com.archive.app.ui.common.CreateSheet;
import com.archive.app.ui.common.HomeBackdropView;
import com.archive.app.ui.common.UiKit;
import com.archive.app.ui.journal.JournalEditorActivity;
import com.archive.app.ui.library.LibraryFragment;
import com.archive.app.utils.DateFormats;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

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
    private final ArchiveRepository repo = ArchiveApp.self().repo();

    private final CollectionAdapter pinnedAdapter = new CollectionAdapter(true);
    private final CollectionAdapter collectionsAdapter = new CollectionAdapter(false);
    private final PageAdapter pageAdapter = new PageAdapter();
    private final MediaAdapter mediaAdapter = new MediaAdapter();

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

        root.findViewById(R.id.search_bar).setOnClickListener(v -> host.openSearch());

        RecyclerView pinned = root.findViewById(R.id.pinned_recycler);
        pinned.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        pinned.setAdapter(pinnedAdapter);

        RecyclerView pages = root.findViewById(R.id.recent_pages_recycler);
        pages.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        pages.setAdapter(pageAdapter);

        RecyclerView collections = root.findViewById(R.id.collections_recycler);
        collections.setAdapter(collectionsAdapter);

        RecyclerView media = root.findViewById(R.id.media_recycler);
        media.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        media.setAdapter(mediaAdapter);

        root.findViewById(R.id.see_all_collections).setOnClickListener(v -> host.openLibraryTab(0));
        root.findViewById(R.id.see_all_pages).setOnClickListener(v -> host.openLibraryTab(0));
        root.findViewById(R.id.quick_collection).setOnClickListener(v -> CreateSheet.newCollection(requireActivity(), () -> { }));
        root.findViewById(R.id.quick_page).setOnClickListener(v -> CreateSheet.newPage(requireActivity(), () -> { }));

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
            collectionsAdapter.submit(list);
            root.findViewById(R.id.section_collections).setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
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
            holder.badge.setText(com.archive.app.domain.model.PageFormat.labelOf(page.formatKey));
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
        static String bytes(long value) { return com.archive.app.utils.UiUtils.humanBytes(value); }
    }
}
