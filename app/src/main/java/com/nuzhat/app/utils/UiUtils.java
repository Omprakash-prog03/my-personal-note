package com.nuzhat.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.nuzhat.app.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Small shared UI helpers. Keeping them here stops Activities from filling with boilerplate. */
public final class UiUtils {
    private UiUtils() {}

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    public static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void snack(View anchor, String message) {
        if (anchor == null || anchor.getContext() == null) return;
        com.google.android.material.snackbar.Snackbar.make(anchor, message,
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
    }

    public static Drawable circleDrawable(Context context, int color, boolean ring) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        if (ring) {
            d.setStroke(dp(context, 2), ThemeController.themeColor(context, R.attr.nuzhatSelection));
        }
        return d;
    }

    /** Generic single-field dialog used for names, URLs and short notes. */
    public static void textInputDialog(Context context, String title, @Nullable String initial,
                                       String hint, Consumer<String> onConfirm) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 20);
        wrapper.setPadding(pad, dp(context, 8), pad, 0);
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (initial != null) {
            input.setText(initial);
            input.setSelection(initial.length());
        }
        wrapper.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(wrapper)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, w) -> onConfirm.accept(input.getText().toString().trim()))
                .show();
    }

    public static void multiselectDialog(Context context, String title, String[] items,
                                         boolean[] checked, DialogInterface.OnMultiChoiceClickListener listener,
                                         Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMultiChoiceItems(items, checked, listener)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.done, (d, w) -> onConfirm.run())
                .show();
    }

    public static void listDialog(Context context, String title, String[] items, IntConsumer onPick) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setItems(items, (d, which) -> onPick.accept(which))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void confirm(Context context, String title, String message, String positive,
                               Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(positive, (d, w) -> onConfirm.run())
                .show();
    }

    /** Row of colour swatches; the selected one gets a ring. */
    public static void populateSwatches(LinearLayout container, int[] colors, int selectedColor,
                                        IntConsumer onPick) {
        container.removeAllViews();
        Context context = container.getContext();
        for (int color : colors) {
            View swatch = new View(context);
            boolean selected = color == selectedColor;
            swatch.setBackground(selected
                    ? circleDrawable(context, color, true)
                    : circleDrawable(context, color, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 32), dp(context, 32));
            lp.setMarginEnd(dp(context, 10));
            swatch.setLayoutParams(lp);
            swatch.setContentDescription("#" + Integer.toHexString(color));
            swatch.setOnClickListener(v -> onPick.accept(color));
            container.addView(swatch);
        }
    }

    public static TextView label(Context context, String text, int colorAttr) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(ThemeController.themeColor(context, colorAttr));
        return tv;
    }

    public static void hideKeyboard(Activity activity) {
        View focus = activity.getCurrentFocus();
        if (focus == null) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    public static int blend(int base, int over, float alpha) {
        int r = (int) (Color.red(over) * alpha + Color.red(base) * (1 - alpha));
        int g = (int) (Color.green(over) * alpha + Color.green(base) * (1 - alpha));
        int b = (int) (Color.blue(over) * alpha + Color.blue(base) * (1 - alpha));
        return Color.rgb(r, g, b);
    }

    public static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.getDefault(), "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f MB", mb);
        return String.format(java.util.Locale.getDefault(), "%.2f GB", mb / 1024.0);
    }

    public static int iconForCategory(String icon) {
        if (icon == null) return R.drawable.ic_folder;
        switch (icon) {
            case "research": return R.drawable.ic_research;
            case "cinema": return R.drawable.ic_camera;
            case "literature": return R.drawable.ic_journal;
            case "philosophy": return R.drawable.ic_quote;
            case "technology": return R.drawable.ic_settings;
            case "programming": return R.drawable.ic_layers;
            case "memories": return R.drawable.ic_heart;
            case "journal": return R.drawable.ic_journal;
            default: return R.drawable.ic_folder;
        }
    }

    public static final String[] ICON_KEYS = {"folder", "research", "cinema", "literature", "philosophy", "technology", "programming", "memories", "journal"};

    public static int accentColor(Context context) {
        return ThemeController.themeColor(context, androidx.appcompat.R.attr.colorPrimary);
    }

    public static int mutedColor(Context context) {
        return ThemeController.themeColor(context, R.attr.nuzhatMuted);
    }

    public static AlertDialog.Builder dialog(Context context) {
        return new MaterialAlertDialogBuilder(context);
    }

    public static int color(Context context, int res) {
        return ContextCompat.getColor(context, res);
    }

    public static View spacer(Context context, int heightDp) {
        View v = new View(context);
        v.setLayoutParams(new FrameLayout.LayoutParams(1, dp(context, heightDp)));
        return v;
    }
}
