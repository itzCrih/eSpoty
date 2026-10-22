package com.espoty.gui.hud;

import com.espoty.config.ModConfig;
import com.espoty.gui.texture.AlbumArtManager;
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
        if (config.isHudAutoHide()) {
            if (!state.isPlaying()) return;
            if (trackAge > config.getHudAutoHideSeconds() * 1000L) {
                return;
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
                x = margin;
                y = margin;
            }
            case BOTTOM_LEFT -> {
                x = margin;
                y = screenHeight - totalHeight - margin;
            }
            case BOTTOM_RIGHT -> {
                x = screenWidth - cardWidth - margin;
                y = screenHeight - totalHeight - margin;
            }
            case TOP_RIGHT -> {
                x = screenWidth - cardWidth - margin;
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
        guiGraphics.fill(x + 2, y + 2, x + cardWidth + 2, y + cardHeight + 2, 0x55000000);

        // 2. Card Background & Luxury Neon Borders
        renderModernCard(guiGraphics, x, y, cardWidth, cardHeight);

        // 3. Album Cover (38x38)
        int artSize = config.isHudCompact() ? 32 : 38;
        int artX = x + 8;
        int artY = y + 8;
        renderModernAlbumArt(guiGraphics, state.getAlbumArtUrl(), artX, artY, artSize);

        // 4. Track Details
        int textX = artX + artSize + 8;
        int maxTextWidth = cardWidth - (textX - x) - 10;

        String trackTitle = state.getTrackName();
        int titleWidth = font.width(trackTitle);
        int titleY = y + 8;

        // Animated Equalizer Visualizer Bars & Liked Heart
        boolean isLiked = stateManager.isCurrentTrackLiked();
        if (isLiked) {
            guiGraphics.drawString(font, "❤", x + cardWidth - 36, titleY, 0xFFFFD700, true);
        }
        renderAnimatedEqualizer(guiGraphics, x + cardWidth - 24, titleY + 1, state.isPlaying(), now);

        // Marquee scrolling for track title
        int rightOffset = isLiked ? 30 : 18;
        guiGraphics.enableScissor(textX, titleY - 1, textX + maxTextWidth - rightOffset, titleY + 10);
        if (titleWidth > maxTextWidth - rightOffset) {
            long cycle = (now / 35) % (titleWidth + 40);
            int scrollX = (int) (textX - cycle);
            guiGraphics.drawString(font, trackTitle, scrollX, titleY, 0xFFFFD700, true);
            guiGraphics.drawString(font, trackTitle, scrollX + titleWidth + 40, titleY, 0xFFFFD700, true);
        } else {
            guiGraphics.drawString(font, trackTitle, textX, titleY, 0xFFFFD700, true);
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
        guiGraphics.drawString(font, artist, textX, artistY, 0xFFFFF066, true);

        // 5. Progress Bar
        long progressMs = stateManager.getInterpolatedProgressMs();
        long durationMs = state.getDurationMs();
        float progressRatio = durationMs > 0 ? Math.min(1.0f, (float) progressMs / durationMs) : 0f;

        int barX = textX;
        int barY = artistY + 13;
        int barWidth = maxTextWidth;
        int barHeight = 4;

        // Progress background track
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF252533);

        // Filled gold track with glowing cap
        int filledWidth = (int) (barWidth * progressRatio);
        if (filledWidth > 0) {
            guiGraphics.fillGradient(barX, barY, barX + filledWidth, barY + barHeight, 0xFFFFD700, 0xFFFFF176);
            // Glowing Scrubber Head
            int capX = barX + filledWidth;
            guiGraphics.fill(capX - 1, barY - 1, capX + 2, barY + barHeight + 1, 0xFFFFFFFF);
        }

        // Time Indicator (if not compact)
        if (!config.isHudCompact()) {
            String timeText = PlaybackState.formatTime(progressMs) + " / " + PlaybackState.formatTime(durationMs);
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().scale(0.72f, 0.72f);
            int timeX = (int) ((barX + barWidth) / 0.72f) - font.width(timeText);
            int timeY = (int) ((barY + 6) / 0.72f);
            guiGraphics.drawString(font, timeText, timeX, timeY, 0xFFAAAAAA, true);
            guiGraphics.pose().popMatrix();
        }

        // 6. Floating Synced Lyric Pill
        if (hasActiveLyric) {
            int lyricY = y + cardHeight + 4;
            int lyricH = 14;

            // Lyric pill shadow & background
            guiGraphics.fill(x + 2, lyricY + 1, x + cardWidth + 2, lyricY + lyricH + 1, 0x44000000);
            guiGraphics.fill(x, lyricY, x + cardWidth, lyricY + lyricH, 0xF0101018);
            guiGraphics.fill(x, lyricY, x + cardWidth, lyricY + 1, 0xAAFFD700);
            guiGraphics.fill(x, lyricY + lyricH - 1, x + cardWidth, lyricY + lyricH, 0x66FFD700);

            // Glowing note icon
            guiGraphics.drawString(font, "♪", x + 6, lyricY + 3, 0xFFFFD700, true);

            // Active lyric text with marquee if long
            String lyricText = currentLyric.getText();
            int lyricWidth = font.width(lyricText);
            int maxLyricW = cardWidth - 24;

            guiGraphics.enableScissor(x + 16, lyricY, x + cardWidth - 6, lyricY + lyricH);
            if (lyricWidth > maxLyricW) {
                long cycle = (now / 35) % (lyricWidth + 30);
                int scrollX = (int) (x + 16 - cycle);
                guiGraphics.drawString(font, lyricText, scrollX, lyricY + 3, 0xFFFFFDE7, true);
                guiGraphics.drawString(font, lyricText, scrollX + lyricWidth + 30, lyricY + 3, 0xFFFFFDE7, true);
            } else {
                guiGraphics.drawString(font, lyricText, x + 16, lyricY + 3, 0xFFFFFDE7, true);
            }
            guiGraphics.disableScissor();
        }

        guiGraphics.pose().popMatrix();
    }

    private static void renderModernCard(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        // Deep Obsidian glass base
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xF20D0D14);

        // Soft Outer Gold Glow Border
        guiGraphics.fill(x, y, x + width, y + 1, 0x88FFD700);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0x88FFD700);
        guiGraphics.fill(x, y, x + 1, y + height, 0x88FFD700);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0x88FFD700);

        // Top Golden Specular Accent Line
        guiGraphics.fillGradient(x + 2, y + 1, x + width - 2, y + 2, 0xFFFFD700, 0xFFFFF59D);
    }

    private static void renderModernAlbumArt(GuiGraphics guiGraphics, String url, int x, int y, int size) {
        Identifier texture = AlbumArtManager.getInstance().getAlbumTexture(url);

        // Art frame background with double neon ring
        guiGraphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xAAFFD700);
        guiGraphics.fill(x, y, x + size, y + size, 0xFF14141E);

        if (texture != null) {
            guiGraphics.blit(texture, x, y, x + size, y + size, 0.0f, 1.0f, 0.0f, 1.0f);
        } else {
            Font font = Minecraft.getInstance().font;
            guiGraphics.drawCenteredString(font, "♫", x + (size / 2), y + (size / 2) - 4, 0xFFFFD700);
        }

        // Inner rim
        guiGraphics.fill(x, y, x + size, y + 1, 0x44FFFFFF);
    }

    private static void renderAnimatedEqualizer(GuiGraphics guiGraphics, int x, int y, boolean isPlaying, long now) {
        int barW = 2;
        int gap = 1;
        int maxH = 8;

        for (int i = 0; i < 4; i++) {
            int bx = x + (i * (barW + gap));
            int h;
            if (isPlaying) {
                double wave = Math.sin((now / 140.0) + (i * 1.2)) * 0.5 + 0.5;
                h = Math.max(2, (int) (wave * maxH));
            } else {
                h = 2;
            }
            int by = y + maxH - h;
            guiGraphics.fill(bx, by, bx + barW, by + h, (i % 2 == 0) ? 0xFFFFD700 : 0xFFFFF176);
        }
    }
}
