package io.projects.spotifymcp.pitchfork;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scrapes Pitchfork's "Best New Music" review listings (albums + tracks).
 *
 * <p><b>This was built without being able to load pitchfork.com</b> -- the domain is blocked by
 * this environment's fetch tooling (safety restriction), so there was no way to inspect the live
 * page and verify these selectors against real markup. They're a best-effort guess based on
 * Pitchfork's historically stable review URL convention ({@code /reviews/albums/<slug>/} and
 * {@code /reviews/tracks/<slug>/}), which is far less likely to have changed than CSS class names
 * (Cond&eacute; Nast's shared component library uses build-hashed class names that are not stable
 * selectors). If {@link #fetchBestNewMusic()} returns an empty list, or the extracted artist names
 * look wrong, open the URLs below in a real browser, inspect a review card with devtools, and
 * adjust {@link #parse(String, String)} / {@link #extractArtist(Element, String)} accordingly.
 */
@Service
public class PitchforkScraperService {

    private static final Logger log = LoggerFactory.getLogger(PitchforkScraperService.class);

    private static final String ALBUMS_URL = "https://pitchfork.com/reviews/best/albums/";
    private static final String TRACKS_URL = "https://pitchfork.com/reviews/best/tracks/";
    private static final String USER_AGENT =
            "spotify-mcp-server/0.1 (+personal playlist-building tool; single on-demand fetch)";
    private static final int TIMEOUT_MILLIS = (int) Duration.ofSeconds(10).toMillis();
    private static final int MAX_ARTIST_LENGTH = 80;

    public Mono<List<PitchforkPick>> fetchBestNewMusic() {
        return Mono.zip(
                fetchPicks(ALBUMS_URL, "album"),
                fetchPicks(TRACKS_URL, "track")
        ).map(tuple -> {
            List<PitchforkPick> all = new ArrayList<>(tuple.getT1());
            all.addAll(tuple.getT2());
            return all;
        });
    }

    private Mono<List<PitchforkPick>> fetchPicks(String url, String type) {
        return Mono.fromCallable(() -> parse(url, type))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("Failed to fetch/parse Pitchfork page {}: {}", url, e.toString());
                    return Mono.just(List.of());
                });
    }

    List<PitchforkPick> parse(String url, String type) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MILLIS)
                .get();
        return extractPicks(doc, type);
    }

    List<PitchforkPick> extractPicks(Document doc, String type) {
        String hrefFragment = "/reviews/" + (type.equals("album") ? "albums" : "tracks") + "/";
        Elements links = doc.select("a[href*=" + hrefFragment + "]");

        // De-dupe by href (a card's link often appears more than once in the DOM) while
        // preserving page order, which is Pitchfork's own ranking/recency order.
        Map<String, PitchforkPick> picks = new LinkedHashMap<>();
        for (Element link : links) {
            String title = link.text().trim();
            if (title.isEmpty()) {
                continue;
            }
            String href = link.attr("href");
            picks.putIfAbsent(href, new PitchforkPick(extractArtist(link, title), title, type));
        }
        return new ArrayList<>(picks.values());
    }

    /**
     * Best-effort: on a typical review-card layout, the artist name is the other text node
     * sharing the card with the title link. Subtracting the title out of the card's full text
     * is fragile if the card also contains blurb/date/genre text -- capped defensively.
     */
    private String extractArtist(Element titleLink, String title) {
        Element card = titleLink.closest("article, li, div");
        if (card == null) {
            return "";
        }
        String remainder = card.text().replace(title, "").trim();
        return remainder.length() > MAX_ARTIST_LENGTH ? remainder.substring(0, MAX_ARTIST_LENGTH) : remainder;
    }
}
