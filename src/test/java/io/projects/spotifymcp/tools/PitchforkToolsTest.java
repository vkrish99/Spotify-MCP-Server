package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.AlbumTracksResponse;
import io.projects.spotifymcp.client.dto.PlaylistResponse;
import io.projects.spotifymcp.client.dto.SearchResponse;
import io.projects.spotifymcp.pitchfork.PitchforkPick;
import io.projects.spotifymcp.pitchfork.PitchforkScraperService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PitchforkToolsTest {

    private PitchforkScraperService scraper;
    private SpotifyApiClient client;
    private PitchforkTools tools;

    @BeforeEach
    void setUp() {
        scraper = mock(PitchforkScraperService.class);
        client = mock(SpotifyApiClient.class);
        tools = new PitchforkTools(scraper, client);
        // Anything not stubbed explicitly finds nothing.
        when(client.search(anyString(), anyString(), anyInt())).thenReturn(Mono.just(emptySearch()));
        when(client.createPlaylist(anyString(), anyString(), eq(false)))
                .thenReturn(Mono.just(new PlaylistResponse("playlist-1", "name", "spotify:playlist:1")));
        when(client.addItemsToPlaylist(anyString(), anyList())).thenReturn(Mono.empty());
    }

    @Test
    void reportsWhenNoPicksAreExtracted() {
        when(scraper.fetchBestNewMusic()).thenReturn(Mono.just(List.of()));

        assertThat(tools.createPitchforkPlaylist("My Playlist", null)).contains("Couldn't extract any picks");
    }

    @Test
    void reportsWhenNothingMatchesOnSpotifyAndCreatesNoPlaylist() {
        picks(new PitchforkPick("Obscure Artist", "Obscure Song", "track"));

        String result = tools.createPitchforkPlaylist("My Playlist", null);

        assertThat(result).contains("none of them matched");
        verify(client, never()).createPlaylist(anyString(), anyString(), eq(false));
    }

    @Test
    void tracksAreSearchedWithFieldFiltersFirst() {
        picks(new PitchforkPick("Charli XCX", "360", "track"));
        when(client.search("track:360 artist:Charli XCX", "track", 3)).thenReturn(Mono.just(trackSearch("spotify:track:abc")));

        String result = tools.createPitchforkPlaylist("My Playlist", null);

        assertThat(result).contains("Created playlist 'My Playlist' with 1 track from 1 of 1");
        verify(client).addItemsToPlaylist("playlist-1", List.of("spotify:track:abc"));
    }

    @Test
    void fallsBackToPlainSearchWhenFilteredSearchFindsNothing() {
        picks(new PitchforkPick("Charli XCX", "360", "track"));
        when(client.search("Charli XCX 360", "track", 3)).thenReturn(Mono.just(trackSearch("spotify:track:plain")));

        tools.createPitchforkPlaylist("My Playlist", null);

        verify(client).addItemsToPlaylist("playlist-1", List.of("spotify:track:plain"));
    }

    @Test
    void albumsAreMatchedAsAlbumsAndContributeTheirTracks() {
        picks(new PitchforkPick("Big Thief", "Double Infinity", "album"));
        when(client.search("album:Double Infinity artist:Big Thief", "album", 3))
                .thenReturn(Mono.just(albumSearch("album-1")));
        when(client.getAlbumTracks("album-1", 2)).thenReturn(Mono.just(new AlbumTracksResponse(List.of(
                new AlbumTracksResponse.Track("t1", "One", "spotify:track:t1"),
                new AlbumTracksResponse.Track("t2", "Two", "spotify:track:t2")))));

        String result = tools.createPitchforkPlaylist("My Playlist", 2);

        assertThat(result).contains("with 2 tracks from 1 of 1");
        verify(client).addItemsToPlaylist("playlist-1", List.of("spotify:track:t1", "spotify:track:t2"));
        verify(client, never()).search(anyString(), eq("track"), anyInt());
    }

    @Test
    void keepsPitchforkOrderAndListsUnmatchedPicks() {
        picks(new PitchforkPick("A", "First", "track"),
                new PitchforkPick("Obscure Artist", "Obscure Song", "album"),
                new PitchforkPick("C", "Third", "track"));
        when(client.search("track:First artist:A", "track", 3)).thenReturn(Mono.just(trackSearch("spotify:track:1")));
        when(client.search("track:Third artist:C", "track", 3)).thenReturn(Mono.just(trackSearch("spotify:track:3")));

        String result = tools.createPitchforkPlaylist("My Playlist", null);

        assertThat(result).contains("2 of 3").contains("Couldn't find on Spotify: Obscure Artist - Obscure Song");
        verify(client).addItemsToPlaylist("playlist-1", List.of("spotify:track:1", "spotify:track:3"));
    }

    @Test
    void skipsNullSearchEntries() {
        picks(new PitchforkPick("A", "Song", "track"));
        List<SearchResponse.Track> items = new ArrayList<>();
        items.add(null);
        items.add(new SearchResponse.Track("id", "Song", "spotify:track:real", List.of(), null));
        when(client.search("track:Song artist:A", "track", 3))
                .thenReturn(Mono.just(new SearchResponse(new SearchResponse.TracksPage(items), null, null, null)));

        tools.createPitchforkPlaylist("My Playlist", null);

        verify(client).addItemsToPlaylist("playlist-1", List.of("spotify:track:real"));
    }

    @Test
    void rateLimitFailsTheRunInsteadOfLookingLikeNoMatch() {
        picks(new PitchforkPick("A", "Song", "track"));
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "30");
        when(client.search(anyString(), anyString(), anyInt()))
                .thenReturn(Mono.error(WebClientResponseException.create(429, "Too Many Requests", headers, null, null)));

        String result = tools.createPitchforkPlaylist("My Playlist", null);

        assertThat(result).contains("Rate limited by Spotify").contains("30 seconds");
        verify(client, never()).createPlaylist(anyString(), anyString(), eq(false));
    }

    @Test
    void generatesDefaultPlaylistNameWhenNoneProvided() {
        picks(new PitchforkPick("Artist", "Title", "track"));
        when(client.search(anyString(), eq("track"), anyInt())).thenReturn(Mono.just(trackSearch("spotify:track:xyz")));

        assertThat(tools.createPitchforkPlaylist(null, null)).contains("Pitchfork Best New Music -");
    }

    @Test
    void surfacesErrorsAsReadableMessages() {
        when(scraper.fetchBestNewMusic()).thenReturn(Mono.error(new RuntimeException("network down")));

        assertThat(tools.createPitchforkPlaylist("My Playlist", null)).contains("Couldn't build the playlist");
    }

    private void picks(PitchforkPick... picks) {
        when(scraper.fetchBestNewMusic()).thenReturn(Mono.just(List.of(picks)));
    }

    private static SearchResponse emptySearch() {
        return new SearchResponse(new SearchResponse.TracksPage(List.of()),
                new SearchResponse.AlbumsPage(List.of()), null, null);
    }

    private static SearchResponse trackSearch(String uri) {
        return new SearchResponse(new SearchResponse.TracksPage(List.of(
                new SearchResponse.Track("id", "name", uri, List.of(), null))), null, null, null);
    }

    private static SearchResponse albumSearch(String albumId) {
        return new SearchResponse(null, new SearchResponse.AlbumsPage(List.of(
                new SearchResponse.AlbumRef(albumId, "name", "spotify:album:" + albumId))), null, null);
    }
}
