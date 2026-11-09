package com.archive.app.utils;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Records the stack trace of an uncaught exception so a crash that closes the app immediately can
 * still be read on the next launch. Nothing leaves the device: the file is app-private and the user
 * decides whether to copy it.
 */
public final class CrashLog {

    private static final String FILE_NAME = "last_crash.txt";
    /** Keeps the dialog readable on small screens; stacks are dominated by framework frames. */
    private static final int MAX_CHARS = 6000;

    private CrashLog() {}

    /** Must be installed as early as possible: before the database is opened. */
    public static void install(Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable error) {
                try {
                    save(app, thread, error);
                } catch (Throwable ignored) {
                    // The reporter must never mask the crash it is trying to record.
                }
                if (previous != null) previous.uncaughtException(thread, error);
            }
        });
    }

    private static void save(Context context, Thread thread, Throwable error) throws IOException {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);
        writer.println("thread: " + thread.getName());
        writer.println("time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        writer.println();
        error.printStackTrace(writer);
        writer.flush();
        String text = buffer.toString();
        if (text.length() > 20000) text = text.substring(0, 20000);
        try (FileWriter out = new FileWriter(new File(context.getFilesDir(), FILE_NAME), false)) {
            out.write(text);
        }
    }

    /** @return the last recorded crash, or null when the previous run ended normally. */
    public static String read(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) return null;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    public static void clear(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (file.exists() && !file.delete()) {
            // Nothing useful to do: the next launch simply shows the same report again.
        }
    }
}
