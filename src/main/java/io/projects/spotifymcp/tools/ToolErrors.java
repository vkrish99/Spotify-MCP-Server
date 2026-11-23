package io.projects.spotifymcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Locale;

/**
 * Turns Spotify/HTTP failures into messages an LLM can relay to the user directly.
 * <p>
 * Spotify error bodies look like {@code {"error":{"status":404,"message":"...","reason":"NO_ACTIVE_DEVICE"}}}.
 * Player endpoints set {@code reason}; that is the most specific signal, so it is checked before
 * the status code. The status code alone is ambiguous: a 404 can mean "no active device" on a
 * player call but "no such playlist" elsewhere.
 */
final class ToolErrors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String NO_DEVICE = "No active Spotify device found. Open Spotify on a device "
            + "(or use transfer_playback), then try again.";
    private static final String PREMIUM = "This needs Spotify Premium -- Spotify only allows playback "
            + "control for Premium accounts.";

    private ToolErrors() {
    }

    static String describe(Throwable error) {
        if (error instanceof IllegalStateException illegalState) {
            return illegalState.getMessage();
        }
        if (error instanceof WebClientResponseException httpError) {
            return describeHttp(httpError);
        }
        return "Unexpected error: " + error.getMessage();
    }

    private static String describeHttp(WebClientResponseException httpError) {
        SpotifyError body = parse(httpError);
        String reason = body.reason() == null ? "" : body.reason();
        String message = body.message();

        switch (reason) {
            case "NO_ACTIVE_DEVICE" -> {
                return NO_DEVICE;
            }
            case "PREMIUM_REQUIRED" -> {
                return PREMIUM;
            }
            case "RATE_LIMITED" -> {
                return rateLimited(httpError);
            }
            default -> {
            }
        }

        int status = httpError.getStatusCode().value();
        return switch (status) {
            case 401 -> "Spotify authorization expired or was revoked. Re-run the auth "
                    + "bootstrap (see README) to reauthorize.";
            case 403 -> "Spotify refused this request (403)" + suffix(message) + ". Playback control "
                    + "needs Premium, and playlist changes need the playlist-modify scopes -- if you "
                    + "authorized before those were added, re-run the auth bootstrap.";
            case 404 -> message != null && message.toLowerCase(Locale.ROOT).contains("active device")
                    ? NO_DEVICE
                    : "Spotify couldn't find that" + suffix(message) + ".";
            case 429 -> rateLimited(httpError);
            default -> "Spotify API error (" + status + ")"
                    + suffix(message != null ? message : httpError.getStatusText()) + ".";
        };
    }

    private static String rateLimited(WebClientResponseException httpError) {
        String retryAfter = httpError.getHeaders().getFirst("Retry-After");
        return retryAfter == null
                ? "Rate limited by Spotify. Try again in a few seconds."
                : "Rate limited by Spotify. Try again in " + retryAfter + " seconds.";
    }

    private static String suffix(String message) {
        return message == null || message.isBlank() ? "" : ": " + message;
    }

    private static SpotifyError parse(WebClientResponseException httpError) {
        try {
            JsonNode error = MAPPER.readTree(httpError.getResponseBodyAsString()).path("error");
            return new SpotifyError(text(error, "message"), text(error, "reason"));
        } catch (Exception e) {
            return new SpotifyError(null, null);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private record SpotifyError(String message, String reason) {
    }
}
