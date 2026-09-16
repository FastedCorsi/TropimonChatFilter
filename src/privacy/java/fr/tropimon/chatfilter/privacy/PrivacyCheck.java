package fr.tropimon.chatfilter.privacy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/** Build-only guard. Reports locations and categories, never the matched private values. */
public final class PrivacyCheck {
    private static final int MAX_ENTRY = 16 * 1024 * 1024;
    private static final long MAX_TOTAL = 256L * 1024 * 1024;
    private static final Set<String> LOCAL_DIRECTORIES = Set.of(
            ".git", ".gradle", "build", "run", "logs", "releases", "config",
            "screenshots", "backups", "mod-archive");
    private static final Set<String> GENERIC_ACCOUNTS = Set.of(
            "root", "runner", "admin", "administrator", "user", "system", "build", "ci",
            "ubuntu", "nobody", "default", "guest", "codex", "developer", "dev", "bot");
    private static final Pattern PERSONAL_PATH = Pattern.compile(
            "(?i)(?:[a-z]:[\\\\/]+(?:users|documents and settings)[\\\\/]+[^\\s/\\\\\"']+"
                    + "|/(?:users|home)/[^\\s/\\\\\"']+)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern TOKEN = Pattern.compile(
            "(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{30,}"
                    + "|sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{24,}|AKIA[A-Z0-9]{16}"
                    + "|eyJ[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,})");
    private static final Pattern PRIVATE_KEY = Pattern.compile("-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----");
    private static final Pattern URL_CREDENTIAL = Pattern.compile("https?://[^\\s/@:]+:[^\\s/@]+@");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(?:api[_-]?key|access[_-]?token|client[_-]?secret|password|passwd|authorization)"
                    + "[\"']?\\s*[:=]\\s*[\"']([^\"'\\r\\n]{8,})[\"']");
    private static final Pattern AUTHOR = Pattern.compile(
            "\"authors\"\\s*:\\s*\\[\\s*\"By FastedCorsi\"\\s*]");
    private static final Pattern AUTHOR_FIELD = Pattern.compile("\"authors\"\\s*:");
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
    private final List<Pattern> privateTerms;
    private final LinkedHashSet<Finding> findings = new LinkedHashSet<>();
    private long bytesRead;
    private int entries;

    record Finding(String location, String category) { }

    PrivacyCheck(Collection<String> terms) {
        privateTerms = terms.stream().filter(s -> s != null && s.strip().length() >= 3)
                .map(String::strip).distinct()
                .map(s -> Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(s)
                        + "(?![\\p{L}\\p{N}_])")).toList();
    }

    public static void main(String[] args) {
        try {
            if (args.length < 2) throw new IOException("arguments");
            Path root = Path.of(args[1]).toAbsolutePath().normalize();
            PrivacyCheck check = new PrivacyCheck(localTerms(root));
            if (args[0].equals("sources")) check.sources(root);
            else if (args[0].equals("artifacts") && args.length > 2) {
                for (int i = 2; i < args.length; i++) {
                    Path jar = Path.of(args[i]).toAbsolutePath().normalize();
                    if (!jar.startsWith(root)) throw new IOException("artifact outside module");
                    check.inspect(root.relativize(jar).toString().replace('\\', '/'),
                            check.read(Files.newInputStream(jar)), 0);
                }
            } else throw new IOException("mode");
            check.findings.forEach(f -> System.err.println(f.location() + " : " + f.category()));
            if (!check.findings.isEmpty()) {
                System.err.println("Privacy check FAILED: " + check.findings.size() + " finding(s); values redacted.");
                System.exit(1);
            }
            System.out.println("Privacy check OK: " + check.entries + " file/entry checks; public author By FastedCorsi.");
        } catch (Exception invalid) {
            // Exception messages can contain private absolute paths or snippets from malformed input.
            System.err.println("Privacy check could not complete safely (" + invalid.getClass().getSimpleName() + ").");
            System.exit(2);
        }
    }

    List<Finding> findings() { return List.copyOf(findings); }

    void sources(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path relative = root.relativize(dir);
                if (!relative.toString().isEmpty() && LOCAL_DIRECTORIES.contains(relative.getName(0).toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = root.relativize(file).toString().replace('\\', '/');
                // Private local files are excluded from exports, not deleted to make this check pass.
                if (!name.contains("/") && privateFile(name)) return FileVisitResult.CONTINUE;
                if (Files.isSymbolicLink(file)) fail(name, "unreviewed-symbolic-link");
                else {
                    String resource = name.startsWith("src/main/resources/")
                            ? name.substring("src/main/resources/".length()) : name;
                    if (forbidden(resource)) fail(name, "private-file-in-distribution");
                    inspect(name, read(Files.newInputStream(file)), 0);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Path metadata = root.resolve("src/main/resources/fabric.mod.json");
        if (!Files.isRegularFile(metadata)) fail("src/main/resources/fabric.mod.json", "missing-metadata");
        // Ignore rules do not remove already tracked private files from an export.
        for (String tracked : git(root, "ls-files", "--full-name", "--", ".").split("\\R")) {
            int module = tracked.indexOf(root.getFileName() + "/");
            String relative = module >= 0 ? tracked.substring(module + root.getFileName().toString().length() + 1) : tracked;
            if (!relative.isBlank() && (forbidden(relative)
                    || LOCAL_DIRECTORIES.contains(relative.split("/")[0]))) fail(relative, "tracked-private-file");
        }
    }

    void inspect(String name, byte[] data, int depth) throws IOException {
        if (++entries > 20_000 || depth > 4) throw new IOException("scan bound");
        text(name, name); // Entry/file names can leak identities as well as their contents.
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jar") || lower.endsWith(".zip")) {
            boolean metadata = false;
            int count = 0;
            Set<String> seen = new HashSet<>();
            try (var zip = new ZipInputStream(new ByteArrayInputStream(data))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (seen.size() >= 20_000) throw new IOException("archive entry bound");
                    String child = entry.getName().replace('\\', '/');
                    if (!seen.add(child)) fail(name, "duplicate-archive-entry");
                    if (child.startsWith("/") || child.matches("^[A-Za-z]:.*")
                            || Arrays.asList(child.split("/")).contains("..")) fail(name, "unsafe-archive-path");
                    if (entry.isDirectory()) continue;
                    if (forbidden(child)) fail(name + "!/" + child, "private-file-in-archive");
                    if (child.startsWith("fr/tropimon/chatfilter/privacy/") || child.contains("ChatFilterSmoke")) {
                        fail(name + "!/" + child, "build-helper-in-runtime");
                    }
                    if (child.equals("fabric.mod.json")) metadata = true;
                    inspect(name + "!/" + child, readEntry(zip), depth + 1);
                    count++;
                }
            }
            if (count == 0) fail(name, "empty-or-invalid-archive");
            if (depth == 0 && !metadata && !name.equals("gradle/wrapper/gradle-wrapper.jar")) {
                fail(name, "missing-mod-metadata");
            }
        } else if (lower.endsWith(".gz")) {
            inspect(name.substring(0, name.length() - 3), read(new GZIPInputStream(new ByteArrayInputStream(data))), depth + 1);
        } else if (lower.endsWith(".class")) {
            try (var input = new DataInputStream(new ByteArrayInputStream(data))) {
                if (input.readInt() != 0xCAFEBABE) throw new IOException("invalid class");
                input.readUnsignedShort(); input.readUnsignedShort();
                int pool = input.readUnsignedShort();
                for (int index = 1; index < pool; index++) {
                    switch (input.readUnsignedByte()) {
                        case 1 -> text(name, input.readUTF());
                        case 3, 4, 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
                        case 5, 6 -> { input.skipNBytes(8); index++; }
                        case 7, 8, 16, 19, 20 -> input.skipNBytes(2);
                        case 15 -> input.skipNBytes(3);
                        default -> throw new IOException("invalid constant pool");
                    }
                }
            }
        } else {
            String value = new String(data, StandardCharsets.UTF_8);
            text(name, value);
            if (data.length >= 2 && (data[0] == (byte) 0xff && data[1] == (byte) 0xfe
                    || data[0] == (byte) 0xfe && data[1] == (byte) 0xff)) text(name, new String(data, StandardCharsets.UTF_16));
            if (name.endsWith("fabric.mod.json") && (depth == 0 || depth == 1)) {
                if (!AUTHOR.matcher(value).find() || AUTHOR_FIELD.matcher(value).results().limit(2).count() != 1) {
                    fail(name, "invalid-public-author");
                }
            }
        }
    }

    private void text(String name, String raw) {
        String value = UNICODE_ESCAPE.matcher(raw).replaceAll(m -> java.util.regex.Matcher.quoteReplacement(
                String.valueOf((char) Integer.parseInt(m.group(1), 16))));
        if (PERSONAL_PATH.matcher(value).find()) fail(name, "personal-system-path");
        if (privateTerms.stream().anyMatch(p -> p.matcher(value).find())) fail(name, "local-private-identity");
        var emails = EMAIL.matcher(value);
        while (emails.find()) {
            String domain = emails.group().substring(emails.group().indexOf('@') + 1).toLowerCase(Locale.ROOT);
            // Public Git SSH references in dependency POMs are not personal e-mail addresses.
            boolean publicGitReference = emails.group().toLowerCase(Locale.ROOT).startsWith("git@")
                    && Set.of("github.com", "gitlab.com", "bitbucket.org").contains(domain)
                    && emails.end() < value.length() && value.charAt(emails.end()) == ':';
            if (publicGitReference) continue;
            if (!domain.equals("users.noreply.github.com") && !domain.matches("(?:.*\\.)?(?:example\\.(?:com|org|net)|invalid|test)")) {
                fail(name, "email-needs-review");
            }
        }
        if (TOKEN.matcher(value).find() || PRIVATE_KEY.matcher(value).find()
                || URL_CREDENTIAL.matcher(value).find()) fail(name, "possible-secret");
        var assignments = SECRET_ASSIGNMENT.matcher(value);
        while (assignments.find()) {
            String secret = assignments.group(1);
            if (!secret.matches("(?i)(?:example|placeholder|changeme|redacted|your[_-]).*")
                    && !secret.startsWith("${") && !secret.startsWith("<")) fail(name, "literal-credential-needs-review");
        }
    }

    private void fail(String name, String category) {
        String safe = PERSONAL_PATH.matcher(name).replaceAll("[redacted-path]");
        safe = EMAIL.matcher(safe).replaceAll("[redacted-email]");
        safe = TOKEN.matcher(safe).replaceAll("[redacted-token]");
        for (Pattern term : privateTerms) safe = term.matcher(safe).replaceAll("[redacted-identity]");
        findings.add(new Finding(safe, category));
    }

    private static boolean privateFile(String name) {
        String leaf = name.substring(name.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return leaf.startsWith(".env") || leaf.matches(".*\\.(?:log|bak|pending|keystore|jks|p12|hprof)")
                || Set.of("options.txt", "servers.dat", "usercache.json", "launcher_accounts.json").contains(leaf);
    }

    private static boolean forbidden(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        String first = normalized.split("/", 2)[0];
        return privateFile(normalized) || LOCAL_DIRECTORIES.contains(first)
                || Arrays.asList(normalized.split("/")).contains(".git");
    }

    private byte[] read(InputStream input) throws IOException {
        try (input) { return readEntry(input); }
    }

    private byte[] readEntry(InputStream input) throws IOException {
        byte[] data = input.readNBytes(MAX_ENTRY + 1);
        bytesRead += data.length;
        if (data.length > MAX_ENTRY || bytesRead > MAX_TOTAL) throw new IOException("scan size bound");
        return data;
    }

    private static Set<String> localTerms(Path root) throws IOException {
        Set<String> terms = new HashSet<>();
        addIdentity(terms, System.getenv("USERNAME"));
        addIdentity(terms, System.getenv("USER"));
        addIdentity(terms, System.getProperty("user.name"));
        for (String identity : git(root, "log", "--format=%an%n%ae%n%cn%n%ce", "--", ".").split("\\R")) addIdentity(terms, identity);
        for (String field : List.of("user.name", "user.email")) addIdentity(terms, git(root, "config", "--get", field).strip());
        String privateList = System.getenv("CHAT_FILTER_PRIVACY_TERMS_FILE");
        if (privateList != null && !privateList.isBlank()) {
            Path file = Path.of(privateList).toRealPath();
            String gitRoot = git(root, "rev-parse", "--show-toplevel").strip();
            Path repository = gitRoot.isBlank() ? root.toRealPath() : Path.of(gitRoot).toRealPath();
            if (file.startsWith(repository)) throw new IOException("private list must stay outside repository");
            if (Files.size(file) > 65_536) throw new IOException("private list bound");
            for (String line : Files.readAllLines(file)) if (!line.isBlank() && !line.startsWith("#")) terms.add(line.strip());
        }
        return terms;
    }

    private static void addIdentity(Set<String> terms, String identity) {
        if (identity == null) return;
        identity = identity.strip();
        if (identity.isEmpty() || identity.equalsIgnoreCase("By FastedCorsi") || identity.equalsIgnoreCase("FastedCorsi")
                || identity.endsWith("@users.noreply.github.com") || GENERIC_ACCOUNTS.contains(identity.toLowerCase(Locale.ROOT))) return;
        if (identity.contains("@")) { terms.add(identity); return; }
        terms.add(identity);
        for (String part : identity.split("\\s+")) if (part.length() >= 4 && !GENERIC_ACCOUNTS.contains(part.toLowerCase(Locale.ROOT))) terms.add(part);
    }

    private static String git(Path root, String... arguments) {
        try {
            List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
            command.addAll(List.of(arguments));
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            byte[] data;
            try (InputStream out = process.getInputStream()) { data = out.readNBytes(65_537); }
            if (data.length > 65_536 || !process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); return ""; }
            return process.exitValue() == 0 ? new String(data, StandardCharsets.UTF_8) : "";
        } catch (IOException ignored) { return ""; }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return ""; }
    }
}
