package io.projects.spotifymcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlbumTracksResponse(List<Track> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Track(String id, String name, String uri) {
    }
}
