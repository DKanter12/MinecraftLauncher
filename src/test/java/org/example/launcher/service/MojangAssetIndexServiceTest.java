package org.example.launcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.example.launcher.model.AssetIndexContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MojangAssetIndexService parsing")
class MojangAssetIndexServiceTest {

    private final MojangAssetIndexService service = new MojangAssetIndexService();

    private static final String SAMPLE_INDEX = """
            {
              "objects": {
                "minecraft/sounds/music/menu1.ogg": {
                  "hash": "abc123def456789012345678901234567890abcd",
                  "size": 1234567
                },
                "minecraft/textures/blocks/stone.png": {
                  "hash": "def456abc789012345678901234567890123abcd",
                  "size": 4096
                },
                "icons/icon_16x16.png": {
                  "hash": "0000111122223333444455556666777788889999",
                  "size": 512
                }
              },
              "virtual": false
            }
            """;

    @Test
    @DisplayName("parses correct number of asset objects")
    void parsesObjectCount() throws IOException {
        AssetIndexContent content = service.parseIndex(SAMPLE_INDEX);
        assertEquals(3, content.size());
    }

    @Test
    @DisplayName("parses asset hash and size")
    void parsesHashAndSize() throws IOException {
        AssetIndexContent content = service.parseIndex(SAMPLE_INDEX);
        var obj = content.objects().get("minecraft/sounds/music/menu1.ogg");
        assertEquals("abc123def456789012345678901234567890abcd", obj.hash());
        assertEquals(1234567L, obj.size());
    }

    @Test
    @DisplayName("parses hash prefix (first 2 chars)")
    void parsesHashPrefix() throws IOException {
        AssetIndexContent content = service.parseIndex(SAMPLE_INDEX);
        var obj = content.objects().get("minecraft/textures/blocks/stone.png");
        assertEquals("de", obj.hashPrefix());
    }

    @Test
    @DisplayName("parses virtual flag")
    void parsesVirtualFlag() throws IOException {
        AssetIndexContent content = service.parseIndex(SAMPLE_INDEX);
        assertFalse(content.isVirtual());
    }

    @Test
    @DisplayName("parses virtual=true correctly")
    void parsesVirtualTrue() throws IOException {
        String json = """
                { "objects": { "a": { "hash": "abcdef", "size": 1 } }, "virtual": true }
                """;
        AssetIndexContent content = service.parseIndex(json);
        assertTrue(content.isVirtual());
    }

    @Test
    @DisplayName("handles empty objects map")
    void handlesEmptyObjects() throws IOException {
        AssetIndexContent content = service.parseIndex("{ \"objects\": {} }");
        assertEquals(0, content.size());
    }

    @Test
    @DisplayName("handles missing objects field")
    void handlesMissingObjects() throws IOException {
        AssetIndexContent content = service.parseIndex("{ \"virtual\": true }");
        assertEquals(0, content.size());
    }

    @Test
    @DisplayName("throws on invalid JSON")
    void throwsOnInvalidJson() {
        assertThrows(IOException.class, () -> service.parseIndex("{ broken"));
    }

    @Test
    @DisplayName("throws on empty input")
    void throwsOnEmptyInput() {
        assertThrows(IOException.class, () -> service.parseIndex(""));
    }
}
