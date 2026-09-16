package fr.tropimon.chatfilter;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JsonConfigStoreTest {
    @TempDir
    Path directory;

    @Test
    void atomicallyReplacesAValidConfiguration() throws Exception {
        Path file = directory.resolve("settings.json");
        JsonObject first = new JsonObject();
        first.addProperty("value", 1);
        JsonConfigStore.write(file, first);

        JsonObject second = new JsonObject();
        second.addProperty("value", 2);
        JsonConfigStore.write(file, second);

        assertEquals(2, JsonConfigStore.read(file).get("value").getAsInt());
        assertFalse(Files.exists(directory.resolve("settings.json.tmp")));
    }
}
