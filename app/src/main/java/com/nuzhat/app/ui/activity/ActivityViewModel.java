package com.nuzhat.app.ui.activity;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.nuzhat.app.NuzhatApp;
import com.nuzhat.app.data.entity.ActivityEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Holds the timeline filters. The database query stays fixed at “newest 1000 rows” and the three
 * filters (text, action family, collection) are applied here, so typing never hits SQLite.
 */
public class ActivityViewModel extends ViewModel {

    public static final String FILTER_ALL = "all";

    private List<ActivityEntity> source = Collections.emptyList();
    private String query = "";
    private String family = FILTER_ALL;
    @Nullable
    private String collectionId;

    private final MediatorLiveData<List<ActivityEntity>> items = new MediatorLiveData<>();

    public ActivityViewModel() {
        items.addSource(NuzhatApp.self().repo().recentActivity(), list -> {
            source = list == null ? Collections.emptyList() : list;
            apply();
        });
    }

    public LiveData<List<ActivityEntity>> items() {
        return items;
    }

    public void setQuery(String value) {
        query = value == null ? "" : value;
        apply();
    }

    public void setFamily(String value) {
        family = value == null ? FILTER_ALL : value;
        apply();
    }

    public void setCollection(@Nullable String id) {
        collectionId = id;
        apply();
    }

    @Nullable
    public String collectionId() {
        return collectionId;
    }

    public boolean isFiltered() {
        return !query.trim().isEmpty() || !FILTER_ALL.equals(family) || collectionId != null;
    }

    private void apply() {
        String needle = query.trim().toLowerCase(Locale.US);
        List<ActivityEntity> visible = new ArrayList<>();
        for (ActivityEntity activity : source) {
            if (!FILTER_ALL.equals(family) && !family.equals(activity.family())) continue;
            if (collectionId != null && !collectionId.equals(activity.collectionId)) continue;
            if (!needle.isEmpty() && !activity.description.toLowerCase(Locale.US).contains(needle)) continue;
            visible.add(activity);
        }
        items.setValue(visible);
    }
}
