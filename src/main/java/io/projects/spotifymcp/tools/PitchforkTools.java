package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.AlbumTracksResponse;
import io.projects.spotifymcp.client.dto.SearchResponse;
import io.projects.spotifymcp.pitchfork.PitchforkPick;
import io.projects.spotifymcp.pitchfork.PitchforkScraperService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Component
public class PitchforkTools {

    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    // Enough parallelism to keep ~20 picks quick without bursting into Spotify's rate limit.
    private static final int SEARCH_CONCURRENCY = 4;
    // A few results rather than 1: Spotify's search can return null entries in `items`.
    private static final int SEARCH_LIMIT = 3;
    private static final int DEFAULT_TRACKS_PER_ALBUM = 1;
    private static final int MAX_TRACKS_PER_ALBUM = 5;
    // Failures that mean the whole run is doomed, not that one pick is missing from Spotify.
    private static final Set<Integer> FATAL_STATUSES = Set.of(401, 403, 429);

    private final PitchforkScraperService pitchforkScraperService;
    private final SpotifyApiClient client;

    public PitchforkTools(PitchforkScraperService pitchforkScraperService, SpotifyApiClient client) {
        this.pitchforkScraperService = pitchforkScraperService;
        this.client = client;
    }

    @McpTool(name = "create_pitchfork_playlist",
            description = "Fetch Pitchfork's current Best New Albums/Tracks picks, match them against "
                    + "the Spotify catalog, and create a new private playlist from whatever is found. "
                    + "Albums contribute their first track(s). This scrapes a public webpage rather "
                    + "than an official API, so results can be incomplete if Pitchfork's page layout "
                    + "has changed since this tool was built.")
    public String createPitchforkPlaylist(
            @McpToolParam(description = "Name for the new playlist (optional)", required = false)
            String playlistName,
            @McpToolParam(description = "Tracks to add from each Best New Album, 1-5 (optional, default 1)",
                    required = false)
            Integer tracksPerAlbum) {
        String name = (playlistName == null || playlistName.isBlank())
                ? "Pitchfork Best New Music - " + LocalDate.now()
                : playlistName;
        int perAlbum = tracksPerAlbum == null
                ? DEFAULT_TRACKS_PER_ALBUM
                : Math.clamp(tracksPerAlbum, 1, MAX_TRACKS_PER_ALBUM);

        return pitchforkScraperService.fetchBestNewMusic()
                .flatMap(picks -> picks.isEmpty()
                        ? Mono.just("Couldn't extract any picks from Pitchfork's page right now -- "
                                + "its layout may have changed since this tool was built. No playlist was created.")
                        : buildPlaylist(name, picks, perAlbum))
                .onErrorResume(e -> Mono.just("Couldn't build the playlist: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    private Mono<String> buildPlaylist(String name, List<PitchforkPick> picks, int perAlbum) {
        // Only create a playlist once we know there's something to put in it.
        return matchOnSpotify(picks, perAlbum)
                .flatMap(matches -> {
                    if (matches.uris().isEmpty()) {
                        return Mono.just("Found " + picks.size() + " Pitchfork picks, but none of them "
                                + "matched anything on Spotify. No playlist was created.");
                    }
                    String description = "Auto-generated from Pitchfork's Best New Music (" + picks.size()
                            + " picks found, " + matches.matchedPicks() + " matched on Spotify).";
                    return client.createPlaylist(name, description, false)
                            .flatMap(playlist -> client.addItemsToPlaylist(playlist.id(), matches.uris())
                                    .thenReturn(summarize(name, picks, matches)));
                });
    }

    private Mono<MatchResult> matchOnSpotify(List<PitchforkPick> picks, int perAlbum) {
        // flatMapSequential keeps Pitchfork's page order (its ranking) in the playlist.
        return Flux.fromIterable(picks)
                .flatMapSequential(pick -> urisFor(pick, perAlbum).map(uris -> Map.entry(pick, uris)),
                        SEARCH_CONCURRENCY)
                .collectList()
                .map(this::toMatchResult);
    }

    private MatchResult toMatchResult(List<Map.Entry<PitchforkPick, List<String>>> results) {
        List<String> uris = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        int matchedPicks = 0;
        for (Map.Entry<PitchforkPick, List<String>> result : results) {
            if (result.getValue().isEmpty()) {
                PitchforkPick pick = result.getKey();
                unmatched.add(pick.artist().isBlank() ? pick.title() : pick.artist() + " - " + pick.title());
            } else {
                matchedPicks++;
                uris.addAll(result.getValue());
            }
        }
        return new MatchResult(uris, matchedPicks, unmatched);
    }

    /** Track URIs for one pick; empty when it isn't on Spotify. */
    private Mono<List<String>> urisFor(PitchforkPick pick, int perAlbum) {
        boolean isAlbum = "album".equals(pick.type());
        Mono<List<String>> uris = isAlbum
                ? findFirst(pick, "album", response -> response.albums() == null ? null : response.albums().items(),
                        SearchResponse.AlbumRef::id)
                        .flatMap(albumId -> client.getAlbumTracks(albumId, perAlbum))
                        .map(this::trackUris)
                : findFirst(pick, "track", response -> response.tracks() == null ? null : response.tracks().items(),
                        SearchResponse.Track::uri)
                        .map(List::of);

        return uris
                .defaultIfEmpty(List.of())
                .onErrorResume(e -> !isFatal(e), e -> Mono.just(List.of()));
    }

    /**
     * Searches with Spotify field filters first ({@code album:X artist:Y}), which avoids matching a
     * same-titled release by someone else, then falls back to a plain keyword search in case the
     * scraped artist name is off.
     */
    private <T> Mono<String> findFirst(PitchforkPick pick, String type,
                                       Function<SearchResponse, List<T>> items, Function<T, String> field) {
        String filtered = type + ":" + pick.title() + (pick.artist().isBlank() ? "" : " artist:" + pick.artist());
        String plain = (pick.artist() + " " + pick.title()).trim();
        return search(filtered, type, items, field)
                .switchIfEmpty(Mono.defer(() -> search(plain, type, items, field)));
    }

    private <T> Mono<String> search(String query, String type,
                                    Function<SearchResponse, List<T>> items, Function<T, String> field) {
        return client.search(query, type, SEARCH_LIMIT)
                .flatMap(response -> Mono.justOrEmpty(firstNonNull(items.apply(response), field)));
    }

    private static <T> String firstNonNull(List<T> items, Function<T, String> field) {
        if (items == null) {
            return null;
        }
        return items.stream().filter(Objects::nonNull).map(field).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private List<String> trackUris(AlbumTracksResponse response) {
        if (response.items() == null) {
            return List.of();
        }
        return response.items().stream()
                .filter(Objects::nonNull)
                .map(AlbumTracksResponse.Track::uri)
                .filter(Objects::nonNull)
                .toList();
    }

    private static boolean isFatal(Throwable e) {
        return e instanceof IllegalStateException
                || (e instanceof WebClientResponseException http && FATAL_STATUSES.contains(http.getStatusCode().value()));
    }

    private String summarize(String playlistName, List<PitchforkPick> picks, MatchResult matches) {
        StringBuilder summary = new StringBuilder()
                .append("Created playlist '").append(playlistName).append("' with ")
                .append(matches.uris().size()).append(matches.uris().size() == 1 ? " track" : " tracks")
                .append(" from ").append(matches.matchedPicks()).append(" of ").append(picks.size())
                .append(" Pitchfork picks matched on Spotify.");
        if (!matches.unmatched().isEmpty()) {
            summary.append("\nCouldn't find on Spotify: ").append(String.join(", ", matches.unmatched()));
        }
        return summary.toString();
    }

    private record MatchResult(List<String> uris, int matchedPicks, List<String> unmatched) {
    }
}
