package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.DevicesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaybackToolsTest {

    private static final String TRACK_URI = "spotify:track:4uLU6hMCjMI75M1A2tKUQC";

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
        // Real body Spotify sends from player endpoints when no device is active.
        byte[] body = """
                {"error":{"status":404,"message":"Player command failed: No active device found","reason":"NO_ACTIVE_DEVICE"}}
                """.getBytes(StandardCharsets.UTF_8);
        WebClientResponseException notFound = WebClientResponseException.create(
                404, "Not Found", null, body, StandardCharsets.UTF_8);
        when(client.pause()).thenReturn(Mono.error(notFound));

        String result = tools.pausePlayback();

        assertThat(result).contains("No active Spotify device");
    }

    private static WebClientResponseException noActiveDevice() {
        byte[] body = """
                {"error":{"status":404,"message":"Player command failed: No active device found","reason":"NO_ACTIVE_DEVICE"}}
                """.getBytes(StandardCharsets.UTF_8);
        return WebClientResponseException.create(404, "Not Found", null, body, StandardCharsets.UTF_8);
    }

    @Test
    void resumeWithNoActiveDeviceUsesTheOnlyAvailableDevice() {
        when(client.resume()).thenReturn(Mono.error(noActiveDevice()));
        when(client.getDevices()).thenReturn(Mono.just(new DevicesResponse(List.of(
                new DevicesResponse.Device("id-1", false, "iPhone", "Smartphone", 50)))));
        when(client.transferPlayback("id-1")).thenReturn(Mono.empty());

        assertThat(tools.resumePlayback()).isEqualTo("No device was active, so playback resumed on iPhone.");
        verify(client).transferPlayback("id-1");
    }

    @Test
    void resumeWithNoActiveDeviceListsDevicesWhenThereAreSeveral() {
        when(client.resume()).thenReturn(Mono.error(noActiveDevice()));
        when(client.getDevices()).thenReturn(Mono.just(new DevicesResponse(List.of(
                new DevicesResponse.Device("id-1", false, "iPhone", "Smartphone", 50),
                new DevicesResponse.Device("id-2", false, "MacBook Pro", "Computer", 80)))));

        String result = tools.resumePlayback();

        assertThat(result).contains("iPhone, MacBook Pro").contains("transfer_playback");
        verify(client, never()).transferPlayback(anyString());
    }

    @Test
    void resumeWithNoDevicesAtAll() {
        when(client.resume()).thenReturn(Mono.error(noActiveDevice()));
        when(client.getDevices()).thenReturn(Mono.just(new DevicesResponse(List.of())));

        assertThat(tools.resumePlayback()).contains("No active Spotify device");
    }

    @Test
    void playSendsTracksAsUrisAndAlbumsAsContext() {
        when(client.playUris(List.of(TRACK_URI))).thenReturn(Mono.empty());
        when(client.playContext("spotify:album:4uLU6hMCjMI75M1A2tKUQC")).thenReturn(Mono.empty());

        assertThat(tools.play(TRACK_URI)).isEqualTo("Playing track " + TRACK_URI + ".");
        assertThat(tools.play("https://open.spotify.com/album/4uLU6hMCjMI75M1A2tKUQC?si=x"))
                .isEqualTo("Playing album spotify:album:4uLU6hMCjMI75M1A2tKUQC.");
        verify(client).playUris(List.of(TRACK_URI));
        verify(client).playContext("spotify:album:4uLU6hMCjMI75M1A2tKUQC");
    }

    @Test
    void playRejectsTextThatIsNotAUri() {
        assertThat(tools.play("some song")).contains("isn't a Spotify URI");
        verify(client, never()).playUris(anyList());
        verify(client, never()).playContext(anyString());
    }

    @Test
    void seekConvertsSecondsToMilliseconds() {
        when(client.seek(anyLong())).thenReturn(Mono.empty());

        assertThat(tools.seek(95)).isEqualTo("Jumped to 1:35.");
        verify(client).seek(95_000L);
        assertThat(tools.seek(-1)).contains("0 seconds or more");
    }

    @Test
    void shuffleAndRepeat() {
        when(client.setShuffle(true)).thenReturn(Mono.empty());
        when(client.setRepeat("context")).thenReturn(Mono.empty());

        assertThat(tools.setShuffle(true)).isEqualTo("Shuffle is on.");
        assertThat(tools.setRepeat(" Context ")).isEqualTo("Repeating the current album or playlist.");
        assertThat(tools.setRepeat("forever")).contains("track, context, off");
        verify(client, never()).setRepeat("forever");
    }
}
