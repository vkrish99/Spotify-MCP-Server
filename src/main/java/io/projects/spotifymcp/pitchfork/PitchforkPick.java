package io.projects.spotifymcp.pitchfork;

/** One "Best New Music" pick scraped from Pitchfork -- type is "album" or "track". */
public record PitchforkPick(String artist, String title, String type) {
}
