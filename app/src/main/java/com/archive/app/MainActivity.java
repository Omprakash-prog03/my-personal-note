package com.archive.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.archive.app.data.entity.CanvasElementEntity;
import com.archive.app.data.entity.CollectionEntity;
import com.archive.app.data.entity.MediaEntity;
import com.archive.app.data.entity.PageEntity;
import com.archive.app.domain.model.elements.ImageElement;
import com.archive.app.ui.common.CreateSheet;
import com.archive.app.ui.common.ItemDialogs;
import com.archive.app.ui.home.HomeFragment;
import com.archive.app.ui.library.LibraryFragment;
import com.archive.app.ui.search.SearchFragment;
import com.archive.app.ui.settings.SettingsFragment;
import com.archive.app.utils.SettingsStore;
import com.archive.app.utils.UiUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

/**
 * Navigation shell: four content tabs plus a "Create" tab that opens the creation sheet rather than
 * swapping fragments. Fragments are added once and shown/hidden, so scroll state and search text
 * survive tab changes.
 */
public class MainActivity extends com.archive.app.ui.common.BaseActivity implements CreateSheet.Host {

    private BottomNavigationView bottomNav;
    private int currentTab = R.id.tab_home;
    private boolean suppressNavListener;
    private ActivityResultLauncher<PickVisualMediaRequest> importPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            if (suppressNavListener) return false;
            if (item.getItemId() == R.id.tab_create) {
                CreateSheet.show(this, this);
                bottomNav.setSelectedItemId(currentTab);
                return false;
            }
            currentTab = item.getItemId();
            showTab(currentTab);
            return true;
        });

        if (savedInstanceState == null) {
            showTab(R.id.tab_home);
        }

        importPicker = registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(20),
                this::importIntoNewPage);

        maybeRequireUnlock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bottomNav.setSelectedItemId(currentTab);
    }

    private Fragment fragmentFor(int tabId) {
        String tag = "tab-" + tabId;
        FragmentManager manager = getSupportFragmentManager();
        Fragment existing = manager.findFragmentByTag(tag);
        if (existing != null) return existing;
        if (tabId == R.id.tab_search) return new SearchFragment();
        if (tabId == R.id.tab_library) return new LibraryFragment();
        if (tabId == R.id.tab_settings) return new SettingsFragment();
        return new HomeFragment();
    }

    private void showTab(int tabId) {
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        for (int id : new int[]{R.id.tab_home, R.id.tab_search, R.id.tab_library, R.id.tab_settings}) {
            Fragment fragment = manager.findFragmentByTag("tab-" + id);
            if (fragment != null && fragment.isAdded()) transaction.hide(fragment);
        }
        Fragment target = fragmentFor(tabId);
        if (!target.isAdded()) {
            transaction.add(R.id.fragment_container, target, "tab-" + tabId);
        } else {
            transaction.show(target);
        }
        transaction.commit();
        currentTab = tabId;
    }

    @Override
    public void onArchiveChanged() {
        // Fragments observe Room directly, so nothing needs manual refreshing here.
    }

    @Override
    public void requestImportMedia() {
        importPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build());
    }

    /** Imports chosen images straight into a fresh page so nothing is lost if the user backs out. */
    private void importIntoNewPage(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        app.repo().collectionsSync((List<CollectionEntity> collections) -> {
            if (collections.isEmpty()) {
                UiUtils.toast(this, getString(R.string.no_collection_yet));
                return;
            }
            ItemDialogs.chooseCollection(this, collections, collection ->
                    app.repo().createPage(collection.id, getString(R.string.imported_count, uris.size()),
                            settings.defaultFormat(), settings.defaultMode(),
                            com.archive.app.data.entity.BackgroundStyleFactory.defaultJson(
                                    settings.theme() == SettingsStore.THEME_DARK),
                            page -> importImages(page, uris)));
        });
    }

    private void importImages(PageEntity page, List<Uri> uris) {
        List<CanvasElementEntity> elements = new ArrayList<>();
        final int[] remaining = {uris.size()};
        final int columns = 2;
        for (int i = 0; i < uris.size(); i++) {
            final int index = i;
            app.repo().importMedia(uris.get(i), media -> {
                if (media != null) {
                    ImageElement element = new ImageElement();
                    element.setMediaId(media.id);
                    element.setRelativePath(media.relativePath);
                    element.setDisplayName(media.displayName);
                    float cellWidth = page.width / (columns + 1f);
                    float ratio = media.width > 0 && media.height > 0
                            ? media.width / (float) media.height : 1.4f;
                    element.width = cellWidth;
                    element.height = cellWidth / ratio;
                    element.x = 40f + (index % columns) * (cellWidth + 30f);
                    element.y = 40f + (index / columns) * (cellWidth + 60f);
                    element.zIndex = index + 1;
                    elements.add(element.toEntity(page.id));
                }
                if (--remaining[0] == 0) {
                    app.canvasRepo().addElements(page.id, elements, () -> {
                        CreateSheet.openPage(this, page);
                    });
                }
            });
        }
    }

    /**
     * Optional biometric lock. Device credential is accepted as a fallback so a device without a
     * fingerprint sensor is never locked out of its own archive.
     */
    private void maybeRequireUnlock() {
        if (!settings.appLock() || settings.unlockedThisSession()) return;
        androidx.biometric.BiometricManager manager = androidx.biometric.BiometricManager.from(this);
        int authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
                | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        int can = manager.canAuthenticate(authenticators);
        if (can != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            UiUtils.toast(this, getString(R.string.biometric_unavailable));
            return;
        }
        androidx.biometric.BiometricPrompt prompt = new androidx.biometric.BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                        settings.setUnlockedThisSession(true);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        finishAffinity();
                    }
                });
        androidx.biometric.BiometricPrompt.PromptInfo info =
                new androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.biometric_title))
                        .setSubtitle(getString(R.string.biometric_subtitle))
                        .setAllowedAuthenticators(authenticators)
                        .build();
        prompt.authenticate(info);
    }
}
