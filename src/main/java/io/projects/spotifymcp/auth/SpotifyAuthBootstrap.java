package io.projects.spotifymcp.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.projects.spotifymcp.config.SpotifyProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * One-time interactive PKCE Authorization Code flow, run with:
 * {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=auth}
 * <p>
 * Opens a local callback server, prints the Spotify consent URL, exchanges
 * the returned code for tokens, and saves the refresh token to .env so the
 * normal (stdio) run never needs a browser again.
 */
@Component
@Profile("auth")
public class SpotifyAuthBootstrap implements CommandLineRunner {

    private static final String AUTHORIZE_URI = "https://accounts.spotify.com/authorize";
    private static final String TOKEN_URI = "https://accounts.spotify.com/api/token";
    private static final String SCOPES = String.join(" ",
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-currently-playing",
            "playlist-read-private",
            "playlist-read-collaborative",
            "playlist-modify-public",
            "playlist-modify-private"
    );

    private final SpotifyProperties properties;
    private final WebClient.Builder webClientBuilder;

    public SpotifyAuthBootstrap(SpotifyProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public void run(String... args) throws Exception {
        if (isBlank(properties.clientId()) || isBlank(properties.clientSecret())) {
            System.out.println(
                    "SPOTIFY_CLIENT_ID / SPOTIFY_CLIENT_SECRET are not set.\n"
                            + "Create an app at https://developer.spotify.com/dashboard, add "
                            + properties.redirectUri() + " as a Redirect URI, and put the "
                            + "client id/secret in .env (see .env.example) before running this.");
            return;
        }

        String codeVerifier = PkceUtil.generateRandomToken();
        String codeChallenge = PkceUtil.deriveCodeChallenge(codeVerifier);
        String state = PkceUtil.generateRandomToken();

        URI redirectUri = URI.create(properties.redirectUri());
        int port = redirectUri.getPort() == -1 ? 80 : redirectUri.getPort();
        String callbackPath = redirectUri.getPath() == null || redirectUri.getPath().isEmpty()
                ? "/callback" : redirectUri.getPath();

        CompletableFuture<String> authorizationCode = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(callbackPath, exchange -> handleCallback(exchange, state, authorizationCode));
        server.start();

        String authorizeUrl = UriComponentsBuilder.fromUriString(AUTHORIZE_URI)
                .queryParam("client_id", properties.clientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("code_challenge_method", "S256")
                .queryParam("code_challenge", codeChallenge)
                .queryParam("state", state)
                .queryParam("scope", SCOPES)
                .build()
                .toUriString();

        System.out.println();
        System.out.println("Open this URL in a browser and approve access:");
        System.out.println(authorizeUrl);
        System.out.println();
        System.out.println("Waiting for the redirect to " + properties.redirectUri() + " ...");

        String code;
        try {
            code = authorizationCode.get(5, TimeUnit.MINUTES);
        } finally {
            server.stop(0);
        }

        TokenResponse tokens = exchangeCodeForTokens(code, codeVerifier, redirectUri.toString());
        System.out.println();
        System.out.println("Authorization complete.");
        writeRefreshTokenToEnvFile(tokens.refreshToken());
    }

    private void handleCallback(HttpExchange exchange, String expectedState,
                                 CompletableFuture<String> authorizationCode) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String response;
        if (!expectedState.equals(query.get("state"))) {
            response = "State mismatch -- possible CSRF, aborting. You can close this tab.";
            authorizationCode.completeExceptionally(new IllegalStateException("OAuth state mismatch"));
        } else if (query.containsKey("code")) {
            response = "Authorization received. You can close this tab and return to the terminal.";
            authorizationCode.complete(query.get("code"));
        } else {
            response = "Authorization failed: " + query.getOrDefault("error", "unknown error");
            authorizationCode.completeExceptionally(new IllegalStateException(response));
        }
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }

    private TokenResponse exchangeCodeForTokens(String code, String codeVerifier, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", properties.clientId());
        form.add("code_verifier", codeVerifier);

        return webClientBuilder.build().post()
                .uri(TOKEN_URI)
                .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block(Duration.ofSeconds(30));
    }

    private void writeRefreshTokenToEnvFile(String refreshToken) {
        Path envFile = Path.of(".env");
        try {
            List<String> lines = Files.exists(envFile)
                    ? new ArrayList<>(Files.readAllLines(envFile))
                    : new ArrayList<>();
            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("SPOTIFY_REFRESH_TOKEN=")) {
                    lines.set(i, "SPOTIFY_REFRESH_TOKEN=" + refreshToken);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                lines.add("SPOTIFY_REFRESH_TOKEN=" + refreshToken);
            }
            Files.write(envFile, lines);
            System.out.println("Saved SPOTIFY_REFRESH_TOKEN to " + envFile.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("Could not write .env automatically (" + e.getMessage()
                    + "). Add this line to it manually:");
            System.out.println("SPOTIFY_REFRESH_TOKEN=" + refreshToken);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
