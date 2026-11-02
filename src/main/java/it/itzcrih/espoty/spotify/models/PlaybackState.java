package it.itzcrih.espoty.spotify.models;

import java.util.Locale;

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

    private final boolean canPlayPause;
    private final boolean canNext;
    private final boolean canPrev;
    private final boolean canShuffle;
    private final boolean canRepeat;
    private final boolean canSeek;
    private final boolean isAd;

    public static boolean isAdvertisement(String title, String artist, String album, String trackId, boolean isExplicitAd) {
        if (isExplicitAd) return true;
        if (trackId != null && (trackId.startsWith("spotify:ad:") || trackId.contains("advertisement"))) return true;
        if (title == null || title.isEmpty()) return false;
        String t = title.trim().toLowerCase(Locale.ROOT);
        String a = (artist != null) ? artist.trim().toLowerCase(Locale.ROOT) : "";
        String alb = (album != null) ? album.trim().toLowerCase(Locale.ROOT) : "";

        if (t.equals("advertisement") || t.equals("spotify") || t.equals("pubblicità") ||
                t.equals("anuncio") || t.equals("publicidad") || t.equals("werbung") ||
                t.equals("publicité") || t.equals("propaganda") || t.equals("reklama") ||
                t.equals("annons") || t.equals("annonse")) {
            return true;
        }
        if (t.startsWith("advertisement") || t.startsWith("pubblicità") || t.startsWith("werbung") ||
                t.contains("spotify advertisement") || t.startsWith("spotify free") || t.contains("sponsored message")) {
            return true;
        }
        if ((a.equals("spotify") || a.isEmpty()) && (t.equals("spotify") || t.equals("advertisement") || t.equals("pubblicità"))) {
            return true;
        }
        if (alb.equals("advertisement") || alb.equals("pubblicità") || alb.equals("spotify")) {
            return true;
        }
        return false;
    }

    public PlaybackState(String trackId, String trackName, String artistNames, String albumName,
                         String albumArtUrl, long durationMs, long progressMs, boolean isPlaying,
                         boolean shuffleState, String repeatState, int volumePercent,
                         String deviceName, String deviceType) {
        this(trackId, trackName, artistNames, albumName, albumArtUrl, durationMs, progressMs,
             isPlaying, shuffleState, repeatState, volumePercent, deviceName, deviceType,
             true, true, true, true, true, true, false);
    }

    public PlaybackState(String trackId, String trackName, String artistNames, String albumName,
                         String albumArtUrl, long durationMs, long progressMs, boolean isPlaying,
                         boolean shuffleState, String repeatState, int volumePercent,
                         String deviceName, String deviceType,
                         boolean canPlayPause, boolean canNext, boolean canPrev,
                         boolean canShuffle, boolean canRepeat, boolean canSeek) {
        this(trackId, trackName, artistNames, albumName, albumArtUrl, durationMs, progressMs,
             isPlaying, shuffleState, repeatState, volumePercent, deviceName, deviceType,
             canPlayPause, canNext, canPrev, canShuffle, canRepeat, canSeek, false);
    }

    public PlaybackState(String trackId, String trackName, String artistNames, String albumName,
                         String albumArtUrl, long durationMs, long progressMs, boolean isPlaying,
                         boolean shuffleState, String repeatState, int volumePercent,
                         String deviceName, String deviceType,
                         boolean canPlayPause, boolean canNext, boolean canPrev,
                         boolean canShuffle, boolean canRepeat, boolean canSeek,
                         boolean isExplicitAd) {
        this.trackId = trackId != null ? trackId : "";
        this.trackName = trackName != null ? trackName : "Unknown Title";
        this.artistNames = artistNames != null ? artistNames : "Unknown Artist";
        this.albumName = albumName != null ? albumName : "";
        this.albumArtUrl = albumArtUrl != null ? albumArtUrl : "";
        this.durationMs = durationMs;
        this.progressMs = progressMs;
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

        this.isAd = isAdvertisement(this.trackName, this.artistNames, this.albumName, this.trackId, isExplicitAd);
        this.isPlaying = isPlaying && !this.isAd;
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

    public boolean isAd() {
        return isAd;
    }

    public static String formatTime(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
