package com.espoty.spotify.models;

public class QueueItem {
    private final String id;
    private final String name;
    private final String artists;
    private final String albumName;
    private final String albumArtUrl;
    private final long durationMs;

    public QueueItem(String id, String name, String artists, String albumName, String albumArtUrl, long durationMs) {
        this.id = id != null ? id : "";
        this.name = name != null ? name : "Unknown";
        this.artists = artists != null ? artists : "Unknown";
        this.albumName = albumName != null ? albumName : "";
        this.albumArtUrl = albumArtUrl != null ? albumArtUrl : "";
        this.durationMs = durationMs;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getArtists() {
        return artists;
    }

    public String getAlbumName() {
        return albumName;
    }

    public String getAlbumArtUrl() {
        return albumArtUrl;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getFormattedDuration() {
        return PlaybackState.formatTime(durationMs);
    }
}
