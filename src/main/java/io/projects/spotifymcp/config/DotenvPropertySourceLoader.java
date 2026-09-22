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
 * Loads a local .env file (if present) into the Spring Environment before context
 * startup, so application.yml's ${SPOTIFY_CLIENT_ID} style placeholders resolve
 * from it exactly like a real OS environment variable would.
 * Registered via META-INF/spring.factories -- runs too early for a logger, so it
 * fails loudly with an unchecked exception instead of logging.
 */
public class DotenvPropertySourceLoader implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envFile = Path.of(".env");
        if (!Files.exists(envFile)) {
            return;
        }

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
            throw new UncheckedIOException("Failed to read .env", e);
        }

        environment.getPropertySources().addFirst(new MapPropertySource("dotenv", values));
    }

    private String unquote(String value) {
        boolean wrapped = value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")));
        return wrapped ? value.substring(1, value.length() - 1) : value;
    }
}
