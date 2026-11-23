package io.projects.spotifymcp.config;

import io.projects.spotifymcp.auth.SpotifyAuthService;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Adds the bearer token to every Spotify API call and retries the failures that a retry can fix:
 * <ul>
 *   <li><b>401</b>: the cached access token was revoked early -- drop it, refresh, retry once.</li>
 *   <li><b>429</b>: wait for {@code Retry-After} and retry once, but only if the wait is short
 *       enough that the tool call won't time out. Longer waits reach the caller as a 429, which
 *       {@code ToolErrors} reports with the wait time.</li>
 *   <li><b>5xx on GET</b>: retry once after a short pause. Non-GET calls aren't retried because
 *       they aren't safe to repeat (e.g. {@code POST /me/player/queue} would queue twice).</li>
 * </ul>
 */
public class SpotifyAuthRetryFilter implements ExchangeFilterFunction {

    static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(5);
    static final Duration SERVER_ERROR_BACKOFF = Duration.ofMillis(300);

    private final SpotifyAuthService authService;

    public SpotifyAuthRetryFilter(SpotifyAuthService authService) {
        this.authService = authService;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return send(request, next).flatMap(response -> {
            int status = response.statusCode().value();
            if (status == 401) {
                return response.releaseBody().then(Mono.defer(() -> {
                    authService.invalidate();
                    return send(request, next);
                }));
            }
            if (status == 429) {
                Duration wait = retryAfter(response);
                if (wait.compareTo(MAX_RETRY_AFTER) <= 0) {
                    return response.releaseBody().then(Mono.delay(wait)).then(send(request, next));
                }
            }
            if (response.statusCode().is5xxServerError() && HttpMethod.GET.equals(request.method())) {
                return response.releaseBody().then(Mono.delay(SERVER_ERROR_BACKOFF)).then(send(request, next));
            }
            return Mono.just(response);
        });
    }

    private Mono<ClientResponse> send(ClientRequest request, ExchangeFunction next) {
        return authService.getAccessToken().flatMap(token -> next.exchange(
                ClientRequest.from(request).headers(headers -> headers.setBearerAuth(token)).build()));
    }

    /** Spotify sends Retry-After in whole seconds; treat a missing or odd value as 1 second. */
    static Duration retryAfter(ClientResponse response) {
        String header = response.headers().asHttpHeaders().getFirst("Retry-After");
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(header == null ? "1" : header.trim())));
        } catch (NumberFormatException e) {
            return Duration.ofSeconds(1);
        }
    }
}
