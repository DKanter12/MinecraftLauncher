package org.example.launcher.service.modloader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MetaProfileModLoaderInstaller helper")
class MetaProfileModLoaderInstallerTest {

    @Test
    @DisplayName("withInheritsFrom: marker is added when absent")
    void addsInheritsFrom() {
        String json = """
                { "id": "fabric-loader-0.16.9-1.21.4",
                  "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient" }
                """;

        String result = MetaProfileModLoaderInstaller.withInheritsFrom(json, "1.21.4");

        var root = JsonParser.parseString(result).getAsJsonObject();
        assertTrue(root.has("inheritsFrom"));
        assertTrue(root.get("inheritsFrom").getAsString().equals("1.21.4"));
        // original fields preserved
        assertTrue(root.get("id").getAsString().equals("fabric-loader-0.16.9-1.21.4"));
    }

    @Test
    @DisplayName("withInheritsFrom: existing marker is kept")
    void keepsExistingInheritsFrom() {
        String json = """
                { "id": "1.20.1-forge-47.4.10", "inheritsFrom": "1.20.1" }
                """;

        String result = MetaProfileModLoaderInstaller.withInheritsFrom(json, "1.20.1");

        var root = JsonParser.parseString(result).getAsJsonObject();
        assertTrue(root.get("inheritsFrom").getAsString().equals("1.20.1"));
        // not duplicated
        assertTrue(result.split("inheritsFrom", -1).length == 2);
    }
}
