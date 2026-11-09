package com.nuzhat.app.ui.trash;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nuzhat.app.R;
import com.nuzhat.app.data.dao.model.TrashItem;
import com.nuzhat.app.ui.common.BaseActivity;
import com.nuzhat.app.utils.DateFormats;
import com.nuzhat.app.utils.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

/** Soft-deleted items: restore individually or empty the whole trash permanently. */
public class TrashActivity extends BaseActivity {

    private final TrashAdapter adapter = new TrashAdapter();
    private RecyclerView recycler;
    private View empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trash);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        recycler = findViewById(R.id.trash_recycler);
        empty = findViewById(R.id.trash_empty);

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_trash);
        toolbar.setOnMenuItemClickListener(this::onMenu);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        app.repo().trash().observe(this, items -> {
            List<TrashItem> list = items == null ? new ArrayList<>() : items;
            adapter.submit(list);
            empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            recycler.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    private boolean onMenu(MenuItem item) {
        if (item.getItemId() == R.id.action_empty_trash) {
            UiUtils.confirm(this, getString(R.string.empty_trash_title),
                    getString(R.string.empty_trash_body), getString(R.string.empty_trash),
                    () -> app.repo().emptyTrash(
                            () -> UiUtils.toast(this, getString(R.string.trash_emptied))));
            return true;
        }
        return false;
    }

    private static String typeLabel(String type) {
        if (type == null) return "";
        switch (type) {
            case "COLLECTION": return "Collection";
            case "PAGE": return "Page";
            case "JOURNAL": return "Journal";
            case "RESEARCH": return "Research";
            case "REFERENCE": return "Reference";
            case "LIBRARY": return "Library";
            default: return type;
        }
    }

    private class TrashAdapter extends RecyclerView.Adapter<TrashAdapter.Holder> {

        private final List<TrashItem> items = new ArrayList<>();

        void submit(List<TrashItem> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_trash, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            TrashItem item = items.get(position);
            holder.type.setText(typeLabel(item.type) + " · " + DateFormats.relative(item.trashedAt));
            holder.name.setText(item.name);
            holder.restore.setOnClickListener(v -> app.repo().restoreTrashItem(item.type, item.id, () -> {
                UiUtils.toast(TrashActivity.this, getString(R.string.restored));
            }));
            holder.delete.setOnClickListener(v -> UiUtils.confirm(TrashActivity.this,
                    getString(R.string.delete_forever_title),
                    getString(R.string.delete_forever_body, item.name),
                    getString(R.string.delete_forever),
                    () -> app.repo().deleteTrashItemForever(item.type, item.id, () -> {
                        UiUtils.toast(TrashActivity.this, getString(R.string.deleted_forever));
                    })));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class Holder extends RecyclerView.ViewHolder {
            final TextView type;
            final TextView name;
            final TextView restore;
            final TextView delete;

            Holder(View view) {
                super(view);
                type = view.findViewById(R.id.trash_type);
                name = view.findViewById(R.id.trash_name);
                restore = view.findViewById(R.id.trash_restore);
                delete = view.findViewById(R.id.trash_delete);
            }
        }
    }
}
