package io.projects.spotifymcp.tools;

import org.springframework.web.reactive.function.client.WebClientResponseException;

/** Turns Spotify/HTTP failures into messages an LLM can relay to the user directly. */
final class ToolErrors {

    private ToolErrors() {
    }

    static String describe(Throwable error) {
        if (error instanceof IllegalStateException illegalState) {
            return illegalState.getMessage();
        }
        if (error instanceof WebClientResponseException httpError) {
            return switch (httpError.getStatusCode().value()) {
                case 404 -> "No active Spotify device found. Open Spotify on a device first, "
                        + "then try again.";
                case 403 -> "Spotify rejected this request (403). Playback control requires "
                        + "Spotify Premium -- check your account and the app's authorized scopes.";
                case 401 -> "Spotify authorization expired or was revoked. Re-run the auth "
                        + "bootstrap (see README) to reauthorize.";
                case 429 -> "Rate limited by Spotify. Try again in a few seconds.";
                default -> "Spotify API error (" + httpError.getStatusCode().value() + "): "
                        + httpError.getStatusText();
            };
        }
        return "Unexpected error: " + error.getMessage();
    }
}
