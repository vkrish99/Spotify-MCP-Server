package io.projects.spotifymcp.config;

import io.projects.spotifymcp.auth.SpotifyAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpotifyAuthRetryFilterTest {

    private SpotifyAuthService authService;
    private final Deque<ClientResponse> responses = new ConcurrentLinkedDeque<>();
    private final List<ClientRequest> sent = new ArrayList<>();
    private WebClient webClient;

    @BeforeEach
    void setUp() {
        authService = mock(SpotifyAuthService.class);
        when(authService.getAccessToken()).thenReturn(Mono.just("token-1"), Mono.just("token-2"));
        webClient = WebClient.builder()
                .filter(new SpotifyAuthRetryFilter(authService))
                .exchangeFunction(request -> {
                    sent.add(request);
                    return Mono.just(responses.removeFirst());
                })
                .build();
    }

    @Test
    void addsBearerToken() {
        responses.add(status(HttpStatus.NO_CONTENT));

        put();

        assertThat(sent.get(0).headers().getFirst("Authorization")).isEqualTo("Bearer token-1");
    }

    @Test
    void on401InvalidatesTokenAndRetriesOnceWithAFreshOne() {
        responses.add(status(HttpStatus.UNAUTHORIZED));
        responses.add(status(HttpStatus.NO_CONTENT));

        put();

        verify(authService).invalidate();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(1).headers().getFirst("Authorization")).isEqualTo("Bearer token-2");
    }

    @Test
    void persistent401IsReturnedAfterOneRetry() {
        responses.add(status(HttpStatus.UNAUTHORIZED));
        responses.add(status(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(this::put).isInstanceOf(WebClientResponseException.Unauthorized.class);
        assertThat(sent).hasSize(2);
    }

    @Test
    void shortRetryAfterIsHonouredAndRetried() {
        responses.add(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0").build());
        responses.add(status(HttpStatus.NO_CONTENT));

        put();

        assertThat(sent).hasSize(2);
    }

    @Test
    void longRetryAfterIsNotWaitedOn() {
        responses.add(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "60").build());

        assertThatThrownBy(this::put).isInstanceOf(WebClientResponseException.TooManyRequests.class);
        assertThat(sent).hasSize(1);
    }

    @Test
    void serverErrorOnGetIsRetried() {
        responses.add(status(HttpStatus.BAD_GATEWAY));
        responses.add(status(HttpStatus.NO_CONTENT));

        webClient.get().uri("https://api.spotify.com/v1/me/player/devices").retrieve().toBodilessEntity().block();

        assertThat(sent).hasSize(2);
    }

    @Test
    void serverErrorOnNonIdempotentCallIsNotRetried() {
        responses.add(status(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> webClient.post().uri("https://api.spotify.com/v1/me/player/queue")
                .retrieve().toBodilessEntity().block())
                .isInstanceOf(WebClientResponseException.BadGateway.class);
        assertThat(sent).hasSize(1);
    }

    private void put() {
        webClient.put().uri("https://api.spotify.com/v1/me/player/pause").retrieve().toBodilessEntity().block();
    }

    private static ClientResponse status(HttpStatus status) {
        return ClientResponse.create(status).build();
    }
}
