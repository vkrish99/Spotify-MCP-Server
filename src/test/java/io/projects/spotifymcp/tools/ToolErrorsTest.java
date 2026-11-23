package io.projects.spotifymcp.tools;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ToolErrorsTest {

    @Test
    void noActiveDeviceReason() {
        assertThat(ToolErrors.describe(error(404, "Player command failed: No active device found", "NO_ACTIVE_DEVICE")))
                .contains("No active Spotify device");
    }

    @Test
    void premiumRequiredReason() {
        assertThat(ToolErrors.describe(error(403, "Player command failed: Premium required", "PREMIUM_REQUIRED")))
                .contains("Spotify Premium");
    }

    @Test
    void non404PlayerErrorsNoLongerClaimNoDevice() {
        assertThat(ToolErrors.describe(error(404, "Non existing id: 'spotify:album:x'", null)))
                .isEqualTo("Spotify couldn't find that: Non existing id: 'spotify:album:x'.")
                .doesNotContain("device");
    }

    @Test
    void forbiddenIncludesSpotifysMessage() {
        assertThat(ToolErrors.describe(error(403, "Insufficient client scope", null)))
                .contains("Insufficient client scope")
                .contains("re-run the auth bootstrap");
    }

    @Test
    void rateLimitMentionsRetryAfter() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "12");
        WebClientResponseException e = WebClientResponseException.create(429, "Too Many Requests", headers, null, null);

        assertThat(ToolErrors.describe(e)).isEqualTo("Rate limited by Spotify. Try again in 12 seconds.");
    }

    @Test
    void unparseableBodyFallsBackToStatus() {
        WebClientResponseException e = WebClientResponseException.create(500, "Internal Server Error", null,
                "<html>oops</html>".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        assertThat(ToolErrors.describe(e)).isEqualTo("Spotify API error (500): Internal Server Error.");
    }

    @Test
    void configurationErrorsPassThrough() {
        assertThat(ToolErrors.describe(new IllegalStateException("No Spotify refresh token configured.")))
                .isEqualTo("No Spotify refresh token configured.");
    }

    private static WebClientResponseException error(int status, String message, String reason) {
        String body = "{\"error\":{\"status\":%d,\"message\":\"%s\"%s}}".formatted(status, message,
                reason == null ? "" : ",\"reason\":\"" + reason + "\"");
        return WebClientResponseException.create(status, "Status " + status, null,
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
