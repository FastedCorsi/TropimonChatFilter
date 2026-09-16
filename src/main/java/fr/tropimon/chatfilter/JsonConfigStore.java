package fr.tropimon.chatfilter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Lecture et écriture atomique des petits fichiers de configuration du mod. */
final class JsonConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonConfigStore() {
    }

    static Path resolve(String fileName) {
        try {
            Path directory = FabricLoader.getInstance().getConfigDir();
            return directory == null ? null : directory.resolve(fileName);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, JsonObject.class);
        }
    }

    static void write(Path file, JsonObject json) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(json, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
