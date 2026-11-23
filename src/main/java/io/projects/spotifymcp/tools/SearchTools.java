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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Component
public class SearchTools {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Set<String> VALID_TYPES = Set.of("track", "album", "artist", "playlist");

    private final SpotifyApiClient client;
    private final MyPlaylists myPlaylists;

    public SearchTools(SpotifyApiClient client, MyPlaylists myPlaylists) {
        this.client = client;
        this.myPlaylists = myPlaylists;
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
            description = "Add a track or episode to the end of the playback queue, given its Spotify URI "
                    + "(e.g. spotify:track:xxxxx, from search_catalog) or an open.spotify.com link.")
    public String addToQueue(
            @McpToolParam(description = "Spotify track/episode URI or open.spotify.com link", required = true)
            String trackUri) {
        Optional<SpotifyUris.SpotifyUri> parsed = SpotifyUris.parse(trackUri);
        if (parsed.isEmpty() || !parsed.get().isItem()) {
            return "Only tracks and episodes can be queued. Expected spotify:track:<22-character id> "
                    + "or an open.spotify.com/track/... link (got '" + trackUri + "').";
        }
        String uri = parsed.get().uri();
        return client.addToQueue(uri)
                .thenReturn("Added " + uri + " to the queue.")
                .onErrorResume(e -> Mono.just("Couldn't queue that track: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "play_playlist",
            description = "Play a playlist by name. Looks through the user's own and followed playlists "
                    + "first, then searches the Spotify catalog.")
    public String playPlaylist(
            @McpToolParam(description = "Playlist name, or a close match", required = true) String playlistName) {
        return myPlaylists.find(playlistName)
                .onErrorResume(e -> Mono.just(Optional.empty()))  // still try the catalog
                .flatMap(own -> own
                        .map(p -> client.playContext(p.uri())
                                .thenReturn("Now playing your playlist '" + p.name() + "'."))
                        .orElseGet(() -> playFromCatalog(playlistName)))
                .onErrorResume(e -> Mono.just("Couldn't play that playlist: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    private Mono<String> playFromCatalog(String playlistName) {
        // Ask for a few results: Spotify's playlist search can return null entries in `items`.
        return client.search(playlistName, "playlist", 5)
                .flatMap(response -> {
                    List<SearchResponse.PlaylistRef> items = nonNull(
                            response.playlists() == null ? null : response.playlists().items());
                    if (items.isEmpty()) {
                        return Mono.just("No playlist found matching '" + playlistName + "'.");
                    }
                    SearchResponse.PlaylistRef playlist = items.get(0);
                    return client.playContext(playlist.uri())
                            .thenReturn("Now playing playlist '" + playlist.name() + "'.");
                });
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

    private <T> List<String> mapItems(List<T> items, Function<T, String> formatter) {
        return nonNull(items).stream().map(formatter).toList();
    }

    private static <T> List<T> nonNull(List<T> items) {
        return items == null ? List.of() : items.stream().filter(Objects::nonNull).toList();
    }

    private String joinArtists(List<SearchResponse.ArtistRef> artists) {
        if (artists == null || artists.isEmpty()) {
            return "Unknown Artist";
        }
        return artists.stream().filter(Objects::nonNull).map(SearchResponse.ArtistRef::name).reduce((a, b) -> a + ", " + b).orElse("Unknown Artist");
    }
}
