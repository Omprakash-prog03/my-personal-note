package com.archive.app.domain.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Salted PBKDF2 password hashing. Only the derived key and salt are ever persisted — the plain
 * password never touches SharedPreferences, the database, a log line or a crash report.
 *
 * <p>Iteration count is stored with each account so it can be raised later without invalidating
 * existing credentials.</p>
 */
public final class PasswordHasher {

    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int DEFAULT_ITERATIONS = 120_000;
    public static final int KEY_LENGTH_BITS = 256;
    public static final int SALT_BYTES = 16;
    public static final int MIN_PASSWORD_LENGTH = 6;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    public static byte[] newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return salt;
    }

    public static byte[] derive(String password, byte[] salt, int iterations) {
        if (password == null || salt == null || salt.length == 0) {
            throw new IllegalArgumentException("password and salt are required");
        }
        int rounds = iterations < 1 ? DEFAULT_ITERATIONS : iterations;
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, rounds, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            // PBKDF2WithHmacSHA256 is guaranteed from API 26; treat absence as a hard failure
            // rather than silently falling back to a weaker hash.
            throw new IllegalStateException("Password hashing unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Compares in constant time to avoid leaking how much of the hash matched. */
    public static boolean matches(String password, String saltHex, int iterations, String hashHex) {
        byte[] salt = fromHex(saltHex);
        byte[] expected = fromHex(hashHex);
        if (password == null || salt == null || salt.length == 0
                || expected == null || expected.length == 0) {
            return false;
        }
        try {
            return MessageDigest.isEqual(derive(password, salt, iterations), expected);
        } catch (RuntimeException e) {
            // Corrupt or unsupported stored credential: fail closed rather than crash the gate.
            return false;
        }
    }

    public static boolean isStrongEnough(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }

    public static String toHex(byte[] data) {
        if (data == null) return "";
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int value = data[i] & 0xFF;
            out[i * 2] = digits[value >>> 4];
            out[i * 2 + 1] = digits[value & 0x0F];
        }
        return new String(out);
    }

    public static byte[] fromHex(String hex) {
        if (hex == null || (hex.length() & 1) == 1) return null;
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) return null;
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }
}
