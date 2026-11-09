package com.archive.app.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.archive.app.ArchiveApp;
import com.archive.app.data.storage.BackupManager;

import java.io.File;

/** Writes a zip of the database + media + thumbnails and reports the file name back to the UI. */
public class BackupWorker extends Worker {

    public static final String KEY_FILE = "backup_file";
    public static final String KEY_ERROR = "backup_error";

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        ArchiveApp app = (ArchiveApp) getApplicationContext();
        try {
            File backup = BackupManager.createBackup(app, app.database(), app.files());
            return Result.success(new Data.Builder().putString(KEY_FILE, backup.getName()).build());
        } catch (Exception e) {
            return Result.failure(new Data.Builder()
                    .putString(KEY_ERROR, String.valueOf(e.getMessage())).build());
        }
    }
}
