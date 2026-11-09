package com.archive.app.ui.journal;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.archive.app.R;
import com.archive.app.data.entity.JournalEntryEntity;
import com.archive.app.ui.common.BaseActivity;
import com.archive.app.utils.DateFormats;
import com.archive.app.utils.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

/**
 * Journal entry editor with mood tagging and debounced autosave. Nothing is lost if the app is
 * killed: the entry is written on every pause and after 700 ms of typing inactivity.
 */
public class JournalEditorActivity extends BaseActivity {

    public static final String EXTRA_ENTRY_ID = "extra_entry_id";

    private static final String[] MOODS = {"Reflective", "Grateful", "Curious", "Heavy", "Hopeful"};
    private static final int[] MOOD_COLOURS = {0xFF8A6FA8, 0xFF6E8B5A, 0xFF3F5B8C, 0xFF8C4A4A,
            0xFFB08968};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private JournalEntryEntity entry;
    private EditText titleField;
    private EditText bodyField;
    private TextView dateView;
    private LinearLayout moodContainer;
    private MaterialToolbar toolbar;
    private boolean dirty;
    private final Runnable saveTask = this::save;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journal_editor);
        toolbar = findViewById(R.id.toolbar);
        titleField = findViewById(R.id.entry_title);
        bodyField = findViewById(R.id.entry_body);
        dateView = findViewById(R.id.entry_date);
        moodContainer = findViewById(R.id.mood_container);

        toolbar.setNavigationOnClickListener(v -> {
            save();
            finish();
        });
        toolbar.inflateMenu(R.menu.menu_journal);
        toolbar.setOnMenuItemClickListener(this::onMenu);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override public void afterTextChanged(Editable s) { scheduleSave(); }
        };
        titleField.addTextChangedListener(watcher);
        bodyField.addTextChangedListener(watcher);

        String id = getIntent().getStringExtra(EXTRA_ENTRY_ID);
        if (id == null) {
            finish();
            return;
        }
        app.repo().journalOnce(id, loaded -> {
            entry = loaded;
            if (entry == null) {
                finish();
                return;
            }
            dateView.setText(DateFormats.longDate(entry.date == 0 ? entry.createdAt : entry.date));
            titleField.setText(entry.title);
            bodyField.setText(entry.body);
            buildMoods();
            updateMenu();
        });
    }

    private void buildMoods() {
        moodContainer.removeAllViews();
        for (int i = 0; i < MOODS.length; i++) {
            final String mood = MOODS[i];
            final int colour = MOOD_COLOURS[i];
            MaterialButton button = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(mood);
            button.setAllCaps(false);
            button.setTextSize(12f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(UiUtils.dp(this, 8));
            button.setLayoutParams(params);
            boolean selected = entry != null && mood.equalsIgnoreCase(entry.mood);
            button.setStrokeColor(android.content.res.ColorStateList.valueOf(colour));
            button.setTextColor(selected ? colour : com.archive.app.ui.common.UiKit.muted(this));
            if (selected) button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    (colour & 0x00FFFFFF) | 0x22000000));
            button.setOnClickListener(v -> {
                if (entry == null) return;
                entry.mood = mood;
                entry.moodColour = colour;
                buildMoods();
                scheduleSave();
            });
            moodContainer.addView(button);
        }
    }

    private void updateMenu() {
        MenuItem fav = toolbar.getMenu().findItem(R.id.action_favourite);
        if (fav != null && entry != null) {
            fav.setTitle(entry.favourite ? R.string.unfavourite : R.string.favourite);
        }
    }

    private boolean onMenu(MenuItem item) {
        if (entry == null) return false;
        if (item.getItemId() == R.id.action_favourite) {
            entry.favourite = !entry.favourite;
            updateMenu();
            save();
            return true;
        }
        if (item.getItemId() == R.id.action_delete) {
            UiUtils.confirm(this, getString(R.string.delete_entry_title),
                    getString(R.string.delete_entry_body), getString(R.string.move_to_trash), () -> {
                        app.repo().trashJournal(entry.id, () -> {
                            entry = null;
                            UiUtils.toast(this, getString(R.string.entry_moved_to_trash));
                            finish();
                        });
                    });
            return true;
        }
        return false;
    }

    private void scheduleSave() {
        dirty = true;
        handler.removeCallbacks(saveTask);
        handler.postDelayed(saveTask, 700);
    }

    private void save() {
        if (entry == null || !dirty) return;
        handler.removeCallbacks(saveTask);
        dirty = false;
        entry.title = titleField.getText().toString();
        entry.body = bodyField.getText().toString();
        entry.updatedAt = System.currentTimeMillis();
        app.repo().saveJournal(entry, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    @Override
    protected void onStop() {
        super.onStop();
        save();
    }
}
