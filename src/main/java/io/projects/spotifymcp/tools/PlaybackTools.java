package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.DevicesResponse;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class PlaybackTools {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final SpotifyApiClient client;

    public PlaybackTools(SpotifyApiClient client) {
        this.client = client;
    }

    @McpTool(name = "pause_playback", description = "Pause Spotify playback on the current active device.")
    public String pausePlayback() {
        return client.pause()
                .thenReturn("Playback paused.")
                .onErrorResume(e -> Mono.just("Couldn't pause: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "resume_playback", description = "Resume/unpause Spotify playback on the current active device.")
    public String resumePlayback() {
        return client.resume()
                .thenReturn("Playback resumed.")
                .onErrorResume(e -> Mono.just("Couldn't resume: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "next_track", description = "Skip to the next track in the current queue.")
    public String nextTrack() {
        return client.next()
                .thenReturn("Skipped to the next track.")
                .onErrorResume(e -> Mono.just("Couldn't skip forward: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "previous_track", description = "Go back to the previous track.")
    public String previousTrack() {
        return client.previous()
                .thenReturn("Went back to the previous track.")
                .onErrorResume(e -> Mono.just("Couldn't skip back: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "set_volume", description = "Set Spotify playback volume, as a percentage from 0 to 100.")
    public String setVolume(
            @McpToolParam(description = "Target volume, 0-100", required = true) int percent) {
        if (percent < 0 || percent > 100) {
            return "Volume must be between 0 and 100 (got " + percent + ").";
        }
        return client.setVolume(percent)
                .thenReturn("Volume set to " + percent + "%.")
                .onErrorResume(e -> Mono.just("Couldn't set volume: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "transfer_playback",
            description = "Move playback to a different device by name, e.g. 'MacBook Pro' or 'Living Room Echo'.")
    public String transferPlayback(
            @McpToolParam(description = "Device name, or a substring of it", required = true) String deviceName) {
        return client.getDevices()
                .flatMap(devices -> {
                    Optional<DevicesResponse.Device> match = findDevice(devices, deviceName);
                    if (match.isEmpty()) {
                        return Mono.just("No device matching '" + deviceName + "' found. "
                                + "Available devices: " + describeDevices(devices));
                    }
                    return client.transferPlayback(match.get().id())
                            .thenReturn("Switched playback to " + match.get().name() + ".");
                })
                .onErrorResume(e -> Mono.just("Couldn't transfer playback: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    private Optional<DevicesResponse.Device> findDevice(DevicesResponse devices, String name) {
        List<DevicesResponse.Device> list = devices.devices() == null ? List.of() : devices.devices();
        String needle = name.toLowerCase(Locale.ROOT);
        return list.stream()
                .filter(d -> d.name() != null && d.name().toLowerCase(Locale.ROOT).contains(needle))
                .findFirst();
    }

    private String describeDevices(DevicesResponse devices) {
        List<DevicesResponse.Device> list = devices.devices() == null ? List.of() : devices.devices();
        if (list.isEmpty()) {
            return "none currently connected.";
        }
        return list.stream().map(DevicesResponse.Device::name).reduce((a, b) -> a + ", " + b).orElse("none");
    }
}
