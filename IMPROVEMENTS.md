# Spotify MCP Server — Improvement Plan

This plan comes from a full read of the codebase (Sept 2026). Work through it in order: each phase
depends on the one before it. Every task names the files to change and a **Done when** check, so
you can tell when it's finished.

## Status (Sept 2026)

Phases 0 and 1 are done (77 tests pass). Two findings changed the plan while doing them:
- **1.4 was a live bug, not a check.** Spotify's February 2026 changes removed
  `POST /users/{id}/playlists` and `POST /playlists/{id}/tracks`, so `create_pitchfork_playlist`
  could not work at all. It now uses `POST /me/playlists` and `POST /playlists/{id}/items`
  (batched by 100), and no longer needs the `/me` lookup.
- **Phase 2 must use the new endpoints.** `PUT /me/tracks` (like a track) was replaced by
  `PUT /me/library`, which takes URIs. `GET /artists/{id}/top-tracks` was removed, so album
  picks use the album's first N tracks.

Also verified by hand: the built jar, launched from an unrelated directory, finds `.env` via the
jar's location, logs to `<project>/logs/`, and answers `initialize`, `tools/list` and `tools/call`.

---

## Phase 0 — Fix bugs that break real use (do first)

### 0.1 `.env` and logs only work when the server is launched from the project folder — ✅ Done
**Problem:** [`DotenvPropertySourceLoader`](src/main/java/io/projects/spotifymcp/config/DotenvPropertySourceLoader.java)
reads `Path.of(".env")`, and `logging.file.name: logs/...` in `application.yml`. Both are relative
to the **working directory**. Claude Desktop launches `java -jar /abs/path/spotify-mcp-server.jar`
from a different folder (often `/` or the Claude install folder), so:
- the credentials in `.env` are never loaded, and every tool fails with "No Spotify refresh token configured".
- the log file can't be created in a read-only working directory, and startup fails.

**Do:**
- Resolve a home directory in this order: the `SPOTIFY_MCP_HOME` env var, then `~/.spotify-mcp/`.
  Load `.env` from there first, then fall back to `./.env`.
- Set `logging.file.name: ${SPOTIFY_MCP_HOME:${user.home}/.spotify-mcp}/logs/spotify-mcp-server.log`.
- Have `SpotifyAuthBootstrap.writeRefreshTokenToEnvFile` write to the same resolved path.
- Update the README Claude Desktop snippet to show the `"env": { ... }` block as an alternative
  to `.env`.

**Done when:** `cd / && java -jar /abs/path/target/spotify-mcp-server.jar` starts cleanly and
`get_currently_playing` works.

### 0.2 `play_playlist` crashes on null search results — ✅ Done
**Problem:** Spotify's search endpoint can return `null` entries inside `playlists.items`.
[`SearchTools.playPlaylist`](src/main/java/io/projects/spotifymcp/tools/SearchTools.java) calls
`items.get(0).uri()`, which throws an NPE. The user then sees "Unexpected error: null".
`formatResults` has the same bug.

**Do:** Filter out nulls (`items.stream().filter(Objects::nonNull)`) in both places. Request
`limit=5` instead of 1, then pick the first non-null result.

**Done when:** A unit test with `{"playlists":{"items":[null, {...}]}}` passes.

### 0.3 Pitchfork album picks are matched as tracks — ✅ Done
**Problem:** [`PitchforkTools.searchForPick`](src/main/java/io/projects/spotifymcp/tools/PitchforkTools.java)
always searches `type=track`. For a Best New *Album*, it adds whatever random track matches the
album title, which is often the wrong artist.

**Do:** For `type == "album"`, search `type=album`. Then either add the album's top 1–2 tracks
(`GET /albums/{id}/tracks?limit=2`) or add all of its tracks, and make that choice a tool
parameter. Use the Spotify search field filters for accuracy: `artist:"X" album:"Y"` and
`artist:"X" track:"Y"`.

**Done when:** Album picks resolve to tracks from the correct album, and tests cover both the
album path and the track path.

### 0.4 Pitchfork searches run all at once and hide rate limits — ✅ Done
**Problem:** `Mono.zip` fires about 20 searches in parallel. Each one does `onErrorReturn(empty)`,
so a 429 looks like "not on Spotify".

**Do:** Use `Flux.fromIterable(picks).flatMapSequential(this::searchForPick, 4)`, which caps
concurrency at 4. Only map 404/empty results to "unmatched". Let 429 go to the retry logic in 1.2.

### 0.5 `ToolErrors` maps every 404 to "No active device" — ✅ Done
**Problem:** A 404 from search, playlists, or albums is reported as "open Spotify on a device".

**Do:** Parse Spotify's error body (`{"error":{"status":..,"message":..,"reason":..}}`). The
player endpoints return `reason` values such as `NO_ACTIVE_DEVICE` and `PREMIUM_REQUIRED`. Branch
on `reason` first, then on status. Show Spotify's own `message` in the default case.

---

## Phase 1 — Reliability of the HTTP/auth layer

### 1.1 Token refresh thundering herd — ✅ Done
Several concurrent tool calls with an expired token each trigger a separate refresh in
[`SpotifyAuthService`](src/main/java/io/projects/spotifymcp/auth/SpotifyAuthService.java).
**Do:** Keep one in-flight refresh `Mono` (an `AtomicReference<Mono<String>>` holding
`refresh().cache()`) and clear it when it completes.
**Done when:** A test fires 10 concurrent `getAccessToken()` calls on an expired cache and the
token endpoint is hit once.

### 1.2 Retry on 401 and honour `Retry-After` on 429 — ✅ Done
**Do:** In the [`WebClientConfig`](src/main/java/io/projects/spotifymcp/config/WebClientConfig.java) filter:
- On a **401**, invalidate the cached token, refresh, and retry the request once.
- On a **429**, read the `Retry-After` header. If it's 5 s or less, wait that long and retry once.
  Otherwise return the "rate limited, try in N s" message.
- On **5xx**, use `retryWhen(Retry.backoff(2, 300ms))` for GET requests only. Don't retry
  non-idempotent calls like `POST /queue`.

### 1.3 Persist rotated refresh tokens — ✅ Done
Spotify's PKCE refresh responses can return a **new** `refresh_token`. `TokenResponse` gets that
field today, but nothing saves it. **Do:** If the response contains a refresh token that differs
from the configured one, save it to the resolved `.env` (reuse the writer from 0.1) and keep it in
memory.

### 1.4 Check the current Spotify Web API contract — ✅ Done
Spotify has made several restrictive changes to the Web API for apps in development mode, including
the Nov 2024 deprecation of editorial/algorithmic playlists, recommendations, and audio features,
plus later endpoint renames and removals. **Do:** Read the current Web API changelog and confirm:
- whether `POST /users/{id}/playlists` is still valid or should be `POST /me/playlists`.
- whether `POST /playlists/{id}/tracks` is still valid or should be `/playlists/{id}/items`.
- the current `search` `limit` maximum.
- the dev-mode user limits (add yourself under *User Management* in the dashboard).

Update [`SpotifyApiClient`](src/main/java/io/projects/spotifymcp/client/SpotifyApiClient.java) and
the README's "Notes" section to match.

---

## Phase 2 — New tools (highest user value)

**✅ Done (Nov 2026):** all the tools below plus the three behaviour changes; the server now
registers 23 tools (128 tests). Notes from doing it: liking uses `PUT /me/library` with a `uris`
query parameter (max 40), `GET /me/tracks` is still the read endpoint, and `play_playlist` looks
through up to 200 of the user's playlists before searching the catalog. Not yet tried against
the live API.

Put new methods on `SpotifyApiClient` and new `@McpTool`s in the matching `tools/` class, each
with a unit test in the style of `PlaybackToolsTest`. Any new scopes go in
`SpotifyAuthBootstrap.SCOPES`, and the README must say to re-run the auth bootstrap.

| Tool | Endpoint | New scope |
| --- | --- | --- |
| `play(uri)`: play a track, album, artist, or playlist URI | `PUT /me/player/play` (`uris` for tracks, `context_uri` for everything else) | — |
| `get_queue` | `GET /me/player/queue` | — |
| `seek(positionSeconds)` | `PUT /me/player/seek` | — |
| `set_shuffle(on)` / `set_repeat(mode)` | `PUT /me/player/shuffle`, `/repeat` | — |
| `list_my_playlists` | `GET /me/playlists` | `playlist-read-private` (already granted) |
| `add_to_playlist(playlistName, trackUri)` | playlist items endpoint (see 1.4) | already granted |
| `like_current_track` / `get_liked_tracks` | `PUT /me/library` (URIs) / `GET /me/tracks` | `user-library-modify`, `user-library-read` |
| `get_recently_played` | `GET /me/player/recently-played` | `user-read-recently-played` |
| `get_top_items(type, timeRange)` | `GET /me/top/{type}` | `user-top-read` |

Also:
- **`resume_playback` with no active device:** on `NO_ACTIVE_DEVICE`, fetch devices. If exactly
  one is available, transfer playback to it and resume. Otherwise list the devices in the reply.
- **`add_to_queue` validation:** reject input that doesn't match `^spotify:(track|episode):[A-Za-z0-9]{22}$`.
  Also accept `https://open.spotify.com/track/...` links and convert them to URIs.
- **`play_playlist`:** search `list_my_playlists` first, then fall back to catalog search. Users
  usually mean *their own* playlist.

---

## Phase 3 — Use more of MCP

1. **Tool annotations.** Mark the read tools (`get_*`, `search_catalog`) `readOnlyHint=true` and
   the playlist-creating tools `destructiveHint=false, idempotentHint=false`. Clients use these
   hints to decide when to ask for confirmation. Check which annotation attributes the
   `org.springaicommunity.mcp.annotation.McpTool` version in the Spring AI BOM supports.
2. **Prompts.** Add `@McpPrompt`s such as `dj_set(mood, minutes)` ("search, queue N tracks
   matching the mood, and start playback") and `whats_this_song`. The server already enables the
   prompts capability, but nothing is registered.
3. **Resource change notifications.** `spotify://now-playing` is declared subscribable, but the
   server never sends updates. Add a lightweight poller (every 5 s, and only while a client is
   subscribed) that emits `resources/updated` when the track ID changes.
4. **Structured output.** Return JSON (track ID, URI, name, artists, progress) alongside the
   human-readable text, so a client can chain tools without parsing prose.
5. **Optional Streamable HTTP transport** behind a Spring profile (`-Dspring.profiles.active=http`)
   so the server can run remotely. It must require a bearer token, and the README must say so.

---

## Phase 4 — Pitchfork scraper: check it against the real page

The scraper has never run against the live site, as the README caveat says.
1. Open https://pitchfork.com/reviews/best/albums/ in a browser. Save one real page's HTML to
   `src/test/resources/pitchfork/best-albums-YYYY-MM.html`, and do the same for tracks.
2. Point `PitchforkScraperServiceTest` at those fixtures. Fix `extractArtist` so it reads the
   artist from its own element instead of subtracting the title from the card text.
3. Add a `@Tag("live")` smoke test that CI skips by default.
4. Cache the scrape result for about 6 hours, so repeated tool calls don't fetch the site again.

---

## Phase 5 — Testing and CI

- **End-to-end MCP test.** Start the app in a test and connect with the MCP Java SDK's
  `McpClient.sync(...)` over stdio or in-memory. Assert that `tools/list` returns every expected
  tool name with a non-empty description and a valid input schema. This catches tools that
  silently fail to register.
- **Swap the exchange-function stubs for WireMock or `MockWebServer`** in `SpotifyApiClientTest`,
  so retries, 401 refresh, and `Retry-After` are tested against real HTTP semantics.
- **CI:** ~~make `mvnw` executable~~ **Done:** both earlier CI runs failed with
  `./mvnw: Permission denied`. `mvnw` is now mode `100755`, and the actions are on v5 (the Node 20
  runtime is deprecated). Still to add: a JaCoCo coverage report and a `docker build` step.
- **Version in one place:** `application.yml` hard-codes `version: 0.1.0`. Use `@project.version@`
  with Maven resource filtering.

---

## Phase 6 — Packaging and docs

- Publish a GitHub Release with the fat jar attached when a `v*` tag is pushed.
- Optional: build a GraalVM native image (`spring-boot:build-image -Pnative`). Startup drops from
  about 2 s to under 100 ms, which helps because MCP clients start the process on demand.
- README: add a short GIF of Claude Desktop controlling playback, a "Troubleshooting" table
  (403 means Premium or scopes; 404 means no device; "no refresh token" means run the auth
  profile), and the Claude Code setup command:
  `claude mcp add spotify -- java -jar /abs/path/spotify-mcp-server.jar`.

---

## Suggested order and scope

| Order | Items | Effort |
| --- | --- | --- |
| 1 | 0.1 – 0.5 | ~1 day |
| 2 | 1.1 – 1.4 | ~1 day |
| 3 | Phase 2 (tools) | ~1–2 days |
| 4 | Phase 5 (E2E test + CI fixes) | ~0.5 day |
| 5 | Phase 3, 4, 6 | as time allows |

**Resume line once Phases 0–2 and 5 are done:** "Built a Spring AI MCP server (Java 21, reactive
WebClient) that exposes 20+ Spotify tools and resources to LLM clients. It uses OAuth2 PKCE with
single-flight token refresh, handles 401 and Retry-After, and has end-to-end protocol tests in CI."
