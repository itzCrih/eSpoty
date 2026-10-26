package it.itzcrih.espoty.update;

import it.itzcrih.espoty.ESpotyMod;
import it.itzcrih.espoty.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class UpdateChecker {
    private static final String CURRENT_VERSION = "2.0.0";
    private static final String REPO_OWNER = "itzCrih";
    private static final String REPO_NAME = "eSpoty";
    private static final String API_URL = "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";
    private static final String RELEASES_PAGE = "https://github.com/" + REPO_OWNER + "/" + REPO_NAME + "/releases";

    private static UpdateChecker INSTANCE;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    private volatile boolean checked = false;
    private volatile boolean updateAvailable = false;
    private volatile String latestVersion = "";
    private volatile String releaseUrl = RELEASES_PAGE;
    private volatile String downloadUrl = "";
    private volatile String releaseTitle = "";
    private volatile boolean notifiedInChat = false;

    public static synchronized UpdateChecker getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new UpdateChecker();
        }
        return INSTANCE;
    }

    private UpdateChecker() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static String getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public boolean isChecked() {
        return checked;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getReleaseUrl() {
        return releaseUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getReleaseTitle() {
        return releaseTitle;
    }

    public CompletableFuture<Boolean> checkForUpdatesAsync() {
        if (!ModConfig.getInstance().isCheckForUpdates()) {
            return CompletableFuture.completedFuture(false);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("User-Agent", "eSpoty-Updater/" + CURRENT_VERSION + " (Minecraft Fabric Mod)")
                .header("Accept", "application/vnd.github.v3+json")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    checked = true;
                    if (response.statusCode() == 200 && response.body() != null) {
                        try {
                            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                            if (json.has("tag_name")) {
                                String tag = json.get("tag_name").getAsString().trim();
                                String remoteVer = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
                                latestVersion = remoteVer;
                                if (json.has("html_url")) {
                                    releaseUrl = json.get("html_url").getAsString();
                                }
                                if (json.has("name") && !json.get("name").isJsonNull()) {
                                    releaseTitle = json.get("name").getAsString();
                                }

                                if (json.has("assets") && json.get("assets").isJsonArray()) {
                                    JsonArray assets = json.getAsJsonArray("assets");
                                    for (JsonElement elem : assets) {
                                        if (elem.isJsonObject()) {
                                            JsonObject asset = elem.getAsJsonObject();
                                            String name = asset.has("name") ? asset.get("name").getAsString() : "";
                                            if (name.endsWith(".jar") && !name.contains("sources")) {
                                                if (asset.has("browser_download_url")) {
                                                    downloadUrl = asset.get("browser_download_url").getAsString();
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }

                                updateAvailable = isNewerVersion(remoteVer, CURRENT_VERSION);
                                if (updateAvailable) {
                                    ESpotyMod.LOGGER.info("[eSpoty] A new update is available: v{} (Current: v{}). Release page: {}",
                                            latestVersion, CURRENT_VERSION, releaseUrl);
                                }
                                return updateAvailable;
                            }
                        } catch (Exception e) {
                            ESpotyMod.LOGGER.debug("[eSpoty] Failed to parse GitHub release JSON: {}", e.getMessage());
                        }
                    }
                    return false;
                }).exceptionally(ex -> {
                    checked = true;
                    ESpotyMod.LOGGER.debug("[eSpoty] Could not check for updates: {}", ex.getMessage());
                    return false;
                });
    }

    public void checkAndNotifyInChat(Minecraft client) {
        if (!ModConfig.getInstance().isCheckForUpdates()) return;
        if (!updateAvailable || notifiedInChat || client.player == null) return;

        notifiedInChat = true;

        String msg = "§6[eSpoty] §eA new update is available: §a" + latestVersion + " §7(Current: v" + CURRENT_VERSION + ")! §b" + releaseUrl;
        client.player.displayClientMessage(Component.literal(msg), false);
    }

    public static void openWebpage(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {}

        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (Exception e) {
            ESpotyMod.LOGGER.error("[eSpoty] Could not open webpage: " + url, e);
        }
    }

    public static boolean isNewerVersion(String remote, String current) {
        if (remote == null || current == null || remote.isEmpty() || current.isEmpty()) {
            return false;
        }

        String[] rParts = remote.split("[.-]");
        String[] cParts = current.split("[.-]");
        int len = Math.max(rParts.length, cParts.length);

        for (int i = 0; i < len; i++) {
            int r = i < rParts.length ? parseVersionSegment(rParts[i]) : 0;
            int c = i < cParts.length ? parseVersionSegment(cParts[i]) : 0;
            if (r > c) return true;
            if (r < c) return false;
        }
        return false;
    }

    private static int parseVersionSegment(String segment) {
        try {
            return Integer.parseInt(segment.replaceAll("\\D", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
