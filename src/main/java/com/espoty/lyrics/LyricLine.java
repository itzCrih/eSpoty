package com.espoty.lyrics;

import com.espoty.spotify.models.PlaybackState;

public class LyricLine {
    private final long timestampMs;
    private final String text;

    public LyricLine(long timestampMs, String text) {
        this.timestampMs = timestampMs;
        this.text = text != null ? text.trim() : "";
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public String getText() {
        return text;
    }

    public String getFormattedTimestamp() {
        return PlaybackState.formatTime(timestampMs);
    }
}
