package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.SearchResponse;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class SearchTools {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Set<String> VALID_TYPES = Set.of("track", "album", "artist", "playlist");

    private final SpotifyApiClient client;

    public SearchTools(SpotifyApiClient client) {
        this.client = client;
    }

    @McpTool(name = "search_catalog",
            description = "Search the Spotify catalog for tracks, albums, artists, or playlists. "
                    + "Returns names and Spotify URIs, which can be fed into add_to_queue or play_playlist.")
    public String searchCatalog(
            @McpToolParam(description = "Free-text search query", required = true) String query,
            @McpToolParam(description = "One of: track, album, artist, playlist", required = true) String type) {
        String normalizedType = type == null ? "" : type.toLowerCase(Locale.ROOT).trim();
        if (!VALID_TYPES.contains(normalizedType)) {
            return "type must be one of " + VALID_TYPES + " (got '" + type + "').";
        }

        return client.search(query, normalizedType, 10)
                .map(response -> formatResults(normalizedType, response))
                .onErrorResume(e -> Mono.just("Search failed: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "add_to_queue",
            description = "Add a track to the end of the current playback queue, given its Spotify track URI "
                    + "(e.g. spotify:track:xxxxx, from search_catalog).")
    public String addToQueue(
            @McpToolParam(description = "Spotify track URI", required = true) String trackUri) {
        return client.addToQueue(trackUri)
                .thenReturn("Added " + trackUri + " to the queue.")
                .onErrorResume(e -> Mono.just("Couldn't queue that track: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "play_playlist",
            description = "Search for a playlist by name and start playing it on the current active device.")
    public String playPlaylist(
            @McpToolParam(description = "Playlist name, or a close match", required = true) String playlistName) {
        return client.search(playlistName, "playlist", 1)
                .flatMap(response -> {
                    List<SearchResponse.PlaylistRef> items = response.playlists() == null
                            ? List.of() : response.playlists().items();
                    if (items == null || items.isEmpty()) {
                        return Mono.just("No playlist found matching '" + playlistName + "'.");
                    }
                    SearchResponse.PlaylistRef playlist = items.get(0);
                    return client.playContext(playlist.uri())
                            .thenReturn("Now playing playlist '" + playlist.name() + "'.");
                })
                .onErrorResume(e -> Mono.just("Couldn't play that playlist: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    private String formatResults(String type, SearchResponse response) {
        List<String> lines = switch (type) {
            case "track" -> mapItems(response.tracks() == null ? null : response.tracks().items(),
                    t -> t.name() + " by " + joinArtists(t.artists()) + " -- " + t.uri());
            case "album" -> mapItems(response.albums() == null ? null : response.albums().items(),
                    a -> a.name() + " -- " + a.uri());
            case "artist" -> mapItems(response.artists() == null ? null : response.artists().items(),
                    a -> a.name() + " -- " + a.uri());
            case "playlist" -> mapItems(response.playlists() == null ? null : response.playlists().items(),
                    p -> p.name() + " -- " + p.uri());
            default -> List.of();
        };
        if (lines.isEmpty()) {
            return "No " + type + " results found.";
        }
        return String.join("\n", lines);
    }

    private <T> List<String> mapItems(List<T> items, java.util.function.Function<T, String> formatter) {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(formatter).toList();
    }

    private String joinArtists(List<SearchResponse.ArtistRef> artists) {
        if (artists == null || artists.isEmpty()) {
            return "Unknown Artist";
        }
        return artists.stream().map(SearchResponse.ArtistRef::name).reduce((a, b) -> a + ", " + b).orElse("Unknown Artist");
    }
}
