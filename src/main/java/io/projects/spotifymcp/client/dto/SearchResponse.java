package io.projects.spotifymcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResponse(
        TracksPage tracks,
        AlbumsPage albums,
        ArtistsPage artists,
        PlaylistsPage playlists
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TracksPage(List<Track> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Track(String id, String name, String uri, List<ArtistRef> artists, AlbumRef album) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlbumsPage(List<AlbumRef> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlbumRef(String id, String name, String uri) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArtistsPage(List<ArtistRef> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArtistRef(String id, String name, String uri) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlaylistsPage(List<PlaylistRef> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlaylistRef(String id, String name, String uri, OwnerRef owner) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OwnerRef(String displayName) {
    }
}
