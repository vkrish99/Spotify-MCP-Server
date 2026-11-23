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
import java.util.Set;

@Component
public class PlaybackTools {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Set<String> REPEAT_MODES = Set.of("track", "context", "off");

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

    @McpTool(name = "resume_playback",
            description = "Resume/unpause Spotify playback. If no device is active and exactly one is "
                    + "available, playback resumes there.")
    public String resumePlayback() {
        return client.resume()
                .thenReturn("Playback resumed.")
                .onErrorResume(ToolErrors::isNoActiveDevice, e -> resumeOnOnlyDevice())
                .onErrorResume(e -> Mono.just("Couldn't resume: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    /** No device is active: wake the only available one, or say which ones there are. */
    private Mono<String> resumeOnOnlyDevice() {
        return client.getDevices().flatMap(devices -> {
            List<DevicesResponse.Device> list = devices.devices() == null ? List.of() : devices.devices();
            if (list.size() == 1) {
                DevicesResponse.Device only = list.get(0);
                return client.transferPlayback(only.id())
                        .thenReturn("No device was active, so playback resumed on " + only.name() + ".");
            }
            if (list.isEmpty()) {
                return Mono.just("Couldn't resume: " + ToolErrors.NO_DEVICE);
            }
            return Mono.just("No device is active. Available devices: " + describeDevices(devices)
                    + ". Use transfer_playback to pick one.");
        });
    }

    @McpTool(name = "play",
            description = "Play something by its Spotify URI or open.spotify.com link: a track or episode "
                    + "plays on its own; an album, artist, playlist or show plays from the start. "
                    + "Get URIs from search_catalog, list_my_playlists, or get_top_items.")
    public String play(
            @McpToolParam(description = "Spotify URI (spotify:album:...) or open.spotify.com link",
                    required = true) String uri) {
        Optional<SpotifyUris.SpotifyUri> parsed = SpotifyUris.parse(uri);
        if (parsed.isEmpty()) {
            return "That isn't a Spotify URI or link (got '" + uri + "'). Expected something like "
                    + "spotify:track:<22-character id> or https://open.spotify.com/album/<id>.";
        }
        SpotifyUris.SpotifyUri target = parsed.get();
        Mono<Void> call = target.isItem()
                ? client.playUris(List.of(target.uri()))
                : client.playContext(target.uri());
        return call
                .thenReturn("Playing " + target.type() + " " + target.uri() + ".")
                .onErrorResume(e -> Mono.just("Couldn't play that: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "seek", description = "Jump to a position in the current track, in seconds from the start.")
    public String seek(
            @McpToolParam(description = "Position in seconds, 0 or more", required = true) int positionSeconds) {
        if (positionSeconds < 0) {
            return "Position must be 0 seconds or more (got " + positionSeconds + ").";
        }
        return client.seek(positionSeconds * 1000L)
                .thenReturn("Jumped to %d:%02d.".formatted(positionSeconds / 60, positionSeconds % 60))
                .onErrorResume(e -> Mono.just("Couldn't seek: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "set_shuffle", description = "Turn shuffle on or off for the current playback.")
    public String setShuffle(
            @McpToolParam(description = "true to shuffle, false to play in order", required = true) boolean on) {
        return client.setShuffle(on)
                .thenReturn(on ? "Shuffle is on." : "Shuffle is off.")
                .onErrorResume(e -> Mono.just("Couldn't change shuffle: " + ToolErrors.describe(e)))
                .block(TIMEOUT);
    }

    @McpTool(name = "set_repeat",
            description = "Set repeat mode: 'track' repeats the current song, 'context' repeats the "
                    + "album/playlist, 'off' stops repeating.")
    public String setRepeat(
            @McpToolParam(description = "One of: track, context, off", required = true) String mode) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (!REPEAT_MODES.contains(normalized)) {
            return "Repeat mode must be one of track, context, off (got '" + mode + "').";
        }
        return client.setRepeat(normalized)
                .thenReturn(switch (normalized) {
                    case "track" -> "Repeating the current track.";
                    case "context" -> "Repeating the current album or playlist.";
                    default -> "Repeat is off.";
                })
                .onErrorResume(e -> Mono.just("Couldn't change repeat: " + ToolErrors.describe(e)))
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
