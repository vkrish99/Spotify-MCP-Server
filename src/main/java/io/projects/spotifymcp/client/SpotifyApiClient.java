package io.projects.spotifymcp.client;

import io.projects.spotifymcp.client.dto.CurrentlyPlayingResponse;
import io.projects.spotifymcp.client.dto.DevicesResponse;
import io.projects.spotifymcp.client.dto.SearchResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/** Thin, non-blocking wrapper around the Spotify Web API endpoints the MCP tools need. */
@Component
public class SpotifyApiClient {

    private final WebClient webClient;

    public SpotifyApiClient(WebClient spotifyApiWebClient) {
        this.webClient = spotifyApiWebClient;
    }

    public Mono<Void> pause() {
        return webClient.put()
                .uri("/me/player/pause")
                .retrieve()
                .bodyToMono(Void.class);
    }

    public Mono<Void> resume() {
        return webClient.put()
                .uri("/me/player/play")
                .retrieve()
                .bodyToMono(Void.class);
    }

    public Mono<Void> next() {
        return webClient.post()
                .uri("/me/player/next")
                .retrieve()
                .bodyToMono(Void.class);
    }

    public Mono<Void> previous() {
        return webClient.post()
                .uri("/me/player/previous")
                .retrieve()
                .bodyToMono(Void.class);
    }

    public Mono<Void> setVolume(int percent) {
        return webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/player/volume")
                        .queryParam("volume_percent", percent)
                        .build())
                .retrieve()
                .bodyToMono(Void.class);
    }

    public Mono<Void> transferPlayback(String deviceId) {
        return webClient.put()
                .uri("/me/player")
                .bodyValue(Map.of("device_ids", java.util.List.of(deviceId), "play", true))
                .retrieve()
                .bodyToMono(Void.class);
    }

    public Mono<Void> playContext(String contextUri) {
        return webClient.put()
                .uri("/me/player/play")
                .bodyValue(Map.of("context_uri", contextUri))
                .retrieve()
                .bodyToMono(Void.class);
    }

    public Mono<Void> addToQueue(String trackUri) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/player/queue")
                        .queryParam("uri", trackUri)
                        .build())
                .retrieve()
                .bodyToMono(Void.class);
    }

    public Mono<DevicesResponse> getDevices() {
        return webClient.get()
                .uri("/me/player/devices")
                .retrieve()
                .bodyToMono(DevicesResponse.class);
    }

    /** Empty Mono when nothing is currently playing (Spotify returns 204). */
    public Mono<CurrentlyPlayingResponse> getCurrentlyPlaying() {
        return webClient.get()
                .uri("/me/player/currently-playing")
                .retrieve()
                .bodyToMono(CurrentlyPlayingResponse.class);
    }

    public Mono<SearchResponse> search(String query, String types, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", query)
                        .queryParam("type", types)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(SearchResponse.class);
    }
}
