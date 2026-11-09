package com.archive.app.ui.research;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.archive.app.R;
import com.archive.app.data.entity.ResearchProjectEntity;
import com.archive.app.data.entity.ResearchReferenceEntity;
import com.archive.app.ui.common.BaseActivity;
import com.archive.app.ui.common.ItemDialogs;
import com.archive.app.utils.DateFormats;
import com.archive.app.utils.Ids;
import com.archive.app.utils.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Research notebook: free-form sections plus a local reference list. References are typed in by the
 * user — nothing is fetched, so there are never invented citations.
 */
public class ResearchDetailActivity extends BaseActivity {

    public static final String EXTRA_PROJECT_ID = "extra_project_id";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ReferenceAdapter adapter = new ReferenceAdapter();
    private final Runnable saveTask = this::save;

    private ResearchProjectEntity project;
    private MaterialToolbar toolbar;
    private EditText title;
    private EditText description;
    private EditText questions;
    private EditText hypothesis;
    private EditText findings;
    private EditText conclusion;
    private View referencesEmpty;
    private boolean dirty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_research_detail);
        toolbar = findViewById(R.id.toolbar);
        title = findViewById(R.id.project_title);
        description = findViewById(R.id.project_description);
        questions = findViewById(R.id.project_questions);
        hypothesis = findViewById(R.id.project_hypothesis);
        findings = findViewById(R.id.project_findings);
        conclusion = findViewById(R.id.project_conclusion);
        referencesEmpty = findViewById(R.id.references_empty);

        toolbar.setNavigationOnClickListener(v -> {
            save();
            finish();
        });
        toolbar.inflateMenu(R.menu.menu_research);
        toolbar.setOnMenuItemClickListener(this::onMenu);

        RecyclerView recycler = findViewById(R.id.references_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override public void afterTextChanged(Editable s) {
                dirty = true;
                handler.removeCallbacks(saveTask);
                handler.postDelayed(saveTask, 700);
            }
        };
        for (EditText field : new EditText[]{title, description, questions, hypothesis, findings,
                conclusion}) {
            field.addTextChangedListener(watcher);
        }

        findViewById(R.id.btn_add_reference).setOnClickListener(v -> addReference());

        String projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        if (projectId == null) {
            finish();
            return;
        }

        app.repo().researchProject(projectId).observe(this, loaded -> {
            boolean first = project == null;
            project = loaded;
            if (project == null) {
                finish();
                return;
            }
            if (first) {
                title.setText(project.title);
                description.setText(project.description);
                questions.setText(project.questions);
                hypothesis.setText(project.hypothesis);
                findings.setText(project.findings);
                conclusion.setText(project.conclusion);
                toolbar.setTitle(project.title.isEmpty()
                        ? getString(R.string.research_untitled) : project.title);
                updateMenu();
            }
            adapter.submit(new ArrayList<>());
        });

        app.repo().references(projectId).observe(this, references -> {
            List<ResearchReferenceEntity> list = references == null
                    ? new ArrayList<>() : references;
            adapter.submit(list);
            referencesEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void updateMenu() {
        MenuItem fav = toolbar.getMenu().findItem(R.id.action_favourite);
        if (fav != null && project != null) {
            fav.setTitle(project.favourite ? R.string.unfavourite : R.string.favourite);
        }
    }

    private boolean onMenu(MenuItem item) {
        if (project == null) return false;
        if (item.getItemId() == R.id.action_favourite) {
            project.favourite = !project.favourite;
            updateMenu();
            save();
            return true;
        }
        if (item.getItemId() == R.id.action_delete) {
            UiUtils.confirm(this, getString(R.string.delete_project_title),
                    getString(R.string.delete_project_body), getString(R.string.move_to_trash), () -> {
                        app.repo().trashResearchProject(project.id, () -> {
                            project = null;
                            UiUtils.toast(this, getString(R.string.project_moved_to_trash));
                            finish();
                        });
                    });
            return true;
        }
        return false;
    }

    private void addReference() {
        if (project == null) return;
        ResearchReferenceEntity reference = new ResearchReferenceEntity();
        reference.id = Ids.newId();
        reference.projectId = project.id;
        reference.createdAt = System.currentTimeMillis();
        reference.dateCollected = reference.createdAt;
        ItemDialogs.editReference(this, reference, () -> app.repo().saveReference(reference, null));
    }

    private void editReference(ResearchReferenceEntity reference) {
        ItemDialogs.editReference(this, reference,
                () -> app.repo().saveReference(reference, null));
    }

    private void deleteReference(ResearchReferenceEntity reference) {
        UiUtils.confirm(this, getString(R.string.delete_reference_title),
                getString(R.string.delete_reference_body), getString(R.string.move_to_trash),
                () -> app.repo().trashReference(reference.id, null));
    }

    private void save() {
        if (project == null || !dirty) return;
        handler.removeCallbacks(saveTask);
        dirty = false;
        project.title = title.getText().toString().trim();
        project.description = description.getText().toString();
        project.questions = questions.getText().toString();
        project.hypothesis = hypothesis.getText().toString();
        project.findings = findings.getText().toString();
        project.conclusion = conclusion.getText().toString();
        project.updatedAt = System.currentTimeMillis();
        toolbar.setTitle(project.title.isEmpty()
                ? getString(R.string.research_untitled) : project.title);
        app.repo().saveResearchProject(project, null);
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

    private class ReferenceAdapter extends RecyclerView.Adapter<ReferenceAdapter.Holder> {

        private final List<ResearchReferenceEntity> items = new ArrayList<>();

        void submit(List<ResearchReferenceEntity> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_reference, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ResearchReferenceEntity reference = items.get(position);
            holder.title.setText(reference.title);
            StringBuilder meta = new StringBuilder();
            if (!reference.author.isEmpty()) meta.append(reference.author);
            if (!reference.publisher.isEmpty()) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append(reference.publisher);
            }
            if (!reference.publishedDate.isEmpty()) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append(reference.publishedDate);
            }
            holder.meta.setText(meta);
            holder.notes.setText(reference.notes);
            holder.notes.setVisibility(reference.notes.isEmpty() ? View.GONE : View.VISIBLE);
            holder.collected.setText(reference.dateCollected == 0 ? ""
                    : getString(R.string.reference_collected,
                    DateFormats.shortDate(reference.dateCollected)));
            holder.itemView.setOnClickListener(v -> editReference(reference));
            holder.itemView.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(ResearchDetailActivity.this)
                        .setTitle(reference.title)
                        .setItems(new String[]{getString(R.string.edit),
                                getString(R.string.move_to_trash)}, (dialog, which) -> {
                            if (which == 0) editReference(reference);
                            else deleteReference(reference);
                        })
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class Holder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView meta;
            final TextView notes;
            final TextView collected;

            Holder(View view) {
                super(view);
                title = view.findViewById(R.id.reference_title);
                meta = view.findViewById(R.id.reference_meta);
                notes = view.findViewById(R.id.reference_notes);
                collected = view.findViewById(R.id.reference_collected);
            }
        }
    }
}
