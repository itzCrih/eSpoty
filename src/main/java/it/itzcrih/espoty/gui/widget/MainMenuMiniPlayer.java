package it.itzcrih.espoty.gui.widget;

import it.itzcrih.espoty.config.ModConfig;
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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;

public class MainMenuMiniPlayer {
    private static final int CARD_X = 10;
    private static final int CARD_Y = 10;
    private static final int CARD_W = 186;
    private static final int CARD_H = 46;

    // Cache trimmed track strings to prevent while-loop string measurements on every frame
    private static String cachedRawTrack = "";
    private static String cachedTrimmedTrack = "";
    private static String cachedRawArtist = "";
    private static String cachedTrimmedArtist = "";

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                ScreenEvents.afterRender(screen).register(MainMenuMiniPlayer::render);
                ScreenMouseEvents.allowMouseClick(screen).register(MainMenuMiniPlayer::handleMouseClick);
            }
        });
    }

    private static void render(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
        SpotifyStateManager stateManager = SpotifyStateManager.getInstance();
        PlaybackState state = stateManager.getCurrentState();
        Font font = Minecraft.getInstance().font;

        int x = CARD_X;
        int y = CARD_Y;
        int w = CARD_W;
        int h = CARD_H;

        boolean cardHover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        int accent = ModConfig.getInstance().isSyncHudTheme() ? ThemeManager.getAccent() : 0xFFFFD700;
        int accent2 = ModConfig.getInstance().isSyncHudTheme() ? ThemeManager.getAccent2() : 0xFFFFF066;

        // 1. Horizontal gradient glow
        int steps = 20;
        int glowWidth = Math.min(w, 140);
        float stepW = (float) glowWidth / (float) steps;
        for (int i = 0; i < steps; i++) {
            float t = (float) i / (float) steps;
            float falloff = (1.0f - t) * (1.0f - t);
            int glow = ThemeManager.applyAlpha(accent, falloff * 0.20f);
            int gx1 = x + (int) (i * stepW);
            int gx2 = x + (int) ((i + 1) * stepW);
            guiGraphics.fill(gx1, y - 2, gx2, y + h + 2, glow);
        }

        // Subtle hover indicator
        if (cardHover) {
            guiGraphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x12FFFFFF);
        }

        if (!stateManager.isSpotifyRunning() || state == null || state.getTrackName().isEmpty()) {
            // Idle / Offline State
            guiGraphics.drawCenteredString(font, "eSpoty: " + (stateManager.isSpotifyRunning() ? "Paused" : "Offline"), x + (w / 2), y + 12, 0xFFFFFFFF);
            guiGraphics.drawCenteredString(font, "Click to open GUI (Key: K)", x + (w / 2), y + 26, 0xFFAAAAAA);
            return;
        }

        // 2. Mini Album Art
        int artSize = 30;
        int artX = x + 6;
        int artY = y + 8;
        Identifier texture = AlbumArtManager.getInstance().getAlbumTexture(state.getAlbumArtUrl());

        guiGraphics.fill(artX + 1, artY + 1, artX + artSize + 1, artY + artSize + 1, 0x40000000);

        if (texture != null) {
            guiGraphics.blit(texture, artX, artY, artX + artSize, artY + artSize, 0.0f, 1.0f, 0.0f, 1.0f);
        } else {
            guiGraphics.fill(artX, artY, artX + artSize, artY + artSize, 0x22FFFFFF);
            guiGraphics.drawCenteredString(font, "*", artX + (artSize / 2), artY + (artSize / 2) - 4, 0xFFFFFFFF);
        }

        // 3. Track Metadata with caching
        int textX = artX + artSize + 7;
        int maxTextW = 86;

        String rawTrack = state.getTrackName();
        if (!rawTrack.equals(cachedRawTrack)) {
            cachedRawTrack = rawTrack;
            cachedTrimmedTrack = trimWithEllipsis(font, rawTrack, maxTextW);
        }
        guiGraphics.drawString(font, cachedTrimmedTrack, textX, y + 6, 0xFFFFFFFF, true);

        String rawArtist = state.getArtistNames();
        if (!rawArtist.equals(cachedRawArtist)) {
            cachedRawArtist = rawArtist;
            cachedTrimmedArtist = trimWithEllipsis(font, rawArtist, maxTextW);
        }
        guiGraphics.drawString(font, cachedTrimmedArtist, textX, y + 17, 0xFFCBD5E1, true);

        // 4. Mini Interactive Controls Row
        int btnY = y + 29;
        int btnW = 14;
        int btnH = 12;

        // Prev Button
        int prevX = textX;
        boolean prevHover = mouseX >= prevX - 2 && mouseX <= prevX + btnW + 2 && mouseY >= btnY - 1 && mouseY <= btnY + btnH + 1;
        if (prevHover) guiGraphics.fill(prevX - 1, btnY - 1, prevX + btnW + 1, btnY + btnH + 1, 0x25FFFFFF);
        guiGraphics.drawString(font, "<<", prevX, btnY, prevHover ? accent : 0xFFCCCCCC, true);

        // Play / Pause Button
        int playX = prevX + 17;
        boolean playHover = mouseX >= playX - 2 && mouseX <= playX + btnW + 2 && mouseY >= btnY - 1 && mouseY <= btnY + btnH + 1;
        if (playHover) guiGraphics.fill(playX - 1, btnY - 1, playX + btnW + 1, btnY + btnH + 1, 0x25FFFFFF);
        String playIcon = state.isPlaying() ? "||" : ">";
        guiGraphics.drawString(font, playIcon, playX, btnY, playHover ? accent : 0xFFFFFFFF, true);

        // Next Button
        int nextX = playX + 17;
        boolean nextHover = mouseX >= nextX - 2 && mouseX <= nextX + btnW + 2 && mouseY >= btnY - 1 && mouseY <= btnY + btnH + 1;
        if (nextHover) guiGraphics.fill(nextX - 1, btnY - 1, nextX + btnW + 1, btnY + btnH + 1, 0x25FFFFFF);
        guiGraphics.drawString(font, ">>", nextX, btnY, nextHover ? accent : 0xFFCCCCCC, true);

        // Like Button (Heart)
        int likeX = nextX + 18;
        boolean likeHover = mouseX >= likeX - 2 && mouseX <= likeX + btnW + 2 && mouseY >= btnY - 1 && mouseY <= btnY + btnH + 1;
        if (likeHover) guiGraphics.fill(likeX - 1, btnY - 1, likeX + btnW + 1, btnY + btnH + 1, 0x25FFFFFF);
        boolean isLiked = stateManager.isCurrentTrackLiked();
        String heartIcon = isLiked ? "[+]" : "[-]";
        int heartColor = isLiked ? 0xFFFF4444 : (likeHover ? accent : 0xFFAAAAAA);
        guiGraphics.drawString(font, heartIcon, likeX, btnY, heartColor, true);

        // Mini Waveform
        int waveX = x + w - 38;
        int waveY = y + 39;
        long progressMs = stateManager.getInterpolatedProgressMs();
        AudioWaveformRenderer.renderSyncedWaveform(guiGraphics, waveX, waveY, 32, 12, 6,
                state.isPlaying(), progressMs, 1.0f, accent, accent2,
                state.getTrackName(), state.getArtistNames(), false);
    }

    private static boolean handleMouseClick(Screen screen, MouseButtonEvent event) {
        if (event.button() != 0) return true; // Only handle left clicks

        int mx = (int) event.x();
        int my = (int) event.y();

        int x = CARD_X;
        int y = CARD_Y;
        int w = CARD_W;
        int h = CARD_H;

        if (mx < x || mx > x + w || my < y || my > y + h) {
            return true; // Click outside mini player, let screen handle
        }

        SpotifyStateManager stateManager = SpotifyStateManager.getInstance();
        PlaybackState state = stateManager.getCurrentState();

        if (!stateManager.isSpotifyRunning() || state == null || state.getTrackName().isEmpty()) {
            // Click opens full Spotify GUI
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

        // Clicking anywhere else on the card opens full Spotify Screen
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
