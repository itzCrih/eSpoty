package it.itzcrih.espoty.gui.hud;

import it.itzcrih.espoty.config.ModConfig;
import it.itzcrih.espoty.gui.theme.ThemeManager;
import it.itzcrih.espoty.gui.texture.AlbumArtManager;
import it.itzcrih.espoty.gui.widget.AudioWaveformRenderer;
import it.itzcrih.espoty.lyrics.LyricLine;
import it.itzcrih.espoty.lyrics.LyricsManager;
import it.itzcrih.espoty.spotify.SpotifyStateManager;
import it.itzcrih.espoty.spotify.models.PlaybackState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public class SpotifyHudRenderer {

    // Fluid Animation States
    private static float currentExtraHeight = 0f;
    private static String lastLyricLine = "";
    private static String prevLyricLine = "";
    private static long lyricLineChangeTime = 0;

    // Zero-allocation lyric component & metric cache for 0 FPS drops
    private static Component cachedLyricComp = null;
    private static int cachedLyricWidth = 0;
    private static Component cachedPrevLyricComp = null;
    private static ModConfig.LyricFont lastCachedFont = null;

    // Chat Interactive Playback Controls State
    private static float chatControlsProgress = 0.0f;
    private static int cachedScreenX = 0;
    private static int cachedScreenY = 0;
    private static float cachedScale = 1.0f;
    private static int cachedCardWidth = 228;
    private static int cachedTotalHeight = 54;
    private static boolean cachedIsBottom = false;

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) return;

        ModConfig config = ModConfig.getInstance();
        if (!config.isEnabled() || config.getHudPosition() == ModConfig.HudPosition.OFF) {
            return;
        }

        // HUD Opacity Check: when set to 0, completely hide the HUD
        float hudOpacity = config.getHudOpacity();
        if (hudOpacity <= 0.005f) {
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

        // Combined effective alpha applying user hudOpacity to the entire HUD
        float effectiveAlpha = animAlpha * hudOpacity;
        if (effectiveAlpha <= 0.005f) return;

        Font font = client.font;
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        int cardWidth = 228;
        boolean showLyrics = config.isHudShowLyrics() && !config.isHudCompact();
        LyricLine currentLyric = showLyrics ? LyricsManager.getInstance().getActiveLyricLine(stateManager.getInterpolatedProgressMs()) : null;
        boolean hasActiveLyric = currentLyric != null && !currentLyric.getText().isEmpty();

        int cardHeight = config.isHudCompact() ? 44 : 54;

        // 1. Fluid Dynamic Height Expansion / Collapse Animation for Lyrics
        float targetExtraHeight = (showLyrics && hasActiveLyric) ? 17f : 0f;
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

        // Cache positions for mouse hit-testing in ChatScreen
        cachedScreenX = screenX;
        cachedScreenY = screenY;
        cachedScale = scale;
        cachedCardWidth = cardWidth;
        cachedTotalHeight = dynamicTotalHeight;
        cachedIsBottom = (config.getHudPosition() == ModConfig.HudPosition.BOTTOM_LEFT || config.getHudPosition() == ModConfig.HudPosition.BOTTOM_RIGHT);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate((float) screenX, (float) screenY);
        if (scale != 1.0f) {
            guiGraphics.pose().scale(scale, scale);
        }
        int x = 0;
        int y = 0;

        // 2. Unified Modern Glass Card with dynamic user opacity
        renderModernCard(guiGraphics, x, y, cardWidth, dynamicTotalHeight, effectiveAlpha);

        // 3. Subtle Track Change Golden Shimmer Wave
        if (trackAge < 450) {
            float sweepProgress = (float) trackAge / 450.0f;
            int sweepCenter = x + (int) (sweepProgress * cardWidth);
            int sweepW = 32;
            int sweepX1 = Math.max(x + 4, sweepCenter - sweepW);
            int sweepX2 = Math.min(x + cardWidth - 4, sweepCenter + sweepW);
            if (sweepX2 > sweepX1) {
                float sweepAlpha = (float) Math.sin(sweepProgress * Math.PI) * effectiveAlpha;
                guiGraphics.fillGradient(sweepX1, y, sweepX2, y + 2,
                        applyAlpha(0x00FFD700, sweepAlpha),
                        applyAlpha(0xFFFFFDE7, sweepAlpha));
            }
        }

        // 4. Album Cover (Chamfered frame)
        int artSize = config.isHudCompact() ? 32 : 38;
        int artX = x + 8;
        int artY = y + 8;
        renderModernAlbumArt(guiGraphics, state.getAlbumArtUrl(), artX, artY, artSize, effectiveAlpha);

        // 5. Track Details
        int textX = artX + artSize + 8;
        int maxTextWidth = cardWidth - (textX - x) - 10;

        String trackTitle = state.getTrackName();
        int titleWidth = font.width(trackTitle);
        int titleY = y + 8;

        int accentColor = config.isSyncHudTheme() ? ThemeManager.getAccent() : 0xFFFFD700;
        int accent2Color = config.isSyncHudTheme() ? ThemeManager.getAccent2() : 0xFFFFF066;

        // Synchronized Audio Waveform & Liked Heart
        long progressMs = stateManager.getInterpolatedProgressMs();
        boolean isLiked = stateManager.isCurrentTrackLiked();
        if (isLiked) {
            guiGraphics.drawString(font, "❤", x + cardWidth - 44, titleY, applyAlpha(accentColor, effectiveAlpha), true);
        }
        AudioWaveformRenderer.renderSyncedWaveform(guiGraphics, x + cardWidth - 32, titleY + 9, 24, 9, 6,
                state.isPlaying(), progressMs, effectiveAlpha, accentColor, accent2Color,
                state.getTrackName(), state.getArtistNames(), hasActiveLyric);

        // Marquee scrolling for track title
        int rightOffset = isLiked ? 46 : 34;
        guiGraphics.enableScissor(textX, titleY - 1, textX + maxTextWidth - rightOffset, titleY + 10);
        if (titleWidth > maxTextWidth - rightOffset) {
            long cycle = (now / 35) % (titleWidth + 40);
            int scrollX = (int) (textX - cycle);
            guiGraphics.drawString(font, trackTitle, scrollX, titleY, applyAlpha(accentColor, effectiveAlpha), true);
            guiGraphics.drawString(font, trackTitle, scrollX + titleWidth + 40, titleY, applyAlpha(accentColor, effectiveAlpha), true);
        } else {
            guiGraphics.drawString(font, trackTitle, textX, titleY, applyAlpha(accentColor, effectiveAlpha), true);
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
        guiGraphics.drawString(font, artist, textX, artistY, applyAlpha(accent2Color, effectiveAlpha), true);

        // 6. Progress Bar
        long durationMs = state.getDurationMs();
        float progressRatio = durationMs > 0 ? Math.min(1.0f, (float) progressMs / durationMs) : 0f;

        int barX = textX;
        int barY = artistY + 13;
        int barWidth = maxTextWidth;
        int barHeight = 3;

        // Progress background track
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, applyAlpha(0x44FFFFFF, effectiveAlpha));

        // Filled track with glowing cap
        int filledWidth = (int) (barWidth * progressRatio);
        if (filledWidth > 0) {
            guiGraphics.fillGradient(barX, barY, barX + filledWidth, barY + barHeight, applyAlpha(accentColor, effectiveAlpha), applyAlpha(accent2Color, effectiveAlpha));
            // Glowing Scrubber Head
            int capX = barX + filledWidth;
            guiGraphics.fill(capX - 1, barY - 1, capX + 2, barY + barHeight + 1, applyAlpha(0xFFFFFFFF, effectiveAlpha));
        }

        // Time Indicator (if not compact)
        if (!config.isHudCompact()) {
            String timeText = PlaybackState.formatTime(progressMs) + " / " + PlaybackState.formatTime(durationMs);
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().scale(0.72f, 0.72f);
            int timeX = (int) ((barX + barWidth) / 0.72f) - font.width(timeText);
            int timeY = (int) ((barY + 5) / 0.72f);
            guiGraphics.drawString(font, timeText, timeX, timeY, applyAlpha(0xFFAAAAAA, effectiveAlpha), true);
            guiGraphics.pose().popMatrix();
        }

        // 7. Fluid Animated Lyric Line under HUD (Only when showLyrics is enabled in settings)
        if (showLyrics && hasActiveLyric && currentExtraHeight > 2.0f) {
            String lyricText = currentLyric.getText();
            ModConfig.LyricFont currentFont = config.getLyricFont();
            boolean lyricChanged = !lyricText.equals(lastLyricLine);
            boolean fontChanged = (currentFont != lastCachedFont);

            if (lyricChanged || fontChanged) {
                prevLyricLine = lastLyricLine;
                lastLyricLine = lyricText;
                lastCachedFont = currentFont;
                lyricLineChangeTime = now;

                cachedPrevLyricComp = cachedLyricComp;
                cachedLyricComp = Component.literal(lyricText).withStyle(currentFont.getStyle());
                cachedLyricWidth = font.width(cachedLyricComp);
            }

            long lyricElapsed = now - lyricLineChangeTime;
            float lyricProgress = Math.min(1.0f, lyricElapsed / 280.0f);
            float invProg = 1.0f - lyricProgress;
            float lyricEase = 1.0f - (invProg * invProg * invProg);

            int lyricYOffset = (int) ((1.0f - lyricEase) * 4);
            float heightRatio = Math.min(1.0f, currentExtraHeight / 17.0f);
            float lineAlpha = effectiveAlpha * lyricEase * heightRatio;

            int dividerY = y + cardHeight - 2;
            guiGraphics.fill(x + 8, dividerY, x + cardWidth - 8, dividerY + 1,
                    applyAlpha(0x22FFFFFF, effectiveAlpha * heightRatio));

            int lyricY = dividerY + 4 + lyricYOffset;
            guiGraphics.drawString(font, "♪", x + 8, lyricY, applyAlpha(accentColor, lineAlpha), true);

            int maxLyricW = cardWidth - 28;
            Component lyricComp = (cachedLyricComp != null) ? cachedLyricComp : Component.literal(lyricText).withStyle(currentFont.getStyle());
            int lyricWidth = (cachedLyricComp != null) ? cachedLyricWidth : font.width(lyricComp);

            guiGraphics.enableScissor(x + 18, dividerY + 1, x + cardWidth - 8, y + dynamicTotalHeight);

            // Smooth cross-fade outgoing line if transitioning
            if (prevLyricLine != null && !prevLyricLine.isEmpty() && lyricProgress < 1.0f && cachedPrevLyricComp != null) {
                float prevAlpha = effectiveAlpha * (1.0f - lyricEase) * heightRatio;
                int prevY = dividerY + 4 - (int) (lyricEase * 4);
                guiGraphics.drawString(font, cachedPrevLyricComp, x + 18, prevY, applyAlpha(0xFFCBD5E1, prevAlpha), true);
            }

            if (lyricWidth > maxLyricW) {
                long cycle = (now / 35) % (lyricWidth + 30);
                int scrollX = (int) (x + 18 - cycle);
                guiGraphics.drawString(font, lyricComp, scrollX, lyricY, applyAlpha(0xFFFFFDE7, lineAlpha), true);
                guiGraphics.drawString(font, lyricComp, scrollX + lyricWidth + 30, lyricY, applyAlpha(0xFFFFFDE7, lineAlpha), true);
            } else {
                guiGraphics.drawString(font, lyricComp, x + 18, lyricY, applyAlpha(0xFFFFFDE7, lineAlpha), true);
            }
            guiGraphics.disableScissor();
        } else if (!hasActiveLyric || !showLyrics) {
            lastLyricLine = "";
            prevLyricLine = "";
            cachedPrevLyricComp = null;
        }

        // 8. Interactive Chat Playback Controls (Revealed smoothly when player opens ChatScreen)
        boolean isChatOpen = (client.screen instanceof ChatScreen);
        chatControlsProgress += ((isChatOpen ? 1.0f : 0.0f) - chatControlsProgress) * 0.22f;
        if (Math.abs((isChatOpen ? 1.0f : 0.0f) - chatControlsProgress) < 0.01f) {
            chatControlsProgress = isChatOpen ? 1.0f : 0.0f;
        }

        if (chatControlsProgress > 0.02f) {
            double mx = client.mouseHandler.xpos() * (double) client.getWindow().getGuiScaledWidth() / (double) client.getWindow().getScreenWidth();
            double my = client.mouseHandler.ypos() * (double) client.getWindow().getGuiScaledHeight() / (double) client.getWindow().getScreenHeight();
            double localMx = (mx - screenX) / scale;
            double localMy = (my - screenY) / scale;

            renderChatControls(guiGraphics, x, y, cardWidth, dynamicTotalHeight, effectiveAlpha * chatControlsProgress,
                    localMx, localMy, state, accentColor, accent2Color, cachedIsBottom);
        }

        guiGraphics.pose().popMatrix();
    }

    private static void renderChatControls(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int cardW,
            int cardH,
            float alpha,
            double mouseX,
            double mouseY,
            PlaybackState state,
            int accentColor,
            int accent2Color,
            boolean isBottom
    ) {
        if (alpha <= 0.01f) return;

        int dockW = cardW;
        int dockH = 22;
        int dockX = x;
        int dockY = isBottom ? (y - dockH - 4) : (y + cardH + 4);

        // 1. Sleek Glass Control Dock Container
        guiGraphics.fill(dockX + 1, dockY + 2, dockX + dockW - 1, dockY + dockH + 3, applyAlpha(0x35000000, alpha * 0.6f));
        guiGraphics.fill(dockX + 2, dockY, dockX + dockW - 2, dockY + dockH, applyAlpha(0xEB0D0D14, alpha));
        guiGraphics.fill(dockX, dockY + 2, dockX + 2, dockY + dockH - 2, applyAlpha(0xEB0D0D14, alpha));
        guiGraphics.fill(dockX + dockW - 2, dockY + 2, dockX + dockW, dockY + dockH - 2, applyAlpha(0xEB0D0D14, alpha));

        // Delicate border & top sheen
        int border = applyAlpha(accentColor, alpha * 0.35f);
        guiGraphics.fill(dockX + 2, dockY + dockH - 1, dockX + dockW - 2, dockY + dockH, border);
        guiGraphics.fill(dockX, dockY + 2, dockX + 1, dockY + dockH - 2, border);
        guiGraphics.fill(dockX + dockW - 1, dockY + 2, dockX + dockW, dockY + dockH - 2, border);
        guiGraphics.fillGradient(dockX + 4, dockY, dockX + dockW - 4, dockY + 1, applyAlpha(accentColor, alpha * 0.75f), applyAlpha(accent2Color, alpha * 0.35f));

        Font font = Minecraft.getInstance().font;
        int centerX = dockX + (dockW / 2);

        // Prev Button: centerX - 46, w = 22, h = 18
        int prevX = centerX - 46;
        int prevY = dockY + 2;
        boolean prevHover = (mouseX >= prevX && mouseX <= prevX + 22 && mouseY >= prevY && mouseY <= prevY + 18);
        renderControlBtn(guiGraphics, font, "⏮", prevX, prevY, 22, 18, prevHover, alpha, accentColor);

        // Play / Pause Button: centerX - 13, w = 26, h = 18
        int playX = centerX - 13;
        int playY = dockY + 2;
        boolean playHover = (mouseX >= playX && mouseX <= playX + 26 && mouseY >= playY && mouseY <= playY + 18);
        String playIcon = state.isPlaying() ? "❚❚" : "▶";
        renderControlBtn(guiGraphics, font, playIcon, playX, playY, 26, 18, playHover, alpha, accentColor);

        // Next Button: centerX + 24, w = 22, h = 18
        int nextX = centerX + 24;
        int nextY = dockY + 2;
        boolean nextHover = (mouseX >= nextX && mouseX <= nextX + 22 && mouseY >= nextY && mouseY <= nextY + 18);
        renderControlBtn(guiGraphics, font, "⏭", nextX, nextY, 22, 18, nextHover, alpha, accentColor);

        // Like / Unlike Button: centerX + 56, w = 20, h = 18
        int likeX = centerX + 56;
        int likeY = dockY + 2;
        boolean likeHover = (mouseX >= likeX && mouseX <= likeX + 20 && mouseY >= likeY && mouseY <= likeY + 18);
        boolean isLiked = SpotifyStateManager.getInstance().isCurrentTrackLiked();
        String likeIcon = isLiked ? "❤" : "♡";
        renderControlBtn(guiGraphics, font, likeIcon, likeX, likeY, 20, 18, likeHover, alpha, isLiked ? 0xFFFF4081 : accentColor);
    }

    private static void renderControlBtn(GuiGraphics g, Font font, String text, int bx, int by, int bw, int bh, boolean hover, float alpha, int accent) {
        int bg = hover ? applyAlpha(accent, alpha * 0.35f) : applyAlpha(0x22FFFFFF, alpha * 0.6f);
        int border = hover ? applyAlpha(accent, alpha * 0.9f) : applyAlpha(0x18FFFFFF, alpha * 0.4f);

        g.fill(bx + 1, by, bx + bw - 1, by + bh, bg);
        g.fill(bx, by + 1, bx + 1, by + bh - 1, border);
        g.fill(bx + bw - 1, by + 1, bx + bw, by + bh - 1, border);
        g.fill(bx + 1, by, bx + bw - 1, by + 1, border);
        g.fill(bx + 1, by + bh - 1, bx + bw - 1, by + bh, border);

        int textColor = hover ? applyAlpha(0xFFFFFFFF, alpha) : applyAlpha(0xD0FFFFFF, alpha);
        int textW = switch (text) {
            case "⏮", "⏭" -> 9;
            case "▶", "❚❚" -> 7;
            case "❤", "♡" -> 8;
            default -> font.width(text);
        };
        int tx = bx + (bw - textW) / 2;
        int ty = by + (bh - 8) / 2;
        g.drawString(font, text, tx, ty, textColor, false);
    }

    public static boolean handleChatMouseClick(double mouseX, double mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof ChatScreen)) return false;
        if (chatControlsProgress < 0.3f) return false;

        ModConfig config = ModConfig.getInstance();
        if (!config.isEnabled() || config.getHudPosition() == ModConfig.HudPosition.OFF) return false;

        float scale = cachedScale;
        double localMx = (mouseX - cachedScreenX) / scale;
        double localMy = (mouseY - cachedScreenY) / scale;

        int dockW = cachedCardWidth;
        int dockH = 22;
        int dockX = 0;
        int dockY = cachedIsBottom ? (-dockH - 4) : (cachedTotalHeight + 4);

        int centerX = dockX + (dockW / 2);

        // Prev: centerX - 46, w = 22, h = 18
        int prevX = centerX - 46;
        int prevY = dockY + 2;
        if (localMx >= prevX && localMx <= prevX + 22 && localMy >= prevY && localMy <= prevY + 18) {
            SpotifyStateManager.getInstance().previous();
            playClickSound();
            return true;
        }

        // Play/Pause: centerX - 13, w = 26, h = 18
        int playX = centerX - 13;
        int playY = dockY + 2;
        if (localMx >= playX && localMx <= playX + 26 && localMy >= playY && localMy <= playY + 18) {
            SpotifyStateManager.getInstance().togglePlayPause();
            playClickSound();
            return true;
        }

        // Next: centerX + 24, w = 22, h = 18
        int nextX = centerX + 24;
        int nextY = dockY + 2;
        if (localMx >= nextX && localMx <= nextX + 22 && localMy >= nextY && localMy <= nextY + 18) {
            SpotifyStateManager.getInstance().next();
            playClickSound();
            return true;
        }

        // Like: centerX + 56, w = 20, h = 18
        int likeX = centerX + 56;
        int likeY = dockY + 2;
        if (localMx >= likeX && localMx <= likeX + 20 && localMy >= likeY && localMy <= likeY + 18) {
            SpotifyStateManager.getInstance().toggleCurrentTrackLiked();
            playClickSound();
            return true;
        }

        return false;
    }

    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }

    private static void renderModernCard(GuiGraphics guiGraphics, int x, int y, int width, int height, float effectiveAlpha) {
        float effectiveBgAlpha = effectiveAlpha;
        float effectiveBorderAlpha = Math.min(1.0f, effectiveAlpha * 1.15f);

        // 1. Soft Multi-Layer Drop Shadow
        guiGraphics.fill(x + 1, y + 2, x + width - 1, y + height + 3, applyAlpha(0x38000000, effectiveAlpha * 0.6f));
        guiGraphics.fill(x + 2, y + 3, x + width - 2, y + height + 5, applyAlpha(0x22000000, effectiveAlpha * 0.5f));

        // 2. Beveled/Chamfered Deep Frosted Obsidian Glass Body
        guiGraphics.fill(x + 2, y, x + width - 2, y + height, applyAlpha(0xEB0D0D14, effectiveBgAlpha));
        guiGraphics.fill(x, y + 2, x + 2, y + height - 2, applyAlpha(0xEB0D0D14, effectiveBgAlpha));
        guiGraphics.fill(x + width - 2, y + 2, x + width, y + height - 2, applyAlpha(0xEB0D0D14, effectiveBgAlpha));

        // 3. Delicate Golden Specular Highlight Top Edge
        guiGraphics.fillGradient(x + 4, y, x + width - 4, y + 1,
                applyAlpha(0xCCFFD700, effectiveBorderAlpha),
                applyAlpha(0x66FFF59D, effectiveBorderAlpha));

        // 4. Subtle Ambient Borders
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
        int newA = Math.max(0, Math.min(255, (int) (a * Math.max(0f, Math.min(1f, alpha)))));
        return (newA << 24) | (argb & 0x00FFFFFF);
    }
}
