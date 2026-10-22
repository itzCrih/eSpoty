package com.espoty.spotify.models;

public class PlaybackState {
    private final String trackId;
    private final String trackName;
    private final String artistNames;
    private final String albumName;
    private final String albumArtUrl;
    private final long durationMs;
    private final long progressMs;
    private final boolean isPlaying;
    private final boolean shuffleState;
    private final String repeatState;
    private final int volumePercent;
    private final String deviceName;
    private final String deviceType;
    private final long timestamp;

    // Controls capability flags from SMTC
    private final boolean canPlayPause;
    private final boolean canNext;
    private final boolean canPrev;
    private final boolean canShuffle;
    private final boolean canRepeat;
    private final boolean canSeek;

    public PlaybackState(String trackId, String trackName, String artistNames, String albumName,
                         String albumArtUrl, long durationMs, long progressMs, boolean isPlaying,
                         boolean shuffleState, String repeatState, int volumePercent,
                         String deviceName, String deviceType) {
        this(trackId, trackName, artistNames, albumName, albumArtUrl, durationMs, progressMs,
             isPlaying, shuffleState, repeatState, volumePercent, deviceName, deviceType,
             true, true, true, true, true, true);
    }

    public PlaybackState(String trackId, String trackName, String artistNames, String albumName,
                         String albumArtUrl, long durationMs, long progressMs, boolean isPlaying,
                         boolean shuffleState, String repeatState, int volumePercent,
                         String deviceName, String deviceType,
                         boolean canPlayPause, boolean canNext, boolean canPrev,
                         boolean canShuffle, boolean canRepeat, boolean canSeek) {
        this.trackId = trackId != null ? trackId : "";
        this.trackName = trackName != null ? trackName : "Unknown Title";
        this.artistNames = artistNames != null ? artistNames : "Unknown Artist";
        this.albumName = albumName != null ? albumName : "";
        this.albumArtUrl = albumArtUrl != null ? albumArtUrl : "";
        this.durationMs = durationMs;
        this.progressMs = progressMs;
        this.isPlaying = isPlaying;
        this.shuffleState = shuffleState;
        this.repeatState = repeatState != null ? repeatState : "off";
        this.volumePercent = volumePercent;
        this.deviceName = deviceName != null ? deviceName : "Spotify Desktop";
        this.deviceType = deviceType != null ? deviceType : "Computer";
        this.timestamp = System.currentTimeMillis();

        this.canPlayPause = canPlayPause;
        this.canNext = canNext;
        this.canPrev = canPrev;
        this.canShuffle = canShuffle;
        this.canRepeat = canRepeat;
        this.canSeek = canSeek;
    }

    public String getTrackId() {
        return trackId;
    }

    public String getTrackName() {
        return trackName;
    }

    public String getArtistNames() {
        return artistNames;
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

    public long getProgressMs() {
        return progressMs;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean isShuffleState() {
        return shuffleState;
    }

    public String getRepeatState() {
        return repeatState;
    }

    public int getVolumePercent() {
        return volumePercent;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean canPlayPause() {
        return canPlayPause;
    }

    public boolean canNext() {
        return canNext;
    }

    public boolean canPrev() {
        return canPrev;
    }

    public boolean canShuffle() {
        return canShuffle;
    }

    public boolean canRepeat() {
        return canRepeat;
    }

    public boolean canSeek() {
        return canSeek;
    }

    public static String formatTime(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
