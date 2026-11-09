package com.nuzhat.app.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.nuzhat.app.R;
import com.nuzhat.app.ui.common.BaseActivity;
import com.nuzhat.app.utils.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;

/** Plain-text list of the open-source libraries this app links against. No network calls. */
public class LicensesActivity extends BaseActivity {

    private static final String[][] LIBRARIES = {
            {"AndroidX / Material Components", "Apache License 2.0"},
            {"Room", "Apache License 2.0"},
            {"CameraX", "Apache License 2.0"},
            {"WorkManager", "Apache License 2.0"},
            {"Biometric", "Apache License 2.0"},
            {"ExifInterface", "Apache License 2.0"},
            {"Guava ListenableFuture", "Apache License 2.0"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(com.nuzhat.app.utils.ThemeController.themeColor(this,
                android.R.attr.colorBackground));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(this, 20);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.licenses_title);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        for (String[] library : LIBRARIES) {
            TextView name = new TextView(this);
            name.setText(library[0]);
            name.setTextAppearance(R.style.TextAppearance_Nuzhat_Title);
            name.setTextColor(com.nuzhat.app.ui.common.UiKit.ink(this));
            content.addView(name);

            TextView detail = new TextView(this);
            detail.setText(library[1]);
            detail.setTextAppearance(R.style.TextAppearance_Nuzhat_Body);
            detail.setTextColor(com.nuzhat.app.ui.common.UiKit.muted(this));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = UiUtils.dp(this, 16);
            content.addView(detail, params);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                UiUtils.dp(this, 56)));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }
}
