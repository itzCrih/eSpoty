package com.espoty.lyrics;

import com.espoty.ESpotyMod;
import com.espoty.spotify.models.PlaybackState;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricsManager {
    private static final String LRCLIB_API_URL = "https://lrclib.net/api/get";
    private static final Pattern LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2}(?:\\.\\d+)?)\\](.*)");
    private static final Gson GSON = new Gson();
    private static LyricsManager INSTANCE;

    private final HttpClient httpClient;
    private final Map<String, SongLyrics> cache = new ConcurrentHashMap<>();
    private String currentTrackKey = "";
    private SongLyrics currentLyrics = null;
    private boolean isFetching = false;

    public static synchronized LyricsManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LyricsManager();
        }
        return INSTANCE;
    }

    private LyricsManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    public static class SongLyrics {
        private final boolean synced;
        private final List<LyricLine> lines;
        private final String plainLyrics;

        public SongLyrics(boolean synced, List<LyricLine> lines, String plainLyrics) {
            this.synced = synced;
            this.lines = lines != null ? lines : Collections.emptyList();
            this.plainLyrics = plainLyrics != null ? plainLyrics : "";
        }

        public boolean isSynced() {
            return synced;
        }

        public List<LyricLine> getLines() {
            return lines;
        }

        public String getPlainLyrics() {
            return plainLyrics;
        }

        public int getActiveIndex(long progressMs) {
            if (!synced || lines.isEmpty()) return -1;
            // 120ms lead time creates perfectly natural lyric delivery aligned with human auditory perception
            long targetMs = progressMs + 120;
            int low = 0;
            int high = lines.size() - 1;
            int active = -1;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                LyricLine line = lines.get(mid);
                if (line.getTimestampMs() <= targetMs) {
                    active = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            return active;
        }

        public LyricLine getActiveLine(long progressMs) {
            int idx = getActiveIndex(progressMs);
            return (idx >= 0 && idx < lines.size()) ? lines.get(idx) : null;
        }

        public LyricLine getNextLine(long progressMs) {
            int idx = getActiveIndex(progressMs);
            if (idx >= 0 && idx + 1 < lines.size()) {
                return lines.get(idx + 1);
            }
            return null;
        }

        public LyricLine getPreviousLine(long progressMs) {
            int idx = getActiveIndex(progressMs);
            if (idx > 0 && idx - 1 < lines.size()) {
                return lines.get(idx - 1);
            }
            return null;
        }
    }

    public void updateForTrack(PlaybackState state) {
        if (state == null || state.getTrackName().isEmpty()) {
            currentLyrics = null;
            currentTrackKey = "";
            return;
        }

        String key = state.getTrackName() + " - " + state.getArtistNames();
        if (key.equals(currentTrackKey)) {
            return;
        }

        currentTrackKey = key;
        currentLyrics = null;
        SongLyrics cached = cache.get(key);
        if (cached != null) {
            currentLyrics = cached;
            return;
        }

        fetchLyricsAsync(state, key);
    }

    private void fetchLyricsAsync(PlaybackState state, String key) {
        if (isFetching) return;
        isFetching = true;

        CompletableFuture.runAsync(() -> {
            try {
                String firstArtist = state.getArtistNames();
                if (firstArtist.contains(",")) {
                    firstArtist = firstArtist.split(",")[0].trim();
                }

                String url = LRCLIB_API_URL + "?" +
                        "track_name=" + URLEncoder.encode(state.getTrackName(), StandardCharsets.UTF_8) +
                        "&artist_name=" + URLEncoder.encode(firstArtist, StandardCharsets.UTF_8) +
                        "&duration=" + (state.getDurationMs() / 1000L);

                if (!state.getAlbumName().isEmpty()) {
                    url += "&album_name=" + URLEncoder.encode(state.getAlbumName(), StandardCharsets.UTF_8);
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "eSpoty Minecraft Mod/1.0.9")
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                    SongLyrics parsed = parseLrcLibResponse(json);
                    cache.put(key, parsed);
                    if (key.equals(currentTrackKey)) {
                        currentLyrics = parsed;
                    }
                } else {
                    // Try fallback search without album if not found
                    SongLyrics empty = new SongLyrics(false, Collections.emptyList(), "No lyrics found.");
                    cache.put(key, empty);
                    if (key.equals(currentTrackKey)) {
                        currentLyrics = empty;
                    }
                }
            } catch (Exception e) {
                ESpotyMod.LOGGER.debug("Could not fetch lyrics for {}: {}", key, e.getMessage());
                SongLyrics empty = new SongLyrics(false, Collections.emptyList(), "Lyrics unavailable.");
                cache.put(key, empty);
                if (key.equals(currentTrackKey)) {
                    currentLyrics = empty;
                }
            } finally {
                isFetching = false;
            }
        });
    }

    private SongLyrics parseLrcLibResponse(JsonObject json) {
        if (json == null) return new SongLyrics(false, Collections.emptyList(), "");

        String syncedText = json.has("syncedLyrics") && !json.get("syncedLyrics").isJsonNull()
                ? json.get("syncedLyrics").getAsString() : null;
        String plainText = json.has("plainLyrics") && !json.get("plainLyrics").isJsonNull()
                ? json.get("plainLyrics").getAsString() : "";

        if (syncedText != null && !syncedText.isEmpty()) {
            List<LyricLine> lines = new ArrayList<>();
            String[] rawLines = syncedText.split("\\r?\\n");
            for (String lineStr : rawLines) {
                Matcher matcher = LRC_PATTERN.matcher(lineStr);
                if (matcher.matches()) {
                    try {
                        int minutes = Integer.parseInt(matcher.group(1));
                        double seconds = Double.parseDouble(matcher.group(2));
                        long timeMs = (long) ((minutes * 60 + seconds) * 1000);
                        String content = matcher.group(3).trim();
                        lines.add(new LyricLine(timeMs, content));
                    } catch (Exception ignored) {}
                }
            }
            if (!lines.isEmpty()) {
                lines.sort(Comparator.comparingLong(LyricLine::getTimestampMs));
                return new SongLyrics(true, lines, plainText);
            }
        }

        return new SongLyrics(false, Collections.emptyList(), plainText);
    }

    public SongLyrics getCurrentLyrics() {
        return currentLyrics;
    }

    public LyricLine getActiveLyricLine(long progressMs) {
        if (currentLyrics == null) return null;
        return currentLyrics.getActiveLine(progressMs);
    }

    public LyricLine getNextLyricLine(long progressMs) {
        if (currentLyrics == null) return null;
        return currentLyrics.getNextLine(progressMs);
    }

    public LyricLine getPreviousLyricLine(long progressMs) {
        if (currentLyrics == null) return null;
        return currentLyrics.getPreviousLine(progressMs);
    }
}
