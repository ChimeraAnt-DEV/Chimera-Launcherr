package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The zero-setup cloud relay's pure parts: the topic rendezvous rule and the JSON field reader.
 * The live HTTP is exercised separately (an integration check against ntfy.sh).
 */
public class CosmeticSyncCloudTest {

    @Test
    public void twoClientsOnTheSameWorldMeetOnTheSameTopic() {
        assertEquals(CosmeticSyncCloudRelay.topicForWorld("My Realm"),
                CosmeticSyncCloudRelay.topicForWorld("my realm"));
        // Case and separators are normalised, so "My Realm" and "my_realm" rendezvous.
        assertEquals(CosmeticSyncCloudRelay.topicForWorld("my realm"),
                CosmeticSyncCloudRelay.topicForWorld("my_realm"));
    }

    @Test
    public void differentWorldsDoNotCollide() {
        assertFalse(CosmeticSyncCloudRelay.topicForWorld("world-a")
                .equals(CosmeticSyncCloudRelay.topicForWorld("world-b")));
    }

    @Test
    public void theTopicIsPrefixedAndSanitised() {
        String topic = CosmeticSyncCloudRelay.topicForWorld("The Hive!! / server#1");
        assertTrue("must be prefixed", topic.startsWith("glowberry-cosmetics-"));
        for (char c : topic.toCharArray()) {
            assertTrue("only ntfy-safe characters: " + c,
                    (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-');
        }
    }

    @Test
    public void anEmptyWorldYieldsTheSharedDefaultTopic() {
        assertEquals("glowberry-cosmetics-world", CosmeticSyncCloudRelay.topicForWorld(""));
        assertEquals("glowberry-cosmetics-world", CosmeticSyncCloudRelay.topicForWorld(null));
    }

    @Test
    public void theJsonReaderExtractsTheMessageField() {
        String line = "{\"id\":\"abc\",\"time\":1791367672,\"event\":\"message\","
                + "\"topic\":\"t\",\"message\":\"SGVsbG8=\"}";
        assertEquals("SGVsbG8=", CosmeticSyncCloudRelay.jsonStringField(line, "message"));
        assertEquals("abc", CosmeticSyncCloudRelay.jsonStringField(line, "id"));
        assertEquals(null, CosmeticSyncCloudRelay.jsonStringField(line, "missing"));
    }

    @Test
    public void theJsonReaderHandlesEscapes() {
        String line = "{\"message\":\"a\\\"b\\\\c\"}";
        assertEquals("a\"b\\c", CosmeticSyncCloudRelay.jsonStringField(line, "message"));
    }

    @Test
    public void theConfigReportsTheCloudRouteWhenATopicIsSet() {
        CosmeticSyncConfig without = CosmeticSyncConfig.resolve(true, false, "", "", "world", "");
        assertFalse(without.hasCloudTopic());
        assertEquals("lan", without.routeLabel());

        CosmeticSyncConfig with = without.withCloudTopic("glowberry-cosmetics-world");
        assertTrue(with.hasCloudTopic());
        assertEquals("cloud", with.routeLabel());
    }
}
