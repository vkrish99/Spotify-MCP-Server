package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.MyPlaylistsResponse;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyPlaylistsTest {

    private final SpotifyApiClient client = mock(SpotifyApiClient.class);
    private final MyPlaylists myPlaylists = new MyPlaylists(client);

    private static MyPlaylistsResponse.Playlist named(String name) {
        return new MyPlaylistsResponse.Playlist(name, name, "spotify:playlist:" + name, null, null);
    }

    private static MyPlaylistsResponse page(int from, int count, boolean more) {
        List<MyPlaylistsResponse.Playlist> items = IntStream.range(from, from + count)
                .mapToObj(i -> named("p" + i)).toList();
        return new MyPlaylistsResponse(items, more ? "https://api.spotify.com/next" : null, 120);
    }

    @Test
    void readsPagesUntilThereIsNoNextPage() {
        when(client.getMyPlaylists(50, 0)).thenReturn(Mono.just(page(0, 50, true)));
        when(client.getMyPlaylists(50, 50)).thenReturn(Mono.just(page(50, 50, true)));
        when(client.getMyPlaylists(50, 100)).thenReturn(Mono.just(page(100, 20, false)));

        assertThat(myPlaylists.all().block()).hasSize(120);
        verify(client, times(3)).getMyPlaylists(anyInt(), anyInt());
    }

    @Test
    void stopsAfterTheMaximumNumberOfPages() {
        when(client.getMyPlaylists(anyInt(), anyInt())).thenAnswer(inv ->
                Mono.just(page(inv.getArgument(1), 50, true)));

        assertThat(myPlaylists.all().block()).hasSize(MyPlaylists.PAGE_SIZE * MyPlaylists.MAX_PAGES);
        verify(client, times(MyPlaylists.MAX_PAGES)).getMyPlaylists(anyInt(), anyInt());
    }

    @Test
    void exactNameMatchBeatsAnEarlierPartialMatch() {
        when(client.getMyPlaylists(anyInt(), anyInt())).thenReturn(Mono.just(new MyPlaylistsResponse(
                List.of(named("Gym Hits 2025"), named("gym")), null, 2)));

        assertThat(myPlaylists.find(" GYM ").block()).hasValueSatisfying(p ->
                assertThat(p.name()).isEqualTo("gym"));
    }

    @Test
    void fallsBackToPartialMatchThenToEmpty() {
        when(client.getMyPlaylists(anyInt(), anyInt())).thenReturn(Mono.just(new MyPlaylistsResponse(
                List.of(named("Morning Run")), null, 1)));

        assertThat(myPlaylists.find("run").block()).hasValueSatisfying(p ->
                assertThat(p.name()).isEqualTo("Morning Run"));
        assertThat(myPlaylists.find("jazz").block()).isEmpty();
    }
}
