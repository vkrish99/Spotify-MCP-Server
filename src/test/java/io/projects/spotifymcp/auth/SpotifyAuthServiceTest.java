package io.projects.spotifymcp.auth;

import io.projects.spotifymcp.config.SpotifyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicInteger;

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
    void errorsClearlyWhenNoRefreshTokenConfigured() {
        SpotifyProperties noToken = new SpotifyProperties("client-id", "client-secret",
                "http://127.0.0.1:8888/callback", "");
        SpotifyAuthService service = new SpotifyAuthService(WebClient.builder(), noToken);

        StepVerifier.create(service.getAccessToken())
                .expectErrorMatches(e -> e instanceof IllegalStateException
                        && e.getMessage().toLowerCase().contains("refresh token"))
                .verify();
    }

    private SpotifyAuthService serviceWithCannedResponse(AtomicInteger callCount, int expiresInSeconds) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            callCount.incrementAndGet();
            String body = """
                    {"access_token":"fresh-access-token","token_type":"Bearer","expires_in":%d,"scope":"user-read-playback-state"}
                    """.formatted(expiresInSeconds);
            return Mono.just(ClientResponse.create(org.springframework.http.HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        });
        return new SpotifyAuthService(builder, PROPERTIES);
    }
}
