package fr.tropimon.chatfilter.privacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class PrivacyCheckTest {
    @TempDir Path temporary;
    private static final String METADATA = "{\"id\":\"tropimon_chat_filter\",\"authors\":[\"By FastedCorsi\"]}";

    @Test void detectsPersonalPathsIncludingEscapedWindowsAndUnixForms() throws Exception {
        for (String path : List.of("C:" + "\\Users\\fictional-account\\project",
                "D:" + "\\\\Users\\\\fictional-account\\\\project",
                "/" + "home/fictional-account/project", "/" + "Users/fictional-account/project")) {
            PrivacyCheck check = new PrivacyCheck(Set.of());
            check.inspect("README.md", bytes(path), 0);
            assertCategory(check, "personal-system-path");
        }
    }

    @Test void detectsPrivateTermsInTextAndDoesNotLeakValuesInReports() throws Exception {
        PrivacyCheck check = new PrivacyCheck(Set.of("Fictional Person", "FictionalEmployer"));
        check.inspect("notes/Fictional Person.txt", bytes("Author: Fictional Person; FictionalEmployer"), 0);
        assertCategory(check, "local-private-identity");
        assertFalse(check.findings().toString().contains("Fictional Person"));
        assertFalse(check.findings().toString().contains("FictionalEmployer"));
    }

    @Test void inspectsCompiledConstantPoolAndUnicodeEscapes() throws Exception {
        PrivacyCheck check = new PrivacyCheck(Set.of("FictionalPerson"));
        check.inspect("Example.class", constantClass("FictionalPerson"), 0);
        assertCategory(check, "local-private-identity");
        check = new PrivacyCheck(Set.of("FictionalPerson"));
        check.inspect("example.json", bytes("Fictional" + "\\u0050" + "erson"), 0);
        assertCategory(check, "local-private-identity");
    }

    @Test void detectsSyntheticSecretsWithoutRealCredentials() throws Exception {
        for (String value : List.of("ghp_" + "A".repeat(36), "sk-proj-" + "B".repeat(40),
                "-----BEGIN " + "PRIVATE KEY-----", "https://" + "someone:synthetic-password@host.invalid",
                "password" + " = \"synthetic-value-for-test\"")) {
            PrivacyCheck check = new PrivacyCheck(Set.of());
            check.inspect("example.txt", bytes(value), 0);
            assertFalse(check.findings().isEmpty());
            assertTrue(check.findings().stream().anyMatch(f -> f.category().contains("secret") || f.category().contains("credential")));
            assertFalse(check.findings().toString().contains(value));
        }
    }

    @Test void acceptsPublicUrlsPseudonymsPortablePathsAndFictionalEmails() throws Exception {
        PrivacyCheck check = new PrivacyCheck(Set.of("FictionalPerson"));
        check.inspect("README.md", bytes("By FastedCorsi; /msg Alex; https://api.mojang.com; "
                + "https://github.com/airlift/aircompressor; ${JAVA_HOME}; build/libs; "
                + "tester@example.invalid; 123+Example@users.noreply.github.com"), 0);
        assertTrue(check.findings().isEmpty(), check.findings().toString());
    }

    @Test void flagsUnreviewedEmailWithoutPrintingIt() throws Exception {
        PrivacyCheck check = new PrivacyCheck(Set.of());
        String synthetic = "fictional.contact" + "@" + "fictional-domain.org";
        check.inspect("example.txt", bytes(synthetic), 0);
        assertCategory(check, "email-needs-review");
        assertFalse(check.findings().toString().contains(synthetic));
    }

    @Test void preservesReviewedPublicGitSshReferencesWithoutIgnoringWholeDependencyFiles() throws Exception {
        PrivacyCheck check = new PrivacyCheck(Set.of("FictionalPerson"));
        check.inspect("pom.xml", bytes("scm:git:git@github.com:airlift/aircompressor.git"), 0);
        assertTrue(check.findings().isEmpty(), check.findings().toString());
        check.inspect("pom.xml", bytes("scm:git:git@github.com:airlift/aircompressor.git FictionalPerson"), 0);
        assertCategory(check, "local-private-identity");
        check = new PrivacyCheck(Set.of());
        check.inspect("contact.txt", bytes("git" + "@" + "github.com"), 0);
        assertCategory(check, "email-needs-review");
    }

    @Test void checksNestedArchivesResourcesAndCompressedContent() throws Exception {
        PrivacyCheck check = new PrivacyCheck(Set.of("FictionalPerson"));
        byte[] nested = archive(Map.of("Example.class", constantClass("FictionalPerson")));
        check.inspect("mod.jar", archive(Map.of("fabric.mod.json", bytes(METADATA), "META-INF/jars/library.jar", nested)), 0);
        assertCategory(check, "local-private-identity");
        check = new PrivacyCheck(Set.of("FictionalPerson"));
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compressed)) { gzip.write(bytes("FictionalPerson")); }
        check.inspect("data.json.gz", compressed.toByteArray(), 0);
        assertCategory(check, "local-private-identity");
    }

    @Test void enforcesPublicAuthorAndKeepsThirdPartyCredits() throws Exception {
        PrivacyCheck check = new PrivacyCheck(Set.of());
        check.inspect("mod.jar", archive(Map.of("fabric.mod.json", bytes(METADATA),
                "META-INF/NOTICE.txt", bytes("Airlift contributors; Apache License 2.0"))), 0);
        assertTrue(check.findings().isEmpty(), check.findings().toString());
        check.inspect("fabric.mod.json", bytes("{\"authors\":[\"Fictional Author\"]}"), 0);
        assertCategory(check, "invalid-public-author");
        check = new PrivacyCheck(Set.of());
        check.inspect("fabric.mod.json", bytes("{\"authors\":[\"By FastedCorsi\"],\"authors\":[\"Fictional Author\"]}"), 0);
        assertCategory(check, "invalid-public-author");
    }

    @Test void rejectsPrivateFilesAndTraversalInsideArchives() throws Exception {
        for (String entry : List.of("config/session.json", "logs/latest.log", ".env", ".git/config",
                "backups/mod.bak", "screenshots/capture.png")) {
            PrivacyCheck check = new PrivacyCheck(Set.of());
            check.inspect("mod.jar", archive(Map.of("fabric.mod.json", bytes(METADATA), entry, bytes("{}"))), 0);
            assertCategory(check, "private-file-in-archive");
        }
        PrivacyCheck check = new PrivacyCheck(Set.of());
        check.inspect("mod.jar", archive(Map.of("fabric.mod.json", bytes(METADATA), "../outside.txt", bytes(""))), 0);
        assertCategory(check, "unsafe-archive-path");
    }

    @Test void leavesLocalOriginalsUntouchedButRejectsPrivateFilesInResources() throws Exception {
        Path resource = temporary.resolve("src/main/resources");
        Files.createDirectories(resource);
        Files.writeString(resource.resolve("fabric.mod.json"), METADATA);
        Path local = temporary.resolve("releases/old.jar");
        Files.createDirectories(local.getParent());
        Files.writeString(local, "FictionalPerson");
        PrivacyCheck check = new PrivacyCheck(Set.of("FictionalPerson"));
        check.sources(temporary);
        assertTrue(check.findings().isEmpty(), check.findings().toString());
        assertEquals("FictionalPerson", Files.readString(local));
        Files.createDirectories(resource.resolve("config"));
        Files.writeString(resource.resolve("config/session.json"), "{}");
        check.sources(temporary);
        assertCategory(check, "private-file-in-distribution");
    }

    @Test void failsClosedOnMalformedClassesAndExcessiveArchiveDepth() throws Exception {
        PrivacyCheck check = new PrivacyCheck(Set.of());
        assertThrows(IOException.class, () -> check.inspect("bad.class", bytes("not a class"), 0));
        byte[] nested = archive(Map.of("note.txt", bytes("ok")));
        for (int i = 0; i < 6; i++) nested = archive(Map.of("nested.jar", nested));
        byte[] input = nested;
        assertThrows(IOException.class, () -> check.inspect("mod.jar", input, 0));
    }

    private static void assertCategory(PrivacyCheck check, String category) {
        assertTrue(check.findings().stream().anyMatch(f -> f.category().equals(category)), check.findings().toString());
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static byte[] constantClass(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(0xCAFEBABE); out.writeShort(0); out.writeShort(65); out.writeShort(2);
            out.writeByte(1); out.writeUTF(value);
        }
        return bytes.toByteArray();
    }
    private static byte[] archive(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
