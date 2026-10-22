package com.espoty.spotify;

import com.espoty.ESpotyMod;
import com.espoty.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.loader.api.FabricLoader;

import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SpotifyAuth {
    // Default public client ID for eSpoty / open desktop client
    public static final String DEFAULT_CLIENT_ID = "c78c85ebda32454f85e492b4526279f1";
    private static final String SPOTIFY_AUTH_URL = "https://accounts.spotify.com/authorize";
    private static final String SPOTIFY_TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing";
    private static final File TOKEN_FILE = FabricLoader.getInstance().getConfigDir().resolve("espoty/tokens.json").toFile();
    private static final Gson GSON = new Gson();

    private static SpotifyAuth INSTANCE;

    private String accessToken = "";
    private String refreshToken = "";
    private long expiresAt = 0;

    private HttpServer callbackServer;
    private String pendingCodeVerifier;
    private String pendingState;
    private int currentPort = 8888;
    private final HttpClient httpClient;

    private final List<Consumer<Boolean>> authListeners = new ArrayList<>();

    public static synchronized SpotifyAuth getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SpotifyAuth();
        }
        return INSTANCE;
    }

    private SpotifyAuth() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        loadTokens();
    }

    public synchronized boolean isAuthenticated() {
        return (accessToken != null && !accessToken.isEmpty()) || (refreshToken != null && !refreshToken.isEmpty());
    }

    public String getEffectiveClientId() {
        String custom = ModConfig.getInstance().getCustomClientId();
        return (custom != null && !custom.isEmpty()) ? custom : DEFAULT_CLIENT_ID;
    }

    public void addAuthListener(Consumer<Boolean> listener) {
        synchronized (authListeners) {
            authListeners.add(listener);
        }
    }

    private void notifyAuthListeners(boolean success) {
        synchronized (authListeners) {
            for (Consumer<Boolean> listener : authListeners) {
                try {
                    listener.accept(success);
                } catch (Exception e) {
                    ESpotyMod.LOGGER.error("Auth listener error", e);
                }
            }
        }
    }

    public synchronized void startLoginFlow(Consumer<String> statusCallback) {
        stopCallbackServer();

        try {
            int port = findAvailablePort(8888, 8895);
            this.currentPort = port;
            this.pendingCodeVerifier = generateCodeVerifier();
            this.pendingState = generateRandomString(16);

            String codeChallenge = generateCodeChallenge(pendingCodeVerifier);
            String redirectUri = "http://127.0.0.1:" + port + "/callback";

            callbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            callbackServer.createContext("/callback", new AuthCallbackHandler(statusCallback));
            callbackServer.setExecutor(null);
            callbackServer.start();

            String authUrl = SPOTIFY_AUTH_URL + "?" +
                    "client_id=" + URLEncoder.encode(getEffectiveClientId(), StandardCharsets.UTF_8) +
                    "&response_type=code" +
                    "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                    "&code_challenge_method=S256" +
                    "&code_challenge=" + URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8) +
                    "&state=" + URLEncoder.encode(pendingState, StandardCharsets.UTF_8) +
                    "&scope=" + URLEncoder.encode(SCOPES, StandardCharsets.UTF_8);

            if (statusCallback != null) {
                statusCallback.accept("Opening browser for Spotify authorization...");
            }

            openBrowser(authUrl);

        } catch (Exception e) {
            ESpotyMod.LOGGER.error("Failed to start login flow", e);
            if (statusCallback != null) {
                statusCallback.accept("Login failed: " + e.getMessage());
            }
            stopCallbackServer();
        }
    }

    private class AuthCallbackHandler implements HttpHandler {
        private final Consumer<String> statusCallback;

        public AuthCallbackHandler(Consumer<String> statusCallback) {
            this.statusCallback = statusCallback;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI requestURI = exchange.getRequestURI();
            String query = requestURI.getQuery();
            Map<String, String> queryParams = parseQuery(query);

            String state = queryParams.get("state");
            String code = queryParams.get("code");
            String error = queryParams.get("error");

            boolean success = false;
            String message;

            if (error != null) {
                message = "Spotify authorization was denied or canceled: " + error;
            } else if (state == null || !state.equals(pendingState)) {
                message = "Invalid state parameter. Possible security CSRF mismatch.";
            } else if (code != null) {
                message = "Authorization successful! Connecting to eSpoty in Minecraft...";
                success = true;
            } else {
                message = "Unknown error occurred during authorization callback.";
            }

            // Beautiful Gold & Yellow themed response HTML
            String responseHtml = "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
                    "<title>eSpoty - Spotify Integration</title>" +
                    "<style>" +
                    "body{background:#121217;color:#fff;font-family:'Segoe UI',sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;}" +
                    ".card{background:#1a1a24;border:2px solid #ffd700;border-radius:16px;padding:40px;text-align:center;max-width:480px;box-shadow:0 10px 30px rgba(0,0,0,0.8);}" +
                    "h1{color:#ffd700;margin-top:0;font-size:28px;}" +
                    "p{color:#fff59d;font-size:16px;line-height:1.6;}" +
                    ".badge{display:inline-block;padding:8px 16px;background:" + (success ? "#2e7d32" : "#c62828") + ";border-radius:20px;color:white;font-weight:bold;margin-bottom:15px;}" +
                    "</style></head><body><div class='card'>" +
                    "<div class='badge'>" + (success ? "CONNECTED" : "FAILED") + "</div>" +
                    "<h1>eSpoty &bull; Spotify</h1>" +
                    "<p>" + message + "</p>" +
                    (success ? "<p style='color:#ffd700;'>You may now close this window and return to Minecraft!</p>" : "") +
                    "</div></body></html>";

            byte[] bytes = responseHtml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }

            if (success) {
                String redirectUri = "http://127.0.0.1:" + currentPort + "/callback";
                exchangeCodeForTokens(code, redirectUri, pendingCodeVerifier, statusCallback);
            } else {
                if (statusCallback != null) {
                    statusCallback.accept("Authorization failed: " + message);
                }
                notifyAuthListeners(false);
            }

            // Stop server shortly after
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {}
                stopCallbackServer();
            }).start();
        }
    }

    private void exchangeCodeForTokens(String code, String redirectUri, String codeVerifier, Consumer<String> statusCallback) {
        CompletableFuture.runAsync(() -> {
            try {
                String body = "client_id=" + URLEncoder.encode(getEffectiveClientId(), StandardCharsets.UTF_8) +
                        "&grant_type=authorization_code" +
                        "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                        "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                        "&code_verifier=" + URLEncoder.encode(codeVerifier, StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SPOTIFY_TOKEN_URL))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                    this.accessToken = json.get("access_token").getAsString();
                    if (json.has("refresh_token")) {
                        this.refreshToken = json.get("refresh_token").getAsString();
                    }
                    int expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 3600;
                    this.expiresAt = System.currentTimeMillis() + (expiresIn * 1000L);

                    saveTokens();
                    if (statusCallback != null) {
                        statusCallback.accept("Successfully connected to Spotify!");
                    }
                    notifyAuthListeners(true);
                } else {
                    ESpotyMod.LOGGER.error("Failed to exchange code: {} - {}", response.statusCode(), response.body());
                    if (statusCallback != null) {
                        statusCallback.accept("Token exchange failed: HTTP " + response.statusCode());
                    }
                    notifyAuthListeners(false);
                }
            } catch (Exception e) {
                ESpotyMod.LOGGER.error("Error exchanging code for tokens", e);
                if (statusCallback != null) {
                    statusCallback.accept("Connection error: " + e.getMessage());
                }
                notifyAuthListeners(false);
            }
        });
    }

    public synchronized CompletableFuture<String> getValidAccessToken() {
        long now = System.currentTimeMillis();
        // If access token is valid for at least another 60 seconds
        if (accessToken != null && !accessToken.isEmpty() && now < (expiresAt - 60000)) {
            return CompletableFuture.completedFuture(accessToken);
        }

        // Needs refresh
        if (refreshToken != null && !refreshToken.isEmpty()) {
            return refreshAccessToken();
        }

        return CompletableFuture.completedFuture(null);
    }

    public synchronized CompletableFuture<String> refreshAccessToken() {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = "grant_type=refresh_token" +
                        "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8) +
                        "&client_id=" + URLEncoder.encode(getEffectiveClientId(), StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SPOTIFY_TOKEN_URL))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                    this.accessToken = json.get("access_token").getAsString();
                    if (json.has("refresh_token")) {
                        this.refreshToken = json.get("refresh_token").getAsString();
                    }
                    int expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 3600;
                    this.expiresAt = System.currentTimeMillis() + (expiresIn * 1000L);
                    saveTokens();
                    return this.accessToken;
                } else {
                    ESpotyMod.LOGGER.error("Token refresh failed: {} - {}", response.statusCode(), response.body());
                    return null;
                }
            } catch (Exception e) {
                ESpotyMod.LOGGER.error("Exception during token refresh", e);
                return null;
            }
        });
    }

    public synchronized void logout() {
        this.accessToken = "";
        this.refreshToken = "";
        this.expiresAt = 0;
        if (TOKEN_FILE.exists()) {
            TOKEN_FILE.delete();
        }
        notifyAuthListeners(false);
    }

    private synchronized void saveTokens() {
        try {
            File parent = TOKEN_FILE.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            JsonObject json = new JsonObject();
            json.addProperty("accessToken", accessToken);
            json.addProperty("refreshToken", refreshToken);
            json.addProperty("expiresAt", expiresAt);
            try (FileWriter writer = new FileWriter(TOKEN_FILE)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            ESpotyMod.LOGGER.error("Failed to save tokens", e);
        }
    }

    private synchronized void loadTokens() {
        if (TOKEN_FILE.exists()) {
            try (FileReader reader = new FileReader(TOKEN_FILE)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) {
                    if (json.has("accessToken")) this.accessToken = json.get("accessToken").getAsString();
                    if (json.has("refreshToken")) this.refreshToken = json.get("refreshToken").getAsString();
                    if (json.has("expiresAt")) this.expiresAt = json.get("expiresAt").getAsLong();
                }
            } catch (Exception e) {
                ESpotyMod.LOGGER.error("Failed to load tokens", e);
            }
        }
    }

    private void stopCallbackServer() {
        if (callbackServer != null) {
            try {
                callbackServer.stop(0);
            } catch (Exception ignored) {}
            callbackServer = null;
        }
    }

    private int findAvailablePort(int start, int end) {
        for (int p = start; p <= end; p++) {
            try (ServerSocket s = new ServerSocket(p)) {
                return p;
            } catch (IOException ignored) {}
        }
        return start;
    }

    private static String generateCodeVerifier() {
        SecureRandom sr = new SecureRandom();
        byte[] code = new byte[64];
        sr.nextBytes(code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
    }

    private static String generateCodeChallenge(String verifier) throws Exception {
        byte[] bytes = verifier.getBytes(StandardCharsets.US_ASCII);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(bytes, 0, bytes.length);
        byte[] digest = md.digest();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom sr = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(sr.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isEmpty()) return result;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                result.put(key, val);
            }
        }
        return result;
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                if (os.contains("win")) {
                    new ProcessBuilder("cmd.exe", "/c", "start", url.replace("&", "^&")).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", url).start();
                } else {
                    new ProcessBuilder("xdg-open", url).start();
                }
            }
        } catch (Exception e) {
            ESpotyMod.LOGGER.error("Failed to open browser", e);
        }
    }
}
