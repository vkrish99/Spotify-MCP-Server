package io.projects.spotifymcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A track or podcast episode, as returned in the queue, recently-played, saved-tracks and
 * top-tracks responses. Episodes have a {@code show} instead of {@code artists}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayableItem(
        String id,
        String name,
        String uri,
        String type,
        List<ArtistRef> artists,
        AlbumRef album,
        ShowRef show,
        Long durationMs
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArtistRef(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlbumRef(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShowRef(String name) {
    }

    /** "Song by Artist", or "Episode (Show)" for podcast episodes. */
    public String describe() {
        if (show != null && show.name() != null) {
            return name + " (" + show.name() + ")";
        }
        String by = artists == null ? "" : artists.stream()
                .filter(Objects::nonNull)
                .map(ArtistRef::name)
                .collect(Collectors.joining(", "));
        return by.isEmpty() ? name : name + " by " + by;
    }
}
