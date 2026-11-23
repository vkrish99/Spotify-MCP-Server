package io.projects.spotifymcp.config;

import io.projects.spotifymcp.SpotifyMcpServerApplication;
import org.springframework.boot.system.ApplicationHome;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where this server keeps its {@code .env} and log file.
 * <p>
 * MCP clients (Claude Desktop, Cursor) launch the jar from their own working directory, not the
 * project folder, so neither can be resolved relative to the working directory alone. The first
 * of these that applies wins:
 * <ol>
 *   <li>{@code $SPOTIFY_MCP_HOME}, if set</li>
 *   <li>the working directory, if it contains a {@code .env} ({@code ./mvnw spring-boot:run})</li>
 *   <li>the jar's directory or its parent, if either contains a {@code .env}
 *       ({@code java -jar /project/target/spotify-mcp-server.jar} from anywhere)</li>
 *   <li>{@code ~/.spotify-mcp}</li>
 * </ol>
 */
public record McpHome(Path directory, Optional<Path> envFile) {

    public static final String HOME_ENV_VAR = "SPOTIFY_MCP_HOME";
    private static final String ENV_FILE_NAME = ".env";

    public static McpHome detect() {
        return detect(
                System.getenv(HOME_ENV_VAR),
                Path.of("").toAbsolutePath(),
                applicationDirectory(),
                Path.of(System.getProperty("user.home")));
    }

    static McpHome detect(String homeOverride, Path workingDir, Path appDir, Path userHome) {
        if (homeOverride != null && !homeOverride.isBlank()) {
            Path dir = Path.of(homeOverride).toAbsolutePath();
            return new McpHome(dir, existingEnvFile(dir));
        }

        List<Path> candidates = new ArrayList<>();
        candidates.add(workingDir);
        if (appDir != null) {
            candidates.add(appDir);
            if (appDir.getParent() != null) {
                candidates.add(appDir.getParent());
            }
        }
        for (Path dir : candidates) {
            Optional<Path> envFile = existingEnvFile(dir);
            if (envFile.isPresent()) {
                return new McpHome(dir, envFile);
            }
        }

        Path fallback = userHome.resolve(".spotify-mcp");
        return new McpHome(fallback, existingEnvFile(fallback));
    }

    /** The .env to write to: the one that was loaded, or a new one in {@link #directory()}. */
    public Path envFileForWriting() {
        return envFile.orElse(directory.resolve(ENV_FILE_NAME));
    }

    public Path logFile() {
        return directory.resolve("logs").resolve("spotify-mcp-server.log");
    }

    /** Insert or replace {@code key=value} in this home's .env, creating the file if needed. */
    public Path upsertEnvValue(String key, String value) throws IOException {
        Path file = envFileForWriting();
        List<String> lines = Files.exists(file) ? new ArrayList<>(Files.readAllLines(file)) : new ArrayList<>();
        String prefix = key + "=";
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                lines.set(i, prefix + value);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            lines.add(prefix + value);
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.write(file, lines);
        return file;
    }

    private static Optional<Path> existingEnvFile(Path dir) {
        Path file = dir.resolve(ENV_FILE_NAME);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    private static Path applicationDirectory() {
        try {
            return new ApplicationHome(SpotifyMcpServerApplication.class).getDir().toPath();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
