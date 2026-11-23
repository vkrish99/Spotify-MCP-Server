package io.projects.spotifymcp.client;

import io.projects.spotifymcp.client.dto.AlbumTracksResponse;
import io.projects.spotifymcp.client.dto.CurrentlyPlayingResponse;
import io.projects.spotifymcp.client.dto.DevicesResponse;
import io.projects.spotifymcp.client.dto.MyPlaylistsResponse;
import io.projects.spotifymcp.client.dto.PlaylistResponse;
import io.projects.spotifymcp.client.dto.QueueResponse;
import io.projects.spotifymcp.client.dto.RecentlyPlayedResponse;
import io.projects.spotifymcp.client.dto.SavedTracksResponse;
import io.projects.spotifymcp.client.dto.SearchResponse;
import io.projects.spotifymcp.client.dto.TopArtistsResponse;
import io.projects.spotifymcp.client.dto.TopTracksResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Thin, non-blocking wrapper around the Spotify Web API endpoints the MCP tools need. */
@Component
public class SpotifyApiClient {

    private static final int MAX_ITEMS_PER_REQUEST = 100;

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
                .bodyValue(Map.of("device_ids", List.of(deviceId), "play", true))
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

    /** Plays specific tracks/episodes (PUT /me/player/play with {@code uris}). */
    public Mono<Void> playUris(List<String> uris) {
        return webClient.put()
                .uri("/me/player/play")
                .bodyValue(Map.of("uris", uris))
                .retrieve()
                .bodyToMono(Void.class);
    }

    public Mono<Void> seek(long positionMs) {
        return webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/player/seek")
                        .queryParam("position_ms", positionMs)
                        .build())
                .retrieve()
                .bodyToMono(Void.class);
    }

    public Mono<Void> setShuffle(boolean on) {
        return webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/player/shuffle")
                        .queryParam("state", on)
                        .build())
                .retrieve()
                .bodyToMono(Void.class);
    }

    /** {@code mode} is one of track, context, off. */
    public Mono<Void> setRepeat(String mode) {
        return webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/player/repeat")
                        .queryParam("state", mode)
                        .build())
                .retrieve()
                .bodyToMono(Void.class);
    }

    public Mono<QueueResponse> getQueue() {
        return webClient.get()
                .uri("/me/player/queue")
                .retrieve()
                .bodyToMono(QueueResponse.class);
    }

    public Mono<RecentlyPlayedResponse> getRecentlyPlayed(int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/player/recently-played")
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(RecentlyPlayedResponse.class);
    }

    /** {@code timeRange} is one of short_term, medium_term, long_term. */
    public Mono<TopTracksResponse> getTopTracks(String timeRange, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/top/tracks")
                        .queryParam("time_range", timeRange)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(TopTracksResponse.class);
    }

    /** {@code timeRange} is one of short_term, medium_term, long_term. */
    public Mono<TopArtistsResponse> getTopArtists(String timeRange, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/top/artists")
                        .queryParam("time_range", timeRange)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(TopArtistsResponse.class);
    }

    /** One page (max 50) of the playlists the user owns or follows. */
    public Mono<MyPlaylistsResponse> getMyPlaylists(int limit, int offset) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/playlists")
                        .queryParam("limit", limit)
                        .queryParam("offset", offset)
                        .build())
                .retrieve()
                .bodyToMono(MyPlaylistsResponse.class);
    }

    /** The user's Liked Songs, most recent first. */
    public Mono<SavedTracksResponse> getSavedTracks(int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/tracks")
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(SavedTracksResponse.class);
    }

    /**
     * Saves items to the user's library (PUT /me/library?uris=..., which replaced PUT /me/tracks
     * in the Feb 2026 API). A saved track is a "liked" song. Spotify accepts up to 40 URIs.
     */
    public Mono<Void> saveToLibrary(List<String> uris) {
        return webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/library")
                        .queryParam("uris", String.join(",", uris))
                        .build())
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

    public Mono<AlbumTracksResponse> getAlbumTracks(String albumId, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/albums/{id}/tracks")
                        .queryParam("limit", limit)
                        .build(albumId))
                .retrieve()
                .bodyToMono(AlbumTracksResponse.class);
    }

    /** Creates a playlist owned by the authorized user (POST /me/playlists, Feb 2026 API). */
    public Mono<PlaylistResponse> createPlaylist(String name, String description, boolean isPublic) {
        return webClient.post()
                .uri("/me/playlists")
                .bodyValue(Map.of(
                        "name", name,
                        "description", description,
                        "public", isPublic))
                .retrieve()
                .bodyToMono(PlaylistResponse.class);
    }

    /**
     * Appends items to a playlist (POST /playlists/{id}/items, which replaced /tracks in the
     * Feb 2026 API). Spotify accepts at most 100 URIs per request, so larger lists are sent in
     * order-preserving batches.
     */
    public Mono<Void> addItemsToPlaylist(String playlistId, List<String> uris) {
        List<List<String>> batches = new ArrayList<>();
        for (int start = 0; start < uris.size(); start += MAX_ITEMS_PER_REQUEST) {
            batches.add(uris.subList(start, Math.min(start + MAX_ITEMS_PER_REQUEST, uris.size())));
        }
        return Flux.fromIterable(batches)
                .concatMap(batch -> webClient.post()
                        .uri("/playlists/{playlist_id}/items", playlistId)
                        .bodyValue(Map.of("uris", batch))
                        .retrieve()
                        .bodyToMono(Void.class))
                .then();
    }
}
