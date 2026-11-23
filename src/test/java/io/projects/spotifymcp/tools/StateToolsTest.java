package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.CurrentlyPlayingResponse;
import io.projects.spotifymcp.client.dto.DevicesResponse;
import io.projects.spotifymcp.client.dto.PlayableItem;
import io.projects.spotifymcp.client.dto.QueueResponse;
import io.projects.spotifymcp.client.dto.RecentlyPlayedResponse;
import io.projects.spotifymcp.client.dto.TopArtistsResponse;
import io.projects.spotifymcp.client.dto.TopTracksResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StateToolsTest {

    private SpotifyApiClient client;
    private StateTools tools;

    @BeforeEach
    void setUp() {
        client = mock(SpotifyApiClient.class);
        tools = new StateTools(client);
    }

    @Test
    void describesNothingPlayingWhenResponseIsEmpty() {
        when(client.getCurrentlyPlaying()).thenReturn(Mono.empty());

        assertThat(tools.getCurrentlyPlaying()).isEqualTo("Nothing is currently playing.");
    }

    @Test
    void describesNothingPlayingWhenItemIsNull() {
        when(client.getCurrentlyPlaying()).thenReturn(
                Mono.just(new CurrentlyPlayingResponse(false, 0L, null)));

        assertThat(tools.getCurrentlyPlaying()).isEqualTo("Nothing is currently playing.");
    }

    @Test
    void describesCurrentTrackWithArtistAndProgress() {
        CurrentlyPlayingResponse response = new CurrentlyPlayingResponse(
                true,
                65000L,
                new CurrentlyPlayingResponse.Item(
                        "Blinding Lights",
                        "spotify:track:0VjIjW4GlUZAMYd2vXMi3b",
                        List.of(new CurrentlyPlayingResponse.ArtistRef("The Weeknd")),
                        new CurrentlyPlayingResponse.AlbumRef("After Hours"),
                        200000L)
        );
        when(client.getCurrentlyPlaying()).thenReturn(Mono.just(response));

        String result = tools.getCurrentlyPlaying();

        assertThat(result).contains("Playing");
        assertThat(result).contains("Blinding Lights");
        assertThat(result).contains("The Weeknd");
        assertThat(result).contains("After Hours");
        assertThat(result).contains("1:05");
        assertThat(result).contains("3:20");
    }

    @Test
    void listsDevicesAndFlagsTheActiveOne() {
        DevicesResponse devices = new DevicesResponse(List.of(
                new DevicesResponse.Device("id-1", true, "MacBook Pro", "Computer", 80),
                new DevicesResponse.Device("id-2", false, "iPhone", "Smartphone", 60)
        ));
        when(client.getDevices()).thenReturn(Mono.just(devices));

        String result = tools.getActiveDevices();

        assertThat(result).contains("MacBook Pro (Computer, active)");
        assertThat(result).contains("iPhone (Smartphone)");
    }

    @Test
    void reportsNoDevicesWhenListIsEmpty() {
        when(client.getDevices()).thenReturn(Mono.just(new DevicesResponse(List.of())));

        assertThat(tools.getActiveDevices()).contains("No devices currently connected");
    }

    private static PlayableItem track(String name, String artist, String id) {
        return new PlayableItem(id, name, "spotify:track:" + id, "track",
                List.of(new PlayableItem.ArtistRef(artist)), null, null, 180000L);
    }

    @Test
    void queueShowsNowPlayingAndUpNext() {
        PlayableItem episode = new PlayableItem("e1", "Episode 12", "spotify:episode:e1", "episode",
                null, null, new PlayableItem.ShowRef("Football Weekly"), 3_600_000L);
        when(client.getQueue()).thenReturn(Mono.just(new QueueResponse(
                track("Hey Ya!", "Outkast", "t1"), List.of(track("Roses", "Outkast", "t2"), episode))));

        assertThat(tools.getQueue()).isEqualTo("""
                Now playing: Hey Ya! by Outkast
                Up next:
                1. Roses by Outkast -- spotify:track:t2
                2. Episode 12 (Football Weekly) -- spotify:episode:e1""");
    }

    @Test
    void emptyQueue() {
        when(client.getQueue()).thenReturn(Mono.just(new QueueResponse(null, List.of())));

        assertThat(tools.getQueue()).isEqualTo("Nothing is playing right now.\nThe queue is empty.");
    }

    @Test
    void recentlyPlayedClampsLimitAndListsTracks() {
        when(client.getRecentlyPlayed(50)).thenReturn(Mono.just(new RecentlyPlayedResponse(List.of(
                new RecentlyPlayedResponse.Item(track("Hey Ya!", "Outkast", "t1"), "2026-11-23T08:00:00Z")))));

        assertThat(tools.getRecentlyPlayed(500))
                .isEqualTo("1. Hey Ya! by Outkast -- spotify:track:t1 (played 2026-11-23T08:00:00Z)");
    }

    @Test
    void topItemsValidatesTypeAndRangeWithoutCallingSpotify() {
        assertThat(tools.getTopItems("songs", null, null)).contains("'tracks' or 'artists'");
        assertThat(tools.getTopItems("tracks", "forever", null)).contains("timeRange must be one of");
        verify(client, never()).getTopTracks(anyString(), anyInt());
    }

    @Test
    void topTracksDefaultToMediumTermAndTen() {
        when(client.getTopTracks("medium_term", 10)).thenReturn(Mono.just(new TopTracksResponse(List.of(
                track("Hey Ya!", "Outkast", "t1")))));

        assertThat(tools.getTopItems("Tracks", null, null)).isEqualTo("1. Hey Ya! by Outkast -- spotify:track:t1");
    }

    @Test
    void topArtistsShowGenres() {
        when(client.getTopArtists("short_term", 5)).thenReturn(Mono.just(new TopArtistsResponse(List.of(
                new TopArtistsResponse.Artist("Outkast", "spotify:artist:a1", List.of("hip hop", "southern hip hop"))))));

        assertThat(tools.getTopItems("artists", "short_term", 5))
                .isEqualTo("1. Outkast (hip hop, southern hip hop) -- spotify:artist:a1");
    }
}
