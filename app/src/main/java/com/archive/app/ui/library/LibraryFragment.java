package com.archive.app.ui.library;

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
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.archive.app.ArchiveApp;
import com.archive.app.R;
import com.archive.app.data.dao.CollectionDao;
import com.archive.app.data.entity.CollectionEntity;
import com.archive.app.data.entity.JournalEntryEntity;
import com.archive.app.data.entity.LibraryItemEntity;
import com.archive.app.data.entity.MediaEntity;
import com.archive.app.data.entity.ResearchProjectEntity;
import com.archive.app.data.repository.ArchiveRepository;
import com.archive.app.ui.collections.CollectionActivity;
import com.archive.app.ui.common.CollectionCardBinder;
import com.archive.app.ui.common.CreateSheet;
import com.archive.app.ui.common.ItemDialogs;
import com.archive.app.ui.common.UiKit;
import com.archive.app.ui.journal.JournalEditorActivity;
import com.archive.app.ui.research.ResearchDetailActivity;
import com.archive.app.utils.DateFormats;
import com.archive.app.utils.UiUtils;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

/** Library hub: collections, journal, research and the personal reference shelf. */
public class LibraryFragment extends Fragment {

    private androidx.viewpager2.widget.ViewPager2 pager;
    private int pendingSection = -1;

    /** Called by the hosting activity when Home sends the user to a specific section. */
    public void selectSection(int index) {
        int clamped = Math.max(0, Math.min(3, index));
        if (pager != null) {
            pager.setCurrentItem(clamped, false);
        } else {
            pendingSection = clamped;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_library, container, false);
        TabLayout tabs = root.findViewById(R.id.tabs);
        pager = root.findViewById(R.id.pager);
        pager.setAdapter(new TabsAdapter(this));
        if (pendingSection >= 0) {
            pager.setCurrentItem(pendingSection, false);
            pendingSection = -1;
        }
        new TabLayoutMediator(tabs, pager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText(R.string.library_tab_collections); break;
                case 1: tab.setText(R.string.library_tab_journal); break;
                case 2: tab.setText(R.string.library_tab_research); break;
                default: tab.setText(R.string.library_tab_references); break;
            }
        }).attach();
        return root;
    }

    private static class TabsAdapter extends FragmentStateAdapter {
        TabsAdapter(@NonNull Fragment fragment) { super(fragment); }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 1: return new JournalTab();
                case 2: return new ResearchTab();
                case 3: return new ReferencesTab();
                default: return new CollectionsTab();
            }
        }

        @Override
        public int getItemCount() { return 4; }
    }

    // ------------------------------------------------------------------ generic tab

    private abstract static class Tab<T> extends Fragment {

        private final ArchiveRepository repo = ArchiveApp.self().repo();
        private SimpleAdapter<T> adapter;
        protected RecyclerView recycler;
        protected View empty;
        protected View sortButton;

        abstract int labelRes();

        abstract int layoutRes();

        abstract LiveData<List<T>> source();

        abstract void bind(View item, T value);

        abstract void onNew();

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            View root = inflater.inflate(R.layout.fragment_archive_list, container, false);
            ((TextView) root.findViewById(R.id.list_label)).setText(labelRes());
            root.findViewById(R.id.list_new).setOnClickListener(v -> onNew());
            sortButton = root.findViewById(R.id.list_sort);
            sortButton.setVisibility(supportsSorting() ? View.VISIBLE : View.GONE);
            if (supportsSorting()) sortButton.setOnClickListener(v -> chooseSort());
            recycler = root.findViewById(R.id.recycler);
            empty = root.findViewById(R.id.empty_state);
            recycler.setLayoutManager(new LinearLayoutManager(getContext()));
            adapter = new SimpleAdapter<>(layoutRes(), this::bind);
            recycler.setAdapter(adapter);
            source().observe(getViewLifecycleOwner(), items -> {
                List<T> list = items == null ? new ArrayList<>() : items;
                adapter.submit(list);
                boolean nothing = list.isEmpty();
                empty.setVisibility(nothing ? View.VISIBLE : View.GONE);
                recycler.setVisibility(nothing ? View.GONE : View.VISIBLE);
            });
            return root;
        }

        /** Only the collections tab can be re-ordered. */
        protected boolean supportsSorting() { return false; }

        protected void chooseSort() { }

        protected RecyclerView recyclerView() { return recycler; }

        protected SimpleAdapter<T> adapter() { return adapter; }

        protected ArchiveRepository repo() { return repo; }
    }

    private interface Binder<T> { void bind(View item, T value); }

    private static class SimpleAdapter<T> extends RecyclerView.Adapter<SimpleAdapter.Holder> {

        private final List<T> items = new ArrayList<>();
        private final int layoutRes;
        private final Binder<T> binder;

        SimpleAdapter(int layoutRes, Binder<T> binder) {
            this.layoutRes = layoutRes;
            this.binder = binder;
        }

        void submit(List<T> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(layoutRes, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            binder.bind(holder.itemView, items.get(position));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class Holder extends RecyclerView.ViewHolder {
            Holder(View view) { super(view); }
        }
    }

    // ------------------------------------------------------------------ tabs

    public static class CollectionsTab extends Tab<CollectionDao.CollectionWithCount> {

        @Override protected boolean supportsSorting() { return true; }

        @Override
        protected void chooseSort() {
            String[] labels = {getString(R.string.sort_recent), getString(R.string.sort_name),
                    getString(R.string.sort_custom)};
            String[] modes = {com.archive.app.utils.SettingsStore.SORT_RECENT,
                    com.archive.app.utils.SettingsStore.SORT_NAME,
                    com.archive.app.utils.SettingsStore.SORT_CUSTOM};
            int current = 2;
            String mode = com.archive.app.ArchiveApp.self().settings().collectionSort();
            for (int i = 0; i < modes.length; i++) if (modes[i].equals(mode)) current = i;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.sort_collections)
                    .setSingleChoiceItems(labels, current, (dialog, which) -> {
                        com.archive.app.ArchiveApp.self().settings().setCollectionSort(modes[which]);
                        com.archive.app.ArchiveApp.self().repo().setCollectionSort(modes[which]);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }

        @Override int labelRes() { return R.string.library_tab_collections; }

        @Override int layoutRes() { return R.layout.item_collection; }

        @Override LiveData<List<CollectionDao.CollectionWithCount>> source() {
            return repo().collections();
        }

        @Override
        void bind(View item, CollectionDao.CollectionWithCount value) {
            CollectionEntity collection = value.collection;
            CollectionCardBinder.bindWithCountAsync(item, value, repo(), v -> {
                Intent intent = new Intent(requireActivity(), CollectionActivity.class);
                intent.putExtra(CollectionActivity.EXTRA_COLLECTION_ID, collection.id);
                startActivity(intent);
            });
        }

        @Override
        void onNew() {
            CreateSheet.newCollection(requireActivity(), () -> { });
        }

    }

    public static class JournalTab extends Tab<JournalEntryEntity> {

        @Override int labelRes() { return R.string.library_tab_journal; }

        @Override int layoutRes() { return R.layout.item_journal; }

        @Override LiveData<List<JournalEntryEntity>> source() {
            return ArchiveApp.self().repo().journalEntries();
        }

        @Override
        void bind(View item, JournalEntryEntity entry) {
            ((TextView) item.findViewById(R.id.journal_title)).setText(
                    entry.title.trim().isEmpty() ? getString(R.string.journal_untitled) : entry.title);
            ((TextView) item.findViewById(R.id.journal_date)).setText(DateFormats.relative(entry.date));
            ((TextView) item.findViewById(R.id.journal_snippet)).setText(entry.body);
            ((TextView) item.findViewById(R.id.journal_mood)).setText(entry.mood);
            item.findViewById(R.id.journal_mood).setVisibility(
                    entry.mood.isEmpty() ? View.GONE : View.VISIBLE);
            item.findViewById(R.id.journal_fav).setVisibility(
                    entry.favourite ? View.VISIBLE : View.GONE);
            item.setOnClickListener(v -> {
                Intent intent = new Intent(requireActivity(), JournalEditorActivity.class);
                intent.putExtra(JournalEditorActivity.EXTRA_ENTRY_ID, entry.id);
                startActivity(intent);
            });
        }

        @Override
        void onNew() {
            repo().newJournalEntry(entry -> {
                Intent intent = new Intent(requireActivity(), JournalEditorActivity.class);
                intent.putExtra(JournalEditorActivity.EXTRA_ENTRY_ID, entry.id);
                startActivity(intent);
            });
        }
    }

    public static class ResearchTab extends Tab<ResearchProjectEntity> {

        @Override int labelRes() { return R.string.library_tab_research; }

        @Override int layoutRes() { return R.layout.item_research; }

        @Override LiveData<List<ResearchProjectEntity>> source() {
            return ArchiveApp.self().repo().researchProjects();
        }

        @Override
        void bind(View item, ResearchProjectEntity project) {
            ((TextView) item.findViewById(R.id.research_title)).setText(
                    project.title.trim().isEmpty() ? getString(R.string.research_untitled) : project.title);
            ((TextView) item.findViewById(R.id.research_description)).setText(project.description);
            ((TextView) item.findViewById(R.id.research_meta))
                    .setText(DateFormats.relative(project.updatedAt));
            item.setOnClickListener(v -> {
                Intent intent = new Intent(requireActivity(), ResearchDetailActivity.class);
                intent.putExtra(ResearchDetailActivity.EXTRA_PROJECT_ID, project.id);
                startActivity(intent);
            });
        }

        @Override
        void onNew() {
            repo().newResearchProject(project -> {
                Intent intent = new Intent(requireActivity(), ResearchDetailActivity.class);
                intent.putExtra(ResearchDetailActivity.EXTRA_PROJECT_ID, project.id);
                startActivity(intent);
            });
        }
    }

    public static class ReferencesTab extends Tab<LibraryItemEntity> {

        @Override int labelRes() { return R.string.library_tab_references; }

        @Override int layoutRes() { return R.layout.item_library; }

        @Override LiveData<List<LibraryItemEntity>> source() {
            return ArchiveApp.self().repo().libraryItems();
        }

        @Override
        void bind(View item, LibraryItemEntity value) {
            ((TextView) item.findViewById(R.id.library_title)).setText(value.title);
            ((TextView) item.findViewById(R.id.library_type)).setText(typeLabel(value.type));
            StringBuilder meta = new StringBuilder();
            if (value.creator != null && !value.creator.isEmpty()) meta.append(value.creator);
            if (value.year != null && !value.year.isEmpty()) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append(value.year);
            }
            ((TextView) item.findViewById(R.id.library_meta)).setText(meta);
            ((ImageView) item.findViewById(R.id.library_icon)).setImageResource(iconFor(value.type));
            item.findViewById(R.id.library_fav).setVisibility(value.favourite ? View.VISIBLE : View.GONE);
            item.setOnClickListener(v -> ItemDialogs.editLibraryItem(requireActivity(), value,
                    () -> repo().saveLibraryItem(value, null)));
        }

        @Override
        void onNew() {
            repo().newLibraryItem(item -> {
                repo().saveLibraryItem(item, null);
                ItemDialogs.editLibraryItem(requireActivity(), item,
                        () -> repo().saveLibraryItem(item, null));
            });
        }

        private String typeLabel(String type) {
            if (type == null) return getString(R.string.library_type_book);
            switch (type) {
                case LibraryItemEntity.TYPE_MOVIE: return getString(R.string.library_type_movie);
                case LibraryItemEntity.TYPE_MUSIC: return getString(R.string.library_type_music);
                case LibraryItemEntity.TYPE_ARTICLE: return getString(R.string.library_type_article);
                case LibraryItemEntity.TYPE_WEBSITE: return getString(R.string.library_type_website);
                case LibraryItemEntity.TYPE_QUOTE: return getString(R.string.library_type_quote);
                default: return getString(R.string.library_type_book);
            }
        }

        private int iconFor(String type) {
            if (type == null) return R.drawable.ic_library;
            switch (type) {
                case LibraryItemEntity.TYPE_MOVIE: return R.drawable.ic_cinema;
                case LibraryItemEntity.TYPE_MUSIC: return R.drawable.ic_audio;
                case LibraryItemEntity.TYPE_ARTICLE: return R.drawable.ic_research;
                case LibraryItemEntity.TYPE_WEBSITE: return R.drawable.ic_link;
                case LibraryItemEntity.TYPE_QUOTE: return R.drawable.ic_quote;
                default: return R.drawable.ic_library;
            }
        }
    }
}
