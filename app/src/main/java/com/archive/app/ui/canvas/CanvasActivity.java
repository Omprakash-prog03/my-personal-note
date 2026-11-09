package com.archive.app.ui.canvas;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.archive.app.R;
import com.archive.app.canvas.commands.CanvasCommand;
import com.archive.app.canvas.commands.ElementAddCommand;
import com.archive.app.canvas.commands.ElementRemoveCommand;
import com.archive.app.canvas.commands.LayerOrderCommand;
import com.archive.app.canvas.commands.PropertyCommand;
import com.archive.app.canvas.engine.CanvasDocument;
import com.archive.app.canvas.engine.CanvasView;
import com.archive.app.data.entity.MediaEntity;
import com.archive.app.domain.model.BackgroundStyle;
import com.archive.app.domain.model.CanvasElement;
import com.archive.app.domain.model.PageFormat;
import com.archive.app.domain.model.elements.AudioElement;
import com.archive.app.domain.model.elements.DocumentElement;
import com.archive.app.domain.model.elements.DrawElement;
import com.archive.app.domain.model.elements.EnvelopeElement;
import com.archive.app.domain.model.elements.ImageElement;
import com.archive.app.domain.model.elements.LinkElement;
import com.archive.app.domain.model.elements.ShapeElement;
import com.archive.app.domain.model.elements.StickerElement;
import com.archive.app.domain.model.elements.StickyNoteElement;
import com.archive.app.domain.model.elements.TextElement;
import com.archive.app.media.camera.CameraActivity;
import com.archive.app.media.exporter.PageExporter;
import com.archive.app.ui.common.BaseActivity;
import com.archive.app.ui.common.UiKit;
import com.archive.app.ui.pdf.PdfViewerActivity;
import com.archive.app.utils.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The canvas editor screen. It binds gestures, tool surfaces and persistence together but keeps
 * every mutation inside the document's command history so undo/redo and autosave stay consistent.
 */
public class CanvasActivity extends BaseActivity implements CanvasView.Listener,
        CanvasSheets.AddListener, CanvasSheets.PropertyHost {

    public static final String EXTRA_PAGE_ID = "extra_page_id";

    private CanvasView canvasView;
    private MaterialToolbar toolbar;
    private MaterialCardView contextCard;
    private MaterialCardView drawOptionsCard;
    private LinearLayout contextActions;
    private LinearLayout toolRail;
    private LinearLayout drawOptionsContainer;
    private TextView canvasHint;

    private CanvasViewModel viewModel;
    private String pageId;
    private float drawWidth = 5f;
    private String brush = DrawElement.TOOL_PEN;
    private int drawColour = CanvasSheets.defaultInk();
    private android.media.MediaPlayer player;

    private ActivityResultLauncher<PickVisualMediaRequest> pickImages;
    private ActivityResultLauncher<PickVisualMediaRequest> pickBackground;
    private ActivityResultLauncher<String[]> pickDocument;
    private ActivityResultLauncher<Intent> takePhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_canvas);
        pageId = getIntent().getStringExtra(EXTRA_PAGE_ID);
        if (pageId == null) {
            finish();
            return;
        }

        canvasView = findViewById(R.id.canvas_view);
        toolbar = findViewById(R.id.canvas_toolbar);
        contextCard = findViewById(R.id.context_card);
        drawOptionsCard = findViewById(R.id.draw_options_card);
        contextActions = findViewById(R.id.context_actions);
        toolRail = findViewById(R.id.tool_rail);
        drawOptionsContainer = findViewById(R.id.draw_options_container);
        canvasHint = findViewById(R.id.canvas_hint);

        canvasView.setListener(this);
        canvasView.setSnapToGrid(settings.snapToGrid());
        canvasView.setShowGrid(settings.showGrid());

        toolbar.setNavigationOnClickListener(v -> handleExit());
        toolbar.setOnMenuItemClickListener(this::onMenuItem);
        findViewById(R.id.zoom_in).setOnClickListener(v -> canvasView.zoomIn());
        findViewById(R.id.zoom_out).setOnClickListener(v -> canvasView.zoomOut());
        findViewById(R.id.zoom_fit).setOnClickListener(v -> canvasView.requestFit());

        buildToolRail();
        buildDrawOptions();
        registerLaunchers();

        viewModel = new ViewModelProvider(this).get(CanvasViewModel.class);
        viewModel.load(pageId);
        viewModel.document().observe(this, this::onDocumentLoaded);
        viewModel.saveState().observe(this, state -> {
            if (state == null) return;
            if (state == CanvasViewModel.STATE_FAILED) {
                toolbar.setSubtitle(getString(R.string.canvas_save_failed));
                com.google.android.material.snackbar.Snackbar.make(canvasView,
                        R.string.canvas_save_failed,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAction(R.string.retry, v -> viewModel.saveNow())
                        .show();
                return;
            }
            toolbar.setSubtitle(state == CanvasViewModel.STATE_SAVING
                    ? getString(R.string.canvas_saving) : getString(R.string.canvas_saved));
        });
        viewModel.pageMissing().observe(this, missing -> {
            if (missing != null && missing) {
                UiUtils.toast(this, getString(R.string.generic_error, "page missing"));
                finish();
            }
        });
        viewModel.recovery().observe(this, info -> {
            if (info == null) return;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.recovery_title)
                    .setMessage(R.string.recovery_body)
                    .setNegativeButton(R.string.discard, (d, w) -> viewModel.discardRecovery(pageId))
                    .setPositiveButton(R.string.restore_work, (d, w) -> viewModel.acceptRecovery())
                    .show();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleExit();
            }
        });
    }

    private void registerLaunchers() {
        pickImages = registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(20),
                uris -> {
                    if (uris == null || uris.isEmpty()) return;
                    importUris(uris);
                });
        pickBackground = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri == null) return;
                    importBackground(uri);
                });
        pickDocument = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    importDocument(uri);
                });
        takePhoto = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    String mediaId = result.getData().getStringExtra(CameraActivity.EXTRA_MEDIA_ID);
                    if (mediaId == null) return;
                    app.repo().mediaOnce(mediaId, media -> {
                        if (media == null) return;
                        ImageElement element = imageElementFor(media);
                        addElement(element, getString(R.string.image_added));
                    });
                });
    }

    // ------------------------------------------------------------------ document

    private void onDocumentLoaded(CanvasDocument document) {
        if (document == null) return;
        boolean firstLoad = canvasView.document() == null;
        if (firstLoad) {
            canvasView.setDocument(document);
            toolbar.setTitle(document.title);
            canvasHint.setVisibility(hasContent(document) ? View.GONE : View.VISIBLE);
        } else {
            // Recovery replaced the live document.
            canvasView.setDocument(document);
            toolbar.setTitle(document.title);
        }
        updateContextToolbar();
    }

    private boolean hasContent(CanvasDocument document) {
        return document != null && !document.elements().isEmpty();
    }

    private void updateHint() {
        CanvasDocument document = canvasView.document();
        canvasHint.setVisibility(document != null && !hasContent(document) ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onSelectionChanged() {
        updateContextToolbar();
    }

    @Override
    public void onDocumentChanged() {
        viewModel.scheduleSave();
        updateHint();
        updateContextToolbar();
    }

    @Override
    public void onEditText(TextElement element) {
        ElementDialogs.editText(this, element, this, this::afterContentChange);
    }

    @Override
    public void onOpenEnvelope(EnvelopeElement element) {
        ElementDialogs.editEnvelope(this, element, this,
                () -> pickImages.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build()),
                this::afterContentChange);
    }

    @Override
    public void onOpenDocument(DocumentElement element) {
        if (element.getRelativePath() == null) {
            UiUtils.toast(this, getString(R.string.document_missing));
            return;
        }
        Intent intent = new Intent(this, PdfViewerActivity.class);
        intent.putExtra(PdfViewerActivity.EXTRA_TITLE, element.getTitle());
        intent.putExtra(PdfViewerActivity.EXTRA_RELATIVE_PATH, element.getRelativePath());
        startActivity(intent);
    }

    @Override
    public void onOpenLink(LinkElement element) {
        if (element.getUrl() == null || element.getUrl().isEmpty()) {
            ElementDialogs.editLink(this, element, this, this::afterContentChange);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(element.getUrl())));
        } catch (Exception e) {
            UiUtils.toast(this, getString(R.string.no_browser));
        }
    }

    @Override
    public void onOpenAudio(AudioElement element) {
        if (element.getRelativePath() == null) {
            UiUtils.toast(this, getString(R.string.document_missing));
            return;
        }
        try {
            if (player != null) {
                player.release();
                player = null;
            }
            player = new android.media.MediaPlayer();
            player.setDataSource(app.files().fileForRelative(element.getRelativePath()).getAbsolutePath());
            player.prepare();
            player.start();
            UiUtils.snack(canvasView, getString(R.string.playing_audio, element.getTitle()));
        } catch (Exception e) {
            UiUtils.toast(this, getString(R.string.generic_error, "audio"));
        }
    }

    @Override
    public void onObjectTapped(CanvasElement element, boolean longPress) {
        updateContextToolbar();
        if (longPress) showElementMenu(element);
    }

    @Override
    public void onEmptyTextPlacement(float docX, float docY) {
        TextElement text = new TextElement();
        text.x = docX;
        text.y = docY;
        text.width = Math.min(560f, canvasView.document().pageWidth * 0.62f);
        text.height = 160f;
        addElement(text, null);
        ElementDialogs.editText(this, text, this, this::afterContentChange);
    }

    @Override
    public void onZoomChanged(int percent) {
        // Zoom percentage is surfaced through the tool rail hint only; kept for accessibility hooks.
    }

    private void afterContentChange() {
        canvasView.invalidate();
        viewModel.scheduleSave();
        updateHint();
    }

    // ------------------------------------------------------------------ tool rail

    private void buildToolRail() {
        toolRail.removeAllViews();
        addTool(R.drawable.ic_select, getString(R.string.tool_select),
                canvasView.tool() == com.archive.app.canvas.gestures.CanvasTouchHandler.Tool.SELECT,
                () -> {
                    canvasView.setTool(com.archive.app.canvas.gestures.CanvasTouchHandler.Tool.SELECT);
                    drawOptionsCard.setVisibility(View.GONE);
                    buildToolRail();
                });
        addTool(R.drawable.ic_add, getString(R.string.add), false, () -> {
            canvasView.setTool(com.archive.app.canvas.gestures.CanvasTouchHandler.Tool.SELECT);
            CanvasSheets.showAddSheet(this, this);
            buildToolRail();
        });
        addTool(R.drawable.ic_text, getString(R.string.add_text),
                canvasView.tool() == com.archive.app.canvas.gestures.CanvasTouchHandler.Tool.TEXT,
                () -> {
                    canvasView.setTool(com.archive.app.canvas.gestures.CanvasTouchHandler.Tool.TEXT);
                    drawOptionsCard.setVisibility(View.GONE);
                    UiUtils.snack(canvasView, getString(R.string.add_text));
                    buildToolRail();
                });
        addTool(R.drawable.ic_draw, getString(R.string.add_drawing),
                canvasView.tool() == com.archive.app.canvas.gestures.CanvasTouchHandler.Tool.DRAW,
                () -> {
                    canvasView.setTool(com.archive.app.canvas.gestures.CanvasTouchHandler.Tool.DRAW);
                    drawOptionsCard.setVisibility(View.VISIBLE);
                    UiUtils.snack(canvasView, getString(R.string.draw_mode));
                    buildToolRail();
                });
        addTool(R.drawable.ic_shape, getString(R.string.add_shape), false,
                () -> CanvasSheets.showShapeSheet(this, this::onShape));
        addTool(R.drawable.ic_sticker, getString(R.string.add_sticker), false,
                () -> CanvasSheets.showStickerSheet(this, this::onSticker));
        addTool(R.drawable.ic_layers, getString(R.string.layers), false,
                () -> CanvasSheets.showLayersSheet(this, canvasView.document(), canvasView.selection(), () -> {
                    canvasView.invalidate();
                    viewModel.scheduleSave();
                    updateContextToolbar();
                }));
    }

    /** Theme attributes are not resource ids; resolve them before using them as a background. */
    private int selectableBackground() {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(
                androidx.appcompat.R.attr.selectableItemBackgroundBorderless, value, true);
        return value.resourceId;
    }

    private void addTool(int iconRes, String label, boolean active, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(android.view.Gravity.CENTER);
        item.setPadding(UiUtils.dp(this, 10), UiUtils.dp(this, 6),
                UiUtils.dp(this, 10), UiUtils.dp(this, 6));
        item.setBackgroundResource(selectableBackground());

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                active ? UiKit.accent(this) : UiKit.ink(this)));
        item.addView(icon, new LinearLayout.LayoutParams(UiUtils.dp(this, 22), UiUtils.dp(this, 22)));

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(10f);
        text.setGravity(android.view.Gravity.CENTER);
        text.setTextColor(active ? UiKit.accent(this) : UiKit.muted(this));
        text.setPadding(0, UiUtils.dp(this, 3), 0, 0);
        item.addView(text);

        item.setMinimumWidth(UiUtils.dp(this, 62));
        item.setOnClickListener(v -> action.run());
        toolRail.addView(item);
    }

    private void buildDrawOptions() {
        drawOptionsContainer.removeAllViews();
        drawOptionsContainer.addView(CanvasSheets.drawingOptions(this,
                value -> {
                    brush = value;
                    canvasView.setDrawStyle(drawColour, drawWidth, brush);
                },
                value -> {
                    drawColour = value;
                    canvasView.setDrawStyle(drawColour, drawWidth, brush);
                },
                value -> {
                    drawWidth = value;
                    canvasView.setDrawStyle(drawColour, drawWidth, brush);
                }));
        canvasView.setDrawStyle(drawColour, drawWidth, brush);
    }

    // ------------------------------------------------------------------ add element flow

    @Override public void onGallery() {
        pickImages.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build());
    }

    @Override public void onCamera() {
        takePhoto.launch(new Intent(this, CameraActivity.class));
    }

    @Override public void onDocument() {
        pickDocument.launch(new String[]{"application/pdf", "text/*", "image/*"});
    }

    @Override public void onText() {
        onEmptyTextPlacement(centreDocumentX(), centreDocumentY());
    }

    @Override public void onDrawing() {
        canvasView.setTool(com.archive.app.canvas.gestures.CanvasTouchHandler.Tool.DRAW);
        drawOptionsCard.setVisibility(View.VISIBLE);
        buildToolRail();
    }

    @Override public void onShape(String shapeKey) {
        if (shapeKey == null) {
            CanvasSheets.showShapeSheet(this, this::onShape);
            return;
        }
        ShapeElement shape = new ShapeElement();
        shape.setShape(shapeKey);
        shape.setFillColour(0x33A9713A);
        shape.setStrokeColour(0xFF2C251C);
        shape.setStrokeWidth(3f);
        if (ShapeElement.LINE.equals(shapeKey) || ShapeElement.ARROW.equals(shapeKey)
                || ShapeElement.DOUBLE_ARROW.equals(shapeKey)) {
            shape.width = 360f;
            shape.height = 90f;
            shape.setFillColour(0);
        }
        addElement(shape, null);
    }

    @Override public void onSticker(String stickerKey) {
        if (stickerKey == null) {
            CanvasSheets.showStickerSheet(this, this::onSticker);
            return;
        }
        StickerElement sticker = new StickerElement();
        sticker.setStickerKey(stickerKey);
        sticker.width = 170f;
        sticker.height = 170f;
        addElement(sticker, null);
    }

    @Override public void onEnvelope() {
        EnvelopeElement envelope = new EnvelopeElement();
        envelope.setLabel("A note");
        envelope.setMessage("");
        envelope.setFillColour(0xFFD8C0A0);
        addElement(envelope, null);
        ElementDialogs.editEnvelope(this, envelope, this, () -> { }, this::afterContentChange);
    }

    @Override public void onStickyNote() {
        StickyNoteElement note = new StickyNoteElement();
        note.setText("");
        addElement(note, null);
        ElementDialogs.editSticky(this, note, this, this::afterContentChange);
    }

    @Override public void onLink() {
        LinkElement link = new LinkElement();
        link.setTitle("");
        link.setUrl("https://");
        addElement(link, null);
        ElementDialogs.editLink(this, link, this, this::afterContentChange);
    }

    private void importUris(List<Uri> uris) {
        AtomicInteger remaining = new AtomicInteger(uris.size());
        AtomicInteger added = new AtomicInteger();
        for (Uri uri : uris) {
            app.repo().importMedia(uri, media -> {
                if (media != null) {
                    ImageElement element = imageElementFor(media);
                    addElement(element, null);
                    added.incrementAndGet();
                }
                if (remaining.decrementAndGet() == 0) {
                    UiUtils.snack(canvasView, getString(R.string.imported_count, added.get()));
                    afterContentChange();
                }
            });
        }
    }

    private ImageElement imageElementFor(MediaEntity media) {
        ImageElement element = new ImageElement();
        element.setMediaId(media.id);
        element.setRelativePath(media.relativePath);
        element.setDisplayName(media.displayName);
        float maxWidth = canvasView.document() == null ? 600f
                : canvasView.document().pageWidth * 0.52f;
        float maxHeight = canvasView.document() == null ? 600f
                : canvasView.document().pageHeight * 0.52f;
        float ratio = media.width > 0 && media.height > 0 ? media.width / (float) media.height
                : 4f / 3f;
        float width = Math.min(maxWidth, maxHeight * ratio);
        element.width = width;
        element.height = width / ratio;
        return element;
    }

    private void importDocument(Uri uri) {
        String mime = ArchiveMime.of(this, uri);
        String name = com.archive.app.data.repository.ArchiveRepository.displayName(
                getContentResolver(), uri, "document");
        app.repo().importMedia(uri, media -> {
            if (media == null) {
                UiUtils.snack(canvasView, getString(R.string.import_failed, name));
                return;
            }
            DocumentElement element = new DocumentElement();
            element.setMediaId(media.id);
            element.setRelativePath(media.relativePath);
            element.setTitle(media.displayName);
            element.setMime(mime);
            element.setPageCount(pdfPageCount(media.relativePath));
            addElement(element, getString(R.string.document_added));
        });
    }

    private int pdfPageCount(String relativePath) {
        if (relativePath == null) return 0;
        File file = app.files().fileForRelative(relativePath);
        if (!file.exists()) return 0;
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(descriptor)) {
            return renderer.getPageCount();
        } catch (Exception e) {
            return 0;
        }
    }

    private void importBackground(Uri uri) {
        app.repo().importMedia(uri, media -> {
            if (media == null) return;
            BackgroundStyle style = canvasView.document().background.copy();
            style.type = BackgroundStyle.Type.IMAGE;
            style.mediaId = media.id;
            style.relativePath = media.relativePath;
            applyBackground(style);
        });
    }

    private float centreDocumentX() {
        float[] point = {canvasView.getWidth() / 2f, canvasView.getHeight() / 2f};
        canvasView.viewport().screenToDoc(point);
        return point[0];
    }

    private float centreDocumentY() {
        float[] point = {canvasView.getWidth() / 2f, canvasView.getHeight() / 2f};
        canvasView.viewport().screenToDoc(point);
        return point[1];
    }

    private void addElement(CanvasElement element, String message) {
        CanvasDocument document = canvasView.document();
        if (document == null) return;
        if (element.width <= 0) element.width = element.defaultWidth();
        if (element.height <= 0) element.height = element.defaultHeight();
        element.x = centreDocumentX() - element.width / 2f
                + (document.elements().size() % 5) * 16f;
        element.y = centreDocumentY() - element.height / 2f
                + (document.elements().size() % 5) * 16f;
        document.history.execute(new ElementAddCommand(document, element, "Add " + element.label()));
        canvasView.selection().select(element.id);
        canvasView.invalidate();
        updateContextToolbar();
        updateHint();
        viewModel.scheduleSave();
        if (message != null) UiUtils.snack(canvasView, message);
    }

    // ------------------------------------------------------------------ contextual toolbar

    private void updateContextToolbar() {
        CanvasDocument document = canvasView.document();
        if (document == null) {
            contextCard.setVisibility(View.GONE);
            return;
        }
        int count = canvasView.selection().size();
        if (count == 0) {
            contextCard.setVisibility(View.GONE);
            contextActions.removeAllViews();
            return;
        }
        contextCard.setVisibility(View.VISIBLE);
        contextActions.removeAllViews();

        CanvasElement single = count == 1 ? document.byId(canvasView.selection().single()) : null;
        if (single != null) addContextAction(R.drawable.ic_edit, R.string.properties,
                () -> CanvasSheets.showPropertiesSheet(this, document, single, this));
        addContextAction(R.drawable.ic_duplicate, R.string.duplicate_element, this::duplicateSelection);
        addContextAction(R.drawable.ic_front, R.string.bring_front, () -> changeOrder(true));
        addContextAction(R.drawable.ic_backward, R.string.send_back, () -> changeOrder(false));
        if (single != null) {
            addContextAction(single.locked ? R.drawable.ic_lock : R.drawable.ic_unlock,
                    single.locked ? R.string.unlock : R.string.lock,
                    () -> applyProperty(single, () -> single.locked = !single.locked,
                            single.locked ? "Unlock" : "Lock"));
        }
        addContextAction(R.drawable.ic_delete, R.string.delete, this::deleteSelection);
        addContextAction(R.drawable.ic_more_vert, R.string.cd_more,
                () -> { if (single != null) showElementMenu(single); });
    }

    private void addContextAction(int iconRes, int labelRes, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(android.view.Gravity.CENTER);
        item.setPadding(UiUtils.dp(this, 10), UiUtils.dp(this, 6),
                UiUtils.dp(this, 10), UiUtils.dp(this, 4));
        item.setMinimumWidth(UiUtils.dp(this, 64));
        item.setBackgroundResource(selectableBackground());
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(UiKit.ink(this)));
        item.addView(icon, new LinearLayout.LayoutParams(UiUtils.dp(this, 20), UiUtils.dp(this, 20)));
        TextView text = new TextView(this);
        text.setText(labelRes);
        text.setTextSize(10f);
        text.setTextColor(UiKit.muted(this));
        text.setGravity(android.view.Gravity.CENTER);
        text.setPadding(0, UiUtils.dp(this, 2), 0, 0);
        item.addView(text);
        item.setOnClickListener(v -> action.run());
        contextActions.addView(item);
    }

    private void duplicateSelection() {
        CanvasDocument document = canvasView.document();
        if (document == null) return;
        List<CanvasElement> copies = new ArrayList<>();
        com.archive.app.canvas.commands.CompositeCommand composite =
                new com.archive.app.canvas.commands.CompositeCommand("Duplicate objects");
        for (CanvasElement element : canvasView.selection().elements(document)) {
            CanvasElement copy = element.duplicate();
            copy.x += 24f;
            copy.y += 24f;
            document.add(copy);
            copies.add(copy);
            composite.add(new ElementAddCommand(document, copy, "Duplicate object"));
        }
        if (copies.isEmpty()) return;
        document.history.record(composite);
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        for (CanvasElement copy : copies) ids.add(copy.id);
        canvasView.selection().replaceWith(ids);
        canvasView.invalidate();
        afterContentChange();
    }

    private void changeOrder(boolean toFront) {
        CanvasDocument document = canvasView.document();
        if (document == null) return;
        List<String> before = document.orderedIds();
        if (toFront) document.bringToFront(canvasView.selection().ids());
        else document.sendToBack(canvasView.selection().ids());
        document.history.record(new LayerOrderCommand(document, before));
        canvasView.invalidate();
        afterContentChange();
    }

    private void deleteSelection() {
        CanvasDocument document = canvasView.document();
        if (document == null) return;
        List<CanvasElement> selected = canvasView.selection().elements(document);
        if (selected.isEmpty()) return;
        document.history.execute(new ElementRemoveCommand(document, selected));
        canvasView.selection().clear();
        canvasView.invalidate();
        updateContextToolbar();
        afterContentChange();
    }

    private void showElementMenu(CanvasElement element) {
        CanvasDocument document = canvasView.document();
        if (document == null || element == null) return;
        PopupMenu menu = new PopupMenu(this, toolbar);
        menu.getMenu().add(0, 1, 0, R.string.properties);
        menu.getMenu().add(0, 2, 1, R.string.duplicate_element);
        menu.getMenu().add(0, 3, 2, R.string.lock);
        menu.getMenu().add(0, 4, 3, R.string.group);
        menu.getMenu().add(0, 5, 4, R.string.ungroup);
        menu.getMenu().add(0, 6, 5, R.string.delete);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: CanvasSheets.showPropertiesSheet(this, document, element, this); break;
                case 2: duplicateSelection(); break;
                case 3: applyProperty(element, () -> element.locked = !element.locked, "Lock"); break;
                case 4: document.group(canvasView.selection().ids()); afterContentChange(); break;
                case 5: document.ungroup(canvasView.selection().ids()); afterContentChange(); break;
                case 6: deleteSelection(); break;
                default: break;
            }
            return true;
        });
        menu.show();
    }

    // ------------------------------------------------------------------ properties

    @Override
    public void applyProperty(CanvasElement element, Runnable mutation, String label) {
        CanvasDocument document = canvasView.document();
        if (document == null || element == null) return;
        CanvasCommand.Snapshot before = CanvasCommand.Snapshot.of(element);
        mutation.run();
        document.markDirty(element);
        document.touch();
        document.history.record(new PropertyCommand(document, element, before, label));
        canvasView.invalidate();
        afterContentChange();
    }

    @Override
    public void selectElement(String id) {
        canvasView.selection().select(id);
        canvasView.invalidate();
        updateContextToolbar();
    }

    private void applyBackground(BackgroundStyle style) {
        CanvasDocument document = canvasView.document();
        if (document == null) return;
        BackgroundStyle before = document.background.copy();
        document.background = style;
        document.markBackgroundDirty();
        document.touch();
        document.history.record(new com.archive.app.canvas.commands.BackgroundCommand(document, before, style));
        canvasView.invalidate();
        afterContentChange();
    }

    // ------------------------------------------------------------------ menu actions

    private boolean onMenuItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_undo) {
            if (!canvasView.history().canUndo()) UiUtils.snack(canvasView, getString(R.string.nothing_to_undo));
            else canvasView.undo();
            afterContentChange();
            return true;
        }
        if (id == R.id.action_redo) {
            if (!canvasView.history().canRedo()) UiUtils.snack(canvasView, getString(R.string.nothing_to_redo));
            else canvasView.redo();
            afterContentChange();
            return true;
        }
        if (id == R.id.action_layers) {
            CanvasSheets.showLayersSheet(this, canvasView.document(), canvasView.selection(), () -> {
                canvasView.invalidate();
                afterContentChange();
                updateContextToolbar();
            });
            return true;
        }
        if (id == R.id.action_background) {
            CanvasSheets.showBackgroundSheet(this, canvasView.document(), this::applyBackground,
                    () -> pickBackground.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build()));
            return true;
        }
        if (id == R.id.action_export) {
            ElementDialogs.exportSettings(this, canvasView.document(), this::exportPage);
            return true;
        }
        if (id == R.id.action_share) {
            shareCurrentPage();
            return true;
        }
        if (id == R.id.action_page_setup) {
            ElementDialogs.pageSetup(this, canvasView.document(), this::renamePage, this::changeFormat,
                    this::clearPage, this::editTags);
            return true;
        }
        if (id == R.id.action_favourite_page) {
            CanvasDocument document = canvasView.document();
            document.favourite = !document.favourite;
            document.touch();
            viewModel.setTitle(pageId, document.title);
            UiUtils.snack(canvasView, document.favourite ? getString(R.string.favourite)
                    : getString(R.string.unfavourite));
            return true;
        }
        return false;
    }

    private void renamePage(String title) {
        CanvasDocument document = canvasView.document();
        if (document == null) return;
        document.title = title.isEmpty() ? getString(R.string.untitled_page) : title;
        toolbar.setTitle(document.title);
        viewModel.setTitle(pageId, document.title);
    }

    private void changeFormat(String formatKey) {
        CanvasDocument document = canvasView.document();
        if (document == null) return;
        document.formatKey = PageFormat.normalise(formatKey);
        document.pageWidth = PageFormat.widthOf(document.formatKey);
        document.pageHeight = PageFormat.heightOf(document.formatKey);
        document.touch();
        canvasView.viewport().setDocument(document.pageWidth, document.pageHeight,
                !PageFormat.MODE_INFINITE.equals(document.mode));
        canvasView.requestFit();
        viewModel.saveNow();
        viewModel.setTitle(pageId, document.title);
        afterContentChange();
    }

    private void clearPage() {
        CanvasDocument document = canvasView.document();
        if (document == null) return;
        List<CanvasElement> all = new ArrayList<>(document.elements());
        document.history.execute(new ElementRemoveCommand(document, all));
        canvasView.selection().clear();
        canvasView.invalidate();
        afterContentChange();
    }

    private void editTags() {
        app.repo().pageTags(pageId, tags -> {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) builder.append(", ");
                builder.append(tags.get(i).name);
            }
            UiUtils.textInputDialog(this, getString(R.string.tags_label), builder.toString(),
                    getString(R.string.tags_hint), value -> {
                        List<String> names = new ArrayList<>();
                        for (String part : value.split(",")) names.add(part.trim());
                        app.repo().setPageTags(pageId, names,
                                () -> UiUtils.snack(canvasView, getString(R.string.tags_saved)));
                    });
        });
    }

    private void exportPage(PageExporter.Settings exportSettings) {
        final CanvasDocument document = canvasView.document();
        if (document == null) return;
        final com.archive.app.canvas.engine.CanvasRenderer.Palette palette = canvasView.palette();
        final float density = getResources().getDisplayMetrics().density;
        viewModel.saveNow();
        UiUtils.snack(canvasView, getString(R.string.export_working));
        app.executor().execute(() -> {
            try {
                File file = PageExporter.exportPage(app.files(), document, palette, exportSettings, density);
                runOnUiThread(() -> showExported(file));
            } catch (Exception e) {
                runOnUiThread(() -> UiUtils.snack(canvasView,
                        getString(R.string.export_failed, String.valueOf(e.getMessage()))));
            }
        });
    }

    private void showExported(File file) {
        com.google.android.material.snackbar.Snackbar.make(canvasView,
                getString(R.string.export_done, file.getName()),
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction(R.string.share, v -> shareFile(file))
                .show();
    }

    private void shareCurrentPage() {
        final CanvasDocument document = canvasView.document();
        if (document == null) return;
        final com.archive.app.canvas.engine.CanvasRenderer.Palette palette = canvasView.palette();
        final float density = getResources().getDisplayMetrics().density;
        PageExporter.Settings exportSettings = new PageExporter.Settings();
        exportSettings.format = PageExporter.Format.PNG;
        exportSettings.scale = 2f;
        exportSettings.fileName = document.title;
        UiUtils.snack(canvasView, getString(R.string.export_working));
        app.executor().execute(() -> {
            try {
                File file = PageExporter.exportPage(app.files(), document, palette, exportSettings, density);
                runOnUiThread(() -> shareFile(file));
            } catch (Exception e) {
                runOnUiThread(() -> UiUtils.snack(canvasView,
                        getString(R.string.export_failed, String.valueOf(e.getMessage()))));
            }
        });
    }

    private void shareFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(file.getName().endsWith(".pdf") ? "application/pdf" : "image/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_via)));
        } catch (Exception e) {
            UiUtils.toast(this, getString(R.string.no_share_app));
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onPause() {
        super.onPause();
        CanvasDocument document = canvasView.document();
        if (document != null) {
            viewModel.saveNow();
            Bitmap thumbnail = canvasView.renderToBitmap(0.25f, true);
            if (thumbnail != null) {
                viewModel.saveThumbnail(pageId, thumbnail);
                // The thumbnail writer copies before recycling on its own executor thread.
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.saveNow();
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    private void handleExit() {
        viewModel.saveNow();
        CanvasDocument document = canvasView.document();
        if (document != null) {
            Bitmap thumbnail = canvasView.renderToBitmap(0.25f, true);
            if (thumbnail != null) viewModel.saveThumbnail(pageId, thumbnail);
        }
        finish();
    }

    /** Tiny helper so the document MIME type can be read without another import. */
    private static final class ArchiveMime {
        static String of(android.content.Context context, Uri uri) {
            ContentResolver resolver = context.getContentResolver();
            String mime = resolver.getType(uri);
            return mime == null ? "application/octet-stream" : mime;
        }
    }
}
