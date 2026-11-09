package com.archive.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Ids are the primary keys for pages, elements and media; collisions would corrupt the archive. */
public class IdsTest {

    @Test
    public void idsAreNeverNullOrEmpty() {
        for (int i = 0; i < 100; i++) {
            String id = Ids.newId();
            assertNotNull(id);
            assertFalse(id.isEmpty());
        }
    }

    @Test
    public void idsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            assertTrue("duplicate id generated", seen.add(Ids.newId()));
        }
        assertEquals(2000, seen.size());
    }

    @Test
    public void idsAreCanonicalUuids() {
        String id = Ids.newId();
        // Parsing also guards against a future implementation switching to a non-UUID scheme.
        UUID parsed = UUID.fromString(id);
        assertEquals(id, parsed.toString());
        assertEquals(36, id.length());
        assertEquals(4, id.charAt(14)); // version nibble position
    }
}
