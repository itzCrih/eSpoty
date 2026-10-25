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
import net.minecraft.resources.ResourceLocation;

public class MainMenuMiniPlayer {
    private static final int CARD_X = 10;
    private static final int CARD_Y = 10;
    private static final int CARD_W = 186;
    private static final int CARD_H = 46;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                ScreenEvents.afterRender(screen).register((s, poseStack, mouseX, mouseY, tickDelta) -> {
                    render(s, new GuiGraphics(poseStack), mouseX, mouseY, tickDelta);
                });
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

        // 1. Multi-Layer Soft Drop Shadow
        guiGraphics.fill(x + 1, y + 2, x + w - 1, y + h + 3, 0x35000000);
        guiGraphics.fill(x + 2, y + 3, x + w - 2, y + h + 4, 0x20000000);

        // 2. Beveled/Chamfered Deep Obsidian Glass Box
        guiGraphics.fill(x + 2, y, x + w - 2, y + h, 0xEE0D0D14);
        guiGraphics.fill(x, y + 2, x + 2, y + h - 2, 0xEE0D0D14);
        guiGraphics.fill(x + w - 2, y + 2, x + w, y + h - 2, 0xEE0D0D14);

        // Top specular gold line
        guiGraphics.fillGradient(x + 3, y, x + w - 3, y + 1, cardHover ? 0xFFFFF59D : 0xDDFFD700, 0x55FFF066);

        // Soft subtle borders
        int borderColor = cardHover ? 0x66FFD700 : 0x35FFD700;
        guiGraphics.fill(x + 2, y + h - 1, x + w - 2, y + h, borderColor);
        guiGraphics.fill(x, y + 2, x + 1, y + h - 2, borderColor);
        guiGraphics.fill(x + w - 1, y + 2, x + w, y + h - 2, borderColor);

        if (!stateManager.isSpotifyRunning() || state == null || state.getTrackName().isEmpty()) {
            // Idle / Offline State
            guiGraphics.drawCenteredString(font, "♫ eSpoty: " + (stateManager.isSpotifyRunning() ? "Paused" : "Offline"), x + (w / 2), y + 12, 0xFFFFD700);
            guiGraphics.drawCenteredString(font, "Click to open GUI (Key: K)", x + (w / 2), y + 26, 0xFFAAAAAA);
            return;
        }

        // 3. Mini Album Art (30x30) with smooth frame
        int artSize = 30;
        int artX = x + 7;
        int artY = y + 8;
        ResourceLocation texture = AlbumArtManager.getInstance().getAlbumTexture(state.getAlbumArtUrl());

        guiGraphics.fill(artX + 1, artY, artX + artSize - 1, artY + artSize, 0xFF14141E);
        guiGraphics.fill(artX, artY + 1, artX + artSize, artY + artSize - 1, 0xFF14141E);
        guiGraphics.fill(artX + 1, artY, artX + artSize - 1, artY + 1, 0x88FFD700);

        if (texture != null) {
            guiGraphics.blit(texture, artX + 1, artY + 1, 0, 0, artSize - 2, artSize - 2, artSize - 2, artSize - 2);
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
        boolean prevHover = mouseX >= prevX - 2 && mouseX <= prevX + btnW + 2 && mouseY >= btnY - 1 && mouseY <= btnY + btnH + 1;
        if (prevHover) guiGraphics.fill(prevX - 1, btnY - 1, prevX + btnW + 1, btnY + btnH + 1, 0x30FFD700);
        guiGraphics.drawString(font, "⏮", prevX, btnY, prevHover ? 0xFFFFF176 : 0xFFCCCCCC, false);

        // Play / Pause Button
        int playX = prevX + 17;
        boolean playHover = mouseX >= playX - 2 && mouseX <= playX + btnW + 2 && mouseY >= btnY - 1 && mouseY <= btnY + btnH + 1;
        if (playHover) guiGraphics.fill(playX - 1, btnY - 1, playX + btnW + 1, btnY + btnH + 1, 0x45FFD700);
        String playIcon = state.isPlaying() ? "❚❚" : "▶";
        guiGraphics.drawString(font, playIcon, playX, btnY, playHover ? 0xFFFFF59D : 0xFFFFD700, false);

        // Next Button
        int nextX = playX + 17;
        boolean nextHover = mouseX >= nextX - 2 && mouseX <= nextX + btnW + 2 && mouseY >= btnY - 1 && mouseY <= btnY + btnH + 1;
        if (nextHover) guiGraphics.fill(nextX - 1, btnY - 1, nextX + btnW + 1, btnY + btnH + 1, 0x30FFD700);
        guiGraphics.drawString(font, "⏭", nextX, btnY, nextHover ? 0xFFFFF176 : 0xFFCCCCCC, false);

        // Like Button (Heart)
        int likeX = nextX + 18;
        boolean likeHover = mouseX >= likeX - 2 && mouseX <= likeX + btnW + 2 && mouseY >= btnY - 1 && mouseY <= btnY + btnH + 1;
        if (likeHover) guiGraphics.fill(likeX - 1, btnY - 1, likeX + btnW + 1, btnY + btnH + 1, 0x30FFD700);
        boolean isLiked = stateManager.isCurrentTrackLiked();
        String heartIcon = isLiked ? "❤" : "♡";
        int heartColor = isLiked ? 0xFFFFD700 : (likeHover ? 0xFFFFF176 : 0xFF888899);
        guiGraphics.drawString(font, heartIcon, likeX, btnY, heartColor, false);

        // 6. Cute Mini Waveform on Right Side
        int waveX = x + w - 38;
        int waveY = y + 39;
        AudioWaveformRenderer.renderWaveform(guiGraphics, waveX, waveY, 32, 12, 6, state.isPlaying(), now, 1.0f);
    }

    private static boolean handleMouseClick(Screen screen, double mouseX, double mouseY, int button) {
        if (button != 0) return true; // Only handle left clicks

        int mx = (int) mouseX;
        int my = (int) mouseY;

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
