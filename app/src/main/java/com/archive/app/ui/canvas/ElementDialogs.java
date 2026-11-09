package com.archive.app.ui.canvas;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.archive.app.R;
import com.archive.app.canvas.engine.CanvasDocument;
import com.archive.app.domain.model.PageFormat;
import com.archive.app.domain.model.elements.EnvelopeElement;
import com.archive.app.domain.model.elements.LinkElement;
import com.archive.app.domain.model.elements.StickyNoteElement;
import com.archive.app.domain.model.elements.TextElement;
import com.archive.app.media.exporter.PageExporter;
import com.archive.app.ui.common.UiKit;
import com.archive.app.utils.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.function.Consumer;

/** Small focused editors for object content and page-level actions. */
public final class ElementDialogs {

    private ElementDialogs() {}

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
            editText.setMinLines(4);
            editText.setGravity(Gravity.TOP | Gravity.START);
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        editText.setPadding(0, UiUtils.dp(context, 8), 0, UiUtils.dp(context, 8));
        return editText;
    }

    // ------------------------------------------------------------------ text

    public static void editText(Activity activity, TextElement element,
                                CanvasSheets.PropertyHost host, Runnable after) {
        LinearLayout layout = form(activity);
        EditText input = field(activity, activity.getString(R.string.text_hint), element.getText(), true);
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(activity);
        hint.setText("Formatting is available in Properties after editing.");
        hint.setTextColor(UiKit.muted(activity));
        hint.setTextSize(12f);
        hint.setPadding(0, UiUtils.dp(activity, 8), 0, 0);
        layout.addView(hint);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.edit_text)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.properties, (d, w) -> {
                    String value = input.getText().toString();
                    host.applyProperty(element, () -> element.setText(value), "Edit text");
                    CanvasSheets.showPropertiesSheet(activity, null, element, host);
                    after.run();
                })
                .setPositiveButton(R.string.done, (d, w) -> {
                    String value = input.getText().toString();
                    host.applyProperty(element, () -> element.setText(value), "Edit text");
                    after.run();
                })
                .show();
    }

    public static void editSticky(Activity activity, StickyNoteElement element,
                                  CanvasSheets.PropertyHost host, Runnable after) {
        LinearLayout layout = form(activity);
        EditText input = field(activity, activity.getString(R.string.sticky_text), element.getText(), true);
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.sticky_text)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.done, (d, w) -> {
                    String value = input.getText().toString();
                    host.applyProperty(element, () -> element.setText(value), "Sticky note");
                    after.run();
                })
                .show();
    }

    // ------------------------------------------------------------------ envelope

    public static void editEnvelope(Activity activity, EnvelopeElement element,
                                    CanvasSheets.PropertyHost host, Runnable attachImage, Runnable after) {
        LinearLayout layout = form(activity);
        EditText labelField = field(activity, activity.getString(R.string.envelope_label),
                element.getLabel(), false);
        EditText messageField = field(activity, activity.getString(R.string.envelope_message),
                element.getMessage(), true);
        layout.addView(labelField);
        layout.addView(messageField);

        TextView state = new TextView(activity);
        state.setText(element.isOpened() ? "Opened" : "Sealed");
        state.setTextColor(UiKit.muted(activity));
        state.setTextSize(12f);
        state.setPadding(0, UiUtils.dp(activity, 8), 0, 0);
        layout.addView(state);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.envelope_contents)
                .setView(layout)
                .setNeutralButton(R.string.envelope_attach_image, (d, w) -> attachImage.run())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.done, (d, w) -> {
                    String label = labelField.getText().toString().trim();
                    String message = messageField.getText().toString();
                    host.applyProperty(element, () -> {
                        element.setLabel(label.isEmpty() ? "A note" : label);
                        element.setMessage(message);
                        element.setOpened(!message.isEmpty() || element.getImageMediaId() != null);
                    }, "Envelope contents");
                    after.run();
                })
                .show();
    }

    // ------------------------------------------------------------------ link

    public static void editLink(Activity activity, LinkElement element,
                                CanvasSheets.PropertyHost host, Runnable after) {
        LinearLayout layout = form(activity);
        EditText titleField = field(activity, activity.getString(R.string.link_title), element.getTitle(), false);
        EditText urlField = field(activity, activity.getString(R.string.link_url), element.getUrl(), false);
        EditText noteField = field(activity, activity.getString(R.string.link_note), element.getNote(), true);
        layout.addView(titleField);
        layout.addView(urlField);
        layout.addView(noteField);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.add_link)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String title = titleField.getText().toString().trim();
                    String url = urlField.getText().toString().trim();
                    String note = noteField.getText().toString();
                    host.applyProperty(element, () -> {
                        element.setTitle(title);
                        element.setUrl(url);
                        element.setNote(note);
                    }, "Link card");
                    after.run();
                })
                .show();
    }

    // ------------------------------------------------------------------ export

    public static void exportSettings(Activity activity, CanvasDocument document,
                                      Consumer<PageExporter.Settings> onExport) {
        PageExporter.Settings settings = new PageExporter.Settings();
        String[] formats = {activity.getString(R.string.export_png), activity.getString(R.string.export_jpeg),
                activity.getString(R.string.export_pdf)};
        String[] resolutions = {activity.getString(R.string.res_1x) + " (1×)",
                activity.getString(R.string.res_2x) + " (2×)",
                activity.getString(R.string.res_3x) + " (3×)"};
        com.archive.app.utils.SettingsStore prefs =
                com.archive.app.ArchiveApp.get(activity).settings();
        final int[] formatChoice = {"JPEG".equals(prefs.exportFormat()) ? 1
                : "PDF".equals(prefs.exportFormat()) ? 2 : 0};
        final int[] scaleChoice = {Math.max(0, Math.min(2, prefs.exportScale() - 1))};
        final boolean[] includeBackground = {prefs.exportIncludeBackground()};

        LinearLayout layout = form(activity);
        TextView formatLabel = new TextView(activity);
        formatLabel.setText(R.string.export_settings);
        formatLabel.setTextColor(UiKit.muted(activity));
        layout.addView(formatLabel);

        TextView formatValue = new TextView(activity);
        formatValue.setText(formats[formatChoice[0]]);
        formatValue.setTextColor(UiKit.accent(activity));
        formatValue.setPadding(0, UiUtils.dp(activity, 6), 0, UiUtils.dp(activity, 6));
        formatValue.setOnClickListener(v -> UiUtils.listDialog(activity,
                activity.getString(R.string.export), formats, which -> {
                    formatChoice[0] = which;
                    formatValue.setText(formats[which]);
                }));
        layout.addView(formatValue);

        TextView resolutionValue = new TextView(activity);
        resolutionValue.setText(resolutions[scaleChoice[0]]);
        resolutionValue.setTextColor(UiKit.accent(activity));
        resolutionValue.setPadding(0, UiUtils.dp(activity, 6), 0, UiUtils.dp(activity, 6));
        resolutionValue.setOnClickListener(v -> UiUtils.listDialog(activity,
                activity.getString(R.string.export_resolution), resolutions, which -> {
                    scaleChoice[0] = which;
                    resolutionValue.setText(resolutions[which]);
                }));
        layout.addView(resolutionValue);

        com.google.android.material.materialswitch.MaterialSwitch includeSwitch =
                new com.google.android.material.materialswitch.MaterialSwitch(activity);
        includeSwitch.setText(R.string.include_background);
        includeSwitch.setChecked(includeBackground[0]);
        includeSwitch.setOnCheckedChangeListener((b, checked) -> includeBackground[0] = checked);
        layout.addView(includeSwitch);

        EditText nameField = field(activity, activity.getString(R.string.file_name),
                document.title, false);
        layout.addView(nameField);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.export_page)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.export, (d, w) -> {
                    settings.format = formatChoice[0] == 1 ? PageExporter.Format.JPEG
                            : formatChoice[0] == 2 ? PageExporter.Format.PDF : PageExporter.Format.PNG;
                    settings.scale = scaleChoice[0] == 0 ? 1f : scaleChoice[0] == 2 ? 3f : 2f;
                    settings.includeBackground = includeBackground[0];
                    prefs.setExportFormat(formatChoice[0] == 1 ? "JPEG"
                            : formatChoice[0] == 2 ? "PDF" : "PNG");
                    prefs.setExportScale(scaleChoice[0] + 1);
                    prefs.setExportIncludeBackground(includeBackground[0]);
                    String name = nameField.getText().toString().trim();
                    settings.fileName = name.isEmpty() ? "archive-page" : name;
                    onExport.accept(settings);
                })
                .show();
    }

    // ------------------------------------------------------------------ page setup

    public static void pageSetup(Activity activity, CanvasDocument document,
                                 Consumer<String> onRename, Consumer<String> onFormat,
                                 Runnable onClear, Runnable onTags) {
        String[] actions = {
                activity.getString(R.string.rename),
                activity.getString(R.string.page_format),
                activity.getString(R.string.tags_label),
                activity.getString(R.string.clear_page)
        };
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.page_options)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            UiUtils.textInputDialog(activity, activity.getString(R.string.rename),
                                    document.title, activity.getString(R.string.page_title), onRename);
                            break;
                        case 1:
                            UiUtils.listDialog(activity, activity.getString(R.string.page_format),
                                    PageFormat.labels(), index -> onFormat.accept(PageFormat.keys()[index]));
                            break;
                        case 2:
                            onTags.run();
                            break;
                        default:
                            UiUtils.confirm(activity, activity.getString(R.string.clear_page),
                                    activity.getString(R.string.clear_page_confirm),
                                    activity.getString(R.string.delete), onClear);
                            break;
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static AlertDialog.Builder confirm(Activity activity, int title, int message) {
        return new MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(message);
    }
}
