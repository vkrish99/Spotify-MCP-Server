package io.projects.spotifymcp.resources;

import io.projects.spotifymcp.tools.StateTools;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * Exposes playback state as MCP resources (not just tools) -- a client can
 * subscribe to / read these directly instead of invoking a tool call, which
 * is the more idiomatic MCP shape for read-only, always-available state.
 * Backed by {@link StateTools} so the formatting logic lives in one place.
 */
@Component
public class SpotifyResources {

    private final StateTools stateTools;

    public SpotifyResources(StateTools stateTools) {
        this.stateTools = stateTools;
    }

    @McpResource(
            uri = "spotify://now-playing",
            name = "Currently Playing",
            description = "The track, artist, album, and playback state currently on Spotify.",
            mimeType = "text/plain")
    public String nowPlaying() {
        return stateTools.getCurrentlyPlaying();
    }

    @McpResource(
            uri = "spotify://devices",
            name = "Active Devices",
            description = "Spotify Connect devices currently available and which one is active.",
            mimeType = "text/plain")
    public String devices() {
        return stateTools.getActiveDevices();
    }
}
