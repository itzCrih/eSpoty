package com.espoty.gui.screen;

import com.espoty.config.ModConfig;
import com.espoty.gui.texture.AlbumArtManager;
import com.espoty.gui.widget.AudioWaveformRenderer;
import com.espoty.lyrics.LyricLine;
import com.espoty.lyrics.LyricsManager;
import com.espoty.spotify.SpotifyProcessDetector;
import com.espoty.spotify.SpotifyStateManager;
import com.espoty.spotify.models.PlaybackState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class SpotifyScreen extends Screen {

    public enum Tab {
        PLAYER("🎵 Player"),
        LYRICS("📜 Lyrics"),
        QUEUE("📑 Queue"),
        SETTINGS("⚙ Settings");

        private final String title;
        Tab(String title) { this.title = title; }
        public String getTitle() { return title; }
    }

    private Tab currentTab = Tab.PLAYER;
    private final SpotifyStateManager stateManager;
    private final ModConfig config;

    // Layout
    private int modalX;
    private int modalY;
    private int modalWidth = 380;
    private int modalHeight = 250;

    // Interactive controls state
    private boolean isDraggingSeek = false;
    private float dragSeekRatio = 0f;
    private boolean isDraggingVolume = false;
    private float dragVolumeRatio = 0f;

    // Lyrics & Queue scroll
    private int lyricsScrollY = 0;
    private int queueScrollY = 0;
    private String statusMessage = "";
    private long statusMessageTime = 0;
    private long openTime = System.currentTimeMillis();

    public SpotifyScreen() {
        super(Component.literal("eSpoty"));
        this.stateManager = SpotifyStateManager.getInstance();
        this.config = ModConfig.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        this.openTime = System.currentTimeMillis();
        this.modalWidth = Math.min(390, this.width - 20);
        this.modalHeight = Math.min(270, this.height - 20);
        this.modalX = (this.width - this.modalWidth) / 2;
        this.modalY = (this.height - this.modalHeight) / 2;

        if (currentTab == Tab.QUEUE) {
            stateManager.refreshQueue(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();

        // 1. Dark Vignette Backdrop
        guiGraphics.fill(0, 0, this.width, this.height, 0x99000000);

        // Smooth Scale-in Pop Animation for the modal
        long elapsed = now - openTime;
        float animProgress = Math.min(1.0f, elapsed / 220.0f);
        float scale = 0.88f + (0.12f * (1.0f - (float) Math.pow(1.0f - animProgress, 3)));

        int cx = modalX + (modalWidth / 2);
        int cy = modalY + (modalHeight / 2);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate((float) cx, (float) cy);
        guiGraphics.pose().scale(scale, scale);
        guiGraphics.pose().translate((float) -cx, (float) -cy);

        // 2. Modal Frame with Drop Shadow & Cyber-Gold Border
        renderModernModalFrame(guiGraphics);

        // 3. Header Bar with Tabs
        renderHeader(guiGraphics, mouseX, mouseY, now);

        // 4. Tab Content
        switch (currentTab) {
            case PLAYER -> renderPlayerTab(guiGraphics, mouseX, mouseY, now);
            case LYRICS -> renderLyricsTab(guiGraphics, mouseX, mouseY);
            case QUEUE -> renderQueueTab(guiGraphics, mouseX, mouseY);
            case SETTINGS -> renderSettingsTab(guiGraphics, mouseX, mouseY);
        }

        // 5. Status Feedback Banner
        if (!statusMessage.isEmpty() && now - statusMessageTime < 3500) {
            renderStatusBanner(guiGraphics);
        }

        guiGraphics.pose().popMatrix();

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderModernModalFrame(GuiGraphics guiGraphics) {
        int x = modalX;
        int y = modalY;
        int w = modalWidth;
        int h = modalHeight;

        // Outer ambient shadow
        guiGraphics.fill(x + 4, y + 4, x + w + 4, y + h + 4, 0x66000000);

        // Deep Obsidian Glass Body
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xF40D0D14);

        // Multi-tier Gold Neon Outline
        guiGraphics.fill(x, y, x + w, y + 1, 0x88FFD700);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, 0x88FFD700);
        guiGraphics.fill(x, y, x + 1, y + h, 0x88FFD700);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0x88FFD700);

        // Top Radiant Gold Gradient Accent Line
        guiGraphics.fillGradient(x + 2, y + 1, x + w - 2, y + 3, 0xFFFFD700, 0xFFFFF176);
    }

    private void renderHeader(GuiGraphics guiGraphics, int mouseX, int mouseY, long now) {
        int x = modalX + 14;
        int y = modalY + 10;

        // Title: "⚡ ESPOTY"
        guiGraphics.drawString(this.font, "⚡ ESPOTY", x, y + 2, 0xFFFFD700, true);

        // Live status indicator badge
        boolean pcRunning = stateManager.isSpotifyRunning();
        int dotColor = pcRunning ? 0xFF00E676 : 0xFFFF5252;
        String statusText = pcRunning ? "LIVE PC LINK" : "DISCONNECTED";
        int badgeX = x + this.font.width("⚡ ESPOTY") + 12;

        // Pulsing dot
        guiGraphics.fill(badgeX, y + 4, badgeX + 4, y + 8, dotColor);
        guiGraphics.drawString(this.font, statusText, badgeX + 8, y + 2, pcRunning ? 0xFFFFF066 : 0xFFAAAAAA, true);

        // Close button (Top-right "✕")
        int closeX = modalX + modalWidth - 18;
        boolean closeHover = mouseX >= closeX - 4 && mouseX <= closeX + 12 && mouseY >= y && mouseY <= y + 14;
        guiGraphics.drawString(this.font, "✕", closeX, y + 2, closeHover ? 0xFFFF5252 : 0xFF888899, true);

        // Navigation Tabs Bar
        int tabY = modalY + 30;
        guiGraphics.fill(modalX + 10, tabY + 18, modalX + modalWidth - 10, tabY + 19, 0x33FFD700);

        int tabX = modalX + 14;
        Tab[] tabs = Tab.values();
        for (Tab tab : tabs) {
            boolean active = (tab == currentTab);
            String title = tab.getTitle();
            int tabW = this.font.width(title) + 14;
            int tabH = 16;
            boolean hover = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH;

            if (active) {
                // Gold active pill
                guiGraphics.fill(tabX, tabY, tabX + tabW, tabY + tabH, 0x28FFD700);
                guiGraphics.fill(tabX, tabY, tabX + tabW, tabY + 1, 0x88FFD700);
                guiGraphics.fill(tabX, tabY + tabH - 1, tabX + tabW, tabY + tabH, 0xFFFFD700);
                guiGraphics.drawCenteredString(this.font, title, tabX + (tabW / 2), tabY + 4, 0xFFFFD700);
            } else {
                if (hover) {
                    guiGraphics.fill(tabX, tabY, tabX + tabW, tabY + tabH, 0x18FFFFFF);
                }
                guiGraphics.drawCenteredString(this.font, title, tabX + (tabW / 2), tabY + 4, hover ? 0xFFFFF176 : 0xFF888899);
            }

            tabX += tabW + 6;
        }
    }

    private void renderPlayerTab(GuiGraphics guiGraphics, int mouseX, int mouseY, long now) {
        PlaybackState state = stateManager.getCurrentState();
        int contentX = modalX + 16;
        int contentY = modalY + 56;

        if (state == null) {
            int cx = modalX + (modalWidth / 2);
            guiGraphics.drawCenteredString(this.font, "No active Spotify track detected on your PC.", cx, modalY + 90, 0xFFFFD700);
            guiGraphics.drawCenteredString(this.font, "Start playing any music in Spotify, eSpoty links automatically!", cx, modalY + 108, 0xFFFFF066);

            int btnX = cx - 75;
            int btnY = modalY + 138;
            boolean hover = isMouseOver(mouseX, mouseY, btnX, btnY, 150, 24);
            guiGraphics.fill(btnX, btnY, btnX + 150, btnY + 24, hover ? 0xFFFFF176 : 0xFFFFD700);
            guiGraphics.drawCenteredString(this.font, "Launch Spotify App", cx, btnY + 8, 0xFF101018);
            return;
        }

        // 1. Large High-Res Album Art (74x74)
        int artSize = 74;
        Identifier texture = AlbumArtManager.getInstance().getAlbumTexture(state.getAlbumArtUrl());

        guiGraphics.fill(contentX - 2, contentY - 2, contentX + artSize + 2, contentY + artSize + 2, 0xAAFFD700);
        guiGraphics.fill(contentX - 1, contentY - 1, contentX + artSize + 1, contentY + artSize + 1, 0xFF181824);

        if (texture != null) {
            guiGraphics.blit(texture, contentX, contentY, contentX + artSize, contentY + artSize, 0.0f, 1.0f, 0.0f, 1.0f);
        } else {
            guiGraphics.drawCenteredString(this.font, "♫", contentX + (artSize / 2), contentY + (artSize / 2) - 6, 0xFFFFD700);
        }

        // 2. Track Metadata
        int infoX = contentX + artSize + 14;
        int infoW = modalWidth - (infoX - modalX) - 34; // leave room for Like button

        String title = state.getTrackName();
        if (this.font.width(title) > infoW) {
            title = trimWithEllipsis(title, infoW);
        }
        guiGraphics.drawString(this.font, title, infoX, contentY + 4, 0xFFFFD700, true);

        // Heart "Add to Liked Songs" Button (💛 / ♡)
        int heartX = modalX + modalWidth - 30;
        int heartY = contentY + 3;
        boolean isLiked = stateManager.isCurrentTrackLiked();
        boolean heartHover = isMouseOver(mouseX, mouseY, heartX - 3, heartY - 3, 16, 16);
        String heartIcon = isLiked ? "❤" : "♡";
        int heartColor = isLiked ? 0xFFFFD700 : (heartHover ? 0xFFFFF176 : 0xFF888899);
        guiGraphics.drawString(this.font, heartIcon, heartX, heartY, heartColor, true);

        String artist = state.getArtistNames();
        if (this.font.width(artist) > infoW) {
            artist = trimWithEllipsis(artist, infoW);
        }
        guiGraphics.drawString(this.font, artist, infoX, contentY + 18, 0xFFFFF066, true);

        String album = state.getAlbumName();
        if (!album.isEmpty()) {
            if (this.font.width(album) > infoW) {
                album = trimWithEllipsis(album, infoW);
            }
            guiGraphics.drawString(this.font, album, infoX, contentY + 31, 0xFFAAAAAA, true);
        }

        // Device & Link Tag
        guiGraphics.drawString(this.font, "💻 Spotify Desktop", infoX, contentY + 54, 0xFFE5A910, true);

        // Modern Dynamic Audio Waveform Visualizer
        AudioWaveformRenderer.renderWaveform(guiGraphics, infoX + 112, contentY + 66, 120, 15, 20, state.isPlaying(), now, 1.0f);

        // 3. Thick Modern Seek Bar
        int seekY = contentY + 84;
        int seekW = modalWidth - 32;
        int seekH = 6;
        long progressMs = isDraggingSeek ? (long) (state.getDurationMs() * dragSeekRatio) : stateManager.getInterpolatedProgressMs();
        long durationMs = state.getDurationMs();
        float ratio = durationMs > 0 ? Math.min(1.0f, (float) progressMs / durationMs) : 0f;

        // Background track
        guiGraphics.fill(contentX, seekY, contentX + seekW, seekY + seekH, 0xFF222232);

        // Filled Gold gradient bar
        int fillW = (int) (seekW * ratio);
        if (fillW > 0) {
            guiGraphics.fillGradient(contentX, seekY, contentX + fillW, seekY + seekH, 0xFFFFD700, 0xFFFFF176);
        }

        // Circular knob
        int knobX = contentX + fillW;
        boolean canSeek = state.canSeek();
        int knobColor = canSeek ? 0xFFFFD700 : 0xFF555566;
        guiGraphics.fill(knobX - 3, seekY - 2, knobX + 4, seekY + seekH + 2, knobColor);
        guiGraphics.fill(knobX - 1, seekY, knobX + 2, seekY + seekH, canSeek ? 0xFFFFFFFF : 0xFF888899);

        // Time labels
        String curTime = PlaybackState.formatTime(progressMs);
        String totTime = PlaybackState.formatTime(durationMs);
        guiGraphics.drawString(this.font, curTime, contentX, seekY + 9, 0xFFFFF066, true);
        int totX = contentX + seekW - this.font.width(totTime);
        guiGraphics.drawString(this.font, totTime, totX, seekY + 9, 0xFFAAAAAA, true);

        // 4. Modern Playback Controls Bar
        int controlsY = seekY + 26;
        int centerX = modalX + (modalWidth / 2);
        int btnSize = 22;

        // A) Shuffle button (🔀)
        int shufX = centerX - 90;
        boolean canShuf = state.canShuffle();
        boolean shufHover = isMouseOver(mouseX, mouseY, shufX, controlsY, btnSize, btnSize) && canShuf;
        boolean isShuf = state.isShuffleState();
        int shufColor = canShuf ? (isShuf ? 0xFFFFD700 : 0xFF777788) : 0xFF444455;
        renderCircularButton(guiGraphics, "🔀", shufX, controlsY, btnSize, btnSize, shufHover, shufColor, isShuf && canShuf, canShuf);

        // B) Previous button (⏮)
        int prevX = centerX - 50;
        boolean canPrev = state.canPrev();
        boolean prevHover = isMouseOver(mouseX, mouseY, prevX, controlsY, btnSize, btnSize) && canPrev;
        int prevColor = canPrev ? 0xFFFFF066 : 0xFF444455;
        renderCircularButton(guiGraphics, "⏮", prevX, controlsY, btnSize, btnSize, prevHover, prevColor, false, canPrev);

        // C) Central Play/Pause button (▶ / ❚❚)
        int playX = centerX - 18;
        int playW = 36;
        int playH = 26;
        boolean canPlay = state.canPlayPause();
        boolean playHover = isMouseOver(mouseX, mouseY, playX, controlsY - 2, playW, playH) && canPlay;
        String playIcon = state.isPlaying() ? "❚❚" : "▶";

        if (canPlay) {
            // Pulsing gold halo when music is playing
            if (state.isPlaying()) {
                double pulse = Math.sin(now / 180.0) * 0.5 + 0.5;
                int haloAlpha = (int) (50 + (pulse * 65));
                int haloColor = (haloAlpha << 24) | 0x00FFD700;
                guiGraphics.fill(playX - 3, controlsY - 5, playX + playW + 3, controlsY + playH + 3, haloColor);
            }
            guiGraphics.fill(playX - 1, controlsY - 3, playX + playW + 1, controlsY + playH + 1, playHover ? 0xFFFFF59D : 0xFFFFD700);
            guiGraphics.fill(playX, controlsY - 2, playX + playW, controlsY + playH, playHover ? 0xFFFFE082 : 0xFFFFD700);
            guiGraphics.drawCenteredString(this.font, playIcon, playX + (playW / 2), controlsY + 6, 0xFF101018);
        } else {
            guiGraphics.fill(playX, controlsY - 2, playX + playW, controlsY + playH, 0xFF222230);
            guiGraphics.drawCenteredString(this.font, playIcon, playX + (playW / 2), controlsY + 6, 0xFF555566);
        }

        // D) Next button (⏭)
        int nextX = centerX + 28;
        boolean canNext = state.canNext();
        boolean nextHover = isMouseOver(mouseX, mouseY, nextX, controlsY, btnSize, btnSize) && canNext;
        int nextColor = canNext ? 0xFFFFF066 : 0xFF444455;
        renderCircularButton(guiGraphics, "⏭", nextX, controlsY, btnSize, btnSize, nextHover, nextColor, false, canNext);

        // E) Loop/Repeat button (🔁)
        int repX = centerX + 68;
        boolean canRep = state.canRepeat();
        boolean repHover = isMouseOver(mouseX, mouseY, repX, controlsY, btnSize, btnSize) && canRep;
        String repMode = state.getRepeatState();
        boolean isRep = !"none".equalsIgnoreCase(repMode) && !"off".equalsIgnoreCase(repMode);
        String repIcon = "track".equalsIgnoreCase(repMode) ? "🔂" : "🔁";
        int repColor = canRep ? (isRep ? 0xFFFFD700 : 0xFF777788) : 0xFF444455;
        renderCircularButton(guiGraphics, repIcon, repX, controlsY, btnSize, btnSize, repHover, repColor, isRep && canRep, canRep);

        // 5. Modern Interactive Volume Slider Row
        int volY = controlsY + 32;
        int volBarW = 110;
        int volBarH = 4;
        int volStartX = centerX - 80;
        int volBarX = volStartX + 18;

        int curVol = isDraggingVolume ? (int) (dragVolumeRatio * 100) : (state.getVolumePercent() >= 0 ? state.getVolumePercent() : config.getVolume());
        curVol = Math.max(0, Math.min(100, curVol));

        // Speaker Icon
        String speakerIcon = curVol == 0 ? "🔇" : (curVol < 50 ? "🔉" : "🔊");
        boolean speakerHover = isMouseOver(mouseX, mouseY, volStartX - 4, volY - 3, 16, 14);
        guiGraphics.drawString(this.font, speakerIcon, volStartX, volY - 2, speakerHover ? 0xFFFFF176 : 0xFFFFD700, true);

        // Volume Bar Track
        guiGraphics.fill(volBarX, volY, volBarX + volBarW, volY + volBarH, 0xFF222232);
        float volRatio = curVol / 100.0f;
        int volFillW = (int) (volBarW * volRatio);
        if (volFillW > 0) {
            guiGraphics.fillGradient(volBarX, volY, volBarX + volFillW, volY + volBarH, 0xFFFFD700, 0xFFFFF176);
        }

        // Volume Scrubber Knob
        int volKnobX = volBarX + volFillW;
        guiGraphics.fill(volKnobX - 2, volY - 2, volKnobX + 3, volY + volBarH + 2, 0xFFFFD700);
        guiGraphics.fill(volKnobX - 1, volY - 1, volKnobX + 2, volY + volBarH + 1, 0xFFFFFFFF);

        // Volume Percentage text
        String volText = curVol + "%";
        guiGraphics.drawString(this.font, volText, volBarX + volBarW + 8, volY - 2, 0xFFAAAAAA, true);
    }

    private void renderCircularButton(GuiGraphics guiGraphics, String icon, int x, int y, int w, int h, boolean hover, int color, boolean active, boolean enabled) {
        if (!enabled) {
            guiGraphics.fill(x, y, x + w, y + h, 0x15111116);
            guiGraphics.fill(x, y, x + w, y + 1, 0x22555566);
            guiGraphics.fill(x, y + h - 1, x + w, y + h, 0x22555566);
            guiGraphics.fill(x, y, x + 1, y + h, 0x22555566);
            guiGraphics.fill(x + w - 1, y, x + w, y + h, 0x22555566);
            guiGraphics.drawCenteredString(this.font, icon, x + (w / 2), y + (h / 2) - 4, 0xFF444455);
            return;
        }

        guiGraphics.fill(x, y, x + w, y + h, hover ? 0x40FFD700 : 0x221A1A28);
        guiGraphics.fill(x, y, x + w, y + 1, hover ? 0xFFFFD700 : 0x44FFD700);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, hover ? 0xFFFFD700 : 0x44FFD700);
        guiGraphics.fill(x, y, x + 1, y + h, hover ? 0xFFFFD700 : 0x44FFD700);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, hover ? 0xFFFFD700 : 0x44FFD700);
        guiGraphics.drawCenteredString(this.font, icon, x + (w / 2), y + (h / 2) - 4, color);

        if (active) {
            // Small active indicator dot
            guiGraphics.fill(x + (w / 2) - 1, y + h - 3, x + (w / 2) + 2, y + h - 1, 0xFFFFD700);
        }
    }

    private void renderLyricsTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + 16;
        int contentY = modalY + 54;
        int contentW = modalWidth - 32;
        int contentH = modalHeight - 70;

        LyricsManager.SongLyrics lyrics = LyricsManager.getInstance().getCurrentLyrics();
        PlaybackState state = stateManager.getCurrentState();

        if (state == null) {
            guiGraphics.drawCenteredString(this.font, "No track currently playing.", modalX + (modalWidth / 2), modalY + 110, 0xFFAAAAAA);
            return;
        }

        if (lyrics == null || (lyrics.getLines().isEmpty() && lyrics.getPlainLyrics().isEmpty())) {
            guiGraphics.drawCenteredString(this.font, "Searching lyrics for " + state.getTrackName() + "...",
                    modalX + (modalWidth / 2), modalY + 110, 0xFFFFF066);
            return;
        }

        guiGraphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        long progressMs = stateManager.getInterpolatedProgressMs();
        int activeIdx = lyrics.getActiveIndex(progressMs);

        if (lyrics.isSynced()) {
            List<LyricLine> lines = lyrics.getLines();
            int lineHeight = 18;
            int totalHeight = lines.size() * lineHeight;

            int targetScroll = (activeIdx * lineHeight) - (contentH / 2) + 10;
            targetScroll = Math.max(0, Math.min(totalHeight - contentH, targetScroll));

            lyricsScrollY += (targetScroll - lyricsScrollY) * 0.20f;
            int lineY = contentY - lyricsScrollY;

            for (int i = 0; i < lines.size(); i++) {
                LyricLine line = lines.get(i);
                boolean isActive = (i == activeIdx);

                if (lineY + lineHeight >= contentY && lineY <= contentY + contentH) {
                    if (isActive) {
                        guiGraphics.fill(contentX, lineY - 2, contentX + contentW, lineY + 14, 0x33FFD700);
                        guiGraphics.drawString(this.font, "▶", contentX + 6, lineY + 1, 0xFFFFD700, true);
                        guiGraphics.drawString(this.font, line.getText(), contentX + 18, lineY + 1, 0xFFFFD700, true);
                    } else {
                        int color = (i < activeIdx) ? 0x88999999 : 0xFFCCCCCC;
                        guiGraphics.drawString(this.font, line.getText(), contentX + 18, lineY + 1, color, true);
                    }
                }
                lineY += lineHeight;
            }
        } else {
            String[] plainLines = lyrics.getPlainLyrics().split("\\r?\\n");
            int lineY = contentY - lyricsScrollY;
            for (String pl : plainLines) {
                guiGraphics.drawString(this.font, pl, contentX + 6, lineY, 0xFFFFFDE7, true);
                lineY += 14;
            }
        }

        guiGraphics.disableScissor();
    }

    private void renderQueueTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + 16;
        int contentY = modalY + 54;
        int contentW = modalWidth - 32;
        int contentH = modalHeight - 70;

        PlaybackState curState = stateManager.getCurrentState();

        guiGraphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
        int currentY = contentY - queueScrollY;

        if (curState != null) {
            guiGraphics.drawString(this.font, "CURRENTLY PLAYING", contentX, currentY, 0xFFE5A910, true);
            currentY += 14;

            guiGraphics.drawString(this.font, curState.getTrackName(), contentX + 6, currentY, 0xFFFFD700, true);
            String dur = PlaybackState.formatTime(curState.getDurationMs());
            guiGraphics.drawString(this.font, dur, contentX + contentW - this.font.width(dur), currentY, 0xFFAAAAAA, true);
            currentY += 13;

            guiGraphics.drawString(this.font, curState.getArtistNames() + " • " + curState.getAlbumName(), contentX + 6, currentY, 0xFFFFF066, true);
            currentY += 26;

            guiGraphics.fill(contentX, currentY, contentX + contentW, currentY + 1, 0x44FFD700);
            currentY += 12;
        }

        guiGraphics.drawString(this.font, "DIRECT SPOTIFY QUEUE", contentX, currentY, 0xFFE5A910, true);
        currentY += 14;
        guiGraphics.drawString(this.font, "Playback is linked directly to your Spotify PC application.", contentX + 6, currentY, 0xFFAAAAAA, true);
        currentY += 14;
        guiGraphics.drawString(this.font, "Use Next (⏭) and Prev (⏮) to navigate your Spotify queue!", contentX + 6, currentY, 0xFFFFF176, true);

        // Force Refresh button
        int refBtnX = contentX + 6;
        int refBtnY = currentY + 22;
        boolean refHover = isMouseOver(mouseX, mouseY, refBtnX, refBtnY, 130, 20);
        guiGraphics.fill(refBtnX, refBtnY, refBtnX + 130, refBtnY + 20, refHover ? 0xFFFFF176 : 0xFFFFD700);
        guiGraphics.drawCenteredString(this.font, "🔄 Force Refresh Bridge", refBtnX + 65, refBtnY + 6, 0xFF101018);

        guiGraphics.disableScissor();
    }

    private void renderSettingsTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + 16;
        int contentY = modalY + 52;
        int btnW = 160;
        int btnH = 18;

        // 1. HUD Position
        guiGraphics.drawString(this.font, "HUD Position:", contentX, contentY + 5, 0xFFFFD700, true);
        int posBtnX = contentX + 120;
        boolean posHover = isMouseOver(mouseX, mouseY, posBtnX, contentY, btnW, btnH);
        renderSettingsPill(guiGraphics, config.getHudPosition().getDisplayName(), posBtnX, contentY, btnW, btnH, posHover);

        // 2. HUD Lyrics
        int row2Y = contentY + 24;
        guiGraphics.drawString(this.font, "HUD Lyrics:", contentX, row2Y + 5, 0xFFFFD700, true);
        boolean lyricsHover = isMouseOver(mouseX, mouseY, posBtnX, row2Y, btnW, btnH);
        renderSettingsPill(guiGraphics, config.isHudShowLyrics() ? "✔ Enabled" : "✕ Disabled", posBtnX, row2Y, btnW, btnH, lyricsHover);

        // 3. Compact HUD
        int row3Y = contentY + 48;
        guiGraphics.drawString(this.font, "Compact HUD:", contentX, row3Y + 5, 0xFFFFD700, true);
        boolean compactHover = isMouseOver(mouseX, mouseY, posBtnX, row3Y, btnW, btnH);
        renderSettingsPill(guiGraphics, config.isHudCompact() ? "✔ On" : "✕ Off", posBtnX, row3Y, btnW, btnH, compactHover);

        // 4. Auto-Hide HUD
        int row4Y = contentY + 72;
        guiGraphics.drawString(this.font, "Auto-Hide HUD:", contentX, row4Y + 5, 0xFFFFD700, true);
        boolean autoHideHover = isMouseOver(mouseX, mouseY, posBtnX, row4Y, btnW, btnH);
        renderSettingsPill(guiGraphics, config.isHudAutoHide() ? "✔ Enabled (8s)" : "✕ Disabled", posBtnX, row4Y, btnW, btnH, autoHideHover);

        // 5. MC Sound Ducking & Music Mute
        int row5Y = contentY + 96;
        guiGraphics.drawString(this.font, "MC Sound Ducking:", contentX, row5Y + 5, 0xFFFFD700, true);
        boolean duckHover = isMouseOver(mouseX, mouseY, posBtnX, row5Y, btnW, btnH);
        renderSettingsPill(guiGraphics, config.isAudioDuckingEnabled() ? "✔ Auto-Mute Music" : "✕ Disabled", posBtnX, row5Y, btnW, btnH, duckHover);

        // 6. Force Refresh Bridge Button
        int row6Y = contentY + 128;
        guiGraphics.fill(contentX, row6Y - 4, modalX + modalWidth - 16, row6Y - 3, 0x33FFD700);

        boolean reloadHover = isMouseOver(mouseX, mouseY, contentX, row6Y + 6, 170, 22);
        guiGraphics.fill(contentX, row6Y + 6, contentX + 170, row6Y + 28, reloadHover ? 0xFFFFF176 : 0xFFFFD700);
        guiGraphics.drawCenteredString(this.font, "🔄 Reload eSpoty Bridge", contentX + 85, row6Y + 13, 0xFF101018);

        guiGraphics.drawString(this.font, "Connects directly to Spotify PC.", contentX, row6Y + 34, 0xFFAAAAAA, true);
    }

    private void renderSettingsPill(GuiGraphics guiGraphics, String text, int x, int y, int w, int h, boolean hover) {
        guiGraphics.fill(x, y, x + w, y + h, hover ? 0x40FFD700 : 0x22181826);
        guiGraphics.fill(x, y, x + w, y + 1, hover ? 0xFFFFD700 : 0x44FFD700);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, hover ? 0xFFFFD700 : 0x44FFD700);
        guiGraphics.fill(x, y, x + 1, y + h, hover ? 0xFFFFD700 : 0x44FFD700);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, hover ? 0xFFFFD700 : 0x44FFD700);
        guiGraphics.drawCenteredString(this.font, text, x + (w / 2), y + (h / 2) - 4, hover ? 0xFFFFF59D : 0xFFFFF066);
    }

    private void renderStatusBanner(GuiGraphics guiGraphics) {
        int bannerW = this.font.width(statusMessage) + 24;
        int bannerH = 18;
        int bx = modalX + (modalWidth - bannerW) / 2;
        int by = modalY + modalHeight - 24;

        guiGraphics.fill(bx, by, bx + bannerW, by + bannerH, 0xEE12121A);
        guiGraphics.fill(bx, by, bx + bannerW, by + 1, 0xFFFFD700);
        guiGraphics.drawCenteredString(this.font, statusMessage, bx + (bannerW / 2), by + 5, 0xFFFFD700);
    }

    private void setStatus(String message) {
        this.statusMessage = message;
        this.statusMessageTime = System.currentTimeMillis();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        if (event.button() != 0) return super.mouseClicked(event, isDouble);

        int mx = (int) event.x();
        int my = (int) event.y();

        // Close button
        int closeX = modalX + modalWidth - 18;
        int headerY = modalY + 10;
        if (mx >= closeX - 6 && mx <= closeX + 14 && my >= headerY - 2 && my <= headerY + 16) {
            this.onClose();
            return true;
        }

        // Tab selection
        int tabY = modalY + 30;
        int tabX = modalX + 14;
        for (Tab tab : Tab.values()) {
            int tabW = this.font.width(tab.getTitle()) + 14;
            if (isMouseOver(mx, my, tabX, tabY, tabW, 18)) {
                this.currentTab = tab;
                if (tab == Tab.QUEUE) {
                    stateManager.refreshQueue(null);
                }
                return true;
            }
            tabX += tabW + 6;
        }

        // Tab click handling
        switch (currentTab) {
            case PLAYER -> handlePlayerClick(mx, my);
            case QUEUE -> handleQueueClick(mx, my);
            case SETTINGS -> handleSettingsClick(mx, my);
            default -> {}
        }

        return super.mouseClicked(event, isDouble);
    }

    private void handlePlayerClick(int mx, int my) {
        PlaybackState state = stateManager.getCurrentState();
        if (state == null) {
            int cx = modalX + (modalWidth / 2);
            if (isMouseOver(mx, my, cx - 75, modalY + 138, 150, 24)) {
                SpotifyProcessDetector.launchSpotify();
                setStatus("Launching Spotify...");
            }
            return;
        }

        int contentX = modalX + 16;
        int contentY = modalY + 56;

        // 1. Heart "Add to Liked Songs" Click
        int heartX = modalX + modalWidth - 30;
        int heartY = contentY + 3;
        if (isMouseOver(mx, my, heartX - 4, heartY - 4, 18, 18)) {
            boolean nowLiked = stateManager.toggleCurrentTrackLiked();
            setStatus(nowLiked ? "Added to Liked Songs! 💛" : "Removed from Liked Songs");
            return;
        }

        // 2. Seek bar click / drag
        int seekY = contentY + 84;
        int seekW = modalWidth - 32;
        if (state.canSeek() && isMouseOver(mx, my, contentX, seekY - 4, seekW, 14)) {
            this.isDraggingSeek = true;
            float ratio = Math.max(0f, Math.min(1f, (float) (mx - contentX) / seekW));
            this.dragSeekRatio = ratio;
            long targetMs = (long) (state.getDurationMs() * ratio);
            stateManager.seek(targetMs);
            return;
        }

        // 3. Playback buttons (Respecting Spotify Enabled/Disabled flags)
        int controlsY = seekY + 26;
        int centerX = modalX + (modalWidth / 2);
        int btnSize = 22;

        // A) Shuffle (🔀)
        if (state.canShuffle() && isMouseOver(mx, my, centerX - 90, controlsY, btnSize, btnSize)) {
            stateManager.toggleShuffle();
            setStatus("Toggled Shuffle");
            return;
        }

        // B) Previous (⏮)
        if (state.canPrev() && isMouseOver(mx, my, centerX - 50, controlsY, btnSize, btnSize)) {
            stateManager.previous();
            setStatus("Previous Track");
            return;
        }

        // C) Play/Pause (▶ / ❚❚)
        if (state.canPlayPause() && isMouseOver(mx, my, centerX - 18, controlsY - 2, 36, 26)) {
            stateManager.togglePlayPause();
            setStatus(state.isPlaying() ? "Paused" : "Playing");
            return;
        }

        // D) Next (⏭)
        if (state.canNext() && isMouseOver(mx, my, centerX + 28, controlsY, btnSize, btnSize)) {
            stateManager.next();
            setStatus("Next Track");
            return;
        }

        // E) Loop/Repeat (🔁)
        if (state.canRepeat() && isMouseOver(mx, my, centerX + 68, controlsY, btnSize, btnSize)) {
            stateManager.cycleRepeat();
            setStatus("Cycled Repeat Mode");
            return;
        }

        // 4. Volume Controls
        int volY = controlsY + 32;
        int volBarW = 110;
        int volStartX = centerX - 80;
        int volBarX = volStartX + 18;

        // Speaker Mute Toggle Click
        if (isMouseOver(mx, my, volStartX - 4, volY - 4, 18, 16)) {
            int curVol = state.getVolumePercent() >= 0 ? state.getVolumePercent() : config.getVolume();
            int newVol = (curVol > 0) ? 0 : 75;
            stateManager.setVolume(newVol);
            setStatus(newVol == 0 ? "Muted 🔇" : "Volume: " + newVol + "% 🔊");
            return;
        }

        // Volume Slider Click
        if (isMouseOver(mx, my, volBarX - 2, volY - 4, volBarW + 4, 12)) {
            this.isDraggingVolume = true;
            float ratio = Math.max(0f, Math.min(1f, (float) (mx - volBarX) / volBarW));
            this.dragVolumeRatio = ratio;
            int targetVol = (int) (ratio * 100);
            stateManager.setVolume(targetVol);
            setStatus("Volume: " + targetVol + "%");
            return;
        }
    }

    private void handleQueueClick(int mx, int my) {
        int contentX = modalX + 16;
        int refBtnX = contentX + 6;
        int refBtnY = modalY + 120;
        if (isMouseOver(mx, my, refBtnX, refBtnY, 130, 20)) {
            stateManager.fullReload();
            setStatus("Bridge Reloaded!");
        }
    }

    private void handleSettingsClick(int mx, int my) {
        int contentX = modalX + 16;
        int contentY = modalY + 52;
        int posBtnX = contentX + 120;
        int btnW = 160;
        int btnH = 18;

        // HUD Position
        if (isMouseOver(mx, my, posBtnX, contentY, btnW, btnH)) {
            config.setHudPosition(config.getHudPosition().next());
            return;
        }

        // Lyrics toggle
        int row2Y = contentY + 24;
        if (isMouseOver(mx, my, posBtnX, row2Y, btnW, btnH)) {
            config.setHudShowLyrics(!config.isHudShowLyrics());
            return;
        }

        // Compact toggle
        int row3Y = contentY + 48;
        if (isMouseOver(mx, my, posBtnX, row3Y, btnW, btnH)) {
            config.setHudCompact(!config.isHudCompact());
            return;
        }

        // Auto-Hide toggle
        int row4Y = contentY + 72;
        if (isMouseOver(mx, my, posBtnX, row4Y, btnW, btnH)) {
            config.setHudAutoHide(!config.isHudAutoHide());
            return;
        }

        // MC Sound Ducking toggle
        int row5Y = contentY + 96;
        if (isMouseOver(mx, my, posBtnX, row5Y, btnW, btnH)) {
            config.setAudioDuckingEnabled(!config.isAudioDuckingEnabled());
            return;
        }

        // Reload bridge
        int row6Y = contentY + 128;
        if (isMouseOver(mx, my, contentX, row6Y + 6, 170, 22)) {
            stateManager.fullReload();
            setStatus("Bridge Reloaded!");
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0) {
            int mx = (int) event.x();
            int contentX = modalX + 16;
            int seekW = modalWidth - 32;

            if (isDraggingSeek) {
                this.dragSeekRatio = Math.max(0f, Math.min(1f, (float) (mx - contentX) / seekW));
                return true;
            }

            if (isDraggingVolume) {
                int centerX = modalX + (modalWidth / 2);
                int volBarW = 110;
                int volStartX = centerX - 80;
                int volBarX = volStartX + 18;
                this.dragVolumeRatio = Math.max(0f, Math.min(1f, (float) (mx - volBarX) / volBarW));
                int targetVol = (int) (dragVolumeRatio * 100);
                stateManager.setVolume(targetVol);
                setStatus("Volume: " + targetVol + "%");
                return true;
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            if (isDraggingSeek) {
                PlaybackState state = stateManager.getCurrentState();
                if (state != null) {
                    long targetMs = (long) (state.getDurationMs() * dragSeekRatio);
                    stateManager.seek(targetMs);
                }
                isDraggingSeek = false;
            }
            if (isDraggingVolume) {
                int targetVol = (int) (dragVolumeRatio * 100);
                stateManager.setVolume(targetVol);
                isDraggingVolume = false;
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentTab == Tab.LYRICS) {
            this.lyricsScrollY -= (int) (verticalAmount * 18);
            if (lyricsScrollY < 0) lyricsScrollY = 0;
            return true;
        } else if (currentTab == Tab.QUEUE) {
            this.queueScrollY -= (int) (verticalAmount * 18);
            if (queueScrollY < 0) queueScrollY = 0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_O) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private static boolean isMouseOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private String trimWithEllipsis(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) return text;
        while (this.font.width(text + "...") > maxWidth && text.length() > 3) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }
}
