# Spotify MCP Server

A Java/Spring Boot [Model Context Protocol](https://modelcontextprotocol.io) server that exposes
Spotify playback control as tools an LLM client (Claude Desktop, Cursor, etc.) can call directly
from a chat window -- "pause the music", "what's playing?", "switch to my phone and set volume to
50%" -- without switching to the Spotify app.

## Tools

| Tool | Description |
| --- | --- |
| `get_currently_playing` | Current track, artist, album, progress, playing/paused state |
| `get_active_devices` | Connected Spotify Connect devices and which is active |
| `pause_playback` / `resume_playback` | Stop / resume playback |
| `next_track` / `previous_track` | Skip forward / back |
| `set_volume(percent)` | Set volume 0-100 |
| `transfer_playback(deviceName)` | Move playback to another device by name |
| `search_catalog(query, type)` | Search tracks/albums/artists/playlists |
| `add_to_queue(trackUri)` | Queue a track by its Spotify URI |
| `play_playlist(playlistName)` | Find and play a playlist by name |

## Resources

Besides tools (explicit calls), playback state is also exposed as MCP *resources* -- read-only
content a client can fetch directly without a tool call:

| URI | Description |
| --- | --- |
| `spotify://now-playing` | Same content as `get_currently_playing`, as a subscribable resource |
| `spotify://devices` | Same content as `get_active_devices`, as a subscribable resource |

## Architecture

- **MCP protocol**: `spring-ai-starter-mcp-server` (Spring AI 1.1), JSON-RPC 2.0 over STDIO --
  `@McpTool`/`@McpResource`-annotated methods in [`tools/`](src/main/java/io/projects/spotifymcp/tools)
  and [`resources/`](src/main/java/io/projects/spotifymcp/resources) are auto-discovered and
  registered at startup; no manual tool-registration boilerplate.
- **Non-blocking I/O**: all Spotify Web API calls go through a reactive `WebClient`
  ([`SpotifyApiClient`](src/main/java/io/projects/spotifymcp/client/SpotifyApiClient.java)).
  Tool methods `.block()` once at the boundary since MCP tool invocation is a plain synchronous
  call, not a servlet/event-loop thread -- blocking there is safe and keeps each tool's logic
  linear to read.
- **OAuth2 PKCE + refresh tokens**: [`SpotifyAuthBootstrap`](src/main/java/io/projects/spotifymcp/auth/SpotifyAuthBootstrap.java)
  runs the Authorization Code + PKCE flow once (local callback server, no client secret exposed
  to a browser) and saves a refresh token. [`SpotifyAuthService`](src/main/java/io/projects/spotifymcp/auth/SpotifyAuthService.java)
  then silently exchanges it for short-lived access tokens on demand, caching each one until ~60s
  before it expires -- normal runs never need a browser again.

## Setup

1. **Register a Spotify app**: [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard) →
   Create app → add `http://127.0.0.1:8888/callback` as a Redirect URI.
2. **Configure credentials**:
   ```bash
   cp .env.example .env
   # fill in SPOTIFY_CLIENT_ID / SPOTIFY_CLIENT_SECRET
   ```
3. **Authorize once** (opens a browser, captures the redirect, saves a refresh token to `.env`):
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=auth
   ```
4. **Run the MCP server** (STDIO -- normally launched by an MCP client, not run standalone):
   ```bash
   ./mvnw spring-boot:run
   ```
   or build a jar and point a client at it:
   ```bash
   ./mvnw clean package
   java -jar target/spotify-mcp-server.jar
   ```

### Claude Desktop config

Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "spotify": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/spotify-mcp-server.jar"]
    }
  }
}
```

### Running with Docker

```bash
docker build -t spotify-mcp-server .
docker run -i --env-file .env spotify-mcp-server
```

(`-i` keeps stdin open, required for STDIO transport.) To point Claude Desktop at the container
instead of a local jar:

```json
{
  "mcpServers": {
    "spotify": {
      "command": "docker",
      "args": ["run", "-i", "--env-file", "/absolute/path/to/.env", "spotify-mcp-server"]
    }
  }
}
```

## Testing

```bash
./mvnw test
```

`SpotifyApiClientTest` and `SpotifyAuthServiceTest` stub `WebClient` at the exchange-function
level (no live Spotify calls); `PlaybackToolsTest` / `SearchToolsTest` / `StateToolsTest` mock
`SpotifyApiClient` to verify tool-level formatting and error handling. CI (`.github/workflows/ci.yml`)
runs the full suite on every push.

## Notes

- Playback control (pause/resume/skip/volume/transfer/queue) requires **Spotify Premium** --
  the Web API returns 403 for free accounts, which the tools surface as a readable message.
- `set_volume` and playback-state tools implicitly target the currently active device; use
  `transfer_playback` first to change which device that is.
