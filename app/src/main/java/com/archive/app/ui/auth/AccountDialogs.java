package com.archive.app.ui.auth;

import android.app.Activity;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.archive.app.R;
import com.archive.app.data.preferences.AuthStore;
import com.archive.app.domain.security.PasswordHasher;
import com.archive.app.ui.common.UiKit;
import com.archive.app.utils.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Password change flow for the local account. */
public final class AccountDialogs {

    private AccountDialogs() {}

    private static EditText field(Activity activity, String hint) {
        EditText editText = new EditText(activity);
        editText.setHint(hint);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        editText.setTextColor(UiKit.ink(activity));
        editText.setHintTextColor(UiKit.muted(activity));
        return editText;
    }

    public static void changePassword(Activity activity, AuthStore auth, Runnable onChanged) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(activity, 20);
        layout.setPadding(pad, UiUtils.dp(activity, 8), pad, 0);

        EditText current = field(activity, activity.getString(R.string.password_current));
        EditText next = field(activity, activity.getString(R.string.password_new));
        EditText confirm = field(activity, activity.getString(R.string.password_confirm));
        layout.addView(current);
        layout.addView(next);
        layout.addView(confirm);

        TextView hint = new TextView(activity);
        hint.setText(activity.getString(R.string.password_rules,
                PasswordHasher.MIN_PASSWORD_LENGTH));
        hint.setTextColor(UiKit.muted(activity));
        hint.setTextSize(12f);
        hint.setPadding(0, UiUtils.dp(activity, 10), 0, 0);
        layout.addView(hint);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.account_change_password)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String currentValue = current.getText().toString();
                    String nextValue = next.getText().toString();
                    String confirmValue = confirm.getText().toString();

                    if (!PasswordHasher.isStrongEnough(nextValue)) {
                        hint.setText(activity.getString(R.string.password_too_short,
                                PasswordHasher.MIN_PASSWORD_LENGTH));
                        hint.setTextColor(UiKit.accent(activity));
                        return;
                    }
                    if (!nextValue.equals(confirmValue)) {
                        hint.setText(R.string.password_mismatch);
                        hint.setTextColor(UiKit.accent(activity));
                        return;
                    }
                    if (!auth.changePassword(currentValue, nextValue)) {
                        hint.setText(R.string.password_wrong);
                        hint.setTextColor(UiKit.accent(activity));
                        return;
                    }
                    dialog.dismiss();
                    if (onChanged != null) onChanged.run();
                }));
        dialog.show();
    }
}
