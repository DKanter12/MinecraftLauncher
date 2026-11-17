package org.example.launcher.infrastructure.server.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Терпеливый разбор build.json: типичные ошибки ручного редактирования
 * (висячая запятая, пропущенная запятая, BOM, комментарии) чинятся до
 * строгого парсинга Gson.
 */
class LenientJsonTest {

    private final Gson gson = new Gson();

    private JsonObject parse(String json) {
        return gson.fromJson(LenientJson.sanitize(json), JsonObject.class);
    }

    @Test
    @DisplayName("корректный JSON проходит без изменений")
    void validJsonUntouched() {
        String json = "{\"summary\": {\"id\": \"a\", \"version\": \"1.0.0\"}}";
        assertEquals(json, LenientJson.sanitize(json));
    }

    @Test
    @DisplayName("висячая запятая перед скобкой убирается")
    void trailingCommaRemoved() {
        JsonObject root = parse("""
                {
                  "summary": {
                    "id": "a",
                    "version": "1.0.0",
                    "loaderType": "FORGE",
                  }
                }
                """);
        assertNotNull(root);
        assertEquals("FORGE", root.getAsJsonObject("summary")
                .get("loaderType").getAsString());
    }

    @Test
    @DisplayName("пропущенная запятая между полями вставляется")
    void missingCommaInserted() {
        JsonObject root = parse("""
                {
                  "summary": {
                    "id": "a",
                    "description": "Самая крутая сборка"
                    "loaderType": "FORGE",
                    "minecraftVersion": "1.20.1"
                  }
                }
                """);
        assertNotNull(root);
        assertEquals("FORGE", root.getAsJsonObject("summary")
                .get("loaderType").getAsString());
        assertEquals("Самая крутая сборка", root.getAsJsonObject("summary")
                .get("description").getAsString());
    }

    @Test
    @DisplayName("BOM и комментарии убираются")
    void bomAndCommentsRemoved() {
        String json = "\uFEFF// комментарий\r\n/* блок\r\nкомментарий */\r\n"
                + "{\"summary\": {\"id\": \"a\"}}";
        JsonObject root = parse(json);
        assertNotNull(root);
        assertEquals("a", root.getAsJsonObject("summary").get("id")
                .getAsString());
    }

    @Test
    @DisplayName("запятая внутри строкового значения не трогается")
    void commasInsideStringsKept() {
        JsonObject root = parse("{\"summary\": {\"description\": "
                + "\"а, б, в\"}}");
        assertNotNull(root);
        assertEquals("а, б, в", root.getAsJsonObject("summary")
                .get("description").getAsString());
    }

    @Test
    @DisplayName("неисправимый мусор по-прежнему не читается")
    void garbageStillFails() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.google.gson.JsonSyntaxException.class,
                () -> gson.fromJson(LenientJson.sanitize(
                        "{\"summary\": [ unclosed"), JsonObject.class));
    }
}
