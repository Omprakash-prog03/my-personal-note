package com.nuzhat.app.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.nuzhat.app.NuzhatApp;
import com.nuzhat.app.R;
import com.nuzhat.app.data.entity.ActivityEntity;
import com.nuzhat.app.data.entity.CollectionEntity;
import com.nuzhat.app.ui.canvas.CanvasActivity;
import com.nuzhat.app.ui.collections.CollectionActivity;
import com.nuzhat.app.utils.DateFormats;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Chronological history of the archive. Records come from the database, never from the UI, so the
 * timeline always matches what was actually written.
 */
public class ActivityFragment extends Fragment {

    private ActivityViewModel viewModel;
    private ActivityAdapter adapter;
    private View empty;
    private TextView emptyTitle;
    private TextView emptyBody;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_activity, container, false);
        viewModel = new ViewModelProvider(this).get(ActivityViewModel.class);

        RecyclerView list = root.findViewById(R.id.activity_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ActivityAdapter(this::openTarget);
        list.setAdapter(adapter);

        empty = root.findViewById(R.id.activity_empty);
        emptyTitle = root.findViewById(R.id.activity_empty_title);
        emptyBody = root.findViewById(R.id.activity_empty_body);

        ChipGroup chips = root.findViewById(R.id.activity_chips);
        if (chips != null) {
            chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
                int id = checkedIds.isEmpty() ? R.id.chip_all : checkedIds.get(0);
                viewModel.setFamily(familyFor(id));
            });
        }

        TextInputEditText search = root.findViewById(R.id.activity_search);
        if (search != null) {
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

                @Override public void afterTextChanged(Editable s) {
                    viewModel.setQuery(s == null ? "" : s.toString());
                }
            });
        }

        View filter = root.findViewById(R.id.activity_collection_filter);
        if (filter != null) filter.setOnClickListener(v -> chooseCollection());
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel.items().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(@Nullable List<ActivityEntity> activities) {
        if (!isAdded()) return;
        List<ActivityAdapter.Row> rows = new ArrayList<>();
        String currentGroup = null;
        if (activities != null) {
            for (ActivityEntity activity : activities) {
                String group = groupFor(activity.timestamp);
                if (!group.equals(currentGroup)) {
                    rows.add(ActivityAdapter.Row.header(group));
                    currentGroup = group;
                }
                rows.add(ActivityAdapter.Row.entry(activity));
            }
        }
        adapter.submit(rows);

        boolean isEmpty = rows.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (isEmpty) {
            boolean filtered = viewModel.isFiltered();
            emptyTitle.setText(filtered ? R.string.activity_no_results : R.string.activity_empty_title);
            emptyBody.setVisibility(filtered ? View.GONE : View.VISIBLE);
        }
    }

    /** Today / Yesterday / This week / Earlier, computed from the local start of day. */
    private String groupFor(long timestamp) {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        long startOfToday = start.getTimeInMillis();
        if (timestamp >= startOfToday) return getString(R.string.activity_group_today);
        if (timestamp >= startOfToday - DateFormats.DAY_MS) return getString(R.string.activity_group_yesterday);
        if (timestamp >= startOfToday - 7 * DateFormats.DAY_MS) return getString(R.string.activity_group_week);
        return getString(R.string.activity_group_earlier);
    }

    private static String familyFor(int chipId) {
        if (chipId == R.id.chip_collections) return ActivityEntity.FAMILY_COLLECTION;
        if (chipId == R.id.chip_pages) return ActivityEntity.FAMILY_PAGE;
        if (chipId == R.id.chip_media) return ActivityEntity.FAMILY_MEDIA;
        if (chipId == R.id.chip_objects) return ActivityEntity.FAMILY_OBJECT;
        if (chipId == R.id.chip_exports) return ActivityEntity.FAMILY_EXPORT;
        return ActivityViewModel.FILTER_ALL;
    }

    private void chooseCollection() {
        NuzhatApp.self().repo().collectionsSync(list -> {
            if (!isAdded() || list == null) return;
            List<String> names = new ArrayList<>();
            names.add(getString(R.string.activity_all_collections));
            for (CollectionEntity collection : list) names.add(collection.name);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.activity_filter_by_collection)
                    .setItems(names.toArray(new String[0]), (dialog, which) ->
                            viewModel.setCollection(which == 0 ? null : list.get(which - 1).id))
                    .show();
        });
    }

    private void openTarget(ActivityEntity activity) {
        if (!isAdded()) return;
        if (activity.pageId != null) {
            startActivity(new Intent(requireContext(), CanvasActivity.class)
                    .putExtra(CanvasActivity.EXTRA_PAGE_ID, activity.pageId));
            return;
        }
        if (activity.collectionId != null) {
            startActivity(new Intent(requireContext(), CollectionActivity.class)
                    .putExtra(CollectionActivity.EXTRA_COLLECTION_ID, activity.collectionId));
        }
    }
}
