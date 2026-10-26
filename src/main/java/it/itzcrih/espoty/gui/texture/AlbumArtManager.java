package it.itzcrih.espoty.gui.texture;

import it.itzcrih.espoty.ESpotyMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AlbumArtManager {
    private static AlbumArtManager INSTANCE;
    private final HttpClient httpClient;
    private final Map<String, Identifier> textureMap = new ConcurrentHashMap<>();
    private final Set<String> loadingUrls = ConcurrentHashMap.newKeySet();

    public static synchronized AlbumArtManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AlbumArtManager();
        }
        return INSTANCE;
    }

    private AlbumArtManager() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    public Identifier getAlbumTexture(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        Identifier cached = textureMap.get(url);
        if (cached != null) {
            return cached;
        }

        if (loadingUrls.add(url)) {
            loadAsync(url);
        }

        return null;
    }

    private void loadAsync(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "eSpoty Minecraft Mod/" + it.itzcrih.espoty.update.UpdateChecker.getCurrentVersion())
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(response -> {
                    if (response.statusCode() == 200 && response.body() != null) {
                        byte[] rawBytes = response.body();
                        // Re-encode JPEG/WebP to clean PNG in background worker to satisfy NativeImage's strict PNG requirement
                        byte[] pngBytes = rawBytes;
                        try {
                            if (rawBytes.length > 3 && (rawBytes[0] != (byte) 0x89 || rawBytes[1] != (byte) 0x50)) {
                                BufferedImage bImg = ImageIO.read(new ByteArrayInputStream(rawBytes));
                                if (bImg != null) {
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    ImageIO.write(bImg, "png", baos);
                                    pngBytes = baos.toByteArray();
                                }
                            }
                        } catch (Exception e) {
                            ESpotyMod.LOGGER.debug("Image re-encoding note: {}", e.getMessage());
                        }

                        final byte[] finalPng = pngBytes;
                        Minecraft.getInstance().execute(() -> {
                            try {
                                NativeImage image = NativeImage.read(new ByteArrayInputStream(finalPng));
                                DynamicTexture dynamicTexture = new DynamicTexture(() -> "espoty_album", image);
                                String hash = Long.toHexString(url.hashCode() & 0xFFFFFFFFL);
                                Identifier loc = Identifier.fromNamespaceAndPath(ESpotyMod.MOD_ID, "album_" + hash);

                                // Bounded texture cache: evict oldest if size exceeds 40 to prevent long-session memory leak
                                if (textureMap.size() >= 40) {
                                    String oldestKey = textureMap.keySet().iterator().next();
                                    Identifier oldLoc = textureMap.remove(oldestKey);
                                    if (oldLoc != null) {
                                        Minecraft.getInstance().getTextureManager().release(oldLoc);
                                    }
                                }

                                Minecraft.getInstance().getTextureManager().register(loc, dynamicTexture);
                                textureMap.put(url, loc);
                            } catch (Exception e) {
                                ESpotyMod.LOGGER.error("Failed to load album image texture from data", e);
                            } finally {
                                loadingUrls.remove(url);
                            }
                        });
                    } else {
                        loadingUrls.remove(url);
                    }
                }).exceptionally(ex -> {
                    loadingUrls.remove(url);
                    return null;
                });
    }

    public void clearCache() {
        Minecraft.getInstance().execute(() -> {
            for (Identifier loc : textureMap.values()) {
                Minecraft.getInstance().getTextureManager().release(loc);
            }
            textureMap.clear();
            loadingUrls.clear();
        });
    }
}
