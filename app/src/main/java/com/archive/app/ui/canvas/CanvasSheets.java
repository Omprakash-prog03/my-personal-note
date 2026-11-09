package com.archive.app.ui.canvas;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.archive.app.R;
import com.archive.app.canvas.engine.CanvasDocument;
import com.archive.app.canvas.paint.StickerArt;
import com.archive.app.canvas.selection.SelectionState;
import com.archive.app.domain.model.BackgroundStyle;
import com.archive.app.domain.model.CanvasElement;
import com.archive.app.domain.model.ElementType;
import com.archive.app.domain.model.elements.DrawElement;
import com.archive.app.domain.model.elements.EnvelopeElement;
import com.archive.app.domain.model.elements.ImageElement;
import com.archive.app.domain.model.elements.ShapeElement;
import com.archive.app.domain.model.elements.StickerElement;
import com.archive.app.domain.model.elements.StickyNoteElement;
import com.archive.app.domain.model.elements.TextElement;
import com.archive.app.ui.common.UiKit;
import com.archive.app.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sheet-based tool surfaces for the canvas. They are built in code because every one of them is a
 * dynamic, data-driven list; the primary screens stay in XML.
 */
public final class CanvasSheets {

    public interface PropertyHost {
        /** Applies one undoable property change to an element. */
        void applyProperty(CanvasElement element, Runnable mutation, String label);

        void selectElement(String id);
    }

    public interface AddListener {
        void onGallery();

        void onCamera();

        void onDocument();

        void onText();

        void onDrawing();

        void onShape(String shapeKey);

        void onSticker(String stickerKey);

        void onEnvelope();

        void onStickyNote();

        void onLink();
    }

    private static final int[] INKS = {0xFF2C251C, 0xFF8A5A2B, 0xFFB4553F, 0xFF3F5B8C,
            0xFF5E7B63, 0xFF9C4F62, 0xFF6B6B6B, 0xFFFDF7EA};
    private static final int[] STICKY_COLOURS = {0xFFF6E7A8, 0xFFF6D6D6, 0xFFD8EBCF,
            0xFFD6E4F0, 0xFFE3DAF0, 0xFFF3E9D6};
    private static final int[] ENVELOPE_COLOURS = {0xFFD8C0A0, 0xFFF3E9D6, 0xFFC8C6C0,
            0xFFE5C9C4, 0xFF2C3A4E, 0xFF8E6A4E};
    private static final int[] PAPER_COLOURS = {0xFFFDF6E8, 0xFFFBF7EE, 0xFFEFE2C6,
            0xFFD9C3A0, 0xFFF6EFE0, 0xFF1E1D22, 0xFF1B2434};

    private CanvasSheets() {}

    // ------------------------------------------------------------------ chrome

    private static BottomSheetDialog sheet(Activity activity, String title, View content) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiUtils.color(activity, R.color.transparent));

        View handle = new View(activity);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                UiUtils.dp(activity, 36), UiUtils.dp(activity, 4));
        handleParams.topMargin = UiUtils.dp(activity, 10);
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handle.setLayoutParams(handleParams);
        handle.setBackgroundColor(UiUtils.mutedColor(activity));
        handle.setAlpha(0.4f);
        root.addView(handle);

        if (title != null) {
            TextView heading = new TextView(activity);
            heading.setText(title);
            heading.setTextAppearance(R.style.TextAppearance_Archive_Headline);
            heading.setTextColor(UiKit.ink(activity));
            heading.setPadding(UiUtils.dp(activity, 20), UiUtils.dp(activity, 12),
                    UiUtils.dp(activity, 20), UiUtils.dp(activity, 4));
            root.addView(heading);
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(content);
        root.addView(scroll);

        dialog.setContentView(root);
        dialog.show();
        return dialog;
    }

    private static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(context, 16);
        column.setPadding(pad, pad / 2, pad, UiUtils.dp(context, 28));
        return column;
    }

    public static View row(Context context, int iconRes, String title, String subtitle,
                           View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        applySelectableBackground(row);
        int pad = UiUtils.dp(context, 10);
        row.setPadding(pad, pad, pad, pad);
        row.setMinimumHeight(UiUtils.dp(context, 56));
        row.setOnClickListener(onClick);

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                UiKit.accent(context)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                UiUtils.dp(context, 24), UiUtils.dp(context, 24));
        iconParams.setMarginEnd(UiUtils.dp(context, 14));
        icon.setLayoutParams(iconParams);
        row.addView(icon);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextAppearance(R.style.TextAppearance_Archive_Title);
        titleView.setTextColor(UiKit.ink(context));
        texts.addView(titleView);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleView = new TextView(context);
            subtitleView.setText(subtitle);
            subtitleView.setTextAppearance(R.style.TextAppearance_Archive_Body);
            subtitleView.setTextColor(UiKit.muted(context));
            texts.addView(subtitleView);
        }
        row.addView(texts);
        return row;
    }

    private static void applySelectableBackground(View view) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (view.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, value, true) && value.resourceId != 0) {
            view.setBackgroundResource(value.resourceId);
        }
    }

    private static TextView label(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextAppearance(R.style.TextAppearance_Archive_Label);
        view.setTextColor(UiKit.muted(context));
        view.setPadding(0, UiUtils.dp(context, 14), 0, UiUtils.dp(context, 6));
        return view;
    }

    private static LinearLayout swatchRow(Context context, int[] colours, int selected,
                                          Consumer<Integer> onPick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int colour : colours) {
            View swatch = new View(context);
            swatch.setBackground(UiUtils.circleDrawable(context, colour, colour == selected));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    UiUtils.dp(context, 32), UiUtils.dp(context, 32));
            params.setMarginEnd(UiUtils.dp(context, 10));
            swatch.setLayoutParams(params);
            swatch.setOnClickListener(v -> onPick.accept(colour));
            row.addView(swatch);
        }
        return row;
    }

    private static void addSeek(LinearLayout column, String labelText, float min, float max,
                                float value, Consumer<Float> onChanged) {
        Context context = column.getContext();
        column.addView(label(context, labelText + ": " + Math.round(value)));
        SeekBar bar = new SeekBar(context);
        bar.setMax(1000);
        float ratio = (value - min) / (max - min);
        bar.setProgress(Math.round(Math.max(0f, Math.min(1f, ratio)) * 1000));
        TextView caption = (TextView) column.getChildAt(column.getChildCount() - 1);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float result = min + (max - min) * (progress / 1000f);
                caption.setText(labelText + ": " + Math.round(result));
                onChanged.accept(result);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        column.addView(bar);
    }

    private static void addToggleRow(LinearLayout column, String text, boolean checked,
                                     Consumer<Boolean> onChanged) {
        Context context = column.getContext();
        MaterialSwitch toggle = new MaterialSwitch(context);
        toggle.setText(text);
        toggle.setChecked(checked);
        toggle.setTextColor(UiKit.ink(context));
        toggle.setPadding(0, UiUtils.dp(context, 8), 0, UiUtils.dp(context, 8));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> onChanged.accept(isChecked));
        column.addView(toggle);
    }

    private static void addActionRow(LinearLayout column, String[] labels, Runnable[] actions) {
        Context context = column.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, UiUtils.dp(context, 10), 0, 0);
        for (int i = 0; i < labels.length; i++) {
            MaterialButton button = new MaterialButton(context, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(labels[i]);
            button.setTextSize(12f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMarginEnd(UiUtils.dp(context, 6));
            button.setLayoutParams(params);
            final int index = i;
            button.setOnClickListener(v -> actions[index].run());
            row.addView(button);
        }
        column.addView(row);
    }

    // ------------------------------------------------------------------ add element

    public static void showAddSheet(Activity activity, AddListener listener) {
        LinearLayout content = column(activity);
        content.addView(row(activity, R.drawable.ic_image, activity.getString(R.string.add_image),
                activity.getString(R.string.create_import_desc), v -> listener.onGallery()));
        content.addView(row(activity, R.drawable.ic_camera, activity.getString(R.string.add_camera),
                null, v -> listener.onCamera()));
        content.addView(row(activity, R.drawable.ic_pdf, activity.getString(R.string.add_document),
                null, v -> listener.onDocument()));
        content.addView(row(activity, R.drawable.ic_text, activity.getString(R.string.add_text),
                null, v -> listener.onText()));
        content.addView(row(activity, R.drawable.ic_draw, activity.getString(R.string.add_drawing),
                null, v -> listener.onDrawing()));
        content.addView(row(activity, R.drawable.ic_shape, activity.getString(R.string.add_shape),
                null, v -> listener.onShape(null)));
        content.addView(row(activity, R.drawable.ic_sticker, activity.getString(R.string.add_sticker),
                null, v -> listener.onSticker(null)));
        content.addView(row(activity, R.drawable.ic_envelope, activity.getString(R.string.add_envelope),
                null, v -> listener.onEnvelope()));
        content.addView(row(activity, R.drawable.ic_note, activity.getString(R.string.add_sticky),
                null, v -> listener.onStickyNote()));
        content.addView(row(activity, R.drawable.ic_link, activity.getString(R.string.add_link),
                null, v -> listener.onLink()));
        sheet(activity, activity.getString(R.string.add_element), content);
    }

    // ------------------------------------------------------------------ stickers

    public static void showStickerSheet(Activity activity, Consumer<String> onPick) {
        final BottomSheetDialog[] holder = new BottomSheetDialog[1];
        LinearLayout content = column(activity);
        content.addView(label(activity, activity.getString(R.string.sticker_library)));
        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(4);
        for (StickerArt.Spec spec : StickerArt.CATALOG) {
            StickerPreviewView preview = new StickerPreviewView(activity);
            preview.setStickerKey(spec.key);
            preview.setContentDescription(spec.label);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = UiUtils.dp(activity, 72);
            params.height = UiUtils.dp(activity, 72);
            params.setMargins(UiUtils.dp(activity, 4), UiUtils.dp(activity, 4),
                    UiUtils.dp(activity, 4), UiUtils.dp(activity, 4));
            preview.setLayoutParams(params);
            preview.setBackgroundResource(androidx.appcompat.R.attr.selectableItemBackground);
            preview.setOnClickListener(v -> {
                onPick.accept(spec.key);
                if (holder[0] != null) holder[0].dismiss();
            });
            grid.addView(preview);
        }
        content.addView(grid);
        holder[0] = sheet(activity, activity.getString(R.string.sticker_library), content);
    }

    // ------------------------------------------------------------------ shapes

    public static void showShapeSheet(Activity activity, Consumer<String> onPick) {
        final BottomSheetDialog[] holder = new BottomSheetDialog[1];
        LinearLayout content = column(activity);
        content.addView(label(activity, activity.getString(R.string.shape_library)));
        String[][] shapes = {
                {ShapeElement.RECT, activity.getString(R.string.shape_rectangle)},
                {ShapeElement.ROUND_RECT, activity.getString(R.string.shape_rounded)},
                {ShapeElement.ELLIPSE, activity.getString(R.string.shape_ellipse)},
                {ShapeElement.CIRCLE, activity.getString(R.string.shape_circle)},
                {ShapeElement.TRIANGLE, activity.getString(R.string.shape_triangle)},
                {ShapeElement.LINE, activity.getString(R.string.shape_line)},
                {ShapeElement.ARROW, activity.getString(R.string.shape_arrow)},
                {ShapeElement.DOUBLE_ARROW, activity.getString(R.string.shape_double_arrow)},
                {ShapeElement.STAR, activity.getString(R.string.shape_star)},
                {ShapeElement.BURST, activity.getString(R.string.shape_burst)}
        };
        for (String[] shape : shapes) {
            content.addView(row(activity, R.drawable.ic_shape, shape[1], null, v -> {
                onPick.accept(shape[0]);
                if (holder[0] != null) holder[0].dismiss();
            }));
        }
        holder[0] = sheet(activity, activity.getString(R.string.shape_library), content);
    }

    // ------------------------------------------------------------------ drawing options

    public static View drawingOptions(Activity activity, Consumer<String> onBrush,
                                      Consumer<Integer> onColour, Consumer<Float> onWidth) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiUtils.dp(activity, 6), 0, UiUtils.dp(activity, 6), 0);

        String[][] brushes = {
                {DrawElement.TOOL_PEN, activity.getString(R.string.pen)},
                {DrawElement.TOOL_PENCIL, activity.getString(R.string.pencil)},
                {DrawElement.TOOL_MARKER, activity.getString(R.string.marker)},
                {DrawElement.TOOL_HIGHLIGHTER, activity.getString(R.string.highlighter)}
        };
        for (String[] brush : brushes) {
            MaterialButton button = new MaterialButton(activity, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(brush[1]);
            button.setTextSize(11f);
            button.setOnClickListener(v -> onBrush.accept(brush[0]));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(UiUtils.dp(activity, 6));
            button.setLayoutParams(params);
            row.addView(button);
        }

        for (int colour : new int[]{0xFF2C251C, 0xFFB4553F, 0xFF3F5B8C, 0xFF5E7B63, 0xFFF1D97A}) {
            View swatch = new View(activity);
            swatch.setBackground(UiUtils.circleDrawable(activity, colour, false));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    UiUtils.dp(activity, 28), UiUtils.dp(activity, 28));
            params.setMarginEnd(UiUtils.dp(activity, 8));
            swatch.setLayoutParams(params);
            swatch.setOnClickListener(v -> onColour.accept(colour));
            row.addView(swatch);
        }

        for (float width : new float[]{3f, 6f, 12f, 24f}) {
            View dot = new View(activity);
            dot.setBackground(UiUtils.circleDrawable(activity, UiKit.ink(activity), false));
            int size = UiUtils.dp(activity, Math.min(34f, 10f + width));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginEnd(UiUtils.dp(activity, 8));
            dot.setLayoutParams(params);
            dot.setOnClickListener(v -> onWidth.accept(width));
            row.addView(dot);
        }
        return row;
    }

    // ------------------------------------------------------------------ background

    public static void showBackgroundSheet(Activity activity, CanvasDocument document,
                                           Consumer<BackgroundStyle> onPick,
                                           Runnable onCustomImage) {
        LinearLayout content = column(activity);
        content.addView(label(activity, activity.getString(R.string.background_library)));

        Object[][] styles = {
                {BackgroundStyle.Type.PLAIN, activity.getString(R.string.bg_plain)},
                {BackgroundStyle.Type.GRID, activity.getString(R.string.bg_grid)},
                {BackgroundStyle.Type.RULED, activity.getString(R.string.bg_ruled)},
                {BackgroundStyle.Type.DOTS, activity.getString(R.string.bg_dots)},
                {BackgroundStyle.Type.VINTAGE, activity.getString(R.string.bg_vintage)},
                {BackgroundStyle.Type.KRAFT, activity.getString(R.string.bg_kraft)},
                {BackgroundStyle.Type.GRADIENT, activity.getString(R.string.bg_gradient)},
                {BackgroundStyle.Type.CHARCOAL, activity.getString(R.string.bg_charcoal)}
        };
        for (Object[] entry : styles) {
            BackgroundStyle.Type type = (BackgroundStyle.Type) entry[0];
            content.addView(row(activity, R.drawable.ic_palette, (String) entry[1], null,
                    v -> onPick.accept(BackgroundStyle.of(type, document.background.color))));
        }
        content.addView(row(activity, R.drawable.ic_image,
                activity.getString(R.string.bg_image), activity.getString(R.string.choose_from_gallery),
                v -> onCustomImage.run()));

        content.addView(label(activity, activity.getString(R.string.collection_colour)));
        content.addView(swatchRow(activity, PAPER_COLOURS, 0,
                colour -> onPick.accept(BackgroundStyle.of(document.background.type, colour))));
        sheet(activity, activity.getString(R.string.background_library), content);
    }

    // ------------------------------------------------------------------ layers

    public static void showLayersSheet(Activity activity, CanvasDocument document,
                                       SelectionState selection, Runnable onChange) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText(activity.getString(R.string.layers));
        title.setTextAppearance(R.style.TextAppearance_Archive_Headline);
        title.setTextColor(UiKit.ink(activity));
        title.setPadding(UiUtils.dp(activity, 20), UiUtils.dp(activity, 14),
                UiUtils.dp(activity, 20), UiUtils.dp(activity, 6));
        root.addView(title);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(activity, 320));
        scroll.setLayoutParams(scrollParams);
        root.addView(scroll);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(UiUtils.dp(activity, 12), UiUtils.dp(activity, 6),
                UiUtils.dp(activity, 12), UiUtils.dp(activity, 16));
        root.addView(actions);

        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            list.removeAllViews();
            actions.removeAllViews();
            List<CanvasElement> elements = document.elements();
            for (int i = elements.size() - 1; i >= 0; i--) {
                CanvasElement element = elements.get(i);
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(UiUtils.dp(activity, 12), UiUtils.dp(activity, 8),
                        UiUtils.dp(activity, 8), UiUtils.dp(activity, 8));
                row.setBackgroundColor(selection.contains(element.id)
                        ? UiUtils.blend(UiKit.surface(activity), UiKit.accent(activity), 0.12f)
                        : Color.TRANSPARENT);

                ImageView icon = new ImageView(activity);
                icon.setImageResource(UiKit.iconForElement(element.type()));
                icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                        element.visible ? UiKit.ink(activity) : UiKit.muted(activity)));
                row.addView(icon, new LinearLayout.LayoutParams(UiUtils.dp(activity, 20),
                        UiUtils.dp(activity, 20)));

                TextView name = new TextView(activity);
                String label = element.label() + (element.previewText().isEmpty() ? ""
                        : " · " + trim(element.previewText()));
                name.setText(label);
                name.setTextSize(13f);
                name.setTextColor(element.visible ? UiKit.ink(activity) : UiKit.muted(activity));
                name.setPadding(UiUtils.dp(activity, 10), 0, UiUtils.dp(activity, 8), 0);
                name.setMaxLines(1);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(name, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                row.addView(tinyButton(activity, element.locked ? R.drawable.ic_lock : R.drawable.ic_unlock,
                        v -> {
                            document.setLocks(java.util.Collections.singleton(element.id), !element.locked);
                            onChange.run();
                            rebuild[0].run();
                        }));
                row.addView(tinyButton(activity, element.visible ? R.drawable.ic_visible : R.drawable.ic_hidden,
                        v -> {
                            document.setVisibility(java.util.Collections.singleton(element.id), !element.visible);
                            onChange.run();
                            rebuild[0].run();
                        }));

                row.setOnClickListener(v -> {
                    selection.select(element.id);
                    onChange.run();
                    rebuild[0].run();
                });
                list.addView(row);
            }
            if (elements.isEmpty()) {
                TextView empty = new TextView(activity);
                empty.setText(R.string.nothing_here);
                empty.setTextColor(UiKit.muted(activity));
                empty.setPadding(UiUtils.dp(activity, 20), UiUtils.dp(activity, 12),
                        UiUtils.dp(activity, 20), UiUtils.dp(activity, 12));
                list.addView(empty);
            }

            actions.addView(tinyButton(activity, R.drawable.ic_front, v -> {
                document.bringToFront(selection.ids());
                onChange.run();
                rebuild[0].run();
            }));
            actions.addView(tinyButton(activity, R.drawable.ic_backward, v -> {
                document.sendToBack(selection.ids());
                onChange.run();
                rebuild[0].run();
            }));
            actions.addView(tinyButton(activity, R.drawable.ic_layers, v -> {
                document.moveForward(selection.ids());
                onChange.run();
                rebuild[0].run();
            }));
            actions.addView(tinyButton(activity, R.drawable.ic_undo, v -> {
                document.moveBackward(selection.ids());
                onChange.run();
                rebuild[0].run();
            }));
        };
        rebuild[0].run();
        dialog.setContentView(root);
        dialog.show();
    }

    private static String trim(String text) {
        return text.length() > 22 ? text.substring(0, 22) + "…" : text;
    }

    private static View tinyButton(Activity activity, int icon, View.OnClickListener listener) {
        ImageView button = new ImageView(activity);
        button.setImageResource(icon);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(UiKit.muted(activity)));
        button.setPadding(UiUtils.dp(activity, 10), UiUtils.dp(activity, 10),
                UiUtils.dp(activity, 10), UiUtils.dp(activity, 10));
        button.setBackgroundResource(androidx.appcompat.R.attr.selectableItemBackgroundBorderless);
        button.setOnClickListener(listener);
        return button;
    }

    // ------------------------------------------------------------------ properties

    public static void showPropertiesSheet(Activity activity, CanvasDocument document,
                                           CanvasElement element, PropertyHost host) {
        LinearLayout content = column(activity);
        content.addView(label(activity, element.label().toUpperCase()));

        addSeek(content, activity.getString(R.string.opacity), 0.05f, 1f, element.opacity,
                value -> host.applyProperty(element, () -> element.opacity = value, "Opacity"));
        addSeek(content, activity.getString(R.string.rotation), 0f, 360f, element.rotation,
                value -> host.applyProperty(element, () -> element.rotation = value, "Rotate"));
        if (element instanceof TextElement) {
            TextElement text = (TextElement) element;
            addSeek(content, activity.getString(R.string.font_size), 8f, 120f, text.getFontSize(),
                    value -> host.applyProperty(element, () -> text.setFontSize(value), "Text size"));
            addSeek(content, activity.getString(R.string.corner_radius), 0f, 60f, text.getPadding(),
                    value -> host.applyProperty(element, () -> text.setPadding(value), "Text padding"));
            content.addView(label(activity, activity.getString(R.string.text_colour)));
            content.addView(swatchRow(activity, INKS, text.getColour(), colour ->
                    host.applyProperty(element, () -> text.setColour(colour), "Text colour")));
            content.addView(label(activity, activity.getString(R.string.highlight_colour)));
            content.addView(swatchRow(activity, new int[]{0x55F6E7A8, 0x55F6D6D6, 0x55D8EBCF, 0x55D6E4F0, 0x00000000},
                    text.getHighlightColour(), colour ->
                            host.applyProperty(element, () -> text.setHighlightColour(colour), "Highlight")));
            addToggleRow(content, activity.getString(R.string.bold), text.isBold(), value ->
                    host.applyProperty(element, () -> text.setBold(value), "Bold"));
            addToggleRow(content, activity.getString(R.string.italic), text.isItalic(), value ->
                    host.applyProperty(element, () -> text.setItalic(value), "Italic"));
            addToggleRow(content, activity.getString(R.string.underline), text.isUnderline(), value ->
                    host.applyProperty(element, () -> text.setUnderline(value), "Underline"));
            addToggleRow(content, activity.getString(R.string.shadow), text.hasShadow(), value ->
                    host.applyProperty(element, () -> text.setShadow(value), "Text shadow"));
            addActionRow(content,
                    new String[]{activity.getString(R.string.align_left), activity.getString(R.string.align_center),
                            activity.getString(R.string.align_right)},
                    new Runnable[]{
                            () -> host.applyProperty(element, () -> text.setAlign(TextElement.ALIGN_LEFT), "Align"),
                            () -> host.applyProperty(element, () -> text.setAlign(TextElement.ALIGN_CENTER), "Align"),
                            () -> host.applyProperty(element, () -> text.setAlign(TextElement.ALIGN_RIGHT), "Align")});
            addToggleRow(content, "Serif typeface", TextElement.FONT_SERIF.equals(text.getFontFamily()),
                    value -> host.applyProperty(element,
                            () -> text.setFontFamily(value ? TextElement.FONT_SERIF : TextElement.FONT_SANS),
                            "Typeface"));
        } else if (element instanceof ImageElement) {
            ImageElement image = (ImageElement) element;
            content.addView(label(activity, activity.getString(R.string.image_frame)));
            String[] frames = {ImageElement.FRAME_PLAIN, ImageElement.FRAME_ROUNDED,
                    ImageElement.FRAME_POLAROID, ImageElement.FRAME_PAPER, ImageElement.FRAME_FILM,
                    ImageElement.FRAME_BORDERLESS};
            for (String frame : frames) {
                LinearLayout frameRow = new LinearLayout(activity);
                frameRow.setOrientation(LinearLayout.HORIZONTAL);
                MaterialButton chip = new MaterialButton(activity, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                chip.setText(frame.charAt(0) + frame.substring(1).toLowerCase());
                chip.setTextSize(11f);
                chip.setOnClickListener(v -> host.applyProperty(element,
                        () -> image.setFrame(frame), "Image frame"));
                frameRow.addView(chip);
                content.addView(frameRow);
            }
            addActionRow(content,
                    new String[]{activity.getString(R.string.flip_h),
                            activity.getString(R.string.flip_v)},
                    new Runnable[]{
                            () -> host.applyProperty(element, () ->
                                    image.setFlippedHorizontally(!image.isFlippedHorizontally()), "Flip"),
                            () -> host.applyProperty(element, () ->
                                    image.setFlippedVertically(!image.isFlippedVertically()), "Flip")});
            // Aspect presets crop relative to the *source* pixels, not the current frame size.
            addActionRow(content,
                    new String[]{activity.getString(R.string.crop_square),
                            activity.getString(R.string.crop_four_five),
                            activity.getString(R.string.crop_sixteen_nine)},
                    new Runnable[]{
                            () -> cropToAspect(activity, element, image, host, 1f),
                            () -> cropToAspect(activity, element, image, host, 4f / 5f),
                            () -> cropToAspect(activity, element, image, host, 16f / 9f)});
            content.addView(label(activity, activity.getString(R.string.crop_edges)));
            addCropSeek(content, activity.getString(R.string.crop_left), 0f, 90f,
                    image.getCropLeft() * 100f, element, image, host, Edge.LEFT);
            addCropSeek(content, activity.getString(R.string.crop_top), 0f, 90f,
                    image.getCropTop() * 100f, element, image, host, Edge.TOP);
            addCropSeek(content, activity.getString(R.string.crop_right), 10f, 100f,
                    image.getCropRight() * 100f, element, image, host, Edge.RIGHT);
            addCropSeek(content, activity.getString(R.string.crop_bottom), 10f, 100f,
                    image.getCropBottom() * 100f, element, image, host, Edge.BOTTOM);
            addActionRow(content,
                    new String[]{activity.getString(R.string.reset_crop),
                            activity.getString(R.string.shadow)},
                    new Runnable[]{
                            () -> host.applyProperty(element, () ->
                                    image.setCrop(0f, 0f, 1f, 1f), "Reset crop"),
                            () -> host.applyProperty(element, () ->
                                    image.setShadow(!image.hasShadow()), "Shadow")});
            addSeek(content, activity.getString(R.string.corner_radius), 0f, 80f,
                    image.getCornerRadius(),
                    value -> host.applyProperty(element, () -> image.setCornerRadius(value), "Corners"));
            addSeek(content, activity.getString(R.string.border_width), 0f, 20f, image.getBorderWidth(),
                    value -> host.applyProperty(element, () -> image.setBorderWidth(value), "Border"));
        } else if (element instanceof ShapeElement) {
            ShapeElement shape = (ShapeElement) element;
            content.addView(label(activity, activity.getString(R.string.fill_colour)));
            content.addView(swatchRow(activity, INKS, shape.getFillColour(), colour ->
                    host.applyProperty(element, () -> shape.setFillColour(colour), "Fill")));
            content.addView(swatchRow(activity, new int[]{0x00000000, 0x55F6E7A8, 0x55F6D6D6, 0x55D8EBCF}, 0,
                    colour -> host.applyProperty(element, () -> shape.setFillColour(colour), "Fill")));
            content.addView(label(activity, activity.getString(R.string.outline_colour)));
            content.addView(swatchRow(activity, INKS, shape.getStrokeColour(), colour ->
                    host.applyProperty(element, () -> shape.setStrokeColour(colour), "Outline")));
            addSeek(content, activity.getString(R.string.border_width), 0f, 24f, shape.getStrokeWidth(),
                    value -> host.applyProperty(element, () -> shape.setStrokeWidth(value), "Stroke"));
            addSeek(content, activity.getString(R.string.corner_radius), 0f, 120f, shape.getCornerRadius(),
                    value -> host.applyProperty(element, () -> shape.setCornerRadius(value), "Radius"));
        } else if (element instanceof StickyNoteElement) {
            StickyNoteElement note = (StickyNoteElement) element;
            content.addView(label(activity, activity.getString(R.string.sticky_text)));
            content.addView(swatchRow(activity, STICKY_COLOURS, note.getFillColour(), colour ->
                    host.applyProperty(element, () -> note.setFillColour(colour), "Note colour")));
            addSeek(content, activity.getString(R.string.font_size), 10f, 60f, note.getFontSize(),
                    value -> host.applyProperty(element, () -> note.setFontSize(value), "Note text size"));
            addToggleRow(content, "Folded corner", note.hasFoldedCorner(), value ->
                    host.applyProperty(element, () -> note.setFoldedCorner(value), "Folded corner"));
        } else if (element instanceof EnvelopeElement) {
            EnvelopeElement envelope = (EnvelopeElement) element;
            content.addView(label(activity, activity.getString(R.string.envelope_colour)));
            content.addView(swatchRow(activity, ENVELOPE_COLOURS, envelope.getFillColour(), colour ->
                    host.applyProperty(element, () -> envelope.setFillColour(colour), "Envelope colour")));
            addToggleRow(content, activity.getString(R.string.envelope_sealed), envelope.isSealed(), value ->
                    host.applyProperty(element, () -> envelope.setSealed(value), "Seal"));
        } else if (element instanceof StickerElement) {
            StickerElement sticker = (StickerElement) element;
            content.addView(label(activity, "Tint"));
            content.addView(swatchRow(activity, INKS, sticker.getTint(), colour ->
                    host.applyProperty(element, () -> sticker.setTint(colour), "Sticker tint")));
        } else if (element instanceof DrawElement) {
            DrawElement drawing = (DrawElement) element;
            addActionRow(content, new String[]{"Clear strokes"},
                    new Runnable[]{() -> host.applyProperty(element, drawing::clearStrokes, "Clear strokes")});
        }

        sheet(activity, activity.getString(R.string.properties) + " · " + element.label(), content);
    }

    private enum Edge { LEFT, TOP, RIGHT, BOTTOM }

    /** One crop edge as a percentage of the source image. Fractions are clamped by ImageElement. */
    private static void addCropSeek(LinearLayout content, String label, float min, float max,
                                    float percent, CanvasElement element, ImageElement image,
                                    PropertyHost host, Edge edge) {
        addSeek(content, label, min, max, percent, value -> host.applyProperty(element, () -> {
            float fraction = value / 100f;
            switch (edge) {
                case LEFT:
                    image.setCrop(fraction, image.getCropTop(), image.getCropRight(),
                            image.getCropBottom());
                    break;
                case TOP:
                    image.setCrop(image.getCropLeft(), fraction, image.getCropRight(),
                            image.getCropBottom());
                    break;
                case RIGHT:
                    image.setCrop(image.getCropLeft(), image.getCropTop(), fraction,
                            image.getCropBottom());
                    break;
                default:
                    image.setCrop(image.getCropLeft(), image.getCropTop(), image.getCropRight(),
                            fraction);
                    break;
            }
        }, "Crop"));
    }

    /**
     * Centre-crops to an aspect using the media row's pixel size. Falls back to the element's size
     * when the media row is gone, so a missing file never blocks editing.
     */
    private static void cropToAspect(Activity activity, CanvasElement element, ImageElement image,
                                     PropertyHost host, float aspect) {
        String mediaId = image.getMediaId();
        if (mediaId == null) {
            int width = Math.round(image.width);
            int height = Math.round(image.height);
            host.applyProperty(element, () -> image.cropToAspect(aspect, width, height), "Crop");
            return;
        }
        com.archive.app.ArchiveApp.get(activity).repo().mediaOnce(mediaId, media -> {
            int width = media != null && media.width > 0 ? media.width : Math.round(image.width);
            int height = media != null && media.height > 0 ? media.height : Math.round(image.height);
            host.applyProperty(element, () -> image.cropToAspect(aspect, width, height), "Crop");
        });
    }

    // ------------------------------------------------------------------ ink defaults

    public static int defaultInk() { return INKS[0]; }

    public static int backgroundColor(Context context) {
        return ContextCompat.getColor(context, R.color.warm_background);
    }

    public static ElementType[] elementTypes() { return ElementType.values(); }
}
