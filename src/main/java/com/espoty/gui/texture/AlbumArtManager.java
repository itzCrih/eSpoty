package com.espoty.gui.texture;

import com.espoty.ESpotyMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
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
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(response -> {
                    if (response.statusCode() == 200 && response.body() != null) {
                        byte[] data = response.body();
                        Minecraft.getInstance().execute(() -> {
                            try {
                                NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
                                DynamicTexture dynamicTexture = new DynamicTexture(() -> "espoty_album", image);
                                String hash = Integer.toHexString(url.hashCode());
                                Identifier loc = Identifier.fromNamespaceAndPath(ESpotyMod.MOD_ID, "album_" + hash);
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
