import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class PreviewBootstrap {

    private static final String PROJECT_DIRECTORY = "tools/gui-preview";
    private static final String PENDING_CHECKSUM = "PENDING_RELEASE";

    private PreviewBootstrap() {}

    public static void main(String[] arguments) {
        try {
            System.exit(run(arguments));
        } catch (BootstrapFailure failure) {
            System.err.println(failure.getMessage());
            System.exit(2);
        } catch (Exception failure) {
            System.err.println("[bootstrap_error] " + message(failure));
            System.exit(1);
        }
    }

    private static int run(String[] arguments) throws Exception {
        if (Runtime.version().feature() < 25) {
            throw new BootstrapFailure(
                "[jdk_error] Galaxia GUI Preview requires JDK 25 or newer; found " + Runtime.version().feature());
        }
        if (arguments.length < 2) {
            throw new BootstrapFailure("Usage: preview-gui <command> [scenario or family] [options]");
        }
        Path repository = Path.of(arguments[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(repository.resolve("gradlew.bat"))
            && !Files.isRegularFile(repository.resolve("gradlew"))) {
            throw new BootstrapFailure("[project_error] Galaxia Gradle wrapper is missing below " + repository);
        }
        Release release = loadRelease(repository.resolve(PROJECT_DIRECTORY).resolve("previewer.properties"));
        Path cache = cacheRoot().resolve("ModularUI2-Preview").resolve(release.version());
        Files.createDirectories(cache);
        try (FileChannel channel = FileChannel.open(
            cache.resolve("bootstrap.lock"),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE);
            FileLock ignored = channel.lock()) {
            Path archive = prepareArchive(cache, release);
            Path tool = prepareTool(cache, archive, release);
            return launch(tool, repository, arguments);
        }
    }

    private static Release loadRelease(Path manifest) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(manifest)) {
            properties.load(input);
        } catch (IOException failure) {
            throw new BootstrapFailure("[manifest_error] Could not read previewer pin: " + manifest, failure);
        }
        String version = required(properties, "version");
        if (!version.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new BootstrapFailure("[manifest_error] Invalid previewer version: " + version);
        }
        String checksumValue = required(properties, "sha256");
        if (PENDING_CHECKSUM.equals(checksumValue)) {
            throw new BootstrapFailure(
                "[manifest_error] The pinned ModularUI2-Preview release has not been published yet");
        }
        String checksum = checksumValue.toLowerCase(Locale.ROOT);
        if (!checksum.matches("[0-9a-f]{64}")) {
            throw new BootstrapFailure("[manifest_error] Invalid previewer SHA-256 in " + manifest);
        }
        URI url;
        try {
            url = URI.create(required(properties, "url"));
        } catch (IllegalArgumentException failure) {
            throw new BootstrapFailure("[manifest_error] Invalid previewer release URL in " + manifest, failure);
        }
        return new Release(version, url, checksum);
    }

    private static Path prepareArchive(Path cache, Release release) throws Exception {
        Path archive = cache.resolve("modularui2-preview-" + release.version() + ".zip");
        if (Files.isRegularFile(archive)) {
            verifyChecksum(archive, release.sha256(), "cached previewer archive");
            return archive;
        }
        Path candidate = cache.resolve(archive.getFileName() + ".download");
        Files.deleteIfExists(candidate);
        HttpRequest request = HttpRequest.newBuilder(release.url()).GET().build();
        HttpResponse<Path> response;
        try {
            response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofFile(candidate));
        } catch (IOException failure) {
            Files.deleteIfExists(candidate);
            throw new BootstrapFailure("[download_error] Could not download " + release.url(), failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            Files.deleteIfExists(candidate);
            throw new BootstrapFailure("[download_error] Previewer download was interrupted", failure);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(candidate);
            throw new BootstrapFailure(
                "[download_error] Previewer download returned HTTP " + response.statusCode());
        }
        try {
            verifyChecksum(candidate, release.sha256(), "downloaded previewer archive");
        } catch (BootstrapFailure failure) {
            Files.deleteIfExists(candidate);
            throw failure;
        }
        Files.move(candidate, archive, StandardCopyOption.ATOMIC_MOVE);
        return archive;
    }

    private static Path prepareTool(Path cache, Path archive, Release release) throws IOException {
        Path tool = cache.resolve("tool");
        Path marker = tool.resolve(".release-sha256");
        if (Files.isRegularFile(marker)
            && release.sha256().equals(Files.readString(marker, StandardCharsets.UTF_8).trim())
            && findLauncher(tool) != null) {
            return tool;
        }
        Path candidate = cache.resolve("tool.extracting");
        deleteTree(candidate, cache);
        Files.createDirectories(candidate);
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                Path target = candidate.resolve(entry.getName()).normalize();
                if (!target.startsWith(candidate)) {
                    throw new BootstrapFailure("[archive_error] Unsafe previewer archive entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException | RuntimeException failure) {
            deleteTree(candidate, cache);
            throw failure;
        }
        if (findLauncher(candidate) == null) {
            deleteTree(candidate, cache);
            throw new BootstrapFailure("[archive_error] Previewer archive does not contain a launcher");
        }
        deleteTree(tool, cache);
        Files.move(candidate, tool, StandardCopyOption.ATOMIC_MOVE);
        Files.writeString(marker, release.sha256() + System.lineSeparator(), StandardCharsets.UTF_8);
        return tool;
    }

    private static int launch(Path tool, Path repository, String[] arguments) throws Exception {
        Path launcher = findLauncher(tool);
        if (launcher == null) throw new BootstrapFailure("[archive_error] Cached previewer launcher is missing");
        String command = arguments[1];
        List<String> previewArguments = new ArrayList<>();
        boolean windows = isWindows();
        if (windows) {
            previewArguments.add("cmd.exe");
            previewArguments.add("/d");
            previewArguments.add("/c");
        } else {
            previewArguments.add("sh");
        }
        previewArguments.add(launcher.toString());
        previewArguments.add(command);
        if (!command.equals("help") && !command.equals("--help")) {
            previewArguments.add(repository.resolve(PROJECT_DIRECTORY).toString());
        }
        for (int index = 2; index < arguments.length; index++) previewArguments.add(arguments[index]);
        ProcessBuilder process = new ProcessBuilder(previewArguments)
            .directory(repository.toFile())
            .inheritIO();
        process.environment().put("JAVA_HOME", System.getProperty("java.home"));
        return process.start().waitFor();
    }

    private static Path findLauncher(Path tool) throws IOException {
        if (!Files.isDirectory(tool)) return null;
        String name = isWindows() ? "preview.bat" : "preview.sh";
        try (var paths = Files.walk(tool, 3)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals(name))
                .findFirst()
                .orElse(null);
        }
    }

    private static void verifyChecksum(Path archive, String expected, String label) throws IOException {
        String actual = sha256(archive);
        if (!expected.equals(actual)) {
            throw new BootstrapFailure(
                "[checksum_error] SHA-256 mismatch for " + label + ": expected " + expected + ", found " + actual);
        }
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path cacheRoot() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (isWindows() && localAppData != null && !localAppData.isBlank()) return Path.of(localAppData);
        String xdg = System.getenv("XDG_CACHE_HOME");
        if (xdg != null && !xdg.isBlank()) return Path.of(xdg);
        return Path.of(System.getProperty("user.home"), ".cache");
    }

    private static void deleteTree(Path target, Path cache) throws IOException {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path normalizedCache = cache.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedCache) || normalizedTarget.equals(normalizedCache)) {
            throw new BootstrapFailure("[cache_error] Refusing to clean an unsafe cache path: " + target);
        }
        if (Files.notExists(normalizedTarget)) return;
        try (var paths = Files.walk(normalizedTarget)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new BootstrapFailure("[manifest_error] Missing " + key);
        return value.trim();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private record Release(String version, URI url, String sha256) {}

    private static final class BootstrapFailure extends RuntimeException {

        private BootstrapFailure(String message) {
            super(message);
        }

        private BootstrapFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
