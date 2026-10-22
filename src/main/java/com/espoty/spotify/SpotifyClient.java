package com.espoty.spotify;

import com.espoty.ESpotyMod;
import com.espoty.spotify.models.PlaybackState;
import com.espoty.spotify.models.QueueItem;
import com.espoty.spotify.models.QueueResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SpotifyClient {
    private static final String BASE_URL = "https://api.spotify.com/v1";
    private static final Gson GSON = new Gson();
    private static SpotifyClient INSTANCE;

    private final HttpClient httpClient;
    private final SpotifyAuth auth;

    public static synchronized SpotifyClient getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SpotifyClient();
        }
        return INSTANCE;
    }

    private SpotifyClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
        this.auth = SpotifyAuth.getInstance();
    }

    private CompletableFuture<HttpResponse<String>> sendAuthorizedRequest(String method, String endpoint, String jsonBody) {
        return auth.getValidAccessToken().thenCompose(token -> {
            if (token == null || token.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + endpoint))
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(6));

            if ("GET".equalsIgnoreCase(method)) {
                builder.GET();
            } else if ("POST".equalsIgnoreCase(method)) {
                HttpRequest.BodyPublisher publisher = (jsonBody != null && !jsonBody.isEmpty())
                        ? HttpRequest.BodyPublishers.ofString(jsonBody)
                        : HttpRequest.BodyPublishers.noBody();
                if (jsonBody != null) builder.header("Content-Type", "application/json");
                builder.POST(publisher);
            } else if ("PUT".equalsIgnoreCase(method)) {
                HttpRequest.BodyPublisher publisher = (jsonBody != null && !jsonBody.isEmpty())
                        ? HttpRequest.BodyPublishers.ofString(jsonBody)
                        : HttpRequest.BodyPublishers.noBody();
                if (jsonBody != null) builder.header("Content-Type", "application/json");
                builder.PUT(publisher);
            }

            return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
        });
    }

    public CompletableFuture<PlaybackState> fetchPlaybackState() {
        return sendAuthorizedRequest("GET", "/me/player", null).thenApply(response -> {
            if (response == null || response.statusCode() == 204 || response.statusCode() != 200) {
                return null;
            }

            try {
                JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                if (json == null || !json.has("item") || json.get("item").isJsonNull()) {
                    return null;
                }

                JsonObject item = json.getAsJsonObject("item");
                String trackId = item.has("id") && !item.get("id").isJsonNull() ? item.get("id").getAsString() : "";
                String trackName = item.has("name") ? item.get("name").getAsString() : "Unknown Title";
                long durationMs = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0;
                long progressMs = json.has("progress_ms") && !json.get("progress_ms").isJsonNull() ? json.get("progress_ms").getAsLong() : 0;
                boolean isPlaying = json.has("is_playing") && json.get("is_playing").getAsBoolean();
                boolean shuffleState = json.has("shuffle_state") && json.get("shuffle_state").getAsBoolean();
                String repeatState = json.has("repeat_state") ? json.get("repeat_state").getAsString() : "off";

                // Artists
                StringBuilder artists = new StringBuilder();
                if (item.has("artists") && item.get("artists").isJsonArray()) {
                    JsonArray artistsArr = item.getAsJsonArray("artists");
                    for (int i = 0; i < artistsArr.size(); i++) {
                        JsonObject artistObj = artistsArr.get(i).getAsJsonObject();
                        if (artistObj.has("name")) {
                            if (artists.length() > 0) artists.append(", ");
                            artists.append(artistObj.get("name").getAsString());
                        }
                    }
                }

                // Album
                String albumName = "";
                String albumArtUrl = "";
                if (item.has("album") && !item.get("album").isJsonNull()) {
                    JsonObject album = item.getAsJsonObject("album");
                    if (album.has("name")) albumName = album.get("name").getAsString();
                    if (album.has("images") && album.get("images").isJsonArray()) {
                        JsonArray images = album.getAsJsonArray("images");
                        if (!images.isEmpty()) {
                            // First image is usually 640x640 or highest
                            albumArtUrl = images.get(0).getAsJsonObject().get("url").getAsString();
                        }
                    }
                }

                // Device
                String deviceName = "Local Device";
                String deviceType = "Computer";
                int volumePercent = 50;
                if (json.has("device") && !json.get("device").isJsonNull()) {
                    JsonObject device = json.getAsJsonObject("device");
                    if (device.has("name")) deviceName = device.get("name").getAsString();
                    if (device.has("type")) deviceType = device.get("type").getAsString();
                    if (device.has("volume_percent") && !device.get("volume_percent").isJsonNull()) {
                        volumePercent = device.get("volume_percent").getAsInt();
                    }
                }

                return new PlaybackState(trackId, trackName, artists.toString(), albumName, albumArtUrl,
                        durationMs, progressMs, isPlaying, shuffleState, repeatState, volumePercent,
                        deviceName, deviceType);

            } catch (Exception e) {
                ESpotyMod.LOGGER.error("Failed to parse Spotify playback state", e);
                return null;
            }
        });
    }

    public CompletableFuture<QueueResponse> fetchQueue() {
        return sendAuthorizedRequest("GET", "/me/player/queue", null).thenApply(response -> {
            if (response == null || response.statusCode() != 200) {
                return null;
            }

            try {
                JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                if (json == null) return null;

                QueueItem currentlyPlaying = null;
                if (json.has("currently_playing") && !json.get("currently_playing").isJsonNull()) {
                    currentlyPlaying = parseQueueItem(json.getAsJsonObject("currently_playing"));
                }

                List<QueueItem> queueList = new ArrayList<>();
                if (json.has("queue") && json.get("queue").isJsonArray()) {
                    JsonArray queueArray = json.getAsJsonArray("queue");
                    for (JsonElement el : queueArray) {
                        if (el.isJsonObject()) {
                            QueueItem item = parseQueueItem(el.getAsJsonObject());
                            if (item != null) queueList.add(item);
                        }
                    }
                }

                return new QueueResponse(currentlyPlaying, queueList);
            } catch (Exception e) {
                ESpotyMod.LOGGER.error("Failed to parse Spotify queue", e);
                return null;
            }
        });
    }

    private QueueItem parseQueueItem(JsonObject item) {
        if (item == null) return null;
        String id = item.has("id") && !item.get("id").isJsonNull() ? item.get("id").getAsString() : "";
        String name = item.has("name") ? item.get("name").getAsString() : "Unknown";
        long durationMs = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0;

        StringBuilder artists = new StringBuilder();
        if (item.has("artists") && item.get("artists").isJsonArray()) {
            JsonArray arr = item.getAsJsonArray("artists");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject a = arr.get(i).getAsJsonObject();
                if (a.has("name")) {
                    if (artists.length() > 0) artists.append(", ");
                    artists.append(a.get("name").getAsString());
                }
            }
        }

        String albumName = "";
        String albumArt = "";
        if (item.has("album") && !item.get("album").isJsonNull()) {
            JsonObject album = item.getAsJsonObject("album");
            if (album.has("name")) albumName = album.get("name").getAsString();
            if (album.has("images") && album.get("images").isJsonArray()) {
                JsonArray images = album.getAsJsonArray("images");
                if (!images.isEmpty()) {
                    albumArt = images.get(0).getAsJsonObject().get("url").getAsString();
                }
            }
        }

        return new QueueItem(id, name, artists.toString(), albumName, albumArt, durationMs);
    }

    public CompletableFuture<Boolean> play() {
        return sendAuthorizedRequest("PUT", "/me/player/play", null).thenApply(r -> r != null && (r.statusCode() == 204 || r.statusCode() == 200));
    }

    public CompletableFuture<Boolean> pause() {
        return sendAuthorizedRequest("PUT", "/me/player/pause", null).thenApply(r -> r != null && (r.statusCode() == 204 || r.statusCode() == 200));
    }

    public CompletableFuture<Boolean> next() {
        return sendAuthorizedRequest("POST", "/me/player/next", null).thenApply(r -> r != null && (r.statusCode() == 204 || r.statusCode() == 200));
    }

    public CompletableFuture<Boolean> previous() {
        return sendAuthorizedRequest("POST", "/me/player/previous", null).thenApply(r -> r != null && (r.statusCode() == 204 || r.statusCode() == 200));
    }

    public CompletableFuture<Boolean> seek(long positionMs) {
        return sendAuthorizedRequest("PUT", "/me/player/seek?position_ms=" + positionMs, null)
                .thenApply(r -> r != null && (r.statusCode() == 204 || r.statusCode() == 200));
    }

    public CompletableFuture<Boolean> setVolume(int volumePercent) {
        int clamped = Math.max(0, Math.min(100, volumePercent));
        return sendAuthorizedRequest("PUT", "/me/player/volume?volume_percent=" + clamped, null)
                .thenApply(r -> r != null && (r.statusCode() == 204 || r.statusCode() == 200));
    }

    public CompletableFuture<Boolean> setShuffle(boolean state) {
        return sendAuthorizedRequest("PUT", "/me/player/shuffle?state=" + state, null)
                .thenApply(r -> r != null && (r.statusCode() == 204 || r.statusCode() == 200));
    }

    public CompletableFuture<Boolean> setRepeat(String state) {
        return sendAuthorizedRequest("PUT", "/me/player/repeat?state=" + state, null)
                .thenApply(r -> r != null && (r.statusCode() == 204 || r.statusCode() == 200));
    }
}
