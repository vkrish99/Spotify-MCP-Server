package io.projects.spotifymcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpotifyMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpotifyMcpServerApplication.class, args);
    }
}
