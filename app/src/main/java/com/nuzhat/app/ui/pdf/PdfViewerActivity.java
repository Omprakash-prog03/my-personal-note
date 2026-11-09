package com.nuzhat.app.ui.pdf;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nuzhat.app.R;
import com.nuzhat.app.ui.common.BaseActivity;
import com.nuzhat.app.utils.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only PDF viewer backed by the platform renderer. Pages are rasterised once on a background
 * thread and cached in a bounded list, so scrolling stays smooth without holding the renderer open
 * on the UI thread.
 */
public class PdfViewerActivity extends BaseActivity {

    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_RELATIVE_PATH = "extra_relative_path";

    private static final int MAX_PAGES = 80;
    private static final int TARGET_WIDTH = 1240;

    private final List<Bitmap> pages = new ArrayList<>();
    private final PageAdapter adapter = new PageAdapter();

    private RecyclerView recycler;
    private View progress;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        recycler = findViewById(R.id.pages_recycler);
        progress = findViewById(R.id.progress);
        status = findViewById(R.id.pdf_status);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        toolbar.setTitle(title == null ? getString(R.string.nav_library) : title);
        toolbar.setNavigationOnClickListener(v -> finish());

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        String path = getIntent().getStringExtra(EXTRA_RELATIVE_PATH);
        if (path == null) {
            showError(getString(R.string.document_missing));
            return;
        }
        File file = app.files().fileForRelative(path);
        if (!file.exists()) {
            showError(getString(R.string.document_missing));
            return;
        }
        render(file);
    }

    private void render(File file) {
        progress.setVisibility(View.VISIBLE);
        app.executor().execute(() -> {
            List<Bitmap> bitmaps = new ArrayList<>();
            String error = null;
            try (ParcelFileDescriptor descriptor =
                         ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer renderer = new PdfRenderer(descriptor)) {
                int count = Math.min(renderer.getPageCount(), MAX_PAGES);
                for (int i = 0; i < count; i++) {
                    try (PdfRenderer.Page page = renderer.openPage(i)) {
                        int width = Math.min(TARGET_WIDTH, Math.max(320, page.getWidth() * 2));
                        int height = (int) (width * (page.getHeight() / (float) page.getWidth()));
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        bitmaps.add(bitmap);
                    }
                }
            } catch (Exception e) {
                error = String.valueOf(e.getMessage());
            }
            final String finalError = error;
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (finalError != null) {
                    showError(getString(R.string.pdf_open_failed, finalError));
                } else if (bitmaps.isEmpty()) {
                    showError(getString(R.string.document_missing));
                } else {
                    pages.addAll(bitmaps);
                    adapter.notifyDataSetChanged();
                    if (bitmaps.size() >= MAX_PAGES) {
                        status.setVisibility(View.VISIBLE);
                        status.setText(R.string.pdf_truncated);
                    }
                }
            });
        });
    }

    private void showError(String message) {
        progress.setVisibility(View.GONE);
        status.setVisibility(View.VISIBLE);
        status.setText(message);
        UiUtils.toast(this, message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (Bitmap bitmap : pages) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        pages.clear();
    }

    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pdf_page, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.image.setImageBitmap(pages.get(position));
            holder.label.setText(getString(R.string.pdf_page_of, position + 1, pages.size()));
        }

        @Override
        public int getItemCount() { return pages.size(); }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView label;

            Holder(View view) {
                super(view);
                image = view.findViewById(R.id.pdf_page_image);
                label = view.findViewById(R.id.pdf_page_label);
            }
        }
    }
}
