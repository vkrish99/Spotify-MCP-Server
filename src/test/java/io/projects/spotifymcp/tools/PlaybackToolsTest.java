package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.DevicesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaybackToolsTest {

    private SpotifyApiClient client;
    private PlaybackTools tools;

    @BeforeEach
    void setUp() {
        client = mock(SpotifyApiClient.class);
        tools = new PlaybackTools(client);
    }

    @Test
    void pausePlaybackReturnsConfirmation() {
        when(client.pause()).thenReturn(Mono.empty());

        assertThat(tools.pausePlayback()).isEqualTo("Playback paused.");
    }

    @Test
    void resumePlaybackReturnsConfirmation() {
        when(client.resume()).thenReturn(Mono.empty());

        assertThat(tools.resumePlayback()).isEqualTo("Playback resumed.");
    }

    @Test
    void setVolumeRejectsOutOfRangeValuesWithoutCallingSpotify() {
        String result = tools.setVolume(150);

        assertThat(result).contains("between 0 and 100");
    }

    @Test
    void setVolumeCallsSpotifyForValidValue() {
        when(client.setVolume(anyInt())).thenReturn(Mono.empty());

        assertThat(tools.setVolume(50)).isEqualTo("Volume set to 50%.");
    }

    @Test
    void transferPlaybackMatchesDeviceByPartialNameCaseInsensitive() {
        DevicesResponse devices = new DevicesResponse(List.of(
                new DevicesResponse.Device("id-1", false, "MacBook Pro", "Computer", 80),
                new DevicesResponse.Device("id-2", true, "Living Room Echo", "Speaker", 60)
        ));
        when(client.getDevices()).thenReturn(Mono.just(devices));
        when(client.transferPlayback("id-1")).thenReturn(Mono.empty());

        String result = tools.transferPlayback("macbook");

        assertThat(result).isEqualTo("Switched playback to MacBook Pro.");
    }

    @Test
    void transferPlaybackReportsAvailableDevicesWhenNoMatch() {
        DevicesResponse devices = new DevicesResponse(List.of(
                new DevicesResponse.Device("id-1", false, "MacBook Pro", "Computer", 80)
        ));
        when(client.getDevices()).thenReturn(Mono.just(devices));

        String result = tools.transferPlayback("nonexistent-device");

        assertThat(result).contains("No device matching");
        assertThat(result).contains("MacBook Pro");
    }

    @Test
    void surfacesNoActiveDeviceAsReadableMessage() {
        WebClientResponseException notFound = WebClientResponseException.create(
                404, "Not Found", null, null, null);
        when(client.pause()).thenReturn(Mono.error(notFound));

        String result = tools.pausePlayback();

        assertThat(result).contains("No active Spotify device");
    }
}
