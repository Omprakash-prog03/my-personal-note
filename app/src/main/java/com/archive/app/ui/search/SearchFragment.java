package com.archive.app.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.archive.app.ArchiveApp;
import com.archive.app.R;
import com.archive.app.data.dao.model.SearchResult;
import com.archive.app.data.repository.ArchiveRepository;
import com.archive.app.ui.canvas.CanvasActivity;
import com.archive.app.ui.collections.CollectionActivity;
import com.archive.app.ui.common.CreateSheet;
import com.archive.app.ui.common.ItemDialogs;
import com.archive.app.ui.common.UiKit;
import com.archive.app.ui.journal.JournalEditorActivity;
import com.archive.app.ui.research.ResearchDetailActivity;
import com.archive.app.utils.DateFormats;
import com.archive.app.utils.UiUtils;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * Global search across collections, pages, canvas text, journal entries, research references and the
 * personal library. Results are filtered locally by type so the database query stays a single
 * indexed LIKE pass.
 */
public class SearchFragment extends Fragment {

    private final ArchiveRepository repo = ArchiveApp.self().repo();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ResultAdapter adapter = new ResultAdapter();
    private final List<SearchResult> all = new ArrayList<>();

    private String query = "";
    private String filter = "ALL";
    private LiveData<List<SearchResult>> source;
    private final Observer<List<SearchResult>> observer = results -> {
        all.clear();
        if (results != null) all.addAll(results);
        render();
    };

    private RecyclerView recycler;
    private View empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_search, container, false);
        recycler = root.findViewById(R.id.search_recycler);
        empty = root.findViewById(R.id.search_empty);

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);
        adapter.onPick = this::open;

        TextInputEditText edit = root.findViewById(R.id.search_edit);
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(() -> setQuery(s.toString()), 220);
            }
        });

        ChipGroup chips = root.findViewById(R.id.filter_chips);
        chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_pages) filter = "PAGE";
            else if (id == R.id.chip_text) filter = "TEXT";
            else if (id == R.id.chip_journal) filter = "JOURNAL";
            else if (id == R.id.chip_references) filter = "REFERENCE";
            else filter = "ALL";
            render();
        });
        return root;
    }

    private void setQuery(String value) {
        query = value == null ? "" : value.trim();
        if (source != null) source.removeObserver(observer);
        source = repo.search(query);
        source.observe(getViewLifecycleOwner(), observer);
    }

    private void render() {
        List<SearchResult> visible = new ArrayList<>();
        for (SearchResult result : all) {
            if ("ALL".equals(filter)) {
                visible.add(result);
            } else if ("REFERENCE".equals(filter)) {
                if ("REFERENCE".equals(result.type) || "LIBRARY".equals(result.type)) visible.add(result);
            } else if (filter.equals(result.type)) {
                visible.add(result);
            }
        }
        adapter.submit(visible);
        boolean nothing = visible.isEmpty();
        empty.setVisibility(nothing ? View.VISIBLE : View.GONE);
        recycler.setVisibility(nothing ? View.GONE : View.VISIBLE);
        TextView title = empty.findViewById(R.id.empty_title);
        TextView body = empty.findViewById(R.id.empty_body);
        if (title != null) {
            title.setText(query.isEmpty() ? R.string.search_empty_title : R.string.search_no_results);
        }
        if (body != null) body.setText(R.string.search_empty_body);
    }

    private void open(SearchResult result) {
        String type = result.type == null ? "" : result.type;
        if ("COLLECTION".equals(type)) {
            Intent intent = new Intent(requireActivity(), CollectionActivity.class);
            intent.putExtra(CollectionActivity.EXTRA_COLLECTION_ID, result.id);
            startActivity(intent);
        } else if ("PAGE".equals(type) || "TEXT".equals(type)) {
            repo.pageOnce(result.id, page -> {
                if (page == null) {
                    UiUtils.toast(requireContext(), getString(R.string.page_unavailable));
                } else {
                    CreateSheet.openPage(requireActivity(), page);
                }
            });
        } else if ("JOURNAL".equals(type)) {
            Intent intent = new Intent(requireActivity(), JournalEditorActivity.class);
            intent.putExtra(JournalEditorActivity.EXTRA_ENTRY_ID, result.id);
            startActivity(intent);
        } else if ("REFERENCE".equals(type)) {
            repo.referenceOnce(result.id, reference -> {
                if (reference == null) return;
                Intent intent = new Intent(requireActivity(), ResearchDetailActivity.class);
                intent.putExtra(ResearchDetailActivity.EXTRA_PROJECT_ID, reference.projectId);
                startActivity(intent);
            });
        } else if ("LIBRARY".equals(type)) {
            repo.executor().execute(() -> {
                com.archive.app.data.entity.LibraryItemEntity item = repo.libraryItemBlocking(result.id);
                repo.main().post(() -> {
                    if (item == null || !isAdded()) return;
                    ItemDialogs.editLibraryItem(requireActivity(), item,
                            () -> repo.saveLibraryItem(item, null));
                });
            });
        }
    }

    // ------------------------------------------------------------------ adapter

    private static class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.Holder> {

        interface OnPick { void pick(SearchResult result); }

        private final List<SearchResult> items = new ArrayList<>();
        private OnPick onPick;

        void submit(List<SearchResult> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_search_result, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            SearchResult result = items.get(position);
            holder.title.setText(result.title);
            String subtitle = result.subtitle == null ? "" : result.subtitle;
            if (result.updatedAt > 0) {
                subtitle = subtitle + " · " + DateFormats.relative(result.updatedAt);
            }
            holder.subtitle.setText(subtitle);
            holder.type.setText(label(result.type));
            holder.type.setBackground(UiUtils.circleDrawable(holder.itemView.getContext(),
                    UiKit.accent(holder.itemView.getContext()), false));
            holder.icon.setImageResource(icon(result.type));
            holder.itemView.setOnClickListener(v -> {
                if (onPick != null) onPick.pick(result);
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        private static String label(String type) {
            if (type == null) return "";
            switch (type) {
                case "COLLECTION": return "C";
                case "PAGE": return "P";
                case "TEXT": return "T";
                case "JOURNAL": return "J";
                case "REFERENCE": return "R";
                case "LIBRARY": return "L";
                default: return "?";
            }
        }

        private static int icon(String type) {
            if (type == null) return R.drawable.ic_search;
            switch (type) {
                case "COLLECTION": return R.drawable.ic_folder;
                case "PAGE": return R.drawable.ic_pages;
                case "TEXT": return R.drawable.ic_text;
                case "JOURNAL": return R.drawable.ic_journal;
                case "REFERENCE": return R.drawable.ic_research;
                case "LIBRARY": return R.drawable.ic_library;
                default: return R.drawable.ic_search;
            }
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView type;
            final ImageView icon;
            final TextView title;
            final TextView subtitle;

            Holder(View view) {
                super(view);
                type = view.findViewById(R.id.result_type);
                icon = view.findViewById(R.id.result_icon);
                title = view.findViewById(R.id.result_title);
                subtitle = view.findViewById(R.id.result_subtitle);
            }
        }
    }
}
