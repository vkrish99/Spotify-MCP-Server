package io.projects.spotifymcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class McpHomeTest {

    @TempDir
    Path tmp;

    @Test
    void explicitHomeWins() throws IOException {
        Path home = dir("home");
        Path cwd = dirWithEnv("cwd");

        McpHome result = McpHome.detect(home.toString(), cwd, null, dir("user"));

        assertThat(result.directory()).isEqualTo(home.toAbsolutePath());
        assertThat(result.envFile()).isEmpty();
    }

    @Test
    void workingDirectoryWithEnvIsUsed() throws IOException {
        Path cwd = dirWithEnv("project");

        McpHome result = McpHome.detect(null, cwd, dir("elsewhere"), dir("user"));

        assertThat(result.directory()).isEqualTo(cwd);
        assertThat(result.envFile()).contains(cwd.resolve(".env"));
    }

    /** The Claude Desktop case: launched from an unrelated directory as java -jar project/target/x.jar. */
    @Test
    void projectDirAboveTheJarIsFoundWhenLaunchedFromElsewhere() throws IOException {
        Path project = dirWithEnv("project");
        Path target = Files.createDirectories(project.resolve("target"));

        McpHome result = McpHome.detect(null, dir("claude-cwd"), target, dir("user"));

        assertThat(result.directory()).isEqualTo(project);
        assertThat(result.logFile()).isEqualTo(project.resolve("logs").resolve("spotify-mcp-server.log"));
    }

    @Test
    void fallsBackToDotDirectoryInUserHome() throws IOException {
        Path user = dir("user");

        McpHome result = McpHome.detect("", dir("cwd"), dir("app"), user);

        assertThat(result.directory()).isEqualTo(user.resolve(".spotify-mcp"));
        assertThat(result.envFileForWriting()).isEqualTo(user.resolve(".spotify-mcp").resolve(".env"));
    }

    @Test
    void upsertReplacesExistingKeyAndKeepsOtherLines() throws IOException {
        Path project = dirWithEnv("project");
        McpHome home = McpHome.detect(null, project, null, dir("user"));

        home.upsertEnvValue("SPOTIFY_REFRESH_TOKEN", "new-token");

        assertThat(Files.readAllLines(project.resolve(".env")))
                .containsExactly("SPOTIFY_CLIENT_ID=abc", "SPOTIFY_REFRESH_TOKEN=new-token");
    }

    @Test
    void upsertCreatesFileAndParentDirectories() throws IOException {
        McpHome home = McpHome.detect(null, dir("cwd"), null, dir("user"));

        Path written = home.upsertEnvValue("SPOTIFY_REFRESH_TOKEN", "t");

        assertThat(Files.readAllLines(written)).containsExactly("SPOTIFY_REFRESH_TOKEN=t");
    }

    private Path dir(String name) throws IOException {
        return Files.createDirectories(tmp.resolve(name));
    }

    private Path dirWithEnv(String name) throws IOException {
        Path dir = dir(name);
        Files.writeString(dir.resolve(".env"), "SPOTIFY_CLIENT_ID=abc\nSPOTIFY_REFRESH_TOKEN=old-token\n");
        return dir;
    }
}
