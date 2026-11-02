package it.itzcrih.espoty.gui.hud;

import it.itzcrih.espoty.config.ModConfig;
import it.itzcrih.espoty.gui.render.AaRoundRect;
import it.itzcrih.espoty.gui.render.RenderUtils;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

public class SpotifyHudRenderer {

    private static final ResourceLocation HEART_FILLED_ICON = ResourceLocation.fromNamespaceAndPath("espoty", "textures/ui/ctrl_heart_filled.png");
    private static final ResourceLocation HEART_OUTLINE_ICON = ResourceLocation.fromNamespaceAndPath("espoty", "textures/ui/ctrl_heart.png");
    private static final ResourceLocation ICON_CTRL_PLAY = ResourceLocation.fromNamespaceAndPath("espoty", "textures/ui/ctrl_play.png");
    private static final ResourceLocation ICON_CTRL_PAUSE = ResourceLocation.fromNamespaceAndPath("espoty", "textures/ui/ctrl_pause.png");
    private static final ResourceLocation ICON_CTRL_PREV = ResourceLocation.fromNamespaceAndPath("espoty", "textures/ui/ctrl_prev.png");
    private static final ResourceLocation ICON_CTRL_NEXT = ResourceLocation.fromNamespaceAndPath("espoty", "textures/ui/ctrl_next.png");

    private static float currentExtraHeight = 0f;
    private static String lastLyricLine = "";
    private static String prevLyricLine = "";
    private static long lyricLineChangeTime = 0;

    private static Component cachedLyricComp = null;
    private static int cachedLyricWidth = 0;
    private static Component cachedPrevLyricComp = null;
    private static ModConfig.LyricFont lastCachedFont = null;

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

        float bgOpacity = config.getHudBackgroundOpacity();
        float elemOpacity = config.getHudElementsOpacity();
        if (bgOpacity <= 0.005f && elemOpacity <= 0.005f) {
            return;
        }

        SpotifyStateManager stateManager = SpotifyStateManager.getInstance();
        PlaybackState state = stateManager.getCurrentState();

        if (state == null || state.getTrackName().isEmpty() || state.isAd()) {
            return;
        }

        long now = System.currentTimeMillis();
        long trackAge = now - stateManager.getLastTrackChangeTime();

        float animAlpha = 1.0f;
        float slideRatio = 0.0f;

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

        float bgAlpha = animAlpha * bgOpacity;
        float elemAlpha = animAlpha * elemOpacity;
        if (Math.max(bgAlpha, elemAlpha) <= 0.005f) return;

        Font font = client.font;
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        int cardWidth = 228;
        boolean showLyrics = config.isHudShowLyrics() && !config.isHudCompact();
        LyricLine currentLyric = showLyrics ? LyricsManager.getInstance().getActiveLyricLine(stateManager.getInterpolatedProgressMs()) : null;
        boolean hasActiveLyric = currentLyric != null && !currentLyric.getText().isEmpty();

        int cardHeight = config.isHudCompact() ? 44 : 54;

        float targetExtraHeight = (showLyrics && hasActiveLyric) ? 19f : 0f;
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
            case CUSTOM -> {
                int cx = config.getHudCustomX();
                int cy = config.getHudCustomY();
                if (cx < 0 || cy < 0) {
                    screenX = screenWidth - (int) (cardWidth * scale) - margin + slideOffset;
                    screenY = margin;
                } else {
                    screenX = cx;
                    screenY = cy;
                }
            }
            default -> {
                return;
            }
        }

        cachedScreenX = screenX;
        cachedScreenY = screenY;
        cachedScale = scale;
        cachedCardWidth = cardWidth;
        cachedTotalHeight = dynamicTotalHeight;
        cachedIsBottom = (config.getHudPosition() == ModConfig.HudPosition.BOTTOM_LEFT
                || config.getHudPosition() == ModConfig.HudPosition.BOTTOM_RIGHT
                || (config.getHudPosition() == ModConfig.HudPosition.CUSTOM && screenY > screenHeight / 2));

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float) screenX, (float) screenY, 0.0f);
        if (scale != 1.0f) {
            guiGraphics.pose().scale(scale, scale, 1.0f);
        }
        int x = 0;
        int y = 0;

        renderModernCard(guiGraphics, x, y, cardWidth, dynamicTotalHeight, bgAlpha);

        int artSize = config.isHudCompact() ? 32 : 38;
        int artX = x + 8;
        int artY = y + 8;
        renderModernAlbumArt(guiGraphics, state.getAlbumArtUrl(), artX, artY, artSize, elemAlpha);

        int textX = artX + artSize + 8;
        int maxTextWidth = cardWidth - (textX - x) - 10;

        String trackTitle = state.getTrackName();
        int titleWidth = font.width(trackTitle);
        int titleY = y + 8;

        int accentColor = config.isSyncHudTheme() ? ThemeManager.getAccent() : 0xFFFFD700;
        int accent2Color = config.isSyncHudTheme() ? ThemeManager.getAccent2() : 0xFFFFF066;

        long progressMs = stateManager.getInterpolatedProgressMs();
        boolean isLiked = stateManager.isCurrentTrackLiked();
        if (isLiked) {
            RenderUtils.drawTintedIcon(guiGraphics, HEART_FILLED_ICON, x + cardWidth - 46, titleY, 10, applyAlpha(0xFFFF4081, elemAlpha));
        }
        AudioWaveformRenderer.renderSyncedWaveform(guiGraphics, x + cardWidth - 32, titleY + 9, 24, 9, 6,
                state.isPlaying(), progressMs, elemAlpha, accentColor, accent2Color,
                state.getTrackName(), state.getArtistNames(), hasActiveLyric);

        int rightOffset = isLiked ? 46 : 34;
        int maxTitleW = maxTextWidth - rightOffset;
        if (titleWidth > maxTitleW) {
            guiGraphics.enableScissor(textX, titleY - 2, textX + maxTitleW, titleY + 14);

            long scrollDist = titleWidth + 40;
            long scrollDuration = scrollDist * 32L;
            long totalPeriod = scrollDuration + 1800L;
            long periodPos = now % totalPeriod;
            long cycleOffset = (periodPos < 1800L) ? 0L : (periodPos - 1800L) / 32L;

            int scrollX = (int) (textX - cycleOffset);
            RenderUtils.drawGlowText(guiGraphics, font, trackTitle, scrollX, titleY, applyAlpha(accentColor, elemAlpha), accentColor, elemAlpha);
            RenderUtils.drawGlowText(guiGraphics, font, trackTitle, scrollX + (int) scrollDist, titleY, applyAlpha(accentColor, elemAlpha), accentColor, elemAlpha);

            guiGraphics.disableScissor();
        } else {
            RenderUtils.drawGlowText(guiGraphics, font, trackTitle, textX, titleY, applyAlpha(accentColor, elemAlpha), accentColor, elemAlpha);
        }

        int artistY = titleY + 11;
        String artist = state.getArtistNames();
        if (font.width(artist) > maxTextWidth) {
            while (font.width(artist + "...") > maxTextWidth && artist.length() > 3) {
                artist = artist.substring(0, artist.length() - 1);
            }
            artist = artist + "...";
        }
        guiGraphics.drawString(font, artist, textX, artistY, applyAlpha(accent2Color, elemAlpha), true);

        long durationMs = state.getDurationMs();
        float progressRatio = durationMs > 0 ? Math.min(1.0f, (float) progressMs / durationMs) : 0f;

        int barX = textX;
        int barY = artistY + 13;
        int barWidth = maxTextWidth;
        int barHeight = 3;

        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, applyAlpha(0x44FFFFFF, elemAlpha));

        int filledWidth = (int) (barWidth * progressRatio);
        if (filledWidth > 0) {
            guiGraphics.fillGradient(barX, barY, barX + filledWidth, barY + barHeight, applyAlpha(accentColor, elemAlpha), applyAlpha(accent2Color, elemAlpha));

            int capX = barX + filledWidth;
            guiGraphics.fill(capX - 1, barY - 1, capX + 2, barY + barHeight + 1, applyAlpha(0xFFFFFFFF, elemAlpha));
        }

        if (!config.isHudCompact()) {
            String timeText = PlaybackState.formatTime(progressMs) + " / " + PlaybackState.formatTime(durationMs);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(0.72f, 0.72f, 1.0f);
            int timeX = (int) ((barX + barWidth) / 0.72f) - font.width(timeText);
            int timeY = (int) ((barY + 5) / 0.72f);
            guiGraphics.drawString(font, timeText, timeX, timeY, applyAlpha(0xFFAAAAAA, elemAlpha), true);
            guiGraphics.pose().popPose();
        }

        boolean shouldRenderLyrics = showLyrics && (hasActiveLyric || currentExtraHeight > 0.5f);
        if (shouldRenderLyrics) {
            ModConfig.LyricFont currentFont = config.getLyricFont();
            if (hasActiveLyric && currentLyric != null) {
                String lyricText = currentLyric.getText();
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
            }

            if (cachedLyricComp != null && currentExtraHeight > 0.5f) {
                long lyricElapsed = now - lyricLineChangeTime;
                float lyricProgress = Math.min(1.0f, lyricElapsed / 300.0f);
                float inEase = 1.0f - (float) Math.pow(1.0f - lyricProgress, 3);
                float outEase = (float) Math.pow(1.0f - lyricProgress, 2);

                float heightRatio = Math.max(0.0f, Math.min(1.0f, currentExtraHeight / 19.0f));
                float lineAlpha = elemAlpha * inEase * heightRatio;

                int dividerY = y + cardHeight - 2;
                guiGraphics.fill(x + 8, dividerY, x + cardWidth - 8, dividerY + 1,
                        applyAlpha(0x22FFFFFF, bgAlpha * heightRatio));

                int fontYOffset = (currentFont == ModConfig.LyricFont.COOLFONT) ? 1 : 0;
                int noteY = dividerY + 5;
                guiGraphics.drawString(font, "♪", x + 8, noteY, applyAlpha(accentColor, elemAlpha * heightRatio), true);

                int scissorTop = dividerY + 1;
                int scissorBottom = Math.max(scissorTop, y + dynamicTotalHeight - 2);
                boolean scissorActive = (scissorBottom > scissorTop);
                if (scissorActive) {
                    guiGraphics.enableScissor(x + 18, scissorTop, x + cardWidth - 8, scissorBottom);
                }

                if (prevLyricLine != null && !prevLyricLine.isEmpty() && lyricProgress < 1.0f && cachedPrevLyricComp != null) {
                    float prevAlpha = elemAlpha * outEase * heightRatio;
                    int prevY = dividerY + 5 + fontYOffset - (int) ((1.0f - outEase) * 8);
                    if (prevAlpha > 0.01f) {
                        guiGraphics.drawString(font, cachedPrevLyricComp, x + 18, prevY, applyAlpha(0xFFCBD5E1, prevAlpha), true);
                    }
                }

                int lyricY = dividerY + 5 + fontYOffset + (int) ((1.0f - inEase) * 8);
                int maxLyricW = cardWidth - 28;
                Component lyricComp = cachedLyricComp;
                int lyricWidth = cachedLyricWidth;

                if (lyricWidth > maxLyricW) {
                    long scrollDist = lyricWidth + 30;
                    long scrollDuration = scrollDist * 32L;
                    long totalPeriod = scrollDuration + 1500L;
                    long periodPos = now % totalPeriod;
                    long cycleOffset = (periodPos < 1500L) ? 0L : (periodPos - 1500L) / 32L;

                    int scrollX = (int) (x + 18 - cycleOffset);
                    RenderUtils.drawGlowComponent(guiGraphics, font, lyricComp, scrollX, lyricY, applyAlpha(0xFFFFFDE7, lineAlpha), accentColor, lineAlpha * 0.65f);
                    RenderUtils.drawGlowComponent(guiGraphics, font, lyricComp, scrollX + (int) scrollDist, lyricY, applyAlpha(0xFFFFFDE7, lineAlpha), accentColor, lineAlpha * 0.65f);
                } else {
                    RenderUtils.drawGlowComponent(guiGraphics, font, lyricComp, x + 18, lyricY, applyAlpha(0xFFFFFDE7, lineAlpha), accentColor, lineAlpha * 0.65f);
                }

                if (scissorActive) {
                    guiGraphics.disableScissor();
                }
            }
        } else {
            lastLyricLine = "";
            prevLyricLine = "";
            cachedLyricComp = null;
            cachedPrevLyricComp = null;
        }

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

            renderChatControls(guiGraphics, x, y, cardWidth, dynamicTotalHeight, elemAlpha * chatControlsProgress,
                    localMx, localMy, state, accentColor, accent2Color, cachedIsBottom);
        }

        guiGraphics.pose().popPose();
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

        AaRoundRect.draw(guiGraphics, dockX, dockY, dockW, dockH, 7, applyAlpha(0x38000000, alpha), applyAlpha(accentColor, 0.30f * alpha));

        Font font = Minecraft.getInstance().font;
        int centerX = dockX + (dockW / 2);

        int prevX = centerX - 46;
        int prevY = dockY + 2;
        boolean prevHover = (mouseX >= prevX && mouseX <= prevX + 22 && mouseY >= prevY && mouseY <= prevY + 18);
        renderControlIconBtn(guiGraphics, ICON_CTRL_PREV, prevX, prevY, 22, 18, prevHover, alpha, accentColor);

        int playX = centerX - 13;
        int playY = dockY + 2;
        boolean playHover = (mouseX >= playX && mouseX <= playX + 26 && mouseY >= playY && mouseY <= playY + 18);
        ResourceLocation playIcon = state.isPlaying() ? ICON_CTRL_PAUSE : ICON_CTRL_PLAY;
        renderControlIconBtn(guiGraphics, playIcon, playX, playY, 26, 18, playHover, alpha, accentColor);

        int nextX = centerX + 24;
        int nextY = dockY + 2;
        boolean nextHover = (mouseX >= nextX && mouseX <= nextX + 22 && mouseY >= nextY && mouseY <= nextY + 18);
        renderControlIconBtn(guiGraphics, ICON_CTRL_NEXT, nextX, nextY, 22, 18, nextHover, alpha, accentColor);

        int likeX = centerX + 56;
        int likeY = dockY + 2;
        boolean likeHover = (mouseX >= likeX && mouseX <= likeX + 20 && mouseY >= likeY && mouseY <= likeY + 18);
        boolean isLiked = SpotifyStateManager.getInstance().isCurrentTrackLiked();
        ResourceLocation heartIcon = isLiked ? HEART_FILLED_ICON : HEART_OUTLINE_ICON;
        renderControlIconBtn(guiGraphics, heartIcon, likeX, likeY, 20, 18, likeHover, alpha, isLiked ? 0xFFFF4081 : accentColor);
    }

    private static void renderControlIconBtn(GuiGraphics g, ResourceLocation icon, int bx, int by, int bw, int bh, boolean hover, float alpha, int accent) {
        int bg = hover ? applyAlpha(accent, alpha * 0.35f) : applyAlpha(0x22FFFFFF, alpha * 0.6f);
        int border = hover ? applyAlpha(accent, alpha * 0.9f) : applyAlpha(0x18FFFFFF, alpha * 0.4f);

        AaRoundRect.draw(g, bx, by, bw, bh, 5, bg, border);

        int iconColor = hover ? applyAlpha(0xFFFFFFFF, alpha) : applyAlpha(accent, alpha);
        int iconSize = Math.min(bw, bh) - 8;
        int ix = bx + (bw - iconSize) / 2;
        int iy = by + (bh - iconSize) / 2;
        RenderUtils.drawTintedIcon(g, icon, ix, iy, iconSize, iconColor);
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

        int prevX = centerX - 46;
        int prevY = dockY + 2;
        if (localMx >= prevX && localMx <= prevX + 22 && localMy >= prevY && localMy <= prevY + 18) {
            SpotifyStateManager.getInstance().previous();
            playClickSound();
            return true;
        }

        int playX = centerX - 13;
        int playY = dockY + 2;
        if (localMx >= playX && localMx <= playX + 26 && localMy >= playY && localMy <= playY + 18) {
            SpotifyStateManager.getInstance().togglePlayPause();
            playClickSound();
            return true;
        }

        int nextX = centerX + 24;
        int nextY = dockY + 2;
        if (localMx >= nextX && localMx <= nextX + 22 && localMy >= nextY && localMy <= nextY + 18) {
            SpotifyStateManager.getInstance().next();
            playClickSound();
            return true;
        }

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

    private static void renderModernCard(GuiGraphics guiGraphics, int x, int y, int width, int height, float bgAlpha) {
        if (bgAlpha <= 0.01f) return;
        ModConfig config = ModConfig.getInstance();
        int accentColor = config.isSyncHudTheme() ? ThemeManager.getAccent() : 0xFFFFD700;

        int glowColor = applyAlpha(accentColor, 0.12f * bgAlpha);
        AaRoundRect.draw(guiGraphics, x - 2, y - 2, width + 4, height + 4, 11, glowColor, 0);

        int cardBg = applyAlpha(0xE00D0D14, bgAlpha);
        int cardBorder = applyAlpha(accentColor, 0.40f * bgAlpha);
        AaRoundRect.draw(guiGraphics, x, y, width, height, 9, cardBg, cardBorder);
    }

    private static void renderModernAlbumArt(GuiGraphics guiGraphics, String url, int x, int y, int size, float alpha) {
        ResourceLocation texture = AlbumArtManager.getInstance().getAlbumTexture(url);

        AaRoundRect.draw(guiGraphics, x + 1, y + 1, size, size, 4, applyAlpha(0x40000000, alpha), 0);

        if (texture != null) {
            guiGraphics.blit(texture, x, y, 0, 0, size, size, size, size);
            AaRoundRect.draw(guiGraphics, x, y, size, size, 4, 0x00000000, applyAlpha(0x30FFFFFF, alpha));
        } else {
            AaRoundRect.draw(guiGraphics, x, y, size, size, 4, applyAlpha(0x22FFFFFF, alpha), applyAlpha(0x30FFFFFF, alpha));
            Font font = Minecraft.getInstance().font;
            guiGraphics.drawCenteredString(font, "♪", x + (size / 2), y + (size / 2) - 4, applyAlpha(0xFFFFFFFF, alpha));
        }
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = (argb >> 24) & 0xFF;
        int newA = Math.max(0, Math.min(255, (int) (a * Math.max(0f, Math.min(1f, alpha)))));
        return (newA << 24) | (argb & 0x00FFFFFF);
    }

    public static int getCardWidth() {
        return 228;
    }

    public static int getCardHeight(ModConfig config) {
        return config.isHudCompact() ? 44 : 54;
    }

    public static void renderHudPreview(GuiGraphics guiGraphics, int screenX, int screenY, float scale, float alpha) {
        ModConfig config = ModConfig.getInstance();
        SpotifyStateManager stateManager = SpotifyStateManager.getInstance();
        PlaybackState state = stateManager.getCurrentState();
        if (state == null || state.getTrackName().isEmpty() || state.isAd()) {
            state = new PlaybackState("preview", "Starboy", "The Weeknd, Daft Punk", "Starboy", "", 230000L, 75000L, true, false, "off", 80, "Spotify Desktop", "Computer");
        }

        Font font = Minecraft.getInstance().font;
        int cardWidth = getCardWidth();
        int cardHeight = getCardHeight(config);
        int dynamicTotalHeight = cardHeight + (config.isHudShowLyrics() && !config.isHudCompact() ? 17 : 0);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float) screenX, (float) screenY, 0.0f);
        if (scale != 1.0f) {
            guiGraphics.pose().scale(scale, scale, 1.0f);
        }
        int x = 0;
        int y = 0;

        float bgAlpha = alpha * Math.max(0.65f, config.getHudBackgroundOpacity());
        float elemAlpha = alpha * Math.max(0.85f, config.getHudElementsOpacity());

        renderModernCard(guiGraphics, x, y, cardWidth, dynamicTotalHeight, bgAlpha);

        int artSize = config.isHudCompact() ? 32 : 38;
        int artX = x + 8;
        int artY = y + 8;
        renderModernAlbumArt(guiGraphics, state.getAlbumArtUrl(), artX, artY, artSize, elemAlpha);

        int textX = artX + artSize + 8;
        int maxTextWidth = cardWidth - (textX - x) - 10;

        String trackTitle = state.getTrackName();
        int titleY = y + 8;
        int accentColor = config.isSyncHudTheme() ? ThemeManager.getAccent() : 0xFFFFD700;
        int accent2Color = config.isSyncHudTheme() ? ThemeManager.getAccent2() : 0xFFFFF066;

        boolean isLiked = stateManager.isCurrentTrackLiked();
        if (isLiked) {
            RenderUtils.drawTintedIcon(guiGraphics, HEART_FILLED_ICON, x + cardWidth - 46, titleY, 10, applyAlpha(0xFFFF4081, elemAlpha));
        }
        AudioWaveformRenderer.renderSyncedWaveform(guiGraphics, x + cardWidth - 32, titleY + 9, 24, 9, 6,
                true, state.getProgressMs(), elemAlpha, accentColor, accent2Color,
                state.getTrackName(), state.getArtistNames(), false);

        int rightOffset = isLiked ? 46 : 34;
        int maxTitleW = maxTextWidth - rightOffset;
        if (font.width(trackTitle) > maxTitleW) {
            guiGraphics.enableScissor(textX, titleY - 2, textX + maxTitleW, titleY + 14);
            RenderUtils.drawGlowText(guiGraphics, font, trackTitle, textX, titleY, applyAlpha(accentColor, elemAlpha), accentColor, elemAlpha);
            guiGraphics.disableScissor();
        } else {
            RenderUtils.drawGlowText(guiGraphics, font, trackTitle, textX, titleY, applyAlpha(accentColor, elemAlpha), accentColor, elemAlpha);
        }

        int artistY = titleY + 11;
        String artist = state.getArtistNames();
        if (font.width(artist) > maxTextWidth) {
            while (font.width(artist + "...") > maxTextWidth && artist.length() > 3) {
                artist = artist.substring(0, artist.length() - 1);
            }
            artist = artist + "...";
        }
        guiGraphics.drawString(font, artist, textX, artistY, applyAlpha(accent2Color, elemAlpha), true);

        long progressMs = state.getProgressMs();
        long durationMs = state.getDurationMs();
        float progressRatio = durationMs > 0 ? Math.min(1.0f, (float) progressMs / durationMs) : 0.35f;

        int barX = textX;
        int barY = artistY + 13;
        int barWidth = maxTextWidth;
        int barHeight = 3;

        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, applyAlpha(0x44FFFFFF, alpha));
        int filledWidth = (int) (barWidth * progressRatio);
        if (filledWidth > 0) {
            guiGraphics.fillGradient(barX, barY, barX + filledWidth, barY + barHeight, applyAlpha(accentColor, alpha), applyAlpha(accent2Color, alpha));
            int capX = barX + filledWidth;
            guiGraphics.fill(capX - 1, barY - 1, capX + 2, barY + barHeight + 1, applyAlpha(0xFFFFFFFF, alpha));
        }

        if (!config.isHudCompact()) {
            String timeText = PlaybackState.formatTime(progressMs) + " / " + PlaybackState.formatTime(durationMs);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(0.72f, 0.72f, 1.0f);
            int timeX = (int) ((barX + barWidth) / 0.72f) - font.width(timeText);
            int timeY = (int) ((barY + 5) / 0.72f);
            guiGraphics.drawString(font, timeText, timeX, timeY, applyAlpha(0xFFAAAAAA, alpha), true);
            guiGraphics.pose().popPose();
        }

        if (config.isHudShowLyrics() && !config.isHudCompact()) {
            int dividerY = y + cardHeight - 2;
            guiGraphics.fill(x + 8, dividerY, x + cardWidth - 8, dividerY + 1, applyAlpha(0x22FFFFFF, alpha));
            int lyricY = dividerY + 4;
            guiGraphics.drawString(font, "♪", x + 8, lyricY, applyAlpha(accentColor, elemAlpha), true);
            guiGraphics.drawString(font, "Look what you've done... I'm a starboy", x + 18, lyricY, applyAlpha(0xFFFFFDE7, elemAlpha), true);
        }

        guiGraphics.pose().popPose();
    }
}
