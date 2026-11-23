package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.MyPlaylistsResponse;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The user's own library: playlists and Liked Songs. */
@Component
public class LibraryTools {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final SpotifyApiClient client;
    private final MyPlaylists myPlaylists;

    public LibraryTools(SpotifyApiClient client, MyPlaylists myPlaylists) {
        this.client = client;
        this.myPlaylists = myPlaylists;
    }

    @McpTool(name = "list_my_playlists",
            description = "List the user's own and followed Spotify playlists, with track counts and URIs.")
    public String listMyPlaylists() {
        return myPlaylists.all()
                .map(playlists -> {
                    if (playlists.isEmpty()) {
                        return "You don't have any playlists yet.";
                    }
                    return StateTools.numbered(playlists.stream().map(this::describe).toList());
                })
                .onErrorResume(e -> Mono.just("Couldn't list your playlists: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "add_to_playlist",
            description = "Add a track or episode to one of the user's own playlists, found by name. "
                    + "Accepts a Spotify URI or open.spotify.com link.")
    public String addToPlaylist(
            @McpToolParam(description = "Name of one of the user's playlists, or part of it", required = true)
            String playlistName,
            @McpToolParam(description = "Track/episode URI or open.spotify.com link", required = true)
            String trackUri) {
        Optional<SpotifyUris.SpotifyUri> parsed = SpotifyUris.parse(trackUri);
        if (parsed.isEmpty() || !parsed.get().isItem()) {
            return "Expected a track or episode URI or link (got '" + trackUri + "').";
        }
        String uri = parsed.get().uri();
        return myPlaylists.find(playlistName)
                .flatMap(match -> match
                        .map(playlist -> client.addItemsToPlaylist(playlist.id(), List.of(uri))
                                .thenReturn("Added " + uri + " to '" + playlist.name() + "'."))
                        .orElseGet(() -> Mono.just("None of your playlists match '" + playlistName
                                + "'. Use list_my_playlists to see their names.")))
                .onErrorResume(e -> Mono.just("Couldn't add to that playlist: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "like_current_track",
            description = "Save the currently playing track to the user's Liked Songs.")
    public String likeCurrentTrack() {
        return client.getCurrentlyPlaying()
                .flatMap(now -> {
                    if (now.item() == null || now.item().uri() == null) {
                        return Mono.just("Nothing is playing, so there's nothing to like.");
                    }
                    return client.saveToLibrary(List.of(now.item().uri()))
                            .thenReturn("Added '" + now.item().name() + "' to your Liked Songs.");
                })
                .defaultIfEmpty("Nothing is playing, so there's nothing to like.")
                .onErrorResume(e -> Mono.just("Couldn't like that track: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "get_liked_tracks",
            description = "List the most recently liked songs (the user's Liked Songs), newest first.")
    public String getLikedTracks(
            @McpToolParam(description = "How many tracks, 1-50 (default 10)", required = false) Integer limit) {
        return client.getSavedTracks(StateTools.clamp(limit, 10, 50))
                .map(response -> {
                    List<String> lines = response.items() == null ? List.of() : response.items().stream()
                            .filter(item -> item != null && item.track() != null)
                            .map(item -> item.track().describe() + " -- " + item.track().uri())
                            .toList();
                    if (lines.isEmpty()) {
                        return "Your Liked Songs is empty.";
                    }
                    String total = response.total() == null ? "" : " (" + response.total() + " liked in total)";
                    return StateTools.numbered(lines) + total;
                })
                .onErrorResume(e -> Mono.just("Couldn't read your Liked Songs: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    private String describe(MyPlaylistsResponse.Playlist p) {
        List<String> details = new ArrayList<>();
        if (p.owner() != null && p.owner().displayName() != null) {
            details.add("by " + p.owner().displayName());
        }
        if (p.items() != null && p.items().total() != null) {
            details.add(p.items().total() + " items");
        }
        String suffix = details.isEmpty() ? "" : " (" + String.join(", ", details) + ")";
        return Objects.requireNonNullElse(p.name(), "(untitled)") + suffix + " -- " + p.uri();
    }
}
