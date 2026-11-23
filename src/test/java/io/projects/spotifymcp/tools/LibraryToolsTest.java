package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.CurrentlyPlayingResponse;
import io.projects.spotifymcp.client.dto.MyPlaylistsResponse;
import io.projects.spotifymcp.client.dto.PlayableItem;
import io.projects.spotifymcp.client.dto.SavedTracksResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibraryToolsTest {

    private static final String TRACK_URI = "spotify:track:4uLU6hMCjMI75M1A2tKUQC";

    private SpotifyApiClient client;
    private LibraryTools tools;

    @BeforeEach
    void setUp() {
        client = mock(SpotifyApiClient.class);
        tools = new LibraryTools(client, new MyPlaylists(client));
    }

    private static MyPlaylistsResponse.Playlist playlist(String id, String name, int count) {
        return new MyPlaylistsResponse.Playlist(id, name, "spotify:playlist:" + id,
                new MyPlaylistsResponse.Owner("vk"), new MyPlaylistsResponse.ItemCount(count));
    }

    private void givenPlaylists(MyPlaylistsResponse.Playlist... playlists) {
        when(client.getMyPlaylists(anyInt(), anyInt()))
                .thenReturn(Mono.just(new MyPlaylistsResponse(List.of(playlists), null, playlists.length)));
    }

    @Test
    void listsPlaylistsWithOwnerCountAndUri() {
        givenPlaylists(playlist("p1", "Gym", 42));

        assertThat(tools.listMyPlaylists()).isEqualTo("1. Gym (by vk, 42 items) -- spotify:playlist:p1");
    }

    @Test
    void addToPlaylistFindsPlaylistByNameAndAddsTrack() {
        givenPlaylists(playlist("p1", "Gym", 42), playlist("p2", "Chill Evenings", 7));
        when(client.addItemsToPlaylist("p2", List.of(TRACK_URI))).thenReturn(Mono.empty());

        String result = tools.addToPlaylist("chill", "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC");

        assertThat(result).isEqualTo("Added " + TRACK_URI + " to 'Chill Evenings'.");
    }

    @Test
    void addToPlaylistReportsWhenNoPlaylistMatches() {
        givenPlaylists(playlist("p1", "Gym", 42));

        assertThat(tools.addToPlaylist("jazz", TRACK_URI)).contains("None of your playlists match 'jazz'");
        verify(client, never()).addItemsToPlaylist(anyString(), anyList());
    }

    @Test
    void addToPlaylistRejectsNonTrackUris() {
        assertThat(tools.addToPlaylist("Gym", "spotify:album:4uLU6hMCjMI75M1A2tKUQC"))
                .contains("Expected a track or episode");
        verify(client, never()).getMyPlaylists(anyInt(), anyInt());
    }

    @Test
    void likeCurrentTrackSavesItsUriToTheLibrary() {
        when(client.getCurrentlyPlaying()).thenReturn(Mono.just(new CurrentlyPlayingResponse(true, 1000L,
                new CurrentlyPlayingResponse.Item("Hey Ya!", TRACK_URI, List.of(), null, 200000L))));
        when(client.saveToLibrary(List.of(TRACK_URI))).thenReturn(Mono.empty());

        assertThat(tools.likeCurrentTrack()).isEqualTo("Added 'Hey Ya!' to your Liked Songs.");
        verify(client).saveToLibrary(List.of(TRACK_URI));
    }

    @Test
    void likeCurrentTrackWithNothingPlaying() {
        when(client.getCurrentlyPlaying()).thenReturn(Mono.empty());

        assertThat(tools.likeCurrentTrack()).contains("Nothing is playing");
        verify(client, never()).saveToLibrary(anyList());
    }

    @Test
    void likedTracksAreNumberedWithTotal() {
        PlayableItem track = new PlayableItem("t1", "Hey Ya!", TRACK_URI, "track",
                List.of(new PlayableItem.ArtistRef("Outkast")), null, null, 200000L);
        when(client.getSavedTracks(10)).thenReturn(Mono.just(new SavedTracksResponse(
                List.of(new SavedTracksResponse.Item("2026-11-01T10:00:00Z", track)), 321)));

        assertThat(tools.getLikedTracks(null))
                .isEqualTo("1. Hey Ya! by Outkast -- " + TRACK_URI + " (321 liked in total)");
    }
}
