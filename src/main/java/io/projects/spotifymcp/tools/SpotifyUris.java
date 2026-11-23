package io.projects.spotifymcp.tools;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses what a user or LLM pastes in -- a Spotify URI like {@code spotify:track:<id>} or a share
 * link like {@code https://open.spotify.com/intl-de/track/<id>?si=...} -- into a validated URI.
 */
final class SpotifyUris {

    /** Types that play one item at a time (PUT /me/player/play with {@code uris}). */
    static final Set<String> ITEM_TYPES = Set.of("track", "episode");
    /** Types that play as a context (PUT /me/player/play with {@code context_uri}). */
    static final Set<String> CONTEXT_TYPES = Set.of("album", "artist", "playlist", "show");

    private static final String TYPES = "track|episode|album|artist|playlist|show";
    private static final Pattern URI = Pattern.compile(
            "^spotify:(" + TYPES + "):([A-Za-z0-9]{22})$");
    private static final Pattern LINK = Pattern.compile(
            "^(?:https?://)?open\\.spotify\\.com/(?:intl-[a-zA-Z-]+/)?(" + TYPES + ")/([A-Za-z0-9]{22})(?:[/?#].*)?$");

    private SpotifyUris() {
    }

    record SpotifyUri(String type, String id) {
        String uri() {
            return "spotify:" + type + ":" + id;
        }

        boolean isItem() {
            return ITEM_TYPES.contains(type);
        }
    }

    static Optional<SpotifyUri> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        for (Pattern pattern : new Pattern[] {URI, LINK}) {
            Matcher m = pattern.matcher(trimmed);
            if (m.matches()) {
                return Optional.of(new SpotifyUri(m.group(1).toLowerCase(Locale.ROOT), m.group(2)));
            }
        }
        return Optional.empty();
    }
}
