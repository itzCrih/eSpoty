package it.itzcrih.espoty.gui.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * High-performance anti-aliased rounded rectangle renderer.
 * Features zero-allocation fast paths for dynamic progress bars, sliders,
 * and pulsing outlines, coupled with LRU caching for static container cards.
 */
public final class AaRoundRect {
    private static final int MAX_CACHE = 64;
    private static int nextId = 0;

    private static final Map<Key, Baked> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Baked> eldest) {
            if (size() > MAX_CACHE) {
                eldest.getValue().close();
                return true;
            }
            return false;
        }
    };

    private record Key(int scale, int w, int h, int r, int argb, int border) {}

    private record Baked(Identifier id, DynamicTexture texture) {
        void close() {
            try {
                Minecraft.getInstance().getTextureManager().release(id);
                texture.close();
            } catch (Exception ignored) {}
        }
    }

    public static void draw(GuiGraphics g, int x, int y, int w, int h, int radius, int argb) {
        draw(g, x, y, w, h, radius, argb, 0);
    }

    public static void draw(GuiGraphics g, int x, int y, int w, int h, int radius, int argb, int border) {
        if (w <= 0 || h <= 0) return;

        // Fast path 1: Slider tracks, progress fills, and small badges (zero texture allocation)
        if (h <= 5 || radius <= 2 || w <= 5) {
            drawBeveledBar(g, x, y, w, h, radius, argb, border);
            return;
        }

        // Fast path 2: Dynamic pulsing outlines without fill (zero texture allocation)
        if ((argb & 0xFF000000) == 0 && border != 0) {
            drawBeveledOutline(g, x, y, w, h, radius, border);
            return;
        }

        int scale = Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));

        Key key = new Key(scale, w, h, r, argb, border);
        Baked baked = CACHE.get(key);
        if (baked == null) {
            baked = bake(key);
            CACHE.put(key, baked);
        }

        g.blit(baked.id(), x, y, x + w, y + h, 0f, 1f, 0f, 1f);
    }

    private static void drawBeveledBar(GuiGraphics g, int x, int y, int w, int h, int r, int argb, int border) {
        if (r <= 1 || w <= 2 || h <= 2) {
            if (argb != 0) g.fill(x, y, x + w, y + h, argb);
            if (border != 0) {
                g.fill(x, y, x + w, y + 1, border);
                g.fill(x, y + h - 1, x + w, y + h, border);
                g.fill(x, y + 1, x + 1, y + h - 1, border);
                g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
            }
            return;
        }

        if (argb != 0) {
            g.fill(x + 1, y, x + w - 1, y + h, argb);
            g.fill(x, y + 1, x + w, y + h - 1, argb);
        }

        if (border != 0) {
            g.fill(x + 1, y, x + w - 1, y + 1, border);
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
            g.fill(x, y + 1, x + 1, y + h - 1, border);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        }
    }

    private static void drawBeveledOutline(GuiGraphics g, int x, int y, int w, int h, int radius, int border) {
        int r = Math.max(1, Math.min(radius, Math.min(w, h) / 2));
        g.fill(x + r, y, x + w - r, y + 1, border);
        g.fill(x + r, y + h - 1, x + w - r, y + h, border);
        g.fill(x, y + r, x + 1, y + h - r, border);
        g.fill(x + w - 1, y + r, x + w, y + h - r, border);

        for (int i = 1; i < r; i++) {
            g.fill(x + r - i, y + i, x + r - i + 1, y + i + 1, border);
            g.fill(x + w - r + i - 1, y + i, x + w - r + i, y + i + 1, border);
            g.fill(x + r - i, y + h - 1 - i, x + r - i + 1, y + h - i, border);
            g.fill(x + w - r + i - 1, y + h - 1 - i, x + w - r + i, y + h - i, border);
        }
    }

    private static Baked bake(Key key) {
        int scale = key.scale();
        int w = key.w() * scale;
        int h = key.h() * scale;
        float r = key.r() * scale;

        float halfW = w / 2.0f;
        float halfH = h / 2.0f;

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        image.fillRect(0, 0, w, h, 0);

        int baseArgb = key.argb();
        int borderArgb = key.border();
        boolean hasBorder = (borderArgb != 0);

        for (int py = 0; py < h; py++) {
            float dy = Math.abs(py + 0.5f - halfH) - (halfH - r);
            for (int px = 0; px < w; px++) {
                float dx = Math.abs(px + 0.5f - halfW) - (halfW - r);

                float d = (float) Math.sqrt(Math.max(dx, 0f) * Math.max(dx, 0f) + Math.max(dy, 0f) * Math.max(dy, 0f))
                        + Math.min(Math.max(dx, dy), 0f) - r;

                float coverage = clamp01(0.5f - d);
                if (coverage <= 0.005f) {
                    image.setPixel(px, py, 0);
                    continue;
                }

                int color = baseArgb;
                if (hasBorder) {
                    float borderT = clamp01(d + 1.2f * scale);
                    color = lerpColor(baseArgb, borderArgb, borderT);
                }

                int alpha = (color >>> 24) & 0xFF;
                int finalAlpha = Math.round(alpha * coverage);
                if (finalAlpha <= 0) {
                    image.setPixel(px, py, 0);
                    continue;
                }

                int finalArgb = (finalAlpha << 24) | (color & 0x00FFFFFF);
                image.setPixel(px, py, finalArgb);
            }
        }

        Identifier id = Identifier.fromNamespaceAndPath("espoty", "round_" + (++nextId));
        DynamicTexture texture = new DynamicTexture(() -> "round_" + nextId, image);
        texture.upload();
        Minecraft.getInstance().getTextureManager().register(id, texture);

        return new Baked(id, texture);
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private static int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >>> 16) & 0xFF, g2 = (c2 >>> 8) & 0xFF, b2 = c2 & 0xFF;

        int a = Math.round(a1 + (a2 - a1) * t);
        int r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t);
        int b = Math.round(b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void clearCache() {
        for (Baked b : CACHE.values()) {
            b.close();
        }
        CACHE.clear();
    }
}
