package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.PlaylistResponse;
import io.projects.spotifymcp.client.dto.SearchResponse;
import io.projects.spotifymcp.client.dto.UserProfile;
import io.projects.spotifymcp.pitchfork.PitchforkPick;
import io.projects.spotifymcp.pitchfork.PitchforkScraperService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    }

    @Test
    void reportsWhenNoPicksAreExtracted() {
        when(scraper.fetchBestNewMusic()).thenReturn(Mono.just(List.of()));

        String result = tools.createPitchforkPlaylist("My Playlist");

        assertThat(result).contains("Couldn't extract any picks");
    }

    @Test
    void reportsWhenNothingMatchesOnSpotify() {
        when(scraper.fetchBestNewMusic()).thenReturn(Mono.just(List.of(
                new PitchforkPick("Obscure Artist", "Obscure Song", "track"))));
        when(client.search(anyString(), anyString(), anyInt())).thenReturn(Mono.just(emptySearchResponse()));

        String result = tools.createPitchforkPlaylist("My Playlist");

        assertThat(result).contains("none of them matched");
    }

    @Test
    void createsPlaylistAndAddsMatchedTracks() {
        PitchforkPick pick1 = new PitchforkPick("Charli XCX", "360", "track");
        PitchforkPick pick2 = new PitchforkPick("Obscure Artist", "Obscure Song", "album");
        when(scraper.fetchBestNewMusic()).thenReturn(Mono.just(List.of(pick1, pick2)));

        when(client.search("Charli XCX 360", "track", 1))
                .thenReturn(Mono.just(searchResponseWithTrack("spotify:track:abc123")));
        when(client.search("Obscure Artist Obscure Song", "track", 1))
                .thenReturn(Mono.just(emptySearchResponse()));

        when(client.getCurrentUser()).thenReturn(Mono.just(new UserProfile("user-1", "Test User")));
        when(client.createPlaylist(eq("user-1"), eq("My Playlist"), anyString(), eq(false)))
                .thenReturn(Mono.just(new PlaylistResponse("playlist-1", "My Playlist", "spotify:playlist:1")));
        when(client.addTracksToPlaylist("playlist-1", List.of("spotify:track:abc123")))
                .thenReturn(Mono.empty());

        String result = tools.createPitchforkPlaylist("My Playlist");

        assertThat(result).contains("Created playlist 'My Playlist'");
        assertThat(result).contains("1 of 2");
        assertThat(result).contains("Obscure Artist - Obscure Song");
        verify(client).addTracksToPlaylist("playlist-1", List.of("spotify:track:abc123"));
    }

    @Test
    void generatesDefaultPlaylistNameWhenNoneProvided() {
        when(scraper.fetchBestNewMusic()).thenReturn(Mono.just(List.of(
                new PitchforkPick("Artist", "Title", "track"))));
        when(client.search(anyString(), anyString(), anyInt()))
                .thenReturn(Mono.just(searchResponseWithTrack("spotify:track:xyz")));
        when(client.getCurrentUser()).thenReturn(Mono.just(new UserProfile("user-1", "Test User")));
        when(client.createPlaylist(anyString(), anyString(), anyString(), eq(false)))
                .thenReturn(Mono.just(new PlaylistResponse("playlist-1", "generated", "uri")));
        when(client.addTracksToPlaylist(anyString(), anyList()))
                .thenReturn(Mono.empty());

        String result = tools.createPitchforkPlaylist(null);

        assertThat(result).contains("Pitchfork Best New Music -");
    }

    @Test
    void surfacesErrorsAsReadableMessages() {
        when(scraper.fetchBestNewMusic()).thenReturn(Mono.error(new RuntimeException("network down")));

        String result = tools.createPitchforkPlaylist("My Playlist");

        assertThat(result).contains("Couldn't build the playlist");
    }

    private SearchResponse emptySearchResponse() {
        return new SearchResponse(new SearchResponse.TracksPage(List.of()), null, null, null);
    }

    private SearchResponse searchResponseWithTrack(String uri) {
        return new SearchResponse(
                new SearchResponse.TracksPage(List.of(
                        new SearchResponse.Track("id", "name", uri, List.of(), null))),
                null, null, null);
    }
}
