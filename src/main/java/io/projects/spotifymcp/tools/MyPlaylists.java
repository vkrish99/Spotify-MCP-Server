package io.projects.spotifymcp.tools;

import io.projects.spotifymcp.client.SpotifyApiClient;
import io.projects.spotifymcp.client.dto.MyPlaylistsResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Reads the user's own playlists (owned or followed) and finds one by name. */
@Component
class MyPlaylists {

    static final int PAGE_SIZE = 50;     // GET /me/playlists maximum
    static final int MAX_PAGES = 4;      // look at up to 200 playlists

    private final SpotifyApiClient client;

    MyPlaylists(SpotifyApiClient client) {
        this.client = client;
    }

    Mono<List<MyPlaylistsResponse.Playlist>> all() {
        return Flux.range(0, MAX_PAGES)
                .concatMap(page -> client.getMyPlaylists(PAGE_SIZE, page * PAGE_SIZE))
                .takeUntil(response -> response.next() == null)
                .flatMapIterable(response -> response.items() == null ? List.of() : response.items())
                .filter(Objects::nonNull)
                .collectList();
    }

    /** Exact (case-insensitive) name match first, then the first name containing the query. */
    Mono<Optional<MyPlaylistsResponse.Playlist>> find(String name) {
        String needle = name.trim().toLowerCase(Locale.ROOT);
        return all().map(playlists -> playlists.stream()
                .filter(p -> p.name() != null && p.name().toLowerCase(Locale.ROOT).equals(needle))
                .findFirst()
                .or(() -> playlists.stream()
                        .filter(p -> p.name() != null && p.name().toLowerCase(Locale.ROOT).contains(needle))
                        .findFirst()));
    }
}
