package com.nuzhat.app.ui.common;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nuzhat.app.R;
import com.nuzhat.app.data.entity.CollectionEntity;
import com.nuzhat.app.data.entity.LibraryItemEntity;
import com.nuzhat.app.data.entity.ResearchReferenceEntity;
import com.nuzhat.app.domain.model.PageFormat;
import com.nuzhat.app.utils.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.function.Consumer;

/** Reusable editors for collections, library items, research references and page formats. */
public final class ItemDialogs {

    private ItemDialogs() {}

    private static final int[] COVER_COLOURS = {0xFFB08968, 0xFF8A5A2B, 0xFF3F5B8C, 0xFF5E7B63,
            0xFFB4553F, 0xFF9C4F62, 0xFF4A5568, 0xFF2C3A4E};

    private static LinearLayout form(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(context, 20);
        layout.setPadding(pad, UiUtils.dp(context, 8), pad, 0);
        return layout;
    }

    private static EditText field(Context context, String hint, String value, boolean multiline) {
        EditText editText = new EditText(context);
        editText.setHint(hint);
        editText.setText(value == null ? "" : value);
        editText.setInputType(InputType.TYPE_CLASS_TEXT
                | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0)
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setTextColor(UiKit.ink(context));
        editText.setHintTextColor(UiKit.muted(context));
        if (multiline) {
            editText.setMinLines(3);
            editText.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        }
        return editText;
    }

    public static void editCollection(Activity activity, CollectionEntity collection,
                                      Runnable onSaved) {
        LinearLayout layout = form(activity);
        EditText name = field(activity, activity.getString(R.string.collection_name),
                collection.name, false);
        EditText description = field(activity, activity.getString(R.string.collection_description),
                collection.description, true);
        layout.addView(name);
        layout.addView(description);

        TextView colourLabel = new TextView(activity);
        colourLabel.setText(R.string.collection_colour);
        colourLabel.setTextColor(UiKit.muted(activity));
        colourLabel.setPadding(0, UiUtils.dp(activity, 12), 0, UiUtils.dp(activity, 4));
        layout.addView(colourLabel);

        final int[] chosen = {collection.colour};
        LinearLayout swatches = new LinearLayout(activity);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        for (int colour : COVER_COLOURS) {
            android.view.View swatch = new android.view.View(activity);
            swatch.setBackground(UiUtils.circleDrawable(activity, colour, colour == chosen[0]));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    UiUtils.dp(activity, 30), UiUtils.dp(activity, 30));
            params.setMarginEnd(UiUtils.dp(activity, 8));
            swatch.setLayoutParams(params);
            swatch.setOnClickListener(v -> {
                chosen[0] = colour;
                for (int i = 0; i < swatches.getChildCount(); i++) {
                    swatches.getChildAt(i).setBackground(UiUtils.circleDrawable(activity,
                            COVER_COLOURS[i], COVER_COLOURS[i] == chosen[0]));
                }
            });
            swatches.addView(swatch);
        }
        layout.addView(swatches);

        TextView iconLabel = new TextView(activity);
        iconLabel.setText(R.string.collection_icon);
        iconLabel.setTextColor(UiKit.muted(activity));
        iconLabel.setPadding(0, UiUtils.dp(activity, 12), 0, UiUtils.dp(activity, 4));
        layout.addView(iconLabel);

        final String[] icons = UiUtils.ICON_KEYS;
        final int[] iconIndex = {0};
        for (int i = 0; i < icons.length; i++) if (icons[i].equals(collection.icon)) iconIndex[0] = i;
        TextView iconValue = new TextView(activity);
        iconValue.setText(icons[iconIndex[0]]);
        iconValue.setTextColor(UiKit.accent(activity));
        iconValue.setPadding(0, UiUtils.dp(activity, 4), 0, UiUtils.dp(activity, 4));
        iconValue.setOnClickListener(v -> UiUtils.listDialog(activity,
                activity.getString(R.string.collection_icon), icons, which -> {
                    iconIndex[0] = which;
                    iconValue.setText(icons[which]);
                }));
        layout.addView(iconValue);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(collection.name == null || collection.name.isEmpty()
                        ? activity.getString(R.string.new_collection) : collection.name)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String value = name.getText().toString().trim();
                    collection.name = value.isEmpty()
                            ? activity.getString(R.string.collection_default_name) : value;
                    collection.description = description.getText().toString();
                    collection.colour = chosen[0];
                    collection.icon = icons[iconIndex[0]];
                    collection.updatedAt = System.currentTimeMillis();
                    onSaved.run();
                })
                .show();
    }

    public static void editLibraryItem(Activity activity, LibraryItemEntity item, Runnable onSaved) {
        LinearLayout layout = form(activity);
        String[] types = {activity.getString(R.string.library_type_book),
                activity.getString(R.string.library_type_movie),
                activity.getString(R.string.library_type_music),
                activity.getString(R.string.library_type_article),
                activity.getString(R.string.library_type_website),
                activity.getString(R.string.library_type_quote)};
        String[] typeKeys = {LibraryItemEntity.TYPE_BOOK, LibraryItemEntity.TYPE_MOVIE,
                LibraryItemEntity.TYPE_MUSIC, LibraryItemEntity.TYPE_ARTICLE,
                LibraryItemEntity.TYPE_WEBSITE, LibraryItemEntity.TYPE_QUOTE};
        final int[] typeIndex = {0};
        for (int i = 0; i < typeKeys.length; i++) {
            if (typeKeys[i].equals(item.type)) typeIndex[0] = i;
        }

        TextView typeValue = new TextView(activity);
        typeValue.setText(types[typeIndex[0]]);
        typeValue.setTextColor(UiKit.accent(activity));
        typeValue.setPadding(0, 0, 0, UiUtils.dp(activity, 8));
        typeValue.setOnClickListener(v -> UiUtils.listDialog(activity,
                activity.getString(R.string.reference_type), types, which -> {
                    typeIndex[0] = which;
                    typeValue.setText(types[which]);
                }));
        layout.addView(typeValue);

        EditText title = field(activity, activity.getString(R.string.reference_title), item.title, false);
        EditText creator = field(activity, activity.getString(R.string.library_creator), item.creator, false);
        EditText year = field(activity, activity.getString(R.string.library_year), item.year, false);
        EditText notes = field(activity, activity.getString(R.string.library_notes), item.notes, true);
        EditText tags = field(activity, activity.getString(R.string.tags_label), item.tags, false);
        layout.addView(title);
        layout.addView(creator);
        layout.addView(year);
        layout.addView(notes);
        layout.addView(tags);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.library_item_new)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.favourite, (d, w) -> {
                    item.favourite = !item.favourite;
                    onSaved.run();
                })
                .setPositiveButton(R.string.save, (d, w) -> {
                    item.type = typeKeys[typeIndex[0]];
                    item.title = title.getText().toString().trim();
                    item.creator = creator.getText().toString().trim();
                    item.year = year.getText().toString().trim();
                    item.notes = notes.getText().toString();
                    item.tags = tags.getText().toString();
                    item.updatedAt = System.currentTimeMillis();
                    onSaved.run();
                })
                .show();
    }

    public static void editReference(Activity activity, ResearchReferenceEntity reference,
                                     Runnable onSaved) {
        LinearLayout layout = form(activity);
        EditText title = field(activity, activity.getString(R.string.reference_title),
                reference.title, false);
        EditText author = field(activity, activity.getString(R.string.reference_author),
                reference.author, false);
        EditText publisher = field(activity, activity.getString(R.string.reference_publisher),
                reference.publisher, false);
        EditText date = field(activity, activity.getString(R.string.reference_date),
                reference.publishedDate, false);
        EditText url = field(activity, activity.getString(R.string.reference_url),
                reference.sourceUrl, false);
        EditText notes = field(activity, activity.getString(R.string.reference_notes),
                reference.notes, true);
        layout.addView(title);
        layout.addView(author);
        layout.addView(publisher);
        layout.addView(date);
        layout.addView(url);
        layout.addView(notes);

        TextView disclaimer = new TextView(activity);
        disclaimer.setText(R.string.reference_note_local);
        disclaimer.setTextColor(UiKit.muted(activity));
        disclaimer.setTextSize(11f);
        layout.addView(disclaimer);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.research_add_reference)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, w) -> {
                    reference.title = title.getText().toString().trim();
                    reference.author = author.getText().toString().trim();
                    reference.publisher = publisher.getText().toString().trim();
                    reference.publishedDate = date.getText().toString().trim();
                    reference.sourceUrl = url.getText().toString().trim();
                    reference.notes = notes.getText().toString();
                    if (reference.dateCollected == 0L) {
                        reference.dateCollected = System.currentTimeMillis();
                    }
                    reference.updatedAt = System.currentTimeMillis();
                    onSaved.run();
                })
                .show();
    }

    /** Chooses a page format; returns null when cancelled. */
    public static void choosePageFormat(Activity activity, String defaultFormat,
                                        Consumer<String> onChosen) {
        UiUtils.listDialog(activity, activity.getString(R.string.page_format),
                PageFormat.labels(), index -> onChosen.accept(PageFormat.keys()[index]));
    }

    public static void chooseCollection(Activity activity,
                                        java.util.List<CollectionEntity> collections,
                                        Consumer<CollectionEntity> onChosen) {
        if (collections == null || collections.isEmpty()) {
            UiUtils.toast(activity, activity.getString(R.string.no_collection_yet));
            return;
        }
        String[] names = new String[collections.size()];
        for (int i = 0; i < collections.size(); i++) names[i] = collections.get(i).name;
        UiUtils.listDialog(activity, activity.getString(R.string.choose_collection), names,
                index -> onChosen.accept(collections.get(index)));
    }
}
