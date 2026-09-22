package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.CurrentlyPlayingResponse;
import io.projects.spotifymcp.client.dto.DevicesResponse;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
public class StateTools {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

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
