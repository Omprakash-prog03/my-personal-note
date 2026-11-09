package com.nuzhat.app.ui.common;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.nuzhat.app.NuzhatApp;
import com.nuzhat.app.utils.SettingsStore;
import com.nuzhat.app.utils.ThemeController;

/** Every screen applies the saved theme before inflating, so no activity forgets it. */
public abstract class BaseActivity extends AppCompatActivity {

    protected NuzhatApp app;
    protected SettingsStore settings;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        app = NuzhatApp.get(this);
        settings = app.settings();
        ThemeController.apply(this, settings);
        super.onCreate(savedInstanceState);
    }

    protected int accent() {
        return ThemeController.themeColor(this, androidx.appcompat.R.attr.colorPrimary);
    }
}
