package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.CurrentlyPlayingResponse;
import io.projects.spotifymcp.client.dto.DevicesResponse;
import io.projects.spotifymcp.client.dto.QueueResponse;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Component
public class StateTools {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final int QUEUE_PREVIEW = 10;
    private static final Set<String> TIME_RANGES = Set.of("short_term", "medium_term", "long_term");

    private final SpotifyApiClient client;

    public StateTools(SpotifyApiClient client) {
        this.client = client;
    }

    @McpTool(name = "get_currently_playing",
            description = "Get the track, artist, album, playback progress, and paused/playing state "
                    + "currently on Spotify.")
    public String getCurrentlyPlaying() {
        return client.getCurrentlyPlaying()
                .map(this::describe)
                .defaultIfEmpty("Nothing is currently playing.")
                .onErrorResume(e -> Mono.just("Couldn't read playback state: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "get_active_devices",
            description = "List Spotify Connect devices currently available (laptop, phone, speakers, etc.) "
                    + "and which one, if any, is active.")
    public String getActiveDevices() {
        return client.getDevices()
                .map(this::describeDevices)
                .onErrorResume(e -> Mono.just("Couldn't list devices: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "get_queue",
            description = "Show what's playing now and the next tracks in the Spotify queue.")
    public String getQueue() {
        return client.getQueue()
                .map(this::describeQueue)
                .onErrorResume(e -> Mono.just("Couldn't read the queue: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "get_recently_played",
            description = "List the tracks the user played most recently, newest first.")
    public String getRecentlyPlayed(
            @McpToolParam(description = "How many tracks, 1-50 (default 10)", required = false) Integer limit) {
        int n = clamp(limit, 10, 50);
        return client.getRecentlyPlayed(n)
                .map(response -> {
                    List<String> lines = response.items() == null ? List.of() : response.items().stream()
                            .filter(item -> item != null && item.track() != null)
                            .map(item -> item.track().describe() + " -- " + item.track().uri()
                                    + (item.playedAt() == null ? "" : " (played " + item.playedAt() + ")"))
                            .toList();
                    return lines.isEmpty() ? "No recently played tracks." : numbered(lines);
                })
                .onErrorResume(e -> Mono.just("Couldn't read listening history: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "get_top_items",
            description = "The user's most-played tracks or artists over a time range: short_term "
                    + "(about 4 weeks), medium_term (about 6 months), or long_term (about a year).")
    public String getTopItems(
            @McpToolParam(description = "tracks or artists", required = true) String type,
            @McpToolParam(description = "short_term, medium_term (default) or long_term", required = false)
            String timeRange,
            @McpToolParam(description = "How many, 1-50 (default 10)", required = false) Integer limit) {
        String kind = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        String range = timeRange == null || timeRange.isBlank()
                ? "medium_term" : timeRange.trim().toLowerCase(Locale.ROOT);
        if (!kind.equals("tracks") && !kind.equals("artists")) {
            return "type must be 'tracks' or 'artists' (got '" + type + "').";
        }
        if (!TIME_RANGES.contains(range)) {
            return "timeRange must be one of " + TIME_RANGES + " (got '" + timeRange + "').";
        }
        int n = clamp(limit, 10, 50);
        Mono<List<String>> lines = kind.equals("tracks")
                ? client.getTopTracks(range, n).map(r -> r.items() == null ? List.<String>of() : r.items().stream()
                        .filter(Objects::nonNull)
                        .map(t -> t.describe() + " -- " + t.uri())
                        .toList())
                : client.getTopArtists(range, n).map(r -> r.items() == null ? List.<String>of() : r.items().stream()
                        .filter(Objects::nonNull)
                        .map(a -> a.name() + genres(a.genres()) + " -- " + a.uri())
                        .toList());
        return lines
                .map(list -> list.isEmpty()
                        ? "Spotify has no top " + kind + " for that time range yet."
                        : numbered(list))
                .onErrorResume(e -> Mono.just("Couldn't read top " + kind + ": " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    private String describeQueue(QueueResponse response) {
        StringBuilder out = new StringBuilder();
        out.append(response.currentlyPlaying() == null
                ? "Nothing is playing right now."
                : "Now playing: " + response.currentlyPlaying().describe());
        List<String> next = response.queue() == null ? List.of() : response.queue().stream()
                .filter(Objects::nonNull)
                .limit(QUEUE_PREVIEW)
                .map(item -> item.describe() + " -- " + item.uri())
                .toList();
        out.append(next.isEmpty() ? "\nThe queue is empty." : "\nUp next:\n" + numbered(next));
        return out.toString();
    }

    private static String genres(List<String> genres) {
        return genres == null || genres.isEmpty() ? "" : " (" + String.join(", ", genres.subList(0, Math.min(3, genres.size()))) + ")";
    }

    static int clamp(Integer value, int fallback, int max) {
        if (value == null) {
            return fallback;
        }
        return Math.max(1, Math.min(max, value));
    }

    static String numbered(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(i + 1).append(". ").append(lines.get(i));
        }
        return out.toString();
    }

    private String describe(CurrentlyPlayingResponse response) {
        if (response.item() == null) {
            return "Nothing is currently playing.";
        }
        String artists = response.item().artists() == null || response.item().artists().isEmpty()
                ? "Unknown Artist"
                : response.item().artists().stream()
                        .map(CurrentlyPlayingResponse.ArtistRef::name)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("Unknown Artist");
        String album = response.item().album() == null ? "" : " (" + response.item().album().name() + ")";
        String state = Boolean.TRUE.equals(response.isPlaying()) ? "Playing" : "Paused";
        String progress = formatProgress(response.progressMs(), response.item().durationMs());

        return "%s: '%s' by %s%s%s".formatted(state, response.item().name(), artists, album, progress);
    }

    private String formatProgress(Long progressMs, Long durationMs) {
        if (progressMs == null || durationMs == null) {
            return "";
        }
        return " [%s / %s]".formatted(formatMillis(progressMs), formatMillis(durationMs));
    }

    private String formatMillis(long millis) {
        long totalSeconds = millis / 1000;
        return "%d:%02d".formatted(totalSeconds / 60, totalSeconds % 60);
    }

    private String describeDevices(DevicesResponse response) {
        List<DevicesResponse.Device> devices = response.devices();
        if (devices == null || devices.isEmpty()) {
            return "No devices currently connected to Spotify Connect.";
        }
        return devices.stream()
                .map(d -> "%s (%s%s)".formatted(
                        d.name(),
                        d.type(),
                        Boolean.TRUE.equals(d.isActive()) ? ", active" : ""))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("No devices found.");
    }
}
