package io.projects.spotifymcp.auth;

import io.projects.spotifymcp.config.SpotifyProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Caches the Spotify access token and transparently refreshes it from the
 * stored refresh token (obtained once via {@link SpotifyAuthBootstrap})
 * so tool calls never need to re-authenticate.
 */
@Service
public class SpotifyAuthService {

    private static final String TOKEN_URI = "https://accounts.spotify.com/api/token";

    private final WebClient authWebClient;
    private final SpotifyProperties properties;
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    public SpotifyAuthService(WebClient.Builder webClientBuilder, SpotifyProperties properties) {
        this.authWebClient = webClientBuilder.build();
        this.properties = properties;
    }

    public Mono<String> getAccessToken() {
        CachedToken current = cachedToken.get();
        if (current != null && Instant.now().isBefore(current.expiresAt())) {
            return Mono.just(current.accessToken());
        }
        return refreshAccessToken();
    }

    private Mono<String> refreshAccessToken() {
        if (properties.refreshToken() == null || properties.refreshToken().isBlank()) {
            return Mono.error(new IllegalStateException(
                    "No Spotify refresh token configured. Run: "
                            + "mvnw spring-boot:run -Dspring-boot.run.profiles=auth "
                            + "to authorize once (see README)."));
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", properties.refreshToken());

        return authWebClient.post()
                .uri(TOKEN_URI)
                .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .map(this::cache);
    }

    private String cache(TokenResponse response) {
        int ttlSeconds = response.expiresIn() == null ? 0 : response.expiresIn();
        // Refresh a minute early so a call in flight never races token expiry.
        Instant expiresAt = Instant.now().plusSeconds(Math.max(0, ttlSeconds - 60));
        cachedToken.set(new CachedToken(response.accessToken(), expiresAt));
        return response.accessToken();
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
