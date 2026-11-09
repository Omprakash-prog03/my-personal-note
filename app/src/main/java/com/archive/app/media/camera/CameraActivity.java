package com.archive.app.media.camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;

import com.archive.app.R;
import com.archive.app.data.entity.MediaEntity;
import com.archive.app.media.importer.MediaImporter;
import com.archive.app.ui.common.BaseActivity;
import com.archive.app.utils.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * CameraX capture screen. The photo is written to app cache first, then imported into permanent
 * storage only when the user taps "Use photo", so retakes never leave orphan files behind.
 */
public class CameraActivity extends BaseActivity {

    public static final String EXTRA_MEDIA_ID = "extra_media_id";
    public static final String EXTRA_RELATIVE_PATH = "extra_relative_path";

    private PreviewView previewView;
    private ImageView reviewImage;
    private View controls;
    private View reviewControls;
    private ProgressBar captureProgress;
    private ImageButton flashButton;
    private ImageButton flipButton;

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private boolean flashOn = false;
    private File capturedFile;

    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        previewView = findViewById(R.id.preview);
        reviewImage = findViewById(R.id.review_image);
        controls = findViewById(R.id.controls);
        reviewControls = findViewById(R.id.review_controls);
        captureProgress = findViewById(R.id.capture_progress);
        flashButton = findViewById(R.id.flash_button);
        flipButton = findViewById(R.id.flip_button);

        cameraExecutor = app.executor();

        findViewById(R.id.close_camera).setOnClickListener(v -> finish());
        findViewById(R.id.capture_button).setOnClickListener(v -> capture());
        findViewById(R.id.retake_button).setOnClickListener(v -> retake());
        findViewById(R.id.use_button).setOnClickListener(v -> usePhoto());
        flashButton.setOnClickListener(v -> {
            flashOn = !flashOn;
            if (imageCapture != null) {
                imageCapture.setFlashMode(flashOn ? ImageCapture.FLASH_MODE_ON
                        : ImageCapture.FLASH_MODE_OFF);
            }
            flashButton.setImageResource(flashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        });
        flipButton.setOnClickListener(v -> {
            lensFacing = lensFacing == CameraSelector.LENS_FACING_BACK
                    ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
            startCamera();
        });

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startCamera();
                    } else {
                        UiUtils.toast(this, getString(R.string.camera_permission_needed));
                        finish();
                    }
                });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(flashOn ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF)
                        .build();
                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing).build();
                if (!provider.hasCamera(selector)) {
                    UiUtils.toast(this, getString(R.string.camera_unavailable));
                    finish();
                    return;
                }
                provider.unbindAll();
                provider.bindToLifecycle(this, selector, preview, imageCapture);
            } catch (Exception e) {
                UiUtils.toast(this, getString(R.string.camera_unavailable));
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void capture() {
        if (imageCapture == null) return;
        captureProgress.setVisibility(View.VISIBLE);
        findViewById(R.id.capture_button).setEnabled(false);
        File directory = new File(getCacheDir(), "captures");
        if (!directory.exists() && !directory.mkdirs()) {
            captureProgress.setVisibility(View.GONE);
            UiUtils.toast(this, getString(R.string.capture_failed));
            return;
        }
        File file = new File(directory, "capture-" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(file).build();
        imageCapture.takePicture(options, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        runOnUiThread(() -> showReview(file));
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        runOnUiThread(() -> {
                            captureProgress.setVisibility(View.GONE);
                            findViewById(R.id.capture_button).setEnabled(true);
                            UiUtils.toast(CameraActivity.this, getString(R.string.capture_failed));
                        });
                    }
                });
    }

    private void showReview(File file) {
        capturedFile = file;
        captureProgress.setVisibility(View.GONE);
        findViewById(R.id.capture_button).setEnabled(true);
        cameraExecutor.execute(() -> {
            Bitmap bitmap = decodeOriented(file);
            runOnUiThread(() -> {
                if (bitmap != null) {
                    reviewImage.setImageBitmap(bitmap);
                    reviewImage.setVisibility(View.VISIBLE);
                    controls.setVisibility(View.GONE);
                    reviewControls.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private Bitmap decodeOriented(File file) {
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) return null;
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            int degrees = 0;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
            if (degrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(degrees);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                        bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) bitmap.recycle();
                return rotated;
            }
        } catch (IOException ignored) {
            // Orientation metadata is best-effort; a sideways preview is better than a crash.
        }
        return bitmap;
    }

    private void retake() {
        reviewImage.setVisibility(View.GONE);
        reviewImage.setImageDrawable(null);
        controls.setVisibility(View.VISIBLE);
        reviewControls.setVisibility(View.GONE);
        if (capturedFile != null && capturedFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            capturedFile.delete();
        }
        capturedFile = null;
    }

    private void usePhoto() {
        if (capturedFile == null) return;
        captureProgress.setVisibility(View.VISIBLE);
        final File file = capturedFile;
        cameraExecutor.execute(() -> {
            try {
                MediaEntity media = MediaImporter.importCameraPhoto(app.repo(), file);
                runOnUiThread(() -> {
                    captureProgress.setVisibility(View.GONE);
                    Intent data = new Intent();
                    data.putExtra(EXTRA_MEDIA_ID, media.id);
                    data.putExtra(EXTRA_RELATIVE_PATH, media.relativePath);
                    setResult(RESULT_OK, data);
                    finish();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    captureProgress.setVisibility(View.GONE);
                    UiUtils.toast(this, getString(R.string.import_failed, String.valueOf(e.getMessage())));
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && capturedFile != null && capturedFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            capturedFile.delete();
        }
    }
}
