package it.itzcrih.espoty.gui.widget;

import it.itzcrih.espoty.config.ModConfig;
import it.itzcrih.espoty.gui.render.AaRoundRect;
import it.itzcrih.espoty.gui.screen.SpotifyScreen;
import it.itzcrih.espoty.gui.texture.AlbumArtManager;
import it.itzcrih.espoty.gui.theme.ThemeManager;
import it.itzcrih.espoty.spotify.SpotifyStateManager;
import it.itzcrih.espoty.spotify.models.PlaybackState;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;

public class MainMenuMiniPlayer {
    private static final int CARD_X = 10;
    private static final int CARD_Y = 10;
    private static final int CARD_W = 186;
    private static final int CARD_H = 46;

    private static String cachedRawTrack = "";
    private static String cachedTrimmedTrack = "";
    private static String cachedRawArtist = "";
    private static String cachedTrimmedArtist = "";

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                ScreenEvents.afterRender(screen).register((s, poseStack, mouseX, mouseY, tickDelta) -> render(s, new GuiGraphics(poseStack), mouseX, mouseY, tickDelta));
                ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, btn) -> handleMouseClick(s, mx, my, btn));
            }
        });
    }

    private static void render(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
        SpotifyStateManager stateManager = SpotifyStateManager.getInstance();
        if (!stateManager.isSpotifyRunning()) {
            return;
        }

        PlaybackState state = stateManager.getCurrentState();
        if (state != null && state.isAd()) {
            return;
        }
        Font font = Minecraft.getInstance().font;

        int x = CARD_X;
        int y = CARD_Y;
        int w = CARD_W;
        int h = CARD_H;

        boolean cardHover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        int accent = ModConfig.getInstance().isSyncHudTheme() ? ThemeManager.getAccent() : 0xFFFFD700;
        int accent2 = ModConfig.getInstance().isSyncHudTheme() ? ThemeManager.getAccent2() : 0xFFFFF066;

        float bgOpacity = ModConfig.getInstance().getHudBackgroundOpacity();
        float elemOpacity = ModConfig.getInstance().getHudElementsOpacity();

        int glowColor = ThemeManager.applyAlpha(accent, 0.12f * bgOpacity);
        AaRoundRect.draw(guiGraphics, x - 2, y - 2, w + 4, h + 4, 11, glowColor, 0);

        if (bgOpacity > 0.02f) {
            int cardBg = ThemeManager.applyAlpha(0xE00D0D14, bgOpacity);
            int cardBorder = ThemeManager.applyAlpha(accent, 0.40f * bgOpacity);
            AaRoundRect.draw(guiGraphics, x, y, w, h, 9, cardBg, cardBorder);
        }

        if (cardHover) {
            AaRoundRect.draw(guiGraphics, x, y, w, h, 9, ThemeManager.applyAlpha(0x12FFFFFF, bgOpacity), 0);
        }

        if (state == null || state.getTrackName().isEmpty()) {

            guiGraphics.drawCenteredString(font, "eSpoty: Paused", x + (w / 2), y + 12, 0xFFFFFFFF);
            guiGraphics.drawCenteredString(font, "Click to open GUI (Key: K)", x + (w / 2), y + 26, 0xFFAAAAAA);
            return;
        }

        int artSize = 30;
        int artX = x + 6;
        int artY = y + 8;
        ResourceLocation texture = AlbumArtManager.getInstance().getAlbumTexture(state.getAlbumArtUrl());

        AaRoundRect.draw(guiGraphics, artX + 1, artY + 1, artSize, artSize, 4, 0x40000000, 0);

        if (texture != null) {
            guiGraphics.blit(texture, artX, artY, 0, 0, artSize, artSize, artSize, artSize);
            AaRoundRect.draw(guiGraphics, artX, artY, artSize, artSize, 4, 0x00000000, ThemeManager.applyAlpha(0x30FFFFFF, elemOpacity));
        } else {
            AaRoundRect.draw(guiGraphics, artX, artY, artSize, artSize, 4, 0x22FFFFFF, 0x30FFFFFF);
            guiGraphics.drawCenteredString(font, "♪", artX + (artSize / 2), artY + (artSize / 2) - 4, 0xFFFFFFFF);
        }

        int textX = artX + artSize + 7;
        int maxTextW = 86;

        String rawTrack = state.getTrackName();
        if (!rawTrack.equals(cachedRawTrack)) {
            cachedRawTrack = rawTrack;
            cachedTrimmedTrack = trimWithEllipsis(font, rawTrack, maxTextW);
        }
        guiGraphics.drawString(font, cachedTrimmedTrack, textX, y + 6, ThemeManager.applyAlpha(0xFFFFFFFF, elemOpacity), true);

        String rawArtist = state.getArtistNames();
        if (!rawArtist.equals(cachedRawArtist)) {
            cachedRawArtist = rawArtist;
            cachedTrimmedArtist = trimWithEllipsis(font, rawArtist, maxTextW);
        }
        guiGraphics.drawString(font, cachedTrimmedArtist, textX, y + 17, ThemeManager.applyAlpha(0xFFCBD5E1, elemOpacity), true);

        int btnY = y + 29;
        int btnW = 14;
        int btnH = 12;

        int prevX = textX;
        boolean prevHover = mouseX >= prevX - 2 && mouseX <= prevX + btnW + 2 && mouseY >= btnY - 1 && mouseY <= btnY + btnH + 1;
        if (prevHover) AaRoundRect.draw(guiGraphics, prevX - 2, btnY - 2, btnW + 4, btnH + 4, 4, 0x25FFFFFF, 0x30FFFFFF);
        guiGraphics.drawString(font, "◀◀", prevX, btnY, prevHover ? accent : ThemeManager.applyAlpha(0xFFCCCCCC, elemOpacity), true);

        int playX = prevX + 17;
        boolean playHover = mouseX >= playX - 2 && mouseX <= playX + btnW + 2 && mouseY >= btnY - 1 && mouseY <= btnY + btnH + 1;
        if (playHover) AaRoundRect.draw(guiGraphics, playX - 2, btnY - 2, btnW + 4, btnH + 4, 4, 0x25FFFFFF, 0x30FFFFFF);
        String playIcon = state.isPlaying() ? "❚❚" : "▶";
        guiGraphics.drawString(font, playIcon, playX, btnY, playHover ? accent : ThemeManager.applyAlpha(0xFFFFFFFF, elemOpacity), true);

        int nextX = playX + 17;
        boolean nextHover = mouseX >= nextX - 2 && mouseX <= nextX + btnW + 2 && mouseY >= btnY - 1 && mouseY <= btnY + btnH + 1;
        if (nextHover) AaRoundRect.draw(guiGraphics, nextX - 2, btnY - 2, btnW + 4, btnH + 4, 4, 0x25FFFFFF, 0x30FFFFFF);
        guiGraphics.drawString(font, "▶▶", nextX, btnY, nextHover ? accent : ThemeManager.applyAlpha(0xFFCCCCCC, elemOpacity), true);

        int likeX = nextX + 18;
        boolean likeHover = mouseX >= likeX - 2 && mouseX <= likeX + btnW + 2 && mouseY >= btnY - 1 && mouseY <= btnY + btnH + 1;
        if (likeHover) AaRoundRect.draw(guiGraphics, likeX - 2, btnY - 2, btnW + 4, btnH + 4, 4, 0x25FFFFFF, 0x30FFFFFF);
        boolean isLiked = stateManager.isCurrentTrackLiked();
        String heartIcon = isLiked ? "❤" : "♡";
        int heartColor = isLiked ? ThemeManager.applyAlpha(0xFFFF4081, elemOpacity) : (likeHover ? accent : ThemeManager.applyAlpha(0xFFAAAAAA, elemOpacity));
        guiGraphics.drawString(font, heartIcon, likeX, btnY, heartColor, true);

        int waveX = x + w - 38;
        int waveY = y + 39;
        long progressMs = stateManager.getInterpolatedProgressMs();
        AudioWaveformRenderer.renderSyncedWaveform(guiGraphics, waveX, waveY, 32, 12, 6,
                state.isPlaying(), progressMs, elemOpacity, accent, accent2,
                state.getTrackName(), state.getArtistNames(), false);
    }

    private static boolean handleMouseClick(Screen screen, double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        int x = CARD_X;
        int y = CARD_Y;
        int w = CARD_W;
        int h = CARD_H;

        if (mx < x || mx > x + w || my < y || my > y + h) {
            return true;
        }

        SpotifyStateManager stateManager = SpotifyStateManager.getInstance();
        PlaybackState state = stateManager.getCurrentState();

        if (!stateManager.isSpotifyRunning() || (state != null && state.isAd())) {
            return true;
        }

        if (state == null || state.getTrackName().isEmpty()) {

            Minecraft.getInstance().setScreen(new SpotifyScreen());
            return false;
        }

        int artSize = 30;
        int artX = x + 7;
        int textX = artX + artSize + 7;
        int btnY = y + 29;
        int btnW = 14;
        int btnH = 12;

        int prevX = textX;
        if (mx >= prevX - 2 && mx <= prevX + btnW + 2 && my >= btnY - 2 && my <= btnY + btnH + 2) {
            stateManager.previous();
            return false;
        }

        int playX = prevX + 17;
        if (mx >= playX - 2 && mx <= playX + btnW + 2 && my >= btnY - 2 && my <= btnY + btnH + 2) {
            stateManager.togglePlayPause();
            return false;
        }

        int nextX = playX + 17;
        if (mx >= nextX - 2 && mx <= nextX + btnW + 2 && my >= btnY - 2 && my <= btnY + btnH + 2) {
            stateManager.next();
            return false;
        }

        int likeX = nextX + 18;
        if (mx >= likeX - 2 && mx <= likeX + btnW + 2 && my >= btnY - 2 && my <= btnY + btnH + 2) {
            stateManager.toggleCurrentTrackLiked();
            return false;
        }

        Minecraft.getInstance().setScreen(new SpotifyScreen());
        return false;
    }

    private static String trimWithEllipsis(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) return text;
        while (!text.isEmpty() && font.width(text + "…") > maxW) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }
}
