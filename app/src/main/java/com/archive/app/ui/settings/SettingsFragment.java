package com.archive.app.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.archive.app.ArchiveApp;
import com.archive.app.R;
import com.archive.app.data.preferences.AuthStore;
import com.archive.app.ui.auth.AccountDialogs;
import com.archive.app.ui.auth.LoginActivity;
import com.archive.app.data.entity.PageFormat;
import com.archive.app.data.storage.BackupManager;
import com.archive.app.ui.common.ItemDialogs;
import com.archive.app.ui.common.UiKit;
import com.archive.app.ui.trash.TrashActivity;
import com.archive.app.utils.SettingsStore;
import com.archive.app.utils.UiUtils;
import com.archive.app.utils.ThemeController;
import com.archive.app.workers.BackupWorker;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Appearance, canvas defaults, storage, privacy and about — all backed by SharedPreferences. */
public class SettingsFragment extends Fragment {

    private static final int[] ACCENTS = {0xFFA9713A, 0xFF3F5B8C, 0xFF5E7B63, 0xFFB4553F,
            0xFF9C4F62, 0xFF4A5568};
    private static final int[] HOME_BACKGROUNDS = {R.string.home_bg_paper, R.string.home_bg_plain,
            R.string.home_bg_ruled, R.string.home_bg_dots, R.string.home_bg_gradient};

    private SettingsStore settings;
    private ActivityResultLauncher<String[]> restorePicker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);
        ArchiveApp app = ArchiveApp.get(requireContext());
        settings = app.settings();

        final AuthStore auth = new AuthStore(requireContext());
        TextView account = root.findViewById(R.id.account_username);
        account.setText(getString(R.string.account_signed_in_as, auth.username()));
        root.findViewById(R.id.row_change_password).setOnClickListener(v ->
                AccountDialogs.changePassword(requireActivity(), auth,
                        () -> UiUtils.snack(root, getString(R.string.password_updated))));
        root.findViewById(R.id.row_sign_out).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.account_sign_out_title)
                        .setMessage(R.string.account_sign_out_body)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.account_sign_out, (dialog, which) -> {
                            auth.signOut();
                            Intent intent = new Intent(requireActivity(), LoginActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            requireActivity().finishAffinity();
                        })
                        .show());

        restorePicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) restore(uri);
                });

        MaterialButtonToggleGroup themeGroup = root.findViewById(R.id.theme_group);
        themeGroup.check(settings.theme() == SettingsStore.THEME_DARK ? R.id.theme_dark
                : settings.theme() == SettingsStore.THEME_MINIMAL ? R.id.theme_minimal
                : R.id.theme_warm);
        themeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            int theme = checkedId == R.id.theme_dark ? SettingsStore.THEME_DARK
                    : checkedId == R.id.theme_minimal ? SettingsStore.THEME_MINIMAL
                    : SettingsStore.THEME_WARM;
            if (theme == settings.theme()) return;
            settings.setTheme(theme);
            requireActivity().recreate();
        });

        LinearLayout accents = root.findViewById(R.id.accent_container);
        UiUtils.populateSwatches(accents, ACCENTS, ACCENTS[settings.accent()], colour -> {
            for (int i = 0; i < ACCENTS.length; i++) {
                if (ACCENTS[i] == colour) {
                    settings.setAccent(i);
                    requireActivity().recreate();
                    return;
                }
            }
        });

        LinearLayout backgrounds = root.findViewById(R.id.home_background_container);
        buildBackgroundSwatches(backgrounds);

        ((TextView) root.findViewById(R.id.value_default_format)).setText(
                PageFormat.labelOf(settings.defaultFormat()));
        root.findViewById(R.id.row_default_format).setOnClickListener(v ->
                ItemDialogs.choosePageFormat(requireActivity(), settings.defaultFormat(), format -> {
                    settings.setDefaultFormat(format);
                    ((TextView) root.findViewById(R.id.value_default_format))
                            .setText(PageFormat.labelOf(format));
                }));

        ((TextView) root.findViewById(R.id.value_default_mode)).setText(
                PageFormat.MODE_INFINITE.equals(settings.defaultMode())
                        ? R.string.mode_infinite : R.string.mode_page);
        root.findViewById(R.id.row_default_mode).setOnClickListener(v -> {
            boolean infinite = PageFormat.MODE_INFINITE.equals(settings.defaultMode());
            settings.setDefaultMode(infinite ? PageFormat.MODE_PAGE : PageFormat.MODE_INFINITE);
            ((TextView) root.findViewById(R.id.value_default_mode)).setText(
                    infinite ? R.string.mode_page : R.string.mode_infinite);
        });

        MaterialSwitch snap = root.findViewById(R.id.switch_snap);
        snap.setChecked(settings.snapToGrid());
        snap.setOnCheckedChangeListener((button, checked) -> settings.setSnapToGrid(checked));

        MaterialSwitch grid = root.findViewById(R.id.switch_grid);
        grid.setChecked(settings.showGrid());
        grid.setOnCheckedChangeListener((button, checked) -> settings.setShowGrid(checked));

        TextView exportFormat = root.findViewById(R.id.value_export_format);
        exportFormat.setText(exportFormatLabel(settings.exportFormat()));
        root.findViewById(R.id.row_export_format).setOnClickListener(v -> {
            String[] labels = {getString(R.string.export_png), getString(R.string.export_jpeg),
                    getString(R.string.export_pdf)};
            String[] keys = {"PNG", "JPEG", "PDF"};
            UiUtils.listDialog(requireContext(), getString(R.string.settings_export_format),
                    labels, index -> {
                        settings.setExportFormat(keys[index]);
                        exportFormat.setText(labels[index]);
                    });
        });

        TextView exportScale = root.findViewById(R.id.value_export_scale);
        exportScale.setText(getString(R.string.export_scale_value, settings.exportScale()));
        root.findViewById(R.id.row_export_scale).setOnClickListener(v -> {
            String[] labels = {"1×", "2×", "3×"};
            UiUtils.listDialog(requireContext(), getString(R.string.settings_export_scale),
                    labels, index -> {
                        settings.setExportScale(index + 1);
                        exportScale.setText(labels[index]);
                    });
        });

        TextView storage = root.findViewById(R.id.storage_usage);
        app.repo().storageUsage(bytes -> storage.setText(
                getString(R.string.storage_used, UiUtils.humanBytes(bytes))));

        root.findViewById(R.id.btn_backup).setOnClickListener(v -> runBackup(root));
        root.findViewById(R.id.btn_restore).setOnClickListener(v ->
                restorePicker.launch(new String[]{"application/zip", "application/octet-stream",
                        "*/*"}));
        root.findViewById(R.id.btn_clear_cache).setOnClickListener(v -> {
            app.executor().execute(() -> {
                app.files().clearThumbnailCache();
                app.main().post(() -> UiUtils.snack(root, getString(R.string.cache_cleared)));
            });
        });

        TextView trashSummary = root.findViewById(R.id.trash_summary);
        app.repo().trashCount(count -> trashSummary.setText(
                count == 0 ? getString(R.string.trash_empty_summary)
                        : getResources().getQuantityString(R.plurals.trash_items, count, count)));
        root.findViewById(R.id.row_trash).setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), TrashActivity.class)));

        MaterialSwitch lock = root.findViewById(R.id.switch_app_lock);
        lock.setChecked(settings.appLock());
        lock.setOnCheckedChangeListener((button, checked) -> {
            settings.setAppLock(checked);
            settings.setUnlockedThisSession(!checked);
            UiUtils.snack(root, getString(checked ? R.string.app_lock_on : R.string.app_lock_off));
        });

        TextView version = root.findViewById(R.id.about_version);
        version.setText(getString(R.string.about_version_value, versionName()));

        root.findViewById(R.id.btn_licenses).setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), LicensesActivity.class)));

        return root;
    }

    private void buildBackgroundSwatches(LinearLayout container) {
        container.removeAllViews();
        for (int i = 0; i < HOME_BACKGROUNDS.length; i++) {
            final int index = i;
            TextView chip = new TextView(requireContext());
            chip.setText(HOME_BACKGROUNDS[i]);
            chip.setTextSize(12f);
            chip.setPadding(UiUtils.dp(requireContext(), 12), UiUtils.dp(requireContext(), 8),
                    UiUtils.dp(requireContext(), 12), UiUtils.dp(requireContext(), 8));
            boolean selected = settings.homeBackground() == i;
            chip.setTextColor(selected ? ThemeController.themeColor(requireContext(),
                    androidx.appcompat.R.attr.colorPrimary) : UiKit.muted(requireContext()));
            android.graphics.drawable.GradientDrawable background =
                    new android.graphics.drawable.GradientDrawable();
            background.setCornerRadius(UiUtils.dp(requireContext(), 20));
            background.setColor(ThemeController.themeColor(requireContext(), R.attr.archivePaper));
            if (selected) {
                background.setStroke(UiUtils.dp(requireContext(), 1.5f),
                        ThemeController.themeColor(requireContext(),
                                androidx.appcompat.R.attr.colorPrimary));
            }
            chip.setBackground(background);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(UiUtils.dp(requireContext(), 8));
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> {
                settings.setHomeBackground(index);
                buildBackgroundSwatches(container);
            });
            container.addView(chip);
        }
    }

    private void runBackup(View anchor) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackupWorker.class).build();
        WorkManager.getInstance(requireContext()).enqueue(request);
        UiUtils.snack(anchor, getString(R.string.backup_working));
        WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(request.getId())
                .observe(getViewLifecycleOwner(), new Observer<WorkInfo>() {
                    @Override
                    public void onChanged(WorkInfo info) {
                        if (info == null || !info.getState().isFinished()) return;
                        WorkManager.getInstance(requireContext())
                                .getWorkInfoByIdLiveData(request.getId()).removeObserver(this);
                        if (info.getState() == WorkInfo.State.SUCCEEDED) {
                            Data data = info.getOutputData();
                            String name = data.getString(BackupWorker.KEY_FILE);
                            UiUtils.snack(anchor, getString(R.string.backup_done,
                                    name == null ? "" : name));
                        } else {
                            UiUtils.snack(anchor, getString(R.string.backup_failed));
                        }
                    }
                });
    }

    /** Copies the chosen zip into app storage, stages it, then restarts so the database reopens. */
    private void restore(Uri uri) {
        ArchiveApp app = ArchiveApp.get(requireContext());
        UiUtils.snack(requireView(), getString(R.string.restore_working));
        app.executor().execute(() -> {
            File target = new File(app.getCacheDir(), "restore-" + System.currentTimeMillis() + ".zip");
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(target)) {
                if (in == null) throw new IllegalStateException("Unreadable backup");
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                out.getFD().sync();
                BackupManager.stageRestore(app, target);
                app.main().post(() -> {
                    UiUtils.toast(requireContext(), getString(R.string.restore_ready));
                    Intent intent = requireContext().getPackageManager()
                            .getLaunchIntentForPackage(requireContext().getPackageName());
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }
                    android.os.Process.killProcess(android.os.Process.myPid());
                });
            } catch (Exception e) {
                app.main().post(() -> UiUtils.snack(requireView(),
                        getString(R.string.restore_failed, String.valueOf(e.getMessage()))));
            }
        });
    }

    private String exportFormatLabel(String key) {
        if ("JPEG".equals(key)) return getString(R.string.export_jpeg);
        if ("PDF".equals(key)) return getString(R.string.export_pdf);
        return getString(R.string.export_png);
    }

    private String versionName() {
        try {
            return requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }
}
