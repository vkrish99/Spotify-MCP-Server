package io.projects.spotifymcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.projects.spotifymcp.client.dto.CurrentlyPlayingResponse;
import io.projects.spotifymcp.client.dto.DevicesResponse;
import io.projects.spotifymcp.client.dto.MyPlaylistsResponse;
import io.projects.spotifymcp.client.dto.QueueResponse;
import io.projects.spotifymcp.client.dto.RecentlyPlayedResponse;
import io.projects.spotifymcp.client.dto.SavedTracksResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Decodes real-shaped Spotify JSON with the exact ObjectMapper Spring Boot builds for the app. */
class SnakeCaseMappingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withBean("snakeCaseJsonCustomizer",
                    org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer.class,
                    () -> new WebClientConfig().snakeCaseJsonCustomizer());

    @Test
    void multiWordFieldsAreMapped() {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            CurrentlyPlayingResponse playing = mapper.readValue("""
                    {"is_playing":true,"progress_ms":1000,"item":{"name":"Song","duration_ms":2000}}""",
                    CurrentlyPlayingResponse.class);
            DevicesResponse devices = mapper.readValue("""
                    {"devices":[{"id":"d1","is_active":true,"name":"Mac","volume_percent":80}]}""",
                    DevicesResponse.class);

            assertThat(playing.isPlaying()).isTrue();
            assertThat(playing.progressMs()).isEqualTo(1000L);
            assertThat(playing.item().durationMs()).isEqualTo(2000L);
            assertThat(devices.devices().get(0).isActive()).isTrue();
            assertThat(devices.devices().get(0).volumePercent()).isEqualTo(80);
        });
    }

    @Test
    void libraryAndHistoryResponsesAreMapped() {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            QueueResponse queue = mapper.readValue("""
                    {"currently_playing":{"name":"Hey Ya!","uri":"spotify:track:t1","duration_ms":235000,
                      "artists":[{"name":"Outkast"}]},
                     "queue":[{"name":"Ep 1","uri":"spotify:episode:e1","show":{"name":"A Pod"}}]}""",
                    QueueResponse.class);
            RecentlyPlayedResponse recent = mapper.readValue("""
                    {"items":[{"track":{"name":"Roses","uri":"spotify:track:t2"},
                      "played_at":"2026-11-23T08:00:00.000Z"}]}""", RecentlyPlayedResponse.class);
            MyPlaylistsResponse playlists = mapper.readValue("""
                    {"items":[{"id":"p1","name":"Gym","uri":"spotify:playlist:p1",
                      "owner":{"display_name":"vk"},"items":{"total":42}}],"next":null,"total":1}""",
                    MyPlaylistsResponse.class);
            SavedTracksResponse saved = mapper.readValue("""
                    {"items":[{"added_at":"2026-11-01T10:00:00Z","track":{"name":"Hey Ya!"}}],"total":321}""",
                    SavedTracksResponse.class);

            assertThat(queue.currentlyPlaying().durationMs()).isEqualTo(235000L);
            assertThat(queue.currentlyPlaying().describe()).isEqualTo("Hey Ya! by Outkast");
            assertThat(queue.queue().get(0).describe()).isEqualTo("Ep 1 (A Pod)");
            assertThat(recent.items().get(0).playedAt()).isEqualTo("2026-11-23T08:00:00.000Z");
            assertThat(playlists.items().get(0).owner().displayName()).isEqualTo("vk");
            assertThat(playlists.items().get(0).items().total()).isEqualTo(42);
            assertThat(saved.items().get(0).addedAt()).isEqualTo("2026-11-01T10:00:00Z");
            assertThat(saved.total()).isEqualTo(321);
        });
    }
}
