package io.projects.spotifymcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** GET /me/playlists. Since Feb 2026 the item count is under {@code items}, not {@code tracks}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MyPlaylistsResponse(List<Playlist> items, String next, Integer total) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Playlist(String id, String name, String uri, Owner owner, ItemCount items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(String displayName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemCount(Integer total) {
    }
}
