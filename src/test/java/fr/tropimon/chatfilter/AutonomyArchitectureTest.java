package fr.tropimon.chatfilter;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class AutonomyArchitectureTest {
    @Test void productionSourcesHaveNoForeignTropimonCouplingOrReflection() throws Exception {
        Pattern foreign = Pattern.compile("fr\\.(?:erusel\\.|tropimon\\.(?!chatfilter\\b))");
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(foreign.matcher(source).find(), file.toString());
                assertFalse(source.contains("Class.forName(") || source.contains("java.lang.reflect")
                        || source.contains("getDeclaredField("), file.toString());
            }
        }
    }

    @Test void allMixinsAndEntrypointsHaveSourcesAndOnlyOfficialDependenciesAreRequired() throws Exception {
        var mod = JsonParser.parseString(Files.readString(Path.of("src/main/resources/fabric.mod.json"))).getAsJsonObject();
        assertEquals(Set.of("fabricloader", "minecraft", "fabric-api", "java"), mod.getAsJsonObject("depends").keySet());
        for (var config : mod.getAsJsonArray("mixins")) {
            var json = JsonParser.parseString(Files.readString(Path.of("src/main/resources", config.getAsString()))).getAsJsonObject();
            String base = json.get("package").getAsString().replace('.', '/');
            for (var name : json.getAsJsonArray("client")) {
                assertTrue(Files.exists(Path.of("src/main/java", base, name.getAsString() + ".java")), name.getAsString());
            }
        }
        for (var entry : mod.getAsJsonObject("entrypoints").getAsJsonArray("client")) {
            assertTrue(Files.exists(Path.of("src/main/java", entry.getAsString().replace('.', '/') + ".java")));
        }
    }
}
