package com.archive.app.ui.common;

import android.app.Activity;
import android.content.Intent;

import com.archive.app.ArchiveApp;
import com.archive.app.R;
import com.archive.app.data.entity.BackgroundStyleFactory;
import com.archive.app.data.entity.CollectionEntity;
import com.archive.app.data.entity.PageEntity;
import com.archive.app.ui.canvas.CanvasActivity;
import com.archive.app.ui.collections.CollectionActivity;
import com.archive.app.ui.journal.JournalEditorActivity;
import com.archive.app.ui.research.ResearchDetailActivity;
import com.archive.app.utils.SettingsStore;
import com.archive.app.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

/** The single "create" flow used by the bottom navigation and the home quick actions. */
public final class CreateSheet {

    public interface Host {
        void onArchiveChanged();

        /** The host owns the photo picker so it can import into a freshly created page. */
        void requestImportMedia();
    }

    private CreateSheet() {}

    public static void show(Activity activity, Host host) {
        ArchiveApp app = ArchiveApp.get(activity);
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        android.widget.LinearLayout content = new android.widget.LinearLayout(activity);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = UiUtils.dp(activity, 12);
        content.setPadding(pad, pad, pad, UiUtils.dp(activity, 24));

        content.addView(title(activity));
        content.addView(CanvasSheetsRow(activity, R.drawable.ic_folder,
                activity.getString(R.string.create_collection),
                activity.getString(R.string.create_collection_desc), v -> {
                    dialog.dismiss();
                    newCollection(activity, () -> host.onArchiveChanged());
                }));
        content.addView(CanvasSheetsRow(activity, R.drawable.ic_layers,
                activity.getString(R.string.create_page),
                activity.getString(R.string.create_page_desc), v -> {
                    dialog.dismiss();
                    newPage(activity, () -> host.onArchiveChanged());
                }));
        content.addView(CanvasSheetsRow(activity, R.drawable.ic_journal,
                activity.getString(R.string.create_journal),
                activity.getString(R.string.create_journal_desc), v -> {
                    dialog.dismiss();
                    app.repo().newJournalEntry(entry -> {
                        Intent intent = new Intent(activity, JournalEditorActivity.class);
                        intent.putExtra(JournalEditorActivity.EXTRA_ENTRY_ID, entry.id);
                        activity.startActivity(intent);
                    });
                }));
        content.addView(CanvasSheetsRow(activity, R.drawable.ic_research,
                activity.getString(R.string.create_research),
                activity.getString(R.string.create_research_desc), v -> {
                    dialog.dismiss();
                    app.repo().newResearchProject(project -> {
                        Intent intent = new Intent(activity, ResearchDetailActivity.class);
                        intent.putExtra(ResearchDetailActivity.EXTRA_PROJECT_ID, project.id);
                        activity.startActivity(intent);
                    });
                }));
        content.addView(CanvasSheetsRow(activity, R.drawable.ic_quote,
                activity.getString(R.string.create_reference),
                activity.getString(R.string.create_reference_desc), v -> {
                    dialog.dismiss();
                    app.repo().newLibraryItem(item -> {
                        app.repo().saveLibraryItem(item, null);
                        ItemDialogs.editLibraryItem(activity, item,
                                () -> app.repo().saveLibraryItem(item, host::onArchiveChanged));
                    });
                }));
        content.addView(CanvasSheetsRow(activity, R.drawable.ic_image,
                activity.getString(R.string.create_import),
                activity.getString(R.string.create_import_desc), v -> {
                    dialog.dismiss();
                    host.requestImportMedia();
                }));

        dialog.setContentView(content);
        dialog.show();
    }

    private static android.view.View title(Activity activity) {
        android.widget.TextView text = new android.widget.TextView(activity);
        text.setText(R.string.create_title);
        text.setTextAppearance(R.style.TextAppearance_Archive_Headline);
        text.setTextColor(UiKit.ink(activity));
        text.setPadding(UiUtils.dp(activity, 8), UiUtils.dp(activity, 8),
                UiUtils.dp(activity, 8), UiUtils.dp(activity, 4));
        return text;
    }

    private static android.view.View CanvasSheetsRow(Activity activity, int icon, String title,
                                                     String subtitle,
                                                     android.view.View.OnClickListener listener) {
        return com.archive.app.ui.canvas.CanvasSheets.row(activity, icon, title, subtitle, listener);
    }

    public static void newCollection(Activity activity, Runnable onCreated) {
        ArchiveApp app = ArchiveApp.get(activity);
        CollectionEntity collection = new CollectionEntity();
        collection.name = activity.getString(R.string.collection_default_name);
        collection.colour = 0xFFB08968;
        collection.icon = "folder";
        ItemDialogs.editCollection(activity, collection, () -> {
            app.repo().createCollection(collection.name, collection.description, collection.colour,
                    collection.icon, id -> {
                        onCreated.run();
                        Intent intent = new Intent(activity, CollectionActivity.class);
                        intent.putExtra(CollectionActivity.EXTRA_COLLECTION_ID, id);
                        activity.startActivity(intent);
                    });
        });
    }

    public static void newPage(Activity activity, Runnable onCreated) {
        ArchiveApp app = ArchiveApp.get(activity);
        app.repo().collectionsSync((List<CollectionEntity> collections) -> {
            if (collections.isEmpty()) {
                UiUtils.toast(activity, activity.getString(R.string.no_collection_yet));
                newCollection(activity, onCreated);
                return;
            }
            ItemDialogs.chooseCollection(activity, collections, collection ->
                    ItemDialogs.choosePageFormat(activity, app.settings().defaultFormat(), format -> {
                        String background = BackgroundStyleFactory.defaultJson(
                                app.settings().theme() == SettingsStore.THEME_DARK);
                        app.repo().createPage(collection.id, activity.getString(R.string.untitled_page),
                                format, app.settings().defaultMode(), background, page -> {
                                    onCreated.run();
                                    openPage(activity, page);
                                });
                    }));
        });
    }

    public static void openPage(Activity activity, PageEntity page) {
        Intent intent = new Intent(activity, CanvasActivity.class);
        intent.putExtra(CanvasActivity.EXTRA_PAGE_ID, page.id);
        activity.startActivity(intent);
    }
}
