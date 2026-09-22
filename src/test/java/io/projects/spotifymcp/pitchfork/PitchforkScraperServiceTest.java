package io.projects.spotifymcp.pitchfork;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the HTML-extraction logic against a hand-written fixture, NOT a snapshot of the real
 * pitchfork.com page -- this environment couldn't fetch the live site to capture one (see the
 * class-level Javadoc on {@link PitchforkScraperService}). This verifies the extraction logic is
 * internally correct for the card shape it's designed around; it does not prove that shape still
 * matches Pitchfork's actual markup.
 */
class PitchforkScraperServiceTest {

    private final PitchforkScraperService service = new PitchforkScraperService();

    @Test
    void extractsArtistAndTitleFromReviewCards() {
        String html = """
                <html><body>
                  <article>
                    <span class="artist">Charli XCX</span>
                    <a href="/reviews/albums/charli-xcx-brat/">Brat</a>
                  </article>
                  <article>
                    <span class="artist">Mk.gee</span>
                    <a href="/reviews/albums/mk-gee-two-star/">Two Star &amp; Violet Alley</a>
                  </article>
                </body></html>
                """;
        Document doc = Jsoup.parse(html);

        List<PitchforkPick> picks = service.extractPicks(doc, "album");

        assertThat(picks).hasSize(2);
        assertThat(picks.get(0).artist()).isEqualTo("Charli XCX");
        assertThat(picks.get(0).title()).isEqualTo("Brat");
        assertThat(picks.get(0).type()).isEqualTo("album");
        assertThat(picks.get(1).artist()).isEqualTo("Mk.gee");
        assertThat(picks.get(1).title()).isEqualTo("Two Star & Violet Alley");
    }

    @Test
    void deduplicatesRepeatedLinksToTheSameReview() {
        String html = """
                <html><body>
                  <article>
                    <span>Artist One</span>
                    <a href="/reviews/albums/artist-one-album/">Album One</a>
                  </article>
                  <article>
                    <a href="/reviews/albums/artist-one-album/">Album One</a>
                  </article>
                </body></html>
                """;
        Document doc = Jsoup.parse(html);

        List<PitchforkPick> picks = service.extractPicks(doc, "album");

        assertThat(picks).hasSize(1);
    }

    @Test
    void ignoresLinksWithNoVisibleText() {
        String html = """
                <html><body>
                  <article>
                    <a href="/reviews/albums/no-text/"><img src="cover.jpg"/></a>
                  </article>
                </body></html>
                """;
        Document doc = Jsoup.parse(html);

        List<PitchforkPick> picks = service.extractPicks(doc, "album");

        assertThat(picks).isEmpty();
    }

    @Test
    void ignoresLinksThatDoNotMatchTheExpectedReviewTypePath() {
        String html = """
                <html><body>
                  <article>
                    <a href="/reviews/tracks/some-track/">Some Track</a>
                  </article>
                </body></html>
                """;
        Document doc = Jsoup.parse(html);

        List<PitchforkPick> picks = service.extractPicks(doc, "album");

        assertThat(picks).isEmpty();
    }

    @Test
    void returnsEmptyListWhenPageHasNoMatchingLinksAtAll() {
        Document doc = Jsoup.parse("<html><body><p>Nothing here</p></body></html>");

        List<PitchforkPick> picks = service.extractPicks(doc, "track");

        assertThat(picks).isEmpty();
    }
}
