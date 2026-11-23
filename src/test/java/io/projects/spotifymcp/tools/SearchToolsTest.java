package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.MyPlaylistsResponse;
import io.projects.spotifymcp.client.dto.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchToolsTest {

    private static final String TRACK_URI = "spotify:track:4uLU6hMCjMI75M1A2tKUQC";

    private SpotifyApiClient client;
    private MyPlaylists myPlaylists;
    private SearchTools tools;

    @BeforeEach
    void setUp() {
        client = mock(SpotifyApiClient.class);
        myPlaylists = mock(MyPlaylists.class);
        // By default none of the user's own playlists match, so play_playlist searches the catalog.
        when(myPlaylists.find(anyString())).thenReturn(Mono.just(Optional.empty()));
        tools = new SearchTools(client, myPlaylists);
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
        when(client.addToQueue(TRACK_URI)).thenReturn(Mono.empty());

        assertThat(tools.addToQueue(TRACK_URI)).contains(TRACK_URI);
    }

    @Test
    void playPlaylistFindsAndPlaysTopMatch() {
        SearchResponse response = new SearchResponse(null, null, null,
                new SearchResponse.PlaylistsPage(List.of(
                        new SearchResponse.PlaylistRef("p1", "Coding Focus", "spotify:playlist:p1", null))));
        when(client.search("coding music", "playlist", 5)).thenReturn(Mono.just(response));
        when(client.playContext("spotify:playlist:p1")).thenReturn(Mono.empty());

        String result = tools.playPlaylist("coding music");

        assertThat(result).isEqualTo("Now playing playlist 'Coding Focus'.");
        verify(client).playContext("spotify:playlist:p1");
    }

    @Test
    void playPlaylistSkipsNullSearchEntries() {
        // Spotify's playlist search really does return null entries in `items`.
        List<SearchResponse.PlaylistRef> items = new ArrayList<>();
        items.add(null);
        items.add(new SearchResponse.PlaylistRef("p2", "Deep Work", "spotify:playlist:p2", null));
        SearchResponse response = new SearchResponse(null, null, null, new SearchResponse.PlaylistsPage(items));
        when(client.search("deep work", "playlist", 5)).thenReturn(Mono.just(response));
        when(client.playContext("spotify:playlist:p2")).thenReturn(Mono.empty());

        assertThat(tools.playPlaylist("deep work")).isEqualTo("Now playing playlist 'Deep Work'.");
    }

    @Test
    void searchResultsSkipNullEntries() {
        List<SearchResponse.PlaylistRef> items = new ArrayList<>();
        items.add(null);
        items.add(new SearchResponse.PlaylistRef("p1", "Lo-fi", "spotify:playlist:p1", null));
        when(client.search(anyString(), anyString(), anyInt())).thenReturn(Mono.just(
                new SearchResponse(null, null, null, new SearchResponse.PlaylistsPage(items))));

        assertThat(tools.searchCatalog("lofi", "playlist")).isEqualTo("Lo-fi -- spotify:playlist:p1");
    }

    @Test
    void playPlaylistReportsWhenNothingFound() {
        SearchResponse response = new SearchResponse(null, null, null,
                new SearchResponse.PlaylistsPage(List.of()));
        when(client.search("nonexistent", "playlist", 5)).thenReturn(Mono.just(response));

        String result = tools.playPlaylist("nonexistent");

        assertThat(result).contains("No playlist found");
    }

    @Test
    void addToQueueConvertsShareLinksToUris() {
        when(client.addToQueue(TRACK_URI)).thenReturn(Mono.empty());

        String result = tools.addToQueue("https://open.spotify.com/intl-de/track/4uLU6hMCjMI75M1A2tKUQC?si=abc");

        assertThat(result).isEqualTo("Added " + TRACK_URI + " to the queue.");
    }

    @Test
    void addToQueueRejectsInvalidInputWithoutCallingSpotify() {
        assertThat(tools.addToQueue("never gonna give you up")).contains("Only tracks and episodes");
        assertThat(tools.addToQueue("spotify:album:4uLU6hMCjMI75M1A2tKUQC")).contains("Only tracks and episodes");
        verify(client, never()).addToQueue(anyString());
    }

    @Test
    void playPlaylistPrefersTheUsersOwnPlaylist() {
        when(myPlaylists.find("road trip")).thenReturn(Mono.just(Optional.of(
                new MyPlaylistsResponse.Playlist("mine", "Road Trip 2026", "spotify:playlist:mine", null, null))));
        when(client.playContext("spotify:playlist:mine")).thenReturn(Mono.empty());

        String result = tools.playPlaylist("road trip");

        assertThat(result).isEqualTo("Now playing your playlist 'Road Trip 2026'.");
        verify(client, never()).search(anyString(), anyString(), anyInt());
    }

    @Test
    void playPlaylistFallsBackToCatalogWhenOwnPlaylistsCannotBeRead() {
        when(myPlaylists.find("lofi")).thenReturn(Mono.error(new RuntimeException("boom")));
        SearchResponse response = new SearchResponse(null, null, null,
                new SearchResponse.PlaylistsPage(List.of(
                        new SearchResponse.PlaylistRef("p1", "Lofi Beats", "spotify:playlist:p1", null))));
        when(client.search("lofi", "playlist", 5)).thenReturn(Mono.just(response));
        when(client.playContext("spotify:playlist:p1")).thenReturn(Mono.empty());

        assertThat(tools.playPlaylist("lofi")).isEqualTo("Now playing playlist 'Lofi Beats'.");
    }
}
