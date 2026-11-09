package com.nuzhat.app.ui.activity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nuzhat.app.R;
import com.nuzhat.app.data.entity.ActivityEntity;
import com.nuzhat.app.utils.DateFormats;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Flat list of day headers and activity rows. */
public class ActivityAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;

    private final List<Row> rows = new ArrayList<>();
    private final Consumer<ActivityEntity> onClick;

    public ActivityAdapter(Consumer<ActivityEntity> onClick) {
        this.onClick = onClick;
    }

    public void submit(List<Row> next) {
        rows.clear();
        rows.addAll(next);
        notifyDataSetChanged();
    }

    /** One rendered line: either a day label or an entry. */
    public static class Row {
        final String header;
        final ActivityEntity activity;

        private Row(String header, ActivityEntity activity) {
            this.header = header;
            this.activity = activity;
        }

        static Row header(String label) {
            return new Row(label, null);
        }

        static Row entry(ActivityEntity activity) {
            return new Row(null, activity);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).activity == null ? TYPE_HEADER : TYPE_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_activity_group, parent, false));
        }
        return new RowHolder(inflater.inflate(R.layout.item_activity, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).label.setText(row.header);
            return;
        }
        RowHolder rowHolder = (RowHolder) holder;
        ActivityEntity activity = row.activity;
        rowHolder.text.setText(activity.description);
        rowHolder.meta.setText(DateFormats.time(activity.timestamp));
        rowHolder.icon.setImageResource(iconFor(activity.actionType));
        rowHolder.itemView.setOnClickListener(v -> onClick.accept(activity));
        rowHolder.itemView.setClickable(activity.collectionId != null || activity.pageId != null);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private static int iconFor(String actionType) {
        if (actionType.startsWith("COLLECTION_")) return R.drawable.ic_folder;
        if (actionType.startsWith("MEDIA_") || actionType.startsWith("PHOTO_")) return R.drawable.ic_image;
        if (actionType.startsWith("DRAWING_")) return R.drawable.ic_draw;
        if (actionType.endsWith("_EXPORTED")) return R.drawable.ic_export;
        if (actionType.startsWith("PAGE_")) return R.drawable.ic_journal;
        return R.drawable.ic_edit;
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView label;

        HeaderHolder(View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.activity_group_label);
        }
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView text;
        final TextView meta;

        RowHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.activity_icon);
            text = itemView.findViewById(R.id.activity_text);
            meta = itemView.findViewById(R.id.activity_meta);
        }
    }
}
