package io.projects.spotifymcp.auth;

import io.projects.spotifymcp.config.SpotifyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Caches the Spotify access token and transparently refreshes it from the
 * stored refresh token (obtained once via {@link SpotifyAuthBootstrap})
 * so tool calls never need to re-authenticate.
 * <p>
 * Concurrent callers that find the cache expired share one in-flight refresh
 * rather than each hitting the token endpoint.
 */
@Service
public class SpotifyAuthService {

    private static final String TOKEN_URI = "https://accounts.spotify.com/api/token";

    private final WebClient authWebClient;
    private final SpotifyProperties properties;
    private final Consumer<String> rotatedTokenSink;
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();
    private final AtomicReference<Mono<String>> inFlightRefresh = new AtomicReference<>();
    private final AtomicReference<String> refreshToken;

    @Autowired
    public SpotifyAuthService(WebClient.Builder webClientBuilder, SpotifyProperties properties,
                              RefreshTokenStore refreshTokenStore) {
        this(webClientBuilder, properties, refreshTokenStore::saveQuietly);
    }

    SpotifyAuthService(WebClient.Builder webClientBuilder, SpotifyProperties properties,
                       Consumer<String> rotatedTokenSink) {
        this.authWebClient = webClientBuilder.build();
        this.properties = properties;
        this.rotatedTokenSink = rotatedTokenSink;
        this.refreshToken = new AtomicReference<>(properties.refreshToken());
    }

    public Mono<String> getAccessToken() {
        return Mono.defer(() -> {
            CachedToken current = cachedToken.get();
            if (current != null && Instant.now().isBefore(current.expiresAt())) {
                return Mono.just(current.accessToken());
            }
            return sharedRefresh();
        });
    }

    /** Drop the cached access token, e.g. after Spotify rejected it with a 401. */
    public void invalidate() {
        cachedToken.set(null);
    }

    private Mono<String> sharedRefresh() {
        while (true) {
            Mono<String> existing = inFlightRefresh.get();
            if (existing != null) {
                return existing;
            }
            AtomicReference<Mono<String>> self = new AtomicReference<>();
            // doOnTerminate (not doFinally) clears the slot *before* subscribers see the result,
            // so a caller reacting to a failed refresh can immediately start a fresh one.
            Mono<String> created = refreshAccessToken()
                    .doOnTerminate(() -> inFlightRefresh.compareAndSet(self.get(), null))
                    .cache();
            self.set(created);
            if (inFlightRefresh.compareAndSet(null, created)) {
                return created;
            }
        }
    }

    private Mono<String> refreshAccessToken() {
        String currentRefreshToken = refreshToken.get();
        if (currentRefreshToken == null || currentRefreshToken.isBlank()) {
            return Mono.error(new IllegalStateException(
                    "No Spotify refresh token configured. Run: "
                            + "mvnw spring-boot:run -Dspring-boot.run.profiles=auth "
                            + "to authorize once (see README)."));
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", currentRefreshToken);

        return authWebClient.post()
                .uri(TOKEN_URI)
                .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .onErrorMap(WebClientResponseException.class, this::describeRefreshFailure)
                .map(response -> cache(response, currentRefreshToken));
    }

    private Throwable describeRefreshFailure(WebClientResponseException e) {
        if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 401) {
            return new IllegalStateException("Spotify rejected the stored refresh token (it was revoked, "
                    + "expired, or belongs to a different app). Re-run: "
                    + "mvnw spring-boot:run -Dspring-boot.run.profiles=auth", e);
        }
        return e;
    }

    private String cache(TokenResponse response, String usedRefreshToken) {
        int ttlSeconds = response.expiresIn() == null ? 0 : response.expiresIn();
        // Refresh a minute early so a call in flight never races token expiry.
        Instant expiresAt = Instant.now().plusSeconds(Math.max(0, ttlSeconds - 60));
        cachedToken.set(new CachedToken(response.accessToken(), expiresAt));

        String rotated = response.refreshToken();
        if (rotated != null && !rotated.isBlank() && !rotated.equals(usedRefreshToken)
                && refreshToken.compareAndSet(usedRefreshToken, rotated)) {
            rotatedTokenSink.accept(rotated);
        }
        return response.accessToken();
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
