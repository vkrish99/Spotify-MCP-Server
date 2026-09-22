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

import java.util.concurrent.atomic.AtomicReference;

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
