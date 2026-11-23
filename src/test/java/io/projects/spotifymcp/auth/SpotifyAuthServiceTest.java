package io.projects.spotifymcp.auth;

import io.projects.spotifymcp.config.SpotifyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class SpotifyAuthServiceTest {

    private static final SpotifyProperties PROPERTIES = new SpotifyProperties(
            "client-id", "client-secret", "http://127.0.0.1:8888/callback", "stored-refresh-token");

    @Test
    void fetchesAndReturnsAccessToken() {
        AtomicInteger callCount = new AtomicInteger();
        SpotifyAuthService service = serviceWithCannedResponse(callCount, 3600);

        StepVerifier.create(service.getAccessToken())
                .expectNext("fresh-access-token")
                .verifyComplete();

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void cachesTokenUntilNearExpiry() {
        AtomicInteger callCount = new AtomicInteger();
        SpotifyAuthService service = serviceWithCannedResponse(callCount, 3600);

        service.getAccessToken().block();
        service.getAccessToken().block();
        service.getAccessToken().block();

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void refreshesAgainOnceCachedTokenIsNearExpiry() {
        AtomicInteger callCount = new AtomicInteger();
        // expires_in=30s is within the 60s refresh-early buffer, so every call should refresh.
        SpotifyAuthService service = serviceWithCannedResponse(callCount, 30);

        service.getAccessToken().block();
        service.getAccessToken().block();

        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void concurrentCallersShareOneRefresh() {
        AtomicInteger callCount = new AtomicInteger();
        SpotifyAuthService service = service(request -> {
            callCount.incrementAndGet();
            return tokenResponse(3600, null).delayElement(Duration.ofMillis(100));
        }, token -> { });

        List<String> tokens = Flux.range(0, 10)
                .flatMap(i -> service.getAccessToken())
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(tokens).hasSize(10).containsOnly("fresh-access-token");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void invalidateForcesARefresh() {
        AtomicInteger callCount = new AtomicInteger();
        SpotifyAuthService service = serviceWithCannedResponse(callCount, 3600);

        service.getAccessToken().block();
        service.invalidate();
        service.getAccessToken().block();

        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void persistsARotatedRefreshTokenOnce() {
        List<String> saved = new ArrayList<>();
        SpotifyAuthService service = service(request -> tokenResponse(30, "rotated-refresh-token"), saved::add);

        service.getAccessToken().block();
        service.getAccessToken().block();

        // The second response repeats the token now in use, which is not a new rotation.
        assertThat(saved).containsExactly("rotated-refresh-token");
    }

    @Test
    void revokedRefreshTokenGivesAnActionableError() {
        SpotifyAuthService service = service(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"invalid_grant\",\"error_description\":\"Refresh token revoked\"}")
                .build()), token -> { });

        StepVerifier.create(service.getAccessToken())
                .expectErrorMatches(e -> e instanceof IllegalStateException
                        && e.getMessage().contains("rejected the stored refresh token"))
                .verify();
    }

    @Test
    void errorsClearlyWhenNoRefreshTokenConfigured() {
        SpotifyProperties noToken = new SpotifyProperties("client-id", "client-secret",
                "http://127.0.0.1:8888/callback", "");
        SpotifyAuthService service = new SpotifyAuthService(WebClient.builder(), noToken, token -> { });

        StepVerifier.create(service.getAccessToken())
                .expectErrorMatches(e -> e instanceof IllegalStateException
                        && e.getMessage().toLowerCase().contains("refresh token"))
                .verify();
    }

    private SpotifyAuthService serviceWithCannedResponse(AtomicInteger callCount, int expiresInSeconds) {
        return service(request -> {
            callCount.incrementAndGet();
            return tokenResponse(expiresInSeconds, null);
        }, token -> { });
    }

    private SpotifyAuthService service(Function<ClientRequest, Mono<ClientResponse>> exchange,
                                       Consumer<String> rotatedTokenSink) {
        return new SpotifyAuthService(WebClient.builder().exchangeFunction(exchange::apply), PROPERTIES,
                rotatedTokenSink);
    }

    private static Mono<ClientResponse> tokenResponse(int expiresInSeconds, String refreshToken) {
        String body = """
                {"access_token":"fresh-access-token","token_type":"Bearer","expires_in":%d,"scope":"user-read-playback-state"%s}
                """.formatted(expiresInSeconds,
                refreshToken == null ? "" : ",\"refresh_token\":\"" + refreshToken + "\"");
        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }
}
