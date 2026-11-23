package io.projects.spotifymcp.tools;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SpotifyUrisTest {

    private static final String ID = "4uLU6hMCjMI75M1A2tKUQC";

    @ParameterizedTest
    @CsvSource({
            "spotify:track:" + ID + ", track",
            "' spotify:album:" + ID + " ', album",
            "https://open.spotify.com/playlist/" + ID + ", playlist",
            "https://open.spotify.com/intl-de/episode/" + ID + "?si=abc123, episode",
            "open.spotify.com/artist/" + ID + ", artist",
            "http://open.spotify.com/show/" + ID + "/, show",
    })
    void parsesUrisAndShareLinks(String input, String expectedType) {
        assertThat(SpotifyUris.parse(input))
                .hasValueSatisfying(uri -> {
                    assertThat(uri.type()).isEqualTo(expectedType);
                    assertThat(uri.uri()).isEqualTo("spotify:" + expectedType + ":" + ID);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "Blinding Lights",
            "spotify:track:tooShort",
            "spotify:track:" + ID + "extra",
            "spotify:user:" + ID,
            "https://example.com/track/" + ID,
            "https://open.spotify.com.evil.com/track/" + ID,
    })
    void rejectsAnythingElse(String input) {
        assertThat(SpotifyUris.parse(input)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"track, true", "episode, true", "album, false", "playlist, false"})
    void tracksAndEpisodesAreItemsEverythingElseIsAContext(String type, boolean isItem) {
        assertThat(SpotifyUris.parse("spotify:" + type + ":" + ID).orElseThrow().isItem()).isEqualTo(isItem);
    }
}
