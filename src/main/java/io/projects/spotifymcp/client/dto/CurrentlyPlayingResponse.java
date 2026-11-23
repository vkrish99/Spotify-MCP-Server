package io.projects.spotifymcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentlyPlayingResponse(
        Boolean isPlaying,
        Long progressMs,
        Item item
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String name, String uri, List<ArtistRef> artists, AlbumRef album, Long durationMs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArtistRef(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlbumRef(String name) {
    }
}
