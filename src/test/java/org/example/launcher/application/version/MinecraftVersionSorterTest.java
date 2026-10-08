package org.example.launcher.application.version;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.VersionType;

@DisplayName("MinecraftVersionSorter")
class MinecraftVersionSorterTest {

    private static MinecraftVersion v(String id, String date) {
        return MinecraftVersion.of(id, VersionType.RELEASE, date, null);
    }

    @Test
    @DisplayName("sortByReleaseDate puts newest first, dateless last")
    void byDate() {
        var in = List.of(
                v("1.21.9", "2025-03-01T00:00:00+00:00"),
                v("nodate", null),
                v("1.21.11", "2025-10-07T00:00:00+00:00"),
                v("1.21.10", "2025-06-17T00:00:00+00:00"));

        var out = MinecraftVersionSorter.sortByReleaseDate(in);

        assertEquals(List.of("1.21.11", "1.21.10", "1.21.9", "nodate"),
                out.stream().map(MinecraftVersion::id).toList());
    }

    @Test
    @DisplayName("sortByVersion orders 1.21.11 above 1.21.9")
    void byNumber() {
        var in = List.of(
                v("1.21.9", null),
                v("1.21.11", null),
                v("1.21.10", null),
                v("1.20.1", null));

        var out = MinecraftVersionSorter.sortByVersion(in);

        assertEquals(List.of("1.21.11", "1.21.10", "1.21.9", "1.20.1"),
                out.stream().map(MinecraftVersion::id).toList());
    }

    @Test
    @DisplayName("sortByVersion handles snapshots and old versions")
    void mixed() {
        var in = List.of(
                v("a1.2.6", null),
                v("25w14a", null),
                v("b1.7.3", null),
                v("1.0", null));

        var out = MinecraftVersionSorter.sortByVersion(in);

        assertEquals("25w14a", out.get(0).id());
        // чисто числовое сравнение: [1,0] младше [1,2,6]
        assertEquals("1.0", out.get(out.size() - 1).id());
    }
}
