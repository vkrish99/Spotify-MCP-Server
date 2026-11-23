package io.projects.spotifymcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SavedTracksResponse(List<Item> items, Integer total) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String addedAt, PlayableItem track) {
    }
}
