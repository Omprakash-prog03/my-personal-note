package com.archive.app.ui.common;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.archive.app.ArchiveApp;
import com.archive.app.utils.SettingsStore;
import com.archive.app.utils.ThemeController;

/** Every screen applies the saved theme before inflating, so no activity forgets it. */
public abstract class BaseActivity extends AppCompatActivity {

    protected ArchiveApp app;
    protected SettingsStore settings;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        app = ArchiveApp.get(this);
        settings = app.settings();
        ThemeController.apply(this, settings);
        super.onCreate(savedInstanceState);
    }

    protected int accent() {
        return ThemeController.themeColor(this, androidx.appcompat.R.attr.colorPrimary);
    }
}
