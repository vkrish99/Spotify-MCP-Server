package io.projects.spotifymcp.auth;

import io.projects.spotifymcp.config.McpHome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Persists a refresh token to the .env that {@link McpHome} resolves, so a token Spotify rotates
 * during a refresh survives a restart. Used by both the one-time auth bootstrap and
 * {@link SpotifyAuthService}.
 */
@Component
public class RefreshTokenStore {

    static final String ENV_KEY = "SPOTIFY_REFRESH_TOKEN";
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenStore.class);

    /** Saves the token and returns the file written, or throws if it couldn't be written. */
    public Path save(String refreshToken) throws IOException {
        return McpHome.detect().upsertEnvValue(ENV_KEY, refreshToken);
    }

    /** Best-effort variant for background rotation: logs instead of throwing. */
    public void saveQuietly(String refreshToken) {
        try {
            Path file = save(refreshToken);
            log.info("Spotify rotated the refresh token; saved the new one to {}", file);
        } catch (IOException e) {
            log.warn("Spotify rotated the refresh token but it couldn't be saved ({}). "
                    + "It works until restart; re-run the auth bootstrap if calls start failing.", e.getMessage());
        }
    }
}
