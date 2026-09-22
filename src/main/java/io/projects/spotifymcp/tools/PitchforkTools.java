package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.pitchfork.PitchforkPick;
import io.projects.spotifymcp.pitchfork.PitchforkScraperService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class PitchforkTools {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final PitchforkScraperService pitchforkScraperService;
    private final SpotifyApiClient client;

    public PitchforkTools(PitchforkScraperService pitchforkScraperService, SpotifyApiClient client) {
        this.pitchforkScraperService = pitchforkScraperService;
        this.client = client;
    }

    @McpTool(name = "create_pitchfork_playlist",
            description = "Fetch Pitchfork's current Best New Albums/Tracks picks, match them against "
                    + "the Spotify catalog, and create a new playlist from whatever is found. This "
                    + "scrapes a public webpage rather than an official API, so results can be "
                    + "incomplete if Pitchfork's page layout has changed since this tool was built.")
    public String createPitchforkPlaylist(
            @McpToolParam(description = "Name for the new playlist (optional)", required = false)
            String playlistName) {
        String name = (playlistName == null || playlistName.isBlank())
                ? "Pitchfork Best New Music - " + LocalDate.now()
                : playlistName;

        return pitchforkScraperService.fetchBestNewMusic()
                .flatMap(picks -> picks.isEmpty()
                        ? Mono.just("Couldn't extract any picks from Pitchfork's page right now -- "
                                + "its layout may have changed since this tool was built. No playlist was created.")
                        : buildPlaylist(name, picks))
                .onErrorResume(e -> Mono.just("Couldn't build the playlist: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    private Mono<String> buildPlaylist(String name, List<PitchforkPick> picks) {
        // Only touch /me and create a playlist once we know there's something to put in it --
        // avoids a wasted Spotify call on the common "nothing matched" path.
        return matchOnSpotify(picks)
                .flatMap(matches -> {
                    if (matches.trackUris().isEmpty()) {
                        return Mono.just("Found " + picks.size() + " Pitchfork picks, but none of them "
                                + "matched anything on Spotify. No playlist was created.");
                    }
                    String description = "Auto-generated from Pitchfork's Best New Music (" + picks.size()
                            + " picks found, " + matches.trackUris().size() + " matched on Spotify).";
                    return client.getCurrentUser()
                            .flatMap(user -> client.createPlaylist(user.id(), name, description, false))
                            .flatMap(playlist -> client.addTracksToPlaylist(playlist.id(), matches.trackUris())
                                    .thenReturn(summarize(name, picks, matches)));
                });
    }

    private Mono<MatchResult> matchOnSpotify(List<PitchforkPick> picks) {
        List<Mono<Optional<String>>> searches = picks.stream().map(this::searchForPick).toList();
        return Mono.zip(searches, results -> toMatchResult(picks, results));
    }

    // Reactor's Mono.map()/zip() forbid null values, so each search resolves to an
    // Optional<String> (empty = no Spotify match) rather than a nullable String.
    private MatchResult toMatchResult(List<PitchforkPick> picks, Object[] results) {
        List<String> uris = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        for (int i = 0; i < results.length; i++) {
            @SuppressWarnings("unchecked")
            Optional<String> match = (Optional<String>) results[i];
            if (match.isPresent()) {
                uris.add(match.get());
            } else {
                PitchforkPick pick = picks.get(i);
                unmatched.add(pick.artist() + " - " + pick.title());
            }
        }
        return new MatchResult(uris, unmatched);
    }

    private Mono<Optional<String>> searchForPick(PitchforkPick pick) {
        String query = (pick.artist() + " " + pick.title()).trim();
        return client.search(query, "track", 1)
                .map(response -> {
                    if (response.tracks() == null || response.tracks().items() == null
                            || response.tracks().items().isEmpty()) {
                        return Optional.<String>empty();
                    }
                    return Optional.of(response.tracks().items().get(0).uri());
                })
                .onErrorReturn(Optional.empty());
    }

    private String summarize(String playlistName, List<PitchforkPick> picks, MatchResult matches) {
        StringBuilder summary = new StringBuilder()
                .append("Created playlist '").append(playlistName).append("' with ")
                .append(matches.trackUris().size()).append(" of ").append(picks.size())
                .append(" Pitchfork picks matched on Spotify.");
        if (!matches.unmatched().isEmpty()) {
            summary.append("\nCouldn't find on Spotify: ").append(String.join(", ", matches.unmatched()));
        }
        return summary.toString();
    }

    private record MatchResult(List<String> trackUris, List<String> unmatched) {
    }
}
