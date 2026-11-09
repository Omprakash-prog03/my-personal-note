package com.archive.app.utils;

import java.util.UUID;

/** Single source of identifiers. UUID strings survive backup/restore and avoid autoincrement collisions. */
public final class Ids {
    private Ids() {}

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
