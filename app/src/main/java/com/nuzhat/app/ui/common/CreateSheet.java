package com.nuzhat.app.ui.common;

import android.app.Activity;
import android.content.Intent;

import com.nuzhat.app.NuzhatApp;
import com.nuzhat.app.R;
import com.nuzhat.app.data.entity.BackgroundStyleFactory;
import com.nuzhat.app.data.entity.CollectionEntity;
import com.nuzhat.app.data.entity.PageEntity;
import com.nuzhat.app.ui.canvas.CanvasActivity;
import com.nuzhat.app.ui.collections.CollectionActivity;
import com.nuzhat.app.ui.journal.JournalEditorActivity;
import com.nuzhat.app.ui.research.ResearchDetailActivity;
import com.nuzhat.app.utils.SettingsStore;
import com.nuzhat.app.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

/** The single "create" flow used by the bottom navigation and the home quick actions. */
public final class CreateSheet {

    public interface Host {
        void onNuzhatChanged();

        /** The host owns the photo picker so it can import into a freshly created page. */
        void requestImportMedia();
    }

    private CreateSheet() {}

    public static void show(Activity activity, Host host) {
        NuzhatApp app = NuzhatApp.get(activity);
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
                    newCollection(activity, () -> host.onNuzhatChanged());
                }));
        content.addView(CanvasSheetsRow(activity, R.drawable.ic_layers,
                activity.getString(R.string.create_page),
                activity.getString(R.string.create_page_desc), v -> {
                    dialog.dismiss();
                    newPage(activity, () -> host.onNuzhatChanged());
                }));
        content.addView(CanvasSheetsRow(activity, R.drawable.ic_layers,
                activity.getString(R.string.create_template),
                activity.getString(R.string.create_template_desc), v -> {
                    dialog.dismiss();
                    newPageFromTemplate(activity, () -> host.onNuzhatChanged());
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
                                () -> app.repo().saveLibraryItem(item, host::onNuzhatChanged));
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
        text.setTextAppearance(R.style.TextAppearance_Nuzhat_Headline);
        text.setTextColor(UiKit.ink(activity));
        text.setPadding(UiUtils.dp(activity, 8), UiUtils.dp(activity, 8),
                UiUtils.dp(activity, 8), UiUtils.dp(activity, 4));
        return text;
    }

    private static android.view.View CanvasSheetsRow(Activity activity, int icon, String title,
                                                     String subtitle,
                                                     android.view.View.OnClickListener listener) {
        return com.nuzhat.app.ui.canvas.CanvasSheets.row(activity, icon, title, subtitle, listener);
    }

    public static void newCollection(Activity activity, Runnable onCreated) {
        NuzhatApp app = NuzhatApp.get(activity);
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
        NuzhatApp app = NuzhatApp.get(activity);
        app.repo().collectionsSync((List<CollectionEntity> collections) -> {
            if (collections.isEmpty()) {
                UiUtils.toast(activity, activity.getString(R.string.no_collection_yet));
                newCollection(activity, onCreated);
                return;
            }
            ItemDialogs.chooseCollection(activity, collections, collection ->
                    ItemDialogs.choosePageFormat(activity, app.settings().defaultFormat(), format ->
                            chooseBackgroundAndCreate(activity, collection.id, format, onCreated)));
        });
    }

    /**
     * Creation-time paper picker. The chosen preset becomes the page's background property, so it is
     * a real page setting rather than a decorative object on the canvas.
     */
    public static void chooseBackgroundAndCreate(Activity activity, String collectionId, String format,
                                                 Runnable onCreated) {
        NuzhatApp app = NuzhatApp.get(activity);
        String[] keys = BackgroundStyleFactory.PRESET_KEYS;
        String[] labels = {
                activity.getString(R.string.bg_plain),
                activity.getString(R.string.bg_ruled),
                activity.getString(R.string.bg_grid),
                activity.getString(R.string.bg_dots),
                activity.getString(R.string.bg_vintage),
                activity.getString(R.string.bg_kraft),
                activity.getString(R.string.bg_charcoal),
                activity.getString(R.string.bg_gradient)
        };
        UiUtils.listDialog(activity, activity.getString(R.string.background), labels, index -> {
            String background = BackgroundStyleFactory.presetJson(keys[index]);
            app.repo().createPage(collectionId, activity.getString(R.string.untitled_page), format,
                    app.settings().defaultMode(), background, page -> {
                        if (onCreated != null) onCreated.run();
                        openPage(activity, page);
                    });
        });
    }

    /**
     * Template flow: collection → page format → starter. The template contributes a paper preset and
     * a handful of editable objects; nothing is flattened and no stock imagery is inserted.
     */
    public static void newPageFromTemplate(Activity activity, Runnable onCreated) {
        NuzhatApp app = NuzhatApp.get(activity);
        app.repo().collectionsSync((List<CollectionEntity> collections) -> {
            if (collections.isEmpty()) {
                UiUtils.toast(activity, activity.getString(R.string.no_collection_yet));
                newCollection(activity, onCreated);
                return;
            }
            ItemDialogs.chooseCollection(activity, collections, collection ->
                    ItemDialogs.choosePageFormat(activity, app.settings().defaultFormat(), format -> {
                        List<com.nuzhat.app.domain.templates.PageTemplates.Template> templates =
                                com.nuzhat.app.domain.templates.PageTemplates.all();
                        String[] labels = new String[templates.size() + 1];
                        labels[0] = activity.getString(R.string.template_blank);
                        for (int i = 0; i < templates.size(); i++) {
                            labels[i + 1] = activity.getString(templates.get(i).labelRes);
                        }
                        UiUtils.listDialog(activity, activity.getString(R.string.template_title),
                                labels, index -> {
                                    com.nuzhat.app.domain.templates.PageTemplates.Template template =
                                            index == 0
                                                    ? com.nuzhat.app.domain.templates.PageTemplates.blank()
                                                    : templates.get(index - 1);
                                    createFromTemplate(activity, collection, format, template, onCreated);
                                });
                    }));
        });
    }

    private static void createFromTemplate(Activity activity, CollectionEntity collection,
                                           String format,
                                           com.nuzhat.app.domain.templates.PageTemplates.Template template,
                                           Runnable onCreated) {
        NuzhatApp app = NuzhatApp.get(activity);
        String title = "BLANK".equals(template.key)
                ? activity.getString(R.string.untitled_page)
                : activity.getString(template.labelRes);
        app.repo().createPage(collection.id, title, format,
                app.settings().defaultMode(), template.backgroundJson(), page -> {
                    List<com.nuzhat.app.domain.model.CanvasElement> elements =
                            template.build(page.width, page.height);
                    List<com.nuzhat.app.data.entity.CanvasElementEntity> entities =
                            new java.util.ArrayList<>();
                    for (com.nuzhat.app.domain.model.CanvasElement element : elements) {
                        entities.add(element.toEntity(page.id));
                    }
                    if (entities.isEmpty()) {
                        if (onCreated != null) onCreated.run();
                        openPage(activity, page);
                        return;
                    }
                    app.canvasRepo().addElements(page.id, entities, () -> {
                        if (onCreated != null) onCreated.run();
                        openPage(activity, page);
                    });
                });
    }

    public static void openPage(Activity activity, PageEntity page) {
        Intent intent = new Intent(activity, CanvasActivity.class);
        intent.putExtra(CanvasActivity.EXTRA_PAGE_ID, page.id);
        activity.startActivity(intent);
    }
}
