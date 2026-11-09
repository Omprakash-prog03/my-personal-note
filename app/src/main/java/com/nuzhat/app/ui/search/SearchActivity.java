package com.nuzhat.app.ui.search;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.nuzhat.app.R;
import com.nuzhat.app.ui.common.BaseActivity;

/** Full-screen host for {@link SearchFragment}, reachable from the Home header. */
public class SearchActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.search_container, new SearchFragment())
                    .commit();
        }
    }
}
