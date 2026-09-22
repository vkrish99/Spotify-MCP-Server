package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchToolsTest {

    private SpotifyApiClient client;
    private SearchTools tools;

    @BeforeEach
    void setUp() {
        client = mock(SpotifyApiClient.class);
        tools = new SearchTools(client);
    }

    @Test
    void rejectsInvalidSearchType() {
        String result = tools.searchCatalog("Daft Punk", "song");

        assertThat(result).contains("type must be one of");
    }

    @Test
    void formatsTrackResultsWithArtistAndUri() {
        SearchResponse response = new SearchResponse(
                new SearchResponse.TracksPage(List.of(
                        new SearchResponse.Track("t1", "One More Time", "spotify:track:t1",
                                List.of(new SearchResponse.ArtistRef("a1", "Daft Punk", "spotify:artist:a1")),
                                null)
                )),
                null, null, null);
        when(client.search(anyString(), anyString(), anyInt())).thenReturn(Mono.just(response));

        String result = tools.searchCatalog("Daft Punk", "track");

        assertThat(result).contains("One More Time");
        assertThat(result).contains("Daft Punk");
        assertThat(result).contains("spotify:track:t1");
    }

    @Test
    void addToQueueConfirmsWithTrackUri() {
        when(client.addToQueue("spotify:track:abc")).thenReturn(Mono.empty());

        assertThat(tools.addToQueue("spotify:track:abc")).contains("spotify:track:abc");
    }

    @Test
    void playPlaylistFindsAndPlaysTopMatch() {
        SearchResponse response = new SearchResponse(null, null, null,
                new SearchResponse.PlaylistsPage(List.of(
                        new SearchResponse.PlaylistRef("p1", "Coding Focus", "spotify:playlist:p1", null))));
        when(client.search("coding music", "playlist", 1)).thenReturn(Mono.just(response));
        when(client.playContext("spotify:playlist:p1")).thenReturn(Mono.empty());

        String result = tools.playPlaylist("coding music");

        assertThat(result).isEqualTo("Now playing playlist 'Coding Focus'.");
        verify(client).playContext("spotify:playlist:p1");
    }

    @Test
    void playPlaylistReportsWhenNothingFound() {
        SearchResponse response = new SearchResponse(null, null, null,
                new SearchResponse.PlaylistsPage(List.of()));
        when(client.search("nonexistent", "playlist", 1)).thenReturn(Mono.just(response));

        String result = tools.playPlaylist("nonexistent");

        assertThat(result).contains("No playlist found");
    }
}
