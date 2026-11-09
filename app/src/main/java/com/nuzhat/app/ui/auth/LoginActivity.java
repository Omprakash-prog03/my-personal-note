package com.nuzhat.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.nuzhat.app.MainActivity;
import com.nuzhat.app.R;
import com.nuzhat.app.data.preferences.AuthStore;
import com.nuzhat.app.ui.common.BaseActivity;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Local sign-in gate. There is no server and no account recovery: the credential is a salted PBKDF2
 * hash stored on this device, and the data itself can only be opened with it.
 *
 * <p>Repeated failures are throttled in memory (five attempts, then a thirty second pause) so a
 * borrowed phone cannot be brute-forced with the keyboard.</p>
 */
public class LoginActivity extends BaseActivity {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_MS = 30_000L;

    private AuthStore auth;
    private TextInputLayout usernameLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText username;
    private TextInputEditText password;
    private MaterialSwitch keep;
    private TextView error;
    private ProgressBar progress;
    private View button;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int attempts;
    private long lockedUntil;

    private final Runnable unlockTicker = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() >= lockedUntil) {
                attempts = 0;
                lockedUntil = 0;
                error.setVisibility(View.GONE);
                button.setEnabled(true);
                return;
            }
            long remaining = (lockedUntil - System.currentTimeMillis() + 999) / 1000;
            error.setText(getString(R.string.login_locked, remaining));
            error.setVisibility(View.VISIBLE);
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = new AuthStore(this);
        if (auth.isSignedIn()) {
            enter();
            return;
        }
        setContentView(R.layout.activity_login);

        usernameLayout = findViewById(R.id.login_username_layout);
        passwordLayout = findViewById(R.id.login_password_layout);
        username = findViewById(R.id.login_username);
        password = findViewById(R.id.login_password);
        keep = findViewById(R.id.login_keep);
        error = findViewById(R.id.login_error);
        progress = findViewById(R.id.login_progress);
        button = findViewById(R.id.login_button);

        username.setText(auth.username());
        keep.setChecked(auth.keepSignedIn());
        showPreviousCrashIfAny();

        findViewById(R.id.login_button).setOnClickListener(v -> attempt());
        password.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attempt();
                return true;
            }
            return false;
        });
    }

    /**
     * If the last run died, the stack trace is shown here instead of vanishing: the user can copy it
     * and the dialog is dismissed for good once read.
     */
    private void showPreviousCrashIfAny() {
        final String report = com.nuzhat.app.utils.CrashLog.read(this);
        if (report == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.crash_report_title)
                .setMessage(report)
                .setPositiveButton(R.string.crash_report_copy, (dialog, which) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NUZHAT crash", report));
                        android.widget.Toast.makeText(this, R.string.crash_report_copied,
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.crash_report_dismiss, (dialog, which) ->
                        com.nuzhat.app.utils.CrashLog.clear(LoginActivity.this))
                .show();
    }

    private void attempt() {
        if (lockedUntil > System.currentTimeMillis()) return;
        String user = username.getText() == null ? "" : username.getText().toString().trim();
        String pass = password.getText() == null ? "" : password.getText().toString();

        usernameLayout.setError(null);
        passwordLayout.setError(null);

        if (user.isEmpty()) {
            usernameLayout.setError(getString(R.string.login_missing_username));
            return;
        }
        if (pass.isEmpty()) {
            passwordLayout.setError(getString(R.string.login_missing_password));
            return;
        }

        progress.setVisibility(View.VISIBLE);
        button.setEnabled(false);
        // PBKDF2 is intentionally slow; keep it off the main thread so the button never freezes.
        app.executor().execute(() -> {
            boolean ok = auth.verify(user, pass);
            app.main().post(() -> {
                progress.setVisibility(View.GONE);
                button.setEnabled(true);
                if (ok) {
                    attempts = 0;
                    auth.signIn(keep.isChecked());
                    enter();
                } else {
                    onFailure();
                }
            });
        });
    }

    private void onFailure() {
        attempts++;
        password.setText("");
        password.requestFocus();
        if (attempts >= MAX_ATTEMPTS) {
            lockedUntil = System.currentTimeMillis() + LOCKOUT_MS;
            button.setEnabled(false);
            handler.post(unlockTicker);
        } else {
            error.setText(R.string.login_invalid);
            error.setVisibility(View.VISIBLE);
        }
    }

    private void enter() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
