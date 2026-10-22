package com.espoty.gui.widget;

import com.espoty.config.ModConfig;
import com.espoty.gui.screen.SpotifyScreen;
import com.espoty.gui.texture.AlbumArtManager;
import com.espoty.spotify.SpotifyStateManager;
import com.espoty.spotify.models.PlaybackState;
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
    private static final int CARD_W = 184;
    private static final int CARD_H = 46;

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
        long now = System.currentTimeMillis();

        int x = CARD_X;
        int y = CARD_Y;
        int w = CARD_W;
        int h = CARD_H;

        boolean cardHover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        // 1. Ambient Drop Shadow
        guiGraphics.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x66000000);

        // 2. Ultra-Modern Dark Obsidian Glass Box
        guiGraphics.fill(x, y, x + w, y + h, 0xEE0D0D14);
        guiGraphics.fill(x, y, x + w, y + 1, cardHover ? 0xFFFFF176 : 0xAAFFD700); // Gold Neon Top Accent
        guiGraphics.fill(x, y + h - 1, x + w, y + h, 0x55FFD700);
        guiGraphics.fill(x, y, x + 1, y + h, 0x55FFD700);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0x55FFD700);

        if (!stateManager.isSpotifyRunning() || state == null || state.getTrackName().isEmpty()) {
            // Idle / Offline State
            guiGraphics.drawCenteredString(font, "♫ eSpoty: " + (stateManager.isSpotifyRunning() ? "Paused" : "Offline"), x + (w / 2), y + 12, 0xFFFFD700);
            guiGraphics.drawCenteredString(font, "Click to open GUI (Key: K)", x + (w / 2), y + 26, 0xFFAAAAAA);
            return;
        }

        // 3. Mini Album Art (30x30)
        int artSize = 30;
        int artX = x + 7;
        int artY = y + 8;
        Identifier texture = AlbumArtManager.getInstance().getAlbumTexture(state.getAlbumArtUrl());

        guiGraphics.fill(artX - 1, artY - 1, artX + artSize + 1, artY + artSize + 1, 0xAAFFD700);
        guiGraphics.fill(artX, artY, artX + artSize, artY + artSize, 0xFF14141E);

        if (texture != null) {
            guiGraphics.blit(texture, artX, artY, artX + artSize, artY + artSize, 0.0f, 1.0f, 0.0f, 1.0f);
        } else {
            guiGraphics.drawCenteredString(font, "♫", artX + (artSize / 2), artY + (artSize / 2) - 4, 0xFFFFD700);
        }

        // 4. Track Metadata
        int textX = artX + artSize + 7;
        int maxTextW = 86;

        String title = trimWithEllipsis(font, state.getTrackName(), maxTextW);
        guiGraphics.drawString(font, title, textX, y + 6, 0xFFFFD700, true);

        String artist = trimWithEllipsis(font, state.getArtistNames(), maxTextW);
        guiGraphics.drawString(font, artist, textX, y + 17, 0xFFFFF066, true);

        // 5. Mini Interactive Controls Row
        int btnY = y + 29;
        int btnW = 14;
        int btnH = 12;

        // Prev Button
        int prevX = textX;
        boolean prevHover = mouseX >= prevX && mouseX <= prevX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        guiGraphics.drawString(font, "⏮", prevX, btnY, prevHover ? 0xFFFFF176 : 0xFFCCCCCC, false);

        // Play / Pause Button
        int playX = prevX + 16;
        boolean playHover = mouseX >= playX && mouseX <= playX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        String playIcon = state.isPlaying() ? "⏸" : "▶";
        guiGraphics.drawString(font, playIcon, playX, btnY, playHover ? 0xFFFFF176 : 0xFFFFD700, false);

        // Next Button
        int nextX = playX + 16;
        boolean nextHover = mouseX >= nextX && mouseX <= nextX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        guiGraphics.drawString(font, "⏭", nextX, btnY, nextHover ? 0xFFFFF176 : 0xFFCCCCCC, false);

        // Like Button (Heart)
        int likeX = nextX + 17;
        boolean likeHover = mouseX >= likeX && mouseX <= likeX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean isLiked = stateManager.isCurrentTrackLiked();
        String heartIcon = isLiked ? "❤" : "♡";
        int heartColor = isLiked ? 0xFFFFD700 : (likeHover ? 0xFFFFF176 : 0xFF888899);
        guiGraphics.drawString(font, heartIcon, likeX, btnY, heartColor, false);

        // 6. Cute Mini Waveform on Right Side
        int waveX = x + w - 38;
        int waveY = y + 39;
        AudioWaveformRenderer.renderWaveform(guiGraphics, waveX, waveY, 32, 12, 6, state.isPlaying(), now, 1.0f);
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
        if (mx >= prevX && mx <= prevX + btnW && my >= btnY && my <= btnY + btnH) {
            stateManager.previous();
            return false;
        }

        int playX = prevX + 16;
        if (mx >= playX && mx <= playX + btnW && my >= btnY && my <= btnY + btnH) {
            stateManager.togglePlayPause();
            return false;
        }

        int nextX = playX + 16;
        if (mx >= nextX && mx <= nextX + btnW && my >= btnY && my <= btnY + btnH) {
            stateManager.next();
            return false;
        }

        int likeX = nextX + 17;
        if (mx >= likeX && mx <= likeX + btnW && my >= btnY && my <= btnY + btnH) {
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
