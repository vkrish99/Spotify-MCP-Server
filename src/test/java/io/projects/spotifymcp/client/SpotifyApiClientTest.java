package io.projects.spotifymcp.client;

import io.projects.spotifymcp.client.dto.DevicesResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SpotifyApiClientTest {

    @Test
    void pauseSendsPutToPauseEndpoint() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        SpotifyApiClient client = clientCapturing(captured, noContent());

        client.pause().block();

        assertThat(captured.get().method()).isEqualTo(HttpMethod.PUT);
        assertThat(captured.get().url().toString()).endsWith("/me/player/pause");
    }

    @Test
    void setVolumeIncludesVolumePercentQueryParam() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        SpotifyApiClient client = clientCapturing(captured, noContent());

        client.setVolume(42).block();

        assertThat(captured.get().url().toString()).contains("volume_percent=42");
    }

    @Test
    void addToQueueSendsUriAsQueryParam() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        SpotifyApiClient client = clientCapturing(captured, noContent());

        client.addToQueue("spotify:track:abc123").block();

        assertThat(captured.get().method()).isEqualTo(HttpMethod.POST);
        assertThat(captured.get().url().toString()).contains("uri=spotify");
    }

    @Test
    void getCurrentlyPlayingDeserializesTrackInfo() {
        String body = """
                {"isPlaying":true,"progressMs":1000,
                 "item":{"name":"Song","durationMs":2000,
                         "artists":[{"name":"Artist"}],"album":{"name":"Album"}}}
                """;
        SpotifyApiClient client = clientReturning(jsonResponse(HttpStatus.OK, body));

        StepVerifier.create(client.getCurrentlyPlaying())
                .assertNext(response -> {
                    assertThat(response.isPlaying()).isTrue();
                    assertThat(response.item().name()).isEqualTo("Song");
                    assertThat(response.item().artists().get(0).name()).isEqualTo("Artist");
                })
                .verifyComplete();
    }

    @Test
    void getDevicesDeserializesDeviceList() {
        String body = """
                {"devices":[{"id":"d1","isActive":true,"name":"MacBook Pro","type":"Computer","volumePercent":80}]}
                """;
        SpotifyApiClient client = clientReturning(jsonResponse(HttpStatus.OK, body));

        StepVerifier.create(client.getDevices())
                .assertNext(response -> {
                    DevicesResponse.Device device = response.devices().get(0);
                    assertThat(device.name()).isEqualTo("MacBook Pro");
                    assertThat(device.isActive()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void propagatesHttpErrorsAsWebClientResponseException() {
        SpotifyApiClient client = clientReturning(
                Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build()));

        StepVerifier.create(client.pause())
                .expectError()
                .verify();
    }

    @Test
    void createPlaylistUsesMePlaylistsEndpoint() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        SpotifyApiClient client = clientCapturing(captured,
                jsonResponse(HttpStatus.CREATED, "{\"id\":\"p1\",\"name\":\"Mix\",\"uri\":\"spotify:playlist:p1\"}"));

        StepVerifier.create(client.createPlaylist("Mix", "desc", false))
                .assertNext(playlist -> assertThat(playlist.id()).isEqualTo("p1"))
                .verifyComplete();

        // POST /users/{id}/playlists was removed in Spotify's Feb 2026 API changes.
        assertThat(captured.get().method()).isEqualTo(HttpMethod.POST);
        assertThat(captured.get().url().getPath()).isEqualTo("/me/playlists");
    }

    @Test
    void addItemsUsesItemsEndpointAndBatchesByHundred() {
        List<ClientRequest> requests = new ArrayList<>();
        WebClient webClient = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            return noContent();
        }).build();
        List<String> uris = IntStream.range(0, 150).mapToObj(i -> "spotify:track:" + i).toList();

        new SpotifyApiClient(webClient).addItemsToPlaylist("p1", uris).block();

        // /playlists/{id}/tracks was renamed to /items in Spotify's Feb 2026 API changes.
        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(r -> assertThat(r.url().getPath()).isEqualTo("/playlists/p1/items"));
    }

    @Test
    void getAlbumTracksDeserializesTrackUris() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        SpotifyApiClient client = clientCapturing(captured, jsonResponse(HttpStatus.OK,
                "{\"items\":[{\"id\":\"t1\",\"name\":\"One\",\"uri\":\"spotify:track:t1\"}]}"));

        StepVerifier.create(client.getAlbumTracks("a1", 2))
                .assertNext(response -> assertThat(response.items().get(0).uri()).isEqualTo("spotify:track:t1"))
                .verifyComplete();

        assertThat(captured.get().url().toString()).contains("/albums/a1/tracks").contains("limit=2");
    }

    private SpotifyApiClient clientCapturing(AtomicReference<ClientRequest> captured,
                                              Mono<ClientResponse> response) {
        WebClient webClient = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return response;
        }).build();
        return new SpotifyApiClient(webClient);
    }

    private SpotifyApiClient clientReturning(Mono<ClientResponse> response) {
        WebClient webClient = WebClient.builder().exchangeFunction(request -> response).build();
        return new SpotifyApiClient(webClient);
    }

    private Mono<ClientResponse> noContent() {
        return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
    }

    private Mono<ClientResponse> jsonResponse(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }
}
