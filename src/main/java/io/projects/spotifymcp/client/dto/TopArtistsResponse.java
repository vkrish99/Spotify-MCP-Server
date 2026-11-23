package io.projects.spotifymcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TopArtistsResponse(List<Artist> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artist(String name, String uri, List<String> genres) {
    }
}
