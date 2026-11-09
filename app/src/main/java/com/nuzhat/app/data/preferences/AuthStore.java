package com.nuzhat.app.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import com.nuzhat.app.domain.security.PasswordHasher;

/**
 * Local-only account used to gate the archive. Credentials are seeded on first launch as a salted
 * PBKDF2 hash (never plaintext) and can be changed from Settings.
 *
 * <p>"Keep me signed in" persists a session flag; without it the session lives in memory only and
 * closing the app requires signing in again.</p>
 */
public class AuthStore {

    /** Default account, seeded on first launch when no credentials exist. */
    private static final String DEFAULT_USERNAME = "om";
    private static final String DEFAULT_SALT_HEX = "2b0e465530f37707bbe73baed8ca9982";
    private static final String DEFAULT_HASH_HEX =
            "c529fbb907801dfc5721320f5f5db184a789647bb8fb7ba6a4b025a1387db43b";

    private static final String PREFS = "nuzhat_auth";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_SALT = "salt";
    private static final String KEY_HASH = "hash";
    private static final String KEY_ITERATIONS = "iterations";
    private static final String KEY_KEEP = "keep_signed_in";
    private static final String KEY_PERSISTED_SESSION = "signed_in";

    /** Session flag that dies with the process (used when "keep me signed in" is off). */
    private static volatile boolean memorySignedIn;

    private final SharedPreferences prefs;

    public AuthStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        seedIfEmpty();
    }

    private void seedIfEmpty() {
        if (prefs.contains(KEY_HASH)) return;
        prefs.edit()
                .putString(KEY_USERNAME, DEFAULT_USERNAME)
                .putString(KEY_SALT, DEFAULT_SALT_HEX)
                .putString(KEY_HASH, DEFAULT_HASH_HEX)
                .putInt(KEY_ITERATIONS, PasswordHasher.DEFAULT_ITERATIONS)
                .apply();
    }

    public String username() {
        return prefs.getString(KEY_USERNAME, DEFAULT_USERNAME);
    }

    public boolean keepSignedIn() {
        return prefs.getBoolean(KEY_KEEP, false);
    }

    /** Case-insensitive on the username, constant-time on the password. */
    public boolean verify(String username, String password) {
        if (username == null || password == null) return false;
        if (!username.trim().equalsIgnoreCase(username())) return false;
        return PasswordHasher.matches(password,
                prefs.getString(KEY_SALT, ""),
                prefs.getInt(KEY_ITERATIONS, PasswordHasher.DEFAULT_ITERATIONS),
                prefs.getString(KEY_HASH, ""));
    }

    public void signIn(boolean keepSignedIn) {
        memorySignedIn = true;
        prefs.edit()
                .putBoolean(KEY_KEEP, keepSignedIn)
                .putBoolean(KEY_PERSISTED_SESSION, keepSignedIn)
                .apply();
    }

    public void signOut() {
        memorySignedIn = false;
        prefs.edit().putBoolean(KEY_PERSISTED_SESSION, false).apply();
    }

    public boolean isSignedIn() {
        return memorySignedIn || (prefs.getBoolean(KEY_KEEP, false)
                && prefs.getBoolean(KEY_PERSISTED_SESSION, false));
    }

    /**
     * Replaces the password after verifying the current one.
     *
     * @return false when the current password is wrong or the new one is too short/unchanged.
     */
    public boolean changePassword(String currentPassword, String newPassword) {
        if (!verify(username(), currentPassword)) return false;
        if (!PasswordHasher.isStrongEnough(newPassword)) return false;
        if (currentPassword.equals(newPassword)) return false;
        byte[] salt = PasswordHasher.newSalt();
        byte[] hash = PasswordHasher.derive(newPassword, salt, PasswordHasher.DEFAULT_ITERATIONS);
        prefs.edit()
                .putString(KEY_SALT, PasswordHasher.toHex(salt))
                .putString(KEY_HASH, PasswordHasher.toHex(hash))
                .putInt(KEY_ITERATIONS, PasswordHasher.DEFAULT_ITERATIONS)
                .apply();
        return true;
    }
}
