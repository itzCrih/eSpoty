package it.itzcrih.espoty.lyrics;

import it.itzcrih.espoty.ESpotyMod;
import it.itzcrih.espoty.spotify.models.PlaybackState;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
    private static final String LRCLIB_GET_URL = "https://lrclib.net/api/get";
    private static final String LRCLIB_SEARCH_URL = "https://lrclib.net/api/search";
    private static final String LYRICS_OVH_URL = "https://api.lyrics.ovh/v1/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
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
                .followRedirects(HttpClient.Redirect.NORMAL)
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

    public static String cleanTrackName(String name) {
        if (name == null) return "";
        String s = name;
        s = s.replaceAll("(?i)\\s*[\\[\\(]\\s*(?:feat|ft|with)\\.?\\s+[^)\\]]+[\\]\\)]", "");
        s = s.replaceAll("(?i)\\s*[\\[\\(]\\s*(?:\\d{4}\\s+)?remaster(?:ed)?(?:\\s+\\d{4})?[^)\\]]*[\\]\\)]", "");
        s = s.replaceAll("(?i)\\s*-\\s*(?:\\d{4}\\s+)?remaster(?:ed)?.*$", "");
        s = s.replaceAll("(?i)\\s*-\\s*(?:single|bonus track|radio edit|deluxe|live).*$", "");
        s = s.replaceAll("(?i)\\s*[\\[\\(]\\s*(?:live|official video|official audio|lyric video|bonus track|single version|deluxe edition|acoustic)[^)\\]]*[\\]\\)]", "");
        return s.trim();
    }

    public static String getPrimaryArtist(String artists) {
        if (artists == null) return "";
        String a = artists;
        if (a.contains(",")) a = a.split(",")[0];
        if (a.contains("&")) a = a.split("&")[0];
        if (a.toLowerCase(Locale.ROOT).contains(" feat.")) a = a.split("(?i)\\s+feat\\.")[0];
        return a.trim();
    }

    public void clear() {
        this.currentLyrics = null;
        this.currentTrackKey = "";
    }

    public void updateForTrack(PlaybackState state) {
        if (state == null || state.getTrackName().isEmpty() || state.isAd()) {
            clear();
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
                String rawTitle = state.getTrackName();
                String cleanTitle = cleanTrackName(rawTitle);
                String primaryArtist = getPrimaryArtist(state.getArtistNames());

                SongLyrics result = tryFetchLrcLibGet(cleanTitle, primaryArtist);
                if (result == null || !result.isSynced()) {
                    SongLyrics fallbackRaw = tryFetchLrcLibGet(rawTitle, primaryArtist);
                    if (fallbackRaw != null && fallbackRaw.isSynced()) {
                        result = fallbackRaw;
                    }
                }

                if (result == null || !result.isSynced()) {
                    SongLyrics searched = tryFetchLrcLibSearch(cleanTitle, primaryArtist);
                    if (searched != null && searched.isSynced()) {
                        result = searched;
                    }
                }

                if (result == null || (!result.isSynced() && result.getPlainLyrics().isEmpty())) {
                    SongLyrics ovh = tryFetchLyricsOvh(primaryArtist, cleanTitle);
                    if (ovh != null && !ovh.getPlainLyrics().isEmpty()) {
                        result = ovh;
                    }
                }

                if (result == null) {
                    result = new SongLyrics(false, Collections.emptyList(), "No lyrics found.");
                }

                cache.put(key, result);
                if (key.equals(currentTrackKey)) {
                    currentLyrics = result;
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

    private SongLyrics tryFetchLrcLibGet(String trackName, String artistName) {
        if (trackName == null || trackName.isEmpty()) return null;
        try {
            String url = LRCLIB_GET_URL + "?" +
                    "track_name=" + URLEncoder.encode(trackName, StandardCharsets.UTF_8) +
                    "&artist_name=" + URLEncoder.encode(artistName, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                return parseLrcLibResponse(json);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private SongLyrics tryFetchLrcLibSearch(String trackName, String artistName) {
        if (trackName == null || trackName.isEmpty()) return null;
        try {
            String url = LRCLIB_SEARCH_URL + "?" +
                    "track_name=" + URLEncoder.encode(trackName, StandardCharsets.UTF_8) +
                    "&artist_name=" + URLEncoder.encode(artistName, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonElement root = GSON.fromJson(response.body(), JsonElement.class);
                if (root != null && root.isJsonArray()) {
                    JsonArray arr = root.getAsJsonArray();
                    JsonObject bestPlain = null;
                    for (int i = 0; i < arr.size(); i++) {
                        JsonElement el = arr.get(i);
                        if (el.isJsonObject()) {
                            JsonObject obj = el.getAsJsonObject();
                            if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()) {
                                String syn = obj.get("syncedLyrics").getAsString();
                                if (!syn.trim().isEmpty()) {
                                    return parseLrcLibResponse(obj);
                                }
                            }
                            if (bestPlain == null && obj.has("plainLyrics") && !obj.get("plainLyrics").isJsonNull()) {
                                bestPlain = obj;
                            }
                        }
                    }
                    if (bestPlain != null) {
                        return parseLrcLibResponse(bestPlain);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private SongLyrics tryFetchLyricsOvh(String artist, String title) {
        if (artist == null || artist.isEmpty() || title == null || title.isEmpty()) return null;
        try {
            String url = LYRICS_OVH_URL +
                    URLEncoder.encode(artist, StandardCharsets.UTF_8) + "/" +
                    URLEncoder.encode(title, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                if (json != null && json.has("lyrics") && !json.get("lyrics").isJsonNull()) {
                    String lyrics = json.get("lyrics").getAsString().trim();
                    if (!lyrics.isEmpty()) {
                        return new SongLyrics(false, Collections.emptyList(), lyrics);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
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

    public LyricLine getPreviousLine(long progressMs) {
        if (currentLyrics == null) return null;
        return currentLyrics.getPreviousLine(progressMs);
    }
}
