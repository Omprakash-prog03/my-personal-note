package com.nuzhat.app.domain.security;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure-JVM tests for the password hashing used by the local account. */
public class PasswordHasherTest {

    // Seeded default account: username "om". Only the derived key is stored in the app.
    private static final String SEEDED_SALT = "2b0e465530f37707bbe73baed8ca9982";
    private static final String SEEDED_HASH =
            "c529fbb907801dfc5721320f5f5db184a789647bb8fb7ba6a4b025a1387db43b";
    private static final String SEEDED_PASSWORD = "sahuom123";

    @Test
    public void seededCredentialVerifies() {
        assertTrue(PasswordHasher.matches(SEEDED_PASSWORD, SEEDED_SALT,
                PasswordHasher.DEFAULT_ITERATIONS, SEEDED_HASH));
    }

    @Test
    public void wrongPasswordIsRejected() {
        assertFalse(PasswordHasher.matches("not-the-password", SEEDED_SALT,
                PasswordHasher.DEFAULT_ITERATIONS, SEEDED_HASH));
        assertFalse(PasswordHasher.matches("Sahuom123", SEEDED_SALT,
                PasswordHasher.DEFAULT_ITERATIONS, SEEDED_HASH));
        assertFalse(PasswordHasher.matches("", SEEDED_SALT,
                PasswordHasher.DEFAULT_ITERATIONS, SEEDED_HASH));
    }

    @Test
    public void tamperedSaltIsRejected() {
        assertFalse(PasswordHasher.matches(SEEDED_PASSWORD,
                "2b0e465530f37707bbe73baed8ca9983",
                PasswordHasher.DEFAULT_ITERATIONS, SEEDED_HASH));
    }

    @Test
    public void derivationIsDeterministicAndSalted() {
        byte[] salt = PasswordHasher.fromHex(SEEDED_SALT);
        assertNotNull(salt);
        byte[] first = PasswordHasher.derive(SEEDED_PASSWORD, salt, 1000);
        byte[] second = PasswordHasher.derive(SEEDED_PASSWORD, salt, 1000);
        assertArrayEquals(first, second);
        assertEquals(32, first.length);

        byte[] other = PasswordHasher.derive(SEEDED_PASSWORD,
                PasswordHasher.fromHex("000102030405060708090a0b0c0d0e0f"), 1000);
        assertFalse(java.util.Arrays.equals(first, other));
    }

    @Test
    public void iterationCountChangesTheKey() {
        byte[] salt = PasswordHasher.newSalt();
        byte[] low = PasswordHasher.derive(SEEDED_PASSWORD, salt, 1000);
        byte[] high = PasswordHasher.derive(SEEDED_PASSWORD, salt, 2000);
        assertFalse(java.util.Arrays.equals(low, high));
    }

    @Test
    public void hexRoundTrips() {
        byte[] salt = PasswordHasher.newSalt();
        assertEquals(PasswordHasher.SALT_BYTES, salt.length);
        String hex = PasswordHasher.toHex(salt);
        assertEquals(PasswordHasher.SALT_BYTES * 2, hex.length());
        assertArrayEquals(salt, PasswordHasher.fromHex(hex));
        assertNull(PasswordHasher.fromHex("xyz"));
        assertNull(PasswordHasher.fromHex("abc"));
        assertNull(PasswordHasher.fromHex(null));
    }

    @Test
    public void malformedStoredValuesFailClosed() {
        assertFalse(PasswordHasher.matches(SEEDED_PASSWORD, "", 120000, SEEDED_HASH));
        assertFalse(PasswordHasher.matches(SEEDED_PASSWORD, SEEDED_SALT, 120000, ""));
        assertFalse(PasswordHasher.matches(SEEDED_PASSWORD, "zz", 120000, SEEDED_HASH));
    }

    @Test
    public void passwordStrengthRule() {
        assertFalse(PasswordHasher.isStrongEnough(null));
        assertFalse(PasswordHasher.isStrongEnough("12345"));
        assertTrue(PasswordHasher.isStrongEnough("123456"));
        assertTrue(PasswordHasher.isStrongEnough(SEEDED_PASSWORD));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptySaltIsRejected() {
        PasswordHasher.derive(SEEDED_PASSWORD, new byte[0], 1000);
    }
}
