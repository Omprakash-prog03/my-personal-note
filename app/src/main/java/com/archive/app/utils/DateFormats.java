package com.archive.app.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class DateFormats {
    private DateFormats() {}

    public static final long DAY_MS = 24L * 60L * 60L * 1000L;

    public static String longDate(long millis) {
        return new SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(new Date(millis));
    }

    public static String shortDate(long millis) {
        return new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(millis));
    }

    public static String dayAndMonth(long millis) {
        return new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date(millis));
    }

    public static String time(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    /** Human relative label used in lists ("Today", "Yesterday", then a date). */
    public static String relative(long millis) {
        long now = System.currentTimeMillis();
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        a.setTimeInMillis(now);
        b.setTimeInMillis(millis);
        int sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR) ? 0 : 1;
        if (sameDay == 0) {
            return "Today, " + time(millis);
        }
        a.add(Calendar.DAY_OF_YEAR, -1);
        if (a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)) {
            return "Yesterday";
        }
        return shortDate(millis);
    }

    public static String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 5) return "night";
        if (hour < 12) return "morning";
        if (hour < 17) return "afternoon";
        return "evening";
    }
}
