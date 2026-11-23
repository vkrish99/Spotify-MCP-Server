package io.projects.spotifymcp.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads the .env file found by {@link McpHome} (if any) into the Spring Environment before
 * context startup, so application.yml's ${SPOTIFY_CLIENT_ID} style placeholders resolve from it
 * exactly like a real OS environment variable would. Also publishes {@code spotify-mcp.home},
 * which application.yml uses for the log file path.
 * <p>
 * Registered via META-INF/spring.factories. It runs before logging is initialised, which is what
 * lets it set the log location, but also means there's no logger yet -- so it fails loudly with
 * an unchecked exception instead of logging.
 */
public class DotenvPropertySourceLoader implements EnvironmentPostProcessor {

    static final String HOME_PROPERTY = "spotify-mcp.home";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        McpHome home = McpHome.detect();

        // addLast: an explicit -Dspotify-mcp.home=... still wins.
        environment.getPropertySources().addLast(new MapPropertySource("spotify-mcp-home",
                Map.of(HOME_PROPERTY, home.directory().toString().replace('\\', '/'))));

        home.envFile().ifPresent(envFile ->
                environment.getPropertySources().addFirst(new MapPropertySource("dotenv", read(envFile))));
    }

    private Map<String, Object> read(Path envFile) {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int equalsIndex = trimmed.indexOf('=');
                if (equalsIndex < 0) {
                    continue;
                }
                String key = trimmed.substring(0, equalsIndex).trim();
                String value = unquote(trimmed.substring(equalsIndex + 1).trim());
                values.put(key, value);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + envFile, e);
        }
        return values;
    }

    private String unquote(String value) {
        boolean wrapped = value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")));
        return wrapped ? value.substring(1, value.length() - 1) : value;
    }
}
