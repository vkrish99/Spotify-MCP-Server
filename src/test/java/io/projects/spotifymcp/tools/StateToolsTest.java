package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.CurrentlyPlayingResponse;
import io.projects.spotifymcp.client.dto.DevicesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
}
