package io.projects.spotifymcp.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.projects.spotifymcp.auth.SpotifyAuthService;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    private static final String SPOTIFY_API_BASE_URL = "https://api.spotify.com/v1";

    @Bean
    public WebClient spotifyApiWebClient(WebClient.Builder builder, SpotifyAuthService authService) {
        return builder
                .baseUrl(SPOTIFY_API_BASE_URL)
                .filter(bearerTokenFilter(authService))
                .build();
    }

    private ExchangeFilterFunction bearerTokenFilter(SpotifyAuthService authService) {
        return (request, next) -> authService.getAccessToken()
                .flatMap(token -> {
                    ClientRequest authorized = ClientRequest.from(request)
                            .headers(headers -> headers.setBearerAuth(token))
                            .build();
                    return next.exchange(authorized);
                });
    }

    // Spotify's API is snake_case; this lets DTOs stay plain camelCase records.
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer snakeCaseJsonCustomizer() {
        return builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}
