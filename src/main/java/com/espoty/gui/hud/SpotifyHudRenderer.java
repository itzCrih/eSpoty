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

    // Fluid Animation States
    private static float currentExtraHeight = 0f;
    private static String lastLyricLine = "";
    private static long lyricLineChangeTime = 0;

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

        int cardWidth = 228;
        boolean showLyrics = config.isHudShowLyrics() && !config.isHudCompact();
        LyricLine currentLyric = showLyrics ? LyricsManager.getInstance().getActiveLyricLine(stateManager.getInterpolatedProgressMs()) : null;
        boolean hasActiveLyric = currentLyric != null && !currentLyric.getText().isEmpty();

        int cardHeight = config.isHudCompact() ? 44 : 54;

        // 1. Fluid Dynamic Height Expansion / Collapse Animation for Lyrics
        float targetExtraHeight = hasActiveLyric ? 16f : 0f;
        currentExtraHeight += (targetExtraHeight - currentExtraHeight) * 0.18f;
        if (Math.abs(targetExtraHeight - currentExtraHeight) < 0.1f) {
            currentExtraHeight = targetExtraHeight;
        }
        int dynamicTotalHeight = cardHeight + Math.round(currentExtraHeight);

        float scale = config.getHudScale();
        int margin = 10;
        int slideOffset = (int) (slideRatio * ((cardWidth * scale) + 24));
        int screenX;
        int screenY;

        switch (config.getHudPosition()) {
            case TOP_LEFT -> {
                screenX = margin - slideOffset;
                screenY = margin;
            }
            case BOTTOM_LEFT -> {
                screenX = margin - slideOffset;
                screenY = screenHeight - (int) (dynamicTotalHeight * scale) - margin;
            }
            case BOTTOM_RIGHT -> {
                screenX = screenWidth - (int) (cardWidth * scale) - margin + slideOffset;
                screenY = screenHeight - (int) (dynamicTotalHeight * scale) - margin;
            }
            case TOP_RIGHT -> {
                screenX = screenWidth - (int) (cardWidth * scale) - margin + slideOffset;
                screenY = margin;
            }
            default -> {
                return;
            }
        }

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate((float) screenX, (float) screenY);
        if (scale != 1.0f) {
            guiGraphics.pose().scale(scale, scale);
        }
        int x = 0;
        int y = 0;

        // 2. Unified Modern Glass Card with dynamic user opacity
        renderModernCard(guiGraphics, x, y, cardWidth, dynamicTotalHeight, animAlpha, config.getHudOpacity());

        // 3. Subtle Track Change Golden Shimmer Wave
        if (trackAge < 450) {
            float sweepProgress = (float) trackAge / 450.0f;
            int sweepCenter = x + (int) (sweepProgress * cardWidth);
            int sweepW = 32;
            int sweepX1 = Math.max(x + 4, sweepCenter - sweepW);
            int sweepX2 = Math.min(x + cardWidth - 4, sweepCenter + sweepW);
            if (sweepX2 > sweepX1) {
                float sweepAlpha = (float) Math.sin(sweepProgress * Math.PI) * animAlpha;
                guiGraphics.fillGradient(sweepX1, y, sweepX2, y + 2,
                        applyAlpha(0x00FFD700, sweepAlpha),
                        applyAlpha(0xFFFFFDE7, sweepAlpha));
            }
        }

        // 4. Album Cover (Chamfered frame)
        int artSize = config.isHudCompact() ? 32 : 38;
        int artX = x + 8;
        int artY = y + 8;
        renderModernAlbumArt(guiGraphics, state.getAlbumArtUrl(), artX, artY, artSize, animAlpha);

        // 5. Track Details
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

        // 6. Progress Bar
        long progressMs = stateManager.getInterpolatedProgressMs();
        long durationMs = state.getDurationMs();
        float progressRatio = durationMs > 0 ? Math.min(1.0f, (float) progressMs / durationMs) : 0f;

        int barX = textX;
        int barY = artistY + 13;
        int barWidth = maxTextWidth;
        int barHeight = 3;

        // Progress background track
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, applyAlpha(0x44FFFFFF, animAlpha * config.getHudOpacity()));

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
            int timeY = (int) ((barY + 5) / 0.72f);
            guiGraphics.drawString(font, timeText, timeX, timeY, applyAlpha(0xFFAAAAAA, animAlpha), true);
            guiGraphics.pose().popMatrix();
        }

        // 7. Fluid Animated Lyric Line (Float-Up & Fade Transition)
        if (hasActiveLyric && currentExtraHeight > 2.0f) {
            String lyricText = currentLyric.getText();
            if (!lyricText.equals(lastLyricLine)) {
                lastLyricLine = lyricText;
                lyricLineChangeTime = now;
            }

            long lyricElapsed = now - lyricLineChangeTime;
            float lyricProgress = Math.min(1.0f, lyricElapsed / 220.0f);
            float lyricEase = 1.0f - (float) Math.pow(1.0f - lyricProgress, 3);

            int lyricYOffset = (int) ((1.0f - lyricEase) * 4);
            float heightRatio = Math.min(1.0f, currentExtraHeight / 16.0f);
            float lineAlpha = animAlpha * lyricEase * heightRatio;

            int dividerY = y + cardHeight - 2;
            guiGraphics.fill(x + 8, dividerY, x + cardWidth - 8, dividerY + 1,
                    applyAlpha(0x22FFFFFF, animAlpha * config.getHudOpacity() * heightRatio));

            int lyricY = dividerY + 4 + lyricYOffset;
            guiGraphics.drawString(font, "♪", x + 8, lyricY, applyAlpha(0xFFFFD700, lineAlpha), true);

            int maxLyricW = cardWidth - 28;
            int lyricWidth = font.width(lyricText);

            guiGraphics.enableScissor(x + 18, dividerY + 1, x + cardWidth - 8, y + dynamicTotalHeight);
            if (lyricWidth > maxLyricW) {
                long cycle = (now / 35) % (lyricWidth + 30);
                int scrollX = (int) (x + 18 - cycle);
                guiGraphics.drawString(font, lyricText, scrollX, lyricY, applyAlpha(0xFFFFFDE7, lineAlpha), true);
                guiGraphics.drawString(font, lyricText, scrollX + lyricWidth + 30, lyricY, applyAlpha(0xFFFFFDE7, lineAlpha), true);
            } else {
                guiGraphics.drawString(font, lyricText, x + 18, lyricY, applyAlpha(0xFFFFFDE7, lineAlpha), true);
            }
            guiGraphics.disableScissor();
        } else if (!hasActiveLyric) {
            lastLyricLine = "";
        }

        guiGraphics.pose().popMatrix();
    }

    private static void renderModernCard(GuiGraphics guiGraphics, int x, int y, int width, int height, float alpha, float hudOpacity) {
        float effectiveBgAlpha = alpha * hudOpacity;
        float effectiveBorderAlpha = alpha * Math.min(1.0f, hudOpacity * 1.2f);

        // 1. Soft Multi-Layer Drop Shadow
        guiGraphics.fill(x + 1, y + 2, x + width - 1, y + height + 3, applyAlpha(0x38000000, alpha));
        guiGraphics.fill(x + 2, y + 3, x + width - 2, y + height + 5, applyAlpha(0x22000000, alpha));

        // 2. Beveled/Chamfered Deep Frosted Obsidian Glass Body
        guiGraphics.fill(x + 2, y, x + width - 2, y + height, applyAlpha(0xEB0D0D14, effectiveBgAlpha));
        guiGraphics.fill(x, y + 2, x + 2, y + height - 2, applyAlpha(0xEB0D0D14, effectiveBgAlpha));
        guiGraphics.fill(x + width - 2, y + 2, x + width, y + height - 2, applyAlpha(0xEB0D0D14, effectiveBgAlpha));

        // 3. Delicate Golden Specular Highlight Top Edge
        guiGraphics.fillGradient(x + 4, y, x + width - 4, y + 1,
                applyAlpha(0xCCFFD700, effectiveBorderAlpha),
                applyAlpha(0x66FFF59D, effectiveBorderAlpha));

        // 4. Subtle Ambient Borders (never harsh or blocky!)
        int softBorderColor = applyAlpha(0x35FFD700, effectiveBorderAlpha);
        guiGraphics.fill(x + 2, y + height - 1, x + width - 2, y + height, softBorderColor);
        guiGraphics.fill(x, y + 2, x + 1, y + height - 2, softBorderColor);
        guiGraphics.fill(x + width - 1, y + 2, x + width, y + height - 2, softBorderColor);

        // Corner connector pixels for smooth rounded feeling
        int cornerColor = applyAlpha(0x22FFD700, effectiveBorderAlpha);
        guiGraphics.fill(x + 1, y + 1, x + 2, y + 2, cornerColor);
        guiGraphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, cornerColor);
        guiGraphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, cornerColor);
        guiGraphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, cornerColor);
    }

    private static void renderModernAlbumArt(GuiGraphics guiGraphics, String url, int x, int y, int size, float alpha) {
        Identifier texture = AlbumArtManager.getInstance().getAlbumTexture(url);

        // Art frame with smooth chamfered border
        guiGraphics.fill(x + 1, y, x + size - 1, y + size, applyAlpha(0xFF14141E, alpha));
        guiGraphics.fill(x, y + 1, x + 1, y + size - 1, applyAlpha(0xFF14141E, alpha));
        guiGraphics.fill(x + size - 1, y + 1, x + size, y + size - 1, applyAlpha(0xFF14141E, alpha));

        // Delicate gold border ring
        guiGraphics.fill(x + 1, y, x + size - 1, y + 1, applyAlpha(0x66FFD700, alpha));
        guiGraphics.fill(x + 1, y + size - 1, x + size - 1, y + size, applyAlpha(0x44FFD700, alpha));
        guiGraphics.fill(x, y + 1, x + 1, y + size - 1, applyAlpha(0x44FFD700, alpha));
        guiGraphics.fill(x + size - 1, y + 1, x + size, y + size - 1, applyAlpha(0x44FFD700, alpha));

        if (texture != null) {
            guiGraphics.blit(texture, x + 1, y + 1, x + size - 1, y + size - 1, 0.0f, 1.0f, 0.0f, 1.0f);
        } else {
            Font font = Minecraft.getInstance().font;
            guiGraphics.drawCenteredString(font, "♫", x + (size / 2), y + (size / 2) - 4, applyAlpha(0xFFFFD700, alpha));
        }
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = (argb >> 24) & 0xFF;
        int newA = Math.max(0, Math.min(255, (int) (a * alpha)));
        return (newA << 24) | (argb & 0x00FFFFFF);
    }
}
