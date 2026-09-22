package io.projects.spotifymcp.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// Explicit @JsonProperty rather than relying on the app-wide snake_case
// Jackson customizer: accounts.spotify.com's contract should stay correct
// regardless of how some other WebClient's ObjectMapper is configured.
@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Integer expiresIn,
        @JsonProperty("scope") String scope,
        @JsonProperty("refresh_token") String refreshToken
) {
}
