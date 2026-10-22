package com.espoty.gui.hud;

import com.espoty.config.ModConfig;
import com.espoty.gui.texture.AlbumArtManager;
import com.espoty.gui.widget.AudioWaveformRenderer;
import com.espoty.lyrics.LyricLine;
import com.espoty.lyrics.LyricsManager;
import com.espoty.spotify.SpotifyStateManager;
import com.espoty.spotify.models.PlaybackState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

public class SpotifyHudRenderer {

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) return;

        ModConfig config = ModConfig.getInstance();
        if (!config.isEnabled() || config.getHudPosition() == ModConfig.HudPosition.OFF) {
            return;
        }

        SpotifyStateManager stateManager = SpotifyStateManager.getInstance();
        PlaybackState state = stateManager.getCurrentState();

        if (state == null || state.getTrackName().isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long trackAge = now - stateManager.getLastTrackChangeTime();

        float animAlpha = 1.0f;
        float slideRatio = 0.0f; // 0.0f = on screen, 1.0f = fully offscreen

        if (config.isHudAutoHide()) {
            if (!state.isPlaying()) return;
            long totalMs = config.getHudAutoHideSeconds() * 1000L;
            if (trackAge >= totalMs) {
                return;
            }

            long introMs = 500;
            long outroMs = 700;

            if (trackAge < introMs) {
                float p = (float) trackAge / introMs;
                float ease = 1.0f - (float) Math.pow(1.0f - p, 3);
                animAlpha = ease;
                slideRatio = 1.0f - ease;
            } else if (trackAge > totalMs - outroMs) {
                float p = (float) (totalMs - trackAge) / outroMs;
                float ease = p * p * p;
                animAlpha = ease;
                slideRatio = 1.0f - ease;
            } else {
                animAlpha = 1.0f;
                slideRatio = 0.0f;
            }
        }

        Font font = client.font;
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        int cardWidth = 224;
        boolean showLyrics = config.isHudShowLyrics() && !config.isHudCompact();
        LyricLine currentLyric = showLyrics ? LyricsManager.getInstance().getActiveLyricLine(stateManager.getInterpolatedProgressMs()) : null;
        boolean hasActiveLyric = currentLyric != null && !currentLyric.getText().isEmpty();

        int cardHeight = config.isHudCompact() ? 44 : 56;
        int totalHeight = cardHeight + (hasActiveLyric ? 18 : 0);

        int x;
        int y;
        int margin = 10;

        switch (config.getHudPosition()) {
            case TOP_LEFT -> {
                x = margin - (int) (slideRatio * (cardWidth + 24));
                y = margin;
            }
            case BOTTOM_LEFT -> {
                x = margin - (int) (slideRatio * (cardWidth + 24));
                y = screenHeight - totalHeight - margin;
            }
            case BOTTOM_RIGHT -> {
                x = screenWidth - cardWidth - margin + (int) (slideRatio * (cardWidth + 24));
                y = screenHeight - totalHeight - margin;
            }
            case TOP_RIGHT -> {
                x = screenWidth - cardWidth - margin + (int) (slideRatio * (cardWidth + 24));
                y = margin;
            }
            default -> {
                return;
            }
        }

        guiGraphics.pose().pushMatrix();

        float scale = config.getHudScale();
        if (scale != 1.0f) {
            guiGraphics.pose().scale(scale, scale);
            x = (int) (x / scale);
            y = (int) (y / scale);
        }

        // 1. Ambient Drop Shadow
        guiGraphics.fill(x + 2, y + 2, x + cardWidth + 2, y + cardHeight + 2, applyAlpha(0x55000000, animAlpha));

        // 2. Card Background & Luxury Neon Borders
        renderModernCard(guiGraphics, x, y, cardWidth, cardHeight, animAlpha);

        // 3. Album Cover (38x38)
        int artSize = config.isHudCompact() ? 32 : 38;
        int artX = x + 8;
        int artY = y + 8;
        renderModernAlbumArt(guiGraphics, state.getAlbumArtUrl(), artX, artY, artSize, animAlpha);

        // 4. Track Details
        int textX = artX + artSize + 8;
        int maxTextWidth = cardWidth - (textX - x) - 10;

        String trackTitle = state.getTrackName();
        int titleWidth = font.width(trackTitle);
        int titleY = y + 8;

        // Animated Audio Waveform & Liked Heart
        boolean isLiked = stateManager.isCurrentTrackLiked();
        if (isLiked) {
            guiGraphics.drawString(font, "❤", x + cardWidth - 44, titleY, applyAlpha(0xFFFFD700, animAlpha), true);
        }
        AudioWaveformRenderer.renderWaveform(guiGraphics, x + cardWidth - 32, titleY + 9, 24, 9, 6, state.isPlaying(), now, animAlpha);

        // Marquee scrolling for track title
        int rightOffset = isLiked ? 46 : 34;
        guiGraphics.enableScissor(textX, titleY - 1, textX + maxTextWidth - rightOffset, titleY + 10);
        if (titleWidth > maxTextWidth - rightOffset) {
            long cycle = (now / 35) % (titleWidth + 40);
            int scrollX = (int) (textX - cycle);
            guiGraphics.drawString(font, trackTitle, scrollX, titleY, applyAlpha(0xFFFFD700, animAlpha), true);
            guiGraphics.drawString(font, trackTitle, scrollX + titleWidth + 40, titleY, applyAlpha(0xFFFFD700, animAlpha), true);
        } else {
            guiGraphics.drawString(font, trackTitle, textX, titleY, applyAlpha(0xFFFFD700, animAlpha), true);
        }
        guiGraphics.disableScissor();

        // Artist Name
        int artistY = titleY + 11;
        String artist = state.getArtistNames();
        if (font.width(artist) > maxTextWidth) {
            while (font.width(artist + "...") > maxTextWidth && artist.length() > 3) {
                artist = artist.substring(0, artist.length() - 1);
            }
            artist = artist + "...";
        }
        guiGraphics.drawString(font, artist, textX, artistY, applyAlpha(0xFFFFF066, animAlpha), true);

        // 5. Progress Bar
        long progressMs = stateManager.getInterpolatedProgressMs();
        long durationMs = state.getDurationMs();
        float progressRatio = durationMs > 0 ? Math.min(1.0f, (float) progressMs / durationMs) : 0f;

        int barX = textX;
        int barY = artistY + 13;
        int barWidth = maxTextWidth;
        int barHeight = 4;

        // Progress background track
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, applyAlpha(0xFF252533, animAlpha));

        // Filled gold track with glowing cap
        int filledWidth = (int) (barWidth * progressRatio);
        if (filledWidth > 0) {
            guiGraphics.fillGradient(barX, barY, barX + filledWidth, barY + barHeight, applyAlpha(0xFFFFD700, animAlpha), applyAlpha(0xFFFFF176, animAlpha));
            // Glowing Scrubber Head
            int capX = barX + filledWidth;
            guiGraphics.fill(capX - 1, barY - 1, capX + 2, barY + barHeight + 1, applyAlpha(0xFFFFFFFF, animAlpha));
        }

        // Time Indicator (if not compact)
        if (!config.isHudCompact()) {
            String timeText = PlaybackState.formatTime(progressMs) + " / " + PlaybackState.formatTime(durationMs);
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().scale(0.72f, 0.72f);
            int timeX = (int) ((barX + barWidth) / 0.72f) - font.width(timeText);
            int timeY = (int) ((barY + 6) / 0.72f);
            guiGraphics.drawString(font, timeText, timeX, timeY, applyAlpha(0xFFAAAAAA, animAlpha), true);
            guiGraphics.pose().popMatrix();
        }

        // 6. Floating Synced Lyric Pill
        if (hasActiveLyric) {
            int lyricY = y + cardHeight + 4;
            int lyricH = 14;

            // Lyric pill shadow & background
            guiGraphics.fill(x + 2, lyricY + 1, x + cardWidth + 2, lyricY + lyricH + 1, applyAlpha(0x44000000, animAlpha));
            guiGraphics.fill(x, lyricY, x + cardWidth, lyricY + lyricH, applyAlpha(0xF0101018, animAlpha));
            guiGraphics.fill(x, lyricY, x + cardWidth, lyricY + 1, applyAlpha(0xAAFFD700, animAlpha));
            guiGraphics.fill(x, lyricY + lyricH - 1, x + cardWidth, lyricY + lyricH, applyAlpha(0x66FFD700, animAlpha));

            // Glowing note icon
            guiGraphics.drawString(font, "♪", x + 6, lyricY + 3, applyAlpha(0xFFFFD700, animAlpha), true);

            // Active lyric text with marquee if long
            String lyricText = currentLyric.getText();
            int lyricWidth = font.width(lyricText);
            int maxLyricW = cardWidth - 24;

            guiGraphics.enableScissor(x + 16, lyricY, x + cardWidth - 6, lyricY + lyricH);
            if (lyricWidth > maxLyricW) {
                long cycle = (now / 35) % (lyricWidth + 30);
                int scrollX = (int) (x + 16 - cycle);
                guiGraphics.drawString(font, lyricText, scrollX, lyricY + 3, applyAlpha(0xFFFFFDE7, animAlpha), true);
                guiGraphics.drawString(font, lyricText, scrollX + lyricWidth + 30, lyricY + 3, applyAlpha(0xFFFFFDE7, animAlpha), true);
            } else {
                guiGraphics.drawString(font, lyricText, x + 16, lyricY + 3, applyAlpha(0xFFFFFDE7, animAlpha), true);
            }
            guiGraphics.disableScissor();
        }

        guiGraphics.pose().popMatrix();
    }

    private static void renderModernCard(GuiGraphics guiGraphics, int x, int y, int width, int height, float alpha) {
        // Deep Obsidian glass base
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, applyAlpha(0xF20D0D14, alpha));

        // Soft Outer Gold Glow Border
        guiGraphics.fill(x, y, x + width, y + 1, applyAlpha(0x88FFD700, alpha));
        guiGraphics.fill(x, y + height - 1, x + width, y + height, applyAlpha(0x88FFD700, alpha));
        guiGraphics.fill(x, y, x + 1, y + height, applyAlpha(0x88FFD700, alpha));
        guiGraphics.fill(x + width - 1, y, x + width, y + height, applyAlpha(0x88FFD700, alpha));

        // Top Golden Specular Accent Line
        guiGraphics.fillGradient(x + 2, y + 1, x + width - 2, y + 2, applyAlpha(0xFFFFD700, alpha), applyAlpha(0xFFFFF59D, alpha));
    }

    private static void renderModernAlbumArt(GuiGraphics guiGraphics, String url, int x, int y, int size, float alpha) {
        Identifier texture = AlbumArtManager.getInstance().getAlbumTexture(url);

        // Art frame background with double neon ring
        guiGraphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, applyAlpha(0xAAFFD700, alpha));
        guiGraphics.fill(x, y, x + size, y + size, applyAlpha(0xFF14141E, alpha));

        if (texture != null) {
            guiGraphics.blit(texture, x, y, x + size, y + size, 0.0f, 1.0f, 0.0f, 1.0f);
        } else {
            Font font = Minecraft.getInstance().font;
            guiGraphics.drawCenteredString(font, "♫", x + (size / 2), y + (size / 2) - 4, applyAlpha(0xFFFFD700, alpha));
        }

        // Inner rim
        guiGraphics.fill(x, y, x + size, y + 1, applyAlpha(0x44FFFFFF, alpha));
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = (argb >> 24) & 0xFF;
        int newA = Math.max(0, Math.min(255, (int) (a * alpha)));
        return (newA << 24) | (argb & 0x00FFFFFF);
    }
}
