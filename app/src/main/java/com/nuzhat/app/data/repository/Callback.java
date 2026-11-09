package com.nuzhat.app.data.repository;

/** Minimal result callback delivered on the main thread. */
public interface Callback<T> {
    void onResult(T value);
}
