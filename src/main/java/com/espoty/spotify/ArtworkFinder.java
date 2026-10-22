package com.espoty.spotify;

import com.espoty.ESpotyMod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ArtworkFinder {
    private static final String ITUNES_SEARCH_URL = "https://itunes.apple.com/search";
    private static final Gson GSON = new Gson();
    private static ArtworkFinder INSTANCE;

    private final HttpClient httpClient;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public static synchronized ArtworkFinder getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ArtworkFinder();
        }
        return INSTANCE;
    }

    private ArtworkFinder() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public CompletableFuture<String> findArtworkUrl(String title, String artist) {
        if (title == null || title.isEmpty()) {
            return CompletableFuture.completedFuture("");
        }

        String key = title.trim() + " - " + (artist != null ? artist.trim() : "");
        String cached = cache.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String term = title;
                if (artist != null && !artist.isEmpty()) {
                    // Use first artist
                    String firstArtist = artist.split(",")[0].trim();
                    term = firstArtist + " " + title;
                }

                String url = ITUNES_SEARCH_URL + "?term=" + URLEncoder.encode(term, StandardCharsets.UTF_8)
                        + "&entity=song&limit=1";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "eSpoty Minecraft Mod/1.0.8")
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                    if (json != null && json.has("results") && json.get("results").isJsonArray()) {
                        JsonArray results = json.getAsJsonArray("results");
                        if (!results.isEmpty()) {
                            JsonObject first = results.get(0).getAsJsonObject();
                            if (first.has("artworkUrl100")) {
                                String art = first.get("artworkUrl100").getAsString();
                                // Upgrade to 600x600 HD cover
                                art = art.replace("100x100bb", "600x600bb");
                                cache.put(key, art);
                                return art;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                ESpotyMod.LOGGER.debug("Could not find artwork for {}: {}", key, e.getMessage());
            }

            cache.put(key, "");
            return "";
        });
    }
}
