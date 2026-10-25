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

    private final SpotifyStateManager stateManager;
    private final ModConfig config;
    private Tab currentTab = Tab.PLAYER;

    // Responsive Modal Frame
    private int modalX;
    private int modalY;
    private int modalWidth = 390;
    private int modalHeight = 315;

    // Interactive controls state
    private boolean isDraggingSeek = false;
    private float dragSeekRatio = 0f;
    private boolean isDraggingVolume = false;
    private float dragVolumeRatio = 0f;
    private boolean isDraggingHudOpacity = false;
    private float dragHudOpacityRatio = 0.85f;
    private boolean isDraggingHudScale = false;
    private float dragHudScaleRatio = 0.5f;
    private boolean isDraggingSkyScale = false;
    private float dragSkyScaleRatio = 0.333f;
    private boolean isDraggingSkyLines = false;
    private float dragSkyLinesRatio = 0.166f;

    // Closing & Tab Animation States
    private boolean isClosing = false;
    private long closeStartTime = 0;
    private long tabChangeTime = System.currentTimeMillis();

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
        this.isClosing = false;
        this.tabChangeTime = System.currentTimeMillis();
        this.modalWidth = Math.min(390, this.width - 20);
        this.modalHeight = Math.min(315, this.height - 20);
        this.modalX = (this.width - this.modalWidth) / 2;
        this.modalY = (this.height - this.modalHeight) / 2;

        this.dragSkyLinesRatio = (config.getSkyLyricsLength() - 3) / 22.0f;
        this.dragSkyScaleRatio = (config.getSkyLyricsScalePercent() - 50) / 150.0f;
        this.dragHudOpacityRatio = config.getHudOpacity();
        this.dragHudScaleRatio = (config.getHudScalePercent() - 50) / 100.0f;

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

        if (isClosing) {
            long closeElapsed = now - closeStartTime;
            if (closeElapsed >= 150) {
                super.onClose();
                return;
            }
        }

        // Calculate smooth animation progress & scale
        float animScale;
        float backdropAlpha;

        if (isClosing) {
            long closeElapsed = now - closeStartTime;
            float p = Math.min(1.0f, closeElapsed / 150.0f);
            float ease = p * p;
            animScale = 1.0f - (ease * 0.12f);
            backdropAlpha = (1.0f - p) * 0.60f;
        } else {
            long elapsed = now - openTime;
            float animProgress = Math.min(1.0f, elapsed / 220.0f);
            float ease = 1.0f - (float) Math.pow(1.0f - animProgress, 3);
            animScale = 0.88f + (0.12f * ease);
            backdropAlpha = ease * 0.60f;
        }

        // 1. Smooth Fade Dark Vignette Backdrop
        int alphaHex = Math.max(0, Math.min(255, (int) (backdropAlpha * 255)));
        guiGraphics.fill(0, 0, this.width, this.height, (alphaHex << 24));

        int cx = modalX + (modalWidth / 2);
        int cy = modalY + (modalHeight / 2);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate((float) cx, (float) cy);
        guiGraphics.pose().scale(animScale, animScale);
        guiGraphics.pose().translate((float) -cx, (float) -cy);

        // 2. Modal Frame with Drop Shadow & Cyber-Gold Border
        renderModernModalFrame(guiGraphics);

        // 3. Header Bar with Tabs
        renderHeader(guiGraphics, mouseX, mouseY, now);

        // 4. Tab Content with Snappy Smooth Vertical Glide Transition
        long tabElapsed = now - tabChangeTime;
        float tabProgress = Math.min(1.0f, tabElapsed / 160.0f);
        float tabEase = 1.0f - (float) Math.pow(1.0f - tabProgress, 3);
        float tabGlideY = (1.0f - tabEase) * 6.0f;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(0f, tabGlideY);

        switch (currentTab) {
            case PLAYER -> renderPlayerTab(guiGraphics, mouseX, mouseY, now);
            case LYRICS -> renderLyricsTab(guiGraphics, mouseX, mouseY);
            case QUEUE -> renderQueueTab(guiGraphics, mouseX, mouseY);
            case SETTINGS -> renderSettingsTab(guiGraphics, mouseX, mouseY);
        }

        guiGraphics.pose().popMatrix();

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

        // 1. Multi-tier Ambient Drop Shadow
        guiGraphics.fill(x + 2, y + 4, x + w + 2, y + h + 6, 0x40000000);
        guiGraphics.fill(x + 4, y + 6, x + w + 4, y + h + 8, 0x25000000);

        // 2. Chamfered / Rounded Deep Obsidian Glass Body
        guiGraphics.fill(x + 3, y, x + w - 3, y + h, 0xF20D0D14);
        guiGraphics.fill(x, y + 3, x + 3, y + h - 3, 0xF20D0D14);
        guiGraphics.fill(x + w - 3, y + 3, x + w, y + h - 3, 0xF20D0D14);

        // 3. Top Radiant Gold Gradient Specular Line
        guiGraphics.fillGradient(x + 5, y, x + w - 5, y + 2, 0xFFFFD700, 0xFFFFF59D);

        // 4. Subtle Ambient Borders (never harsh or blocky!)
        int softBorder = 0x35FFD700;
        guiGraphics.fill(x + 3, y + h - 1, x + w - 3, y + h, softBorder);
        guiGraphics.fill(x, y + 3, x + 1, y + h - 3, softBorder);
        guiGraphics.fill(x + w - 1, y + 3, x + w, y + h - 3, softBorder);

        // Delicate corner connectors
        int cornerColor = 0x22FFD700;
        guiGraphics.fill(x + 1, y + 1, x + 3, y + 3, cornerColor);
        guiGraphics.fill(x + w - 3, y + 1, x + w - 1, y + 3, cornerColor);
        guiGraphics.fill(x + 1, y + h - 3, x + 3, y + h - 1, cornerColor);
        guiGraphics.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, cornerColor);
    }

    private void renderHeader(GuiGraphics guiGraphics, int mouseX, int mouseY, long now) {
        int x = modalX + 14;
        int y = modalY + 10;

        // Title: "⚡ ESPOTY"
        guiGraphics.drawString(this.font, "⚡ ESPOTY", x, y + 2, 0xFFFFD700, true);

        // Live status indicator pill badge
        boolean pcRunning = stateManager.isSpotifyRunning();
        int dotColor = pcRunning ? 0xFF00E676 : 0xFFFF5252;
        String statusText = pcRunning ? "LIVE PC LINK" : "DISCONNECTED";
        int badgeX = x + this.font.width("⚡ ESPOTY") + 12;

        guiGraphics.fill(badgeX, y + 1, badgeX + this.font.width(statusText) + 16, y + 13, 0x22FFFFFF);
        guiGraphics.fill(badgeX + 4, y + 4, badgeX + 8, y + 8, dotColor);
        guiGraphics.drawString(this.font, statusText, badgeX + 11, y + 3, pcRunning ? 0xFFFFF066 : 0xFFAAAAAA, false);

        // Close button (Top-right "✕")
        int closeX = modalX + modalWidth - 18;
        boolean closeHover = mouseX >= closeX - 4 && mouseX <= closeX + 12 && mouseY >= y && mouseY <= y + 14;
        guiGraphics.drawString(this.font, "✕", closeX, y + 2, closeHover ? 0xFFFF5252 : 0xFF888899, true);

        // Segmented Tab Bar Container
        int tabY = modalY + 28;
        int tabBarW = modalWidth - 28;
        guiGraphics.fill(modalX + 14, tabY, modalX + 14 + tabBarW, tabY + 20, 0x33000000);
        guiGraphics.fill(modalX + 14, tabY + 19, modalX + 14 + tabBarW, tabY + 20, 0x18FFFFFF);

        int tabX = modalX + 16;
        Tab[] tabs = Tab.values();
        for (Tab tab : tabs) {
            boolean active = (tab == currentTab);
            String title = tab.getTitle();
            int tabW = this.font.width(title) + 16;
            int tabH = 18;
            boolean hover = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH;

            if (active) {
                // Illuminated Gold Tab Capsule
                guiGraphics.fill(tabX + 1, tabY + 1, tabX + tabW - 1, tabY + tabH - 1, 0x35FFD700);
                guiGraphics.fill(tabX + 2, tabY + tabH - 2, tabX + tabW - 2, tabY + tabH, 0xFFFFD700);
                guiGraphics.drawCenteredString(this.font, title, tabX + (tabW / 2), tabY + 5, 0xFFFFD700);
            } else {
                if (hover) {
                    guiGraphics.fill(tabX + 1, tabY + 1, tabX + tabW - 1, tabY + tabH - 1, 0x15FFFFFF);
                }
                guiGraphics.drawCenteredString(this.font, title, tabX + (tabW / 2), tabY + 5, hover ? 0xFFFFF59D : 0xFFAAAAAA);
            }

            tabX += tabW + 4;
        }
    }

    private void renderPlayerTab(GuiGraphics guiGraphics, int mouseX, int mouseY, long now) {
        int contentX = modalX + 16;
        int contentY = modalY + 54;

        PlaybackState state = stateManager.getCurrentState();

        if (state == null) {
            int cx = modalX + (modalWidth / 2);
            guiGraphics.drawCenteredString(this.font, "No active Spotify track detected.", cx, modalY + 95, 0xFFAAAAAA);
            guiGraphics.drawCenteredString(this.font, "Start playing music in your Spotify PC app!", cx, modalY + 110, 0xFFFFF066);

            int btnX = cx - 75;
            int btnY = modalY + 138;
            boolean hover = isMouseOver(mouseX, mouseY, btnX, btnY, 150, 24);
            guiGraphics.fill(btnX, btnY, btnX + 150, btnY + 24, hover ? 0xFFFFF176 : 0xFFFFD700);
            guiGraphics.drawCenteredString(this.font, "Launch Spotify App", cx, btnY + 8, 0xFF101018);
            return;
        }

        // 1. High-Res Album Art (72x72) with smooth chamfered frame
        int artSize = 72;
        Identifier texture = AlbumArtManager.getInstance().getAlbumTexture(state.getAlbumArtUrl());

        guiGraphics.fill(contentX + 1, contentY, contentX + artSize - 1, contentY + artSize, 0xFF181824);
        guiGraphics.fill(contentX, contentY + 1, contentX + artSize, contentY + artSize - 1, 0xFF181824);
        guiGraphics.fill(contentX + 1, contentY, contentX + artSize - 1, contentY + 1, 0x88FFD700);
        guiGraphics.fill(contentX + 1, contentY + artSize - 1, contentX + artSize - 1, contentY + artSize, 0x44FFD700);

        if (texture != null) {
            guiGraphics.blit(texture, contentX + 1, contentY + 1, contentX + artSize - 1, contentY + artSize - 1, 0.0f, 1.0f, 0.0f, 1.0f);
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

        // Device Tag
        guiGraphics.drawString(this.font, "💻 Spotify Desktop", infoX, contentY + 54, 0xFFE5A910, true);

        // Modern Dynamic Audio Waveform Visualizer
        AudioWaveformRenderer.renderWaveform(guiGraphics, infoX + 112, contentY + 66, 120, 15, 20, state.isPlaying(), now, 1.0f);

        // 3. Thick Modern Seek Bar
        int seekY = contentY + 84;
        int seekW = modalWidth - 32;
        int seekH = 4;
        long progressMs = isDraggingSeek ? (long) (state.getDurationMs() * dragSeekRatio) : stateManager.getInterpolatedProgressMs();
        long durationMs = state.getDurationMs();
        float ratio = durationMs > 0 ? Math.min(1.0f, (float) progressMs / durationMs) : 0f;

        // Background track (subtle translucent slate)
        guiGraphics.fill(contentX, seekY, contentX + seekW, seekY + seekH, 0x35FFFFFF);

        // Filled Gold gradient bar
        int fillW = (int) (seekW * ratio);
        if (fillW > 0) {
            guiGraphics.fillGradient(contentX, seekY, contentX + fillW, seekY + seekH, 0xFFFFD700, 0xFFFFF176);
        }

        // Smooth Circular Knob
        int knobX = contentX + fillW;
        boolean canSeek = state.canSeek();
        int knobColor = canSeek ? 0xFFFFD700 : 0xFF555566;
        guiGraphics.fill(knobX - 3, seekY - 2, knobX + 4, seekY + seekH + 2, knobColor);
        guiGraphics.fill(knobX - 1, seekY, knobX + 2, seekY + seekH, canSeek ? 0xFFFFFFFF : 0xFF888899);

        // Time labels
        String curTime = PlaybackState.formatTime(progressMs);
        String totTime = PlaybackState.formatTime(durationMs);
        guiGraphics.drawString(this.font, curTime, contentX, seekY + 8, 0xFFFFF066, true);
        int totX = contentX + seekW - this.font.width(totTime);
        guiGraphics.drawString(this.font, totTime, totX, seekY + 8, 0xFFAAAAAA, true);

        // 4. Ultra-Modern Playback Controls Bar
        int controlsY = seekY + 25;
        int centerX = modalX + (modalWidth / 2);
        int btnSize = 22;

        // A) Shuffle button (🔀)
        int shufX = centerX - 90;
        boolean canShuf = state.canShuffle();
        boolean shufHover = isMouseOver(mouseX, mouseY, shufX, controlsY, btnSize, btnSize) && canShuf;
        boolean isShuf = state.isShuffleState();
        int shufColor = canShuf ? (isShuf ? 0xFFFFD700 : 0xFFCCCCCC) : 0xFF555566;
        renderModernIconButton(guiGraphics, "🔀", shufX, controlsY, btnSize, btnSize, shufHover, shufColor, isShuf && canShuf, canShuf);

        // B) Previous button (⏮)
        int prevX = centerX - 50;
        boolean canPrev = state.canPrev();
        boolean prevHover = isMouseOver(mouseX, mouseY, prevX, controlsY, btnSize, btnSize) && canPrev;
        int prevColor = canPrev ? 0xFFFFF066 : 0xFF555566;
        renderModernIconButton(guiGraphics, "⏮", prevX, controlsY, btnSize, btnSize, prevHover, prevColor, false, canPrev);

        // C) Central Play/Pause button (Illuminated Gold Disc)
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
                int haloAlpha = (int) (40 + (pulse * 55));
                int haloColor = (haloAlpha << 24) | 0x00FFD700;
                guiGraphics.fill(playX - 3, controlsY - 5, playX + playW + 3, controlsY + playH + 3, haloColor);
            }
            // Rounded Disc
            guiGraphics.fill(playX + 2, controlsY - 3, playX + playW - 2, controlsY + playH + 1, playHover ? 0xFFFFF9C4 : 0xFFFFD700);
            guiGraphics.fill(playX, controlsY - 1, playX + playW, controlsY + playH - 1, playHover ? 0xFFFFF9C4 : 0xFFFFD700);
            guiGraphics.fillGradient(playX + 1, controlsY - 2, playX + playW - 1, controlsY + playH,
                    playHover ? 0xFFFFFDE7 : 0xFFFFD700,
                    playHover ? 0xFFFFD700 : 0xFFFFA000);

            guiGraphics.drawCenteredString(this.font, playIcon, playX + (playW / 2), controlsY + 6, 0xFF101018);
        } else {
            guiGraphics.fill(playX + 2, controlsY - 2, playX + playW - 2, controlsY + playH, 0xFF222230);
            guiGraphics.fill(playX, controlsY, playX + playW, controlsY + playH - 2, 0xFF222230);
            guiGraphics.drawCenteredString(this.font, playIcon, playX + (playW / 2), controlsY + 6, 0xFF555566);
        }

        // D) Next button (⏭)
        int nextX = centerX + 28;
        boolean canNext = state.canNext();
        boolean nextHover = isMouseOver(mouseX, mouseY, nextX, controlsY, btnSize, btnSize) && canNext;
        int nextColor = canNext ? 0xFFFFF066 : 0xFF555566;
        renderModernIconButton(guiGraphics, "⏭", nextX, controlsY, btnSize, btnSize, nextHover, nextColor, false, canNext);

        // E) Loop/Repeat button (🔁)
        int repX = centerX + 68;
        boolean canRep = state.canRepeat();
        boolean repHover = isMouseOver(mouseX, mouseY, repX, controlsY, btnSize, btnSize) && canRep;
        String repMode = state.getRepeatState();
        boolean isRep = !"none".equalsIgnoreCase(repMode) && !"off".equalsIgnoreCase(repMode);
        String repIcon = "track".equalsIgnoreCase(repMode) ? "🔂" : "🔁";
        int repColor = canRep ? (isRep ? 0xFFFFD700 : 0xFFCCCCCC) : 0xFF555566;
        renderModernIconButton(guiGraphics, repIcon, repX, controlsY, btnSize, btnSize, repHover, repColor, isRep && canRep, canRep);

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
        guiGraphics.fill(volBarX, volY, volBarX + volBarW, volY + volBarH, 0x35FFFFFF);
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

    private void renderModernIconButton(GuiGraphics guiGraphics, String icon, int x, int y, int w, int h, boolean hover, int color, boolean active, boolean enabled) {
        if (!enabled) {
            guiGraphics.drawCenteredString(this.font, icon, x + (w / 2), y + (h / 2) - 4, 0xFF444455);
            return;
        }

        if (hover) {
            // Soft rounded highlight disc (no harsh blocky borders!)
            guiGraphics.fill(x + 1, y, x + w - 1, y + h, 0x30FFD700);
            guiGraphics.fill(x, y + 1, x + w, y + h - 1, 0x30FFD700);
        } else {
            guiGraphics.fill(x + 1, y, x + w - 1, y + h, 0x14FFFFFF);
            guiGraphics.fill(x, y + 1, x + w, y + h - 1, 0x14FFFFFF);
        }

        guiGraphics.drawCenteredString(this.font, icon, x + (w / 2), y + (h / 2) - 4, hover ? 0xFFFFF066 : color);

        if (active) {
            // Small glowing neon dot beneath the icon
            guiGraphics.fill(x + (w / 2) - 1, y + h - 2, x + (w / 2) + 2, y + h, 0xFFFFD700);
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
                        // Soft glowing capsule highlight
                        guiGraphics.fill(contentX, lineY - 2, contentX + contentW, lineY + 14, 0x25FFD700);
                        guiGraphics.fill(contentX, lineY - 2, contentX + 2, lineY + 14, 0xFFFFD700);
                        guiGraphics.drawString(this.font, "♪", contentX + 6, lineY + 1, 0xFFFFD700, true);
                        guiGraphics.drawString(this.font, line.getText(), contentX + 18, lineY + 1, 0xFFFFFDE7, true);
                    } else {
                        int color = (i < activeIdx) ? 0x66AAAAAA : 0xFFCCCCCC;
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

            // Mini Card with subtle border
            guiGraphics.fill(contentX, currentY, contentX + contentW, currentY + 34, 0x22181826);
            guiGraphics.fill(contentX, currentY, contentX + 2, currentY + 34, 0xFFFFD700);

            guiGraphics.drawString(this.font, curState.getTrackName(), contentX + 8, currentY + 6, 0xFFFFD700, true);
            String dur = PlaybackState.formatTime(curState.getDurationMs());
            guiGraphics.drawString(this.font, dur, contentX + contentW - this.font.width(dur) - 8, currentY + 6, 0xFFAAAAAA, true);

            guiGraphics.drawString(this.font, curState.getArtistNames() + " • " + curState.getAlbumName(), contentX + 8, currentY + 19, 0xFFFFF066, true);
            currentY += 46;
        }

        guiGraphics.drawString(this.font, "DIRECT SPOTIFY QUEUE", contentX, currentY, 0xFFE5A910, true);
        currentY += 14;
        guiGraphics.drawString(this.font, "Playback is linked directly to your Spotify PC application.", contentX + 6, currentY, 0xFFAAAAAA, true);
        currentY += 14;
        guiGraphics.drawString(this.font, "Use Next (⏭) and Prev (⏮) to navigate your Spotify queue!", contentX + 6, currentY, 0xFFFFF176, true);

        // Force Refresh button (Modern glassmorphic button)
        int refBtnX = contentX + 6;
        int refBtnY = currentY + 22;
        boolean refHover = isMouseOver(mouseX, mouseY, refBtnX, refBtnY, 150, 22);
        guiGraphics.fill(refBtnX, refBtnY, refBtnX + 150, refBtnY + 22, refHover ? 0x40FFD700 : 0x22FFFFFF);
        guiGraphics.fill(refBtnX, refBtnY, refBtnX + 150, refBtnY + 1, refHover ? 0xFFFFD700 : 0x66FFD700);
        guiGraphics.fill(refBtnX, refBtnY + 21, refBtnX + 150, refBtnY + 22, refHover ? 0xFFFFD700 : 0x66FFD700);
        guiGraphics.drawCenteredString(this.font, "🔄 Force Refresh Bridge", refBtnX + 75, refBtnY + 7, refHover ? 0xFFFFF59D : 0xFFFFD700);

        guiGraphics.disableScissor();
    }

    private void renderSettingsTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + 16;
        int contentY = modalY + 52;
        int btnW = 160;
        int btnH = 16;
        int posBtnX = contentX + 120;

        // 1. HUD Position (Row 1)
        guiGraphics.drawString(this.font, "HUD Position:", contentX, contentY + 4, 0xFFFFD700, true);
        boolean posHover = isMouseOver(mouseX, mouseY, posBtnX, contentY, btnW, btnH);
        renderSettingsPill(guiGraphics, config.getHudPosition().getDisplayName(), posBtnX, contentY, btnW, btnH, posHover);

        // 2. HUD Opacity Slider (Row 2: 0% to 100%)
        int row2Y = contentY + 19;
        guiGraphics.drawString(this.font, "HUD Opacity:", contentX, row2Y + 4, 0xFFFFD700, true);
        int opacPercent = isDraggingHudOpacity ? Math.round(dragHudOpacityRatio * 100) : config.getHudOpacityPercent();

        // Opacity Track
        guiGraphics.fill(posBtnX, row2Y + 6, posBtnX + btnW, row2Y + 10, 0x35FFFFFF);
        float opacRatio = opacPercent / 100.0f;
        int opacFillW = (int) (btnW * opacRatio);
        if (opacFillW > 0) {
            guiGraphics.fillGradient(posBtnX, row2Y + 6, posBtnX + opacFillW, row2Y + 10, 0xFFFFD700, 0xFFFFF176);
        }
        int opacKnobX = posBtnX + opacFillW;
        guiGraphics.fill(opacKnobX - 2, row2Y + 4, opacKnobX + 3, row2Y + 12, 0xFFFFD700);
        guiGraphics.fill(opacKnobX - 1, row2Y + 5, opacKnobX + 2, row2Y + 11, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, opacPercent + "%", posBtnX + btnW + 6, row2Y + 4, 0xFFAAAAAA, true);

        // 3. HUD Scale Slider (Row 3: 50% to 150%)
        int row3Y = contentY + 38;
        guiGraphics.drawString(this.font, "HUD Scale:", contentX, row3Y + 4, 0xFFFFD700, true);
        int scalePercent = isDraggingHudScale ? Math.round(50 + dragHudScaleRatio * 100) : config.getHudScalePercent();

        // Scale Track
        guiGraphics.fill(posBtnX, row3Y + 6, posBtnX + btnW, row3Y + 10, 0x35FFFFFF);
        float scaleRatio = (scalePercent - 50) / 100.0f;
        int scaleFillW = (int) (btnW * Math.max(0f, Math.min(1f, scaleRatio)));
        if (scaleFillW > 0) {
            guiGraphics.fillGradient(posBtnX, row3Y + 6, posBtnX + scaleFillW, row3Y + 10, 0xFFFFD700, 0xFFFFF176);
        }
        int scaleKnobX = posBtnX + scaleFillW;
        guiGraphics.fill(scaleKnobX - 2, row3Y + 4, scaleKnobX + 3, row3Y + 12, 0xFFFFD700);
        guiGraphics.fill(scaleKnobX - 1, row3Y + 5, scaleKnobX + 2, row3Y + 11, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, scalePercent + "%", posBtnX + btnW + 6, row3Y + 4, 0xFFAAAAAA, true);

        // 4. HUD Lyrics Toggle (Row 4)
        int row4Y = contentY + 57;
        guiGraphics.drawString(this.font, "HUD Lyrics:", contentX, row4Y + 4, 0xFFFFD700, true);
        boolean lyricsHover = isMouseOver(mouseX, mouseY, posBtnX, row4Y, btnW, btnH);
        renderSettingsToggle(guiGraphics, config.isHudShowLyrics(), posBtnX, row4Y, btnW, btnH, lyricsHover);

        // 5. Celestial Sky Lyrics Toggle (Row 5)
        int row5Y = contentY + 76;
        guiGraphics.drawString(this.font, "Sky Lyrics (Cielo):", contentX, row5Y + 4, 0xFFFFD700, true);
        boolean skyHover = isMouseOver(mouseX, mouseY, posBtnX, row5Y, btnW, btnH);
        renderSettingsToggle(guiGraphics, config.isSkyLyricsEnabled(), posBtnX, row5Y, btnW, btnH, skyHover);

        // 6. Sky Lyrics Scale Slider (Row 6: 50% to 200%)
        int row6Y = contentY + 95;
        guiGraphics.drawString(this.font, "Sky Lyrics Scale:", contentX, row6Y + 4, 0xFFFFD700, true);
        int skyScalePercent = isDraggingSkyScale ? Math.round(50 + dragSkyScaleRatio * 150) : config.getSkyLyricsScalePercent();

        // Sky Scale Track
        guiGraphics.fill(posBtnX, row6Y + 6, posBtnX + btnW, row6Y + 10, 0x35FFFFFF);
        float skyScaleRatio = (skyScalePercent - 50) / 150.0f;
        int skyScaleFillW = (int) (btnW * Math.max(0f, Math.min(1f, skyScaleRatio)));
        if (skyScaleFillW > 0) {
            guiGraphics.fillGradient(posBtnX, row6Y + 6, posBtnX + skyScaleFillW, row6Y + 10, 0xFFFFD700, 0xFFFFF176);
        }
        int skyScaleKnobX = posBtnX + skyScaleFillW;
        guiGraphics.fill(skyScaleKnobX - 2, row6Y + 4, skyScaleKnobX + 3, row6Y + 12, 0xFFFFD700);
        guiGraphics.fill(skyScaleKnobX - 1, row6Y + 5, skyScaleKnobX + 2, row6Y + 11, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, skyScalePercent + "%", posBtnX + btnW + 6, row6Y + 4, 0xFFAAAAAA, true);

        // 7. Sky Lyrics Lines Slider (Row 7: 3 to 25 lines)
        int row7Y = contentY + 114;
        guiGraphics.drawString(this.font, "Sky Lyrics Lines:", contentX, row7Y + 4, 0xFFFFD700, true);
        int skyLines = isDraggingSkyLines ? Math.round(3 + dragSkyLinesRatio * 22) : config.getSkyLyricsLength();

        // Sky Lines Track
        guiGraphics.fill(posBtnX, row7Y + 6, posBtnX + btnW, row7Y + 10, 0x35FFFFFF);
        float skyLinesRatio = (skyLines - 3) / 22.0f;
        int skyLinesFillW = (int) (btnW * Math.max(0f, Math.min(1f, skyLinesRatio)));
        if (skyLinesFillW > 0) {
            guiGraphics.fillGradient(posBtnX, row7Y + 6, posBtnX + skyLinesFillW, row7Y + 10, 0xFFFFD700, 0xFFFFF176);
        }
        int skyLinesKnobX = posBtnX + skyLinesFillW;
        guiGraphics.fill(skyLinesKnobX - 2, row7Y + 4, skyLinesKnobX + 3, row7Y + 12, 0xFFFFD700);
        guiGraphics.fill(skyLinesKnobX - 1, row7Y + 5, skyLinesKnobX + 2, row7Y + 11, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, skyLines + " lines", posBtnX + btnW + 6, row7Y + 4, 0xFFAAAAAA, true);

        // 8. Compact HUD Toggle (Row 8)
        int row8Y = contentY + 133;
        guiGraphics.drawString(this.font, "Compact HUD:", contentX, row8Y + 4, 0xFFFFD700, true);
        boolean compactHover = isMouseOver(mouseX, mouseY, posBtnX, row8Y, btnW, btnH);
        renderSettingsToggle(guiGraphics, config.isHudCompact(), posBtnX, row8Y, btnW, btnH, compactHover);

        // 9. Auto-Hide HUD Toggle (Row 9)
        int row9Y = contentY + 152;
        guiGraphics.drawString(this.font, "Auto-Hide HUD:", contentX, row9Y + 4, 0xFFFFD700, true);
        boolean autoHideHover = isMouseOver(mouseX, mouseY, posBtnX, row9Y, btnW, btnH);
        renderSettingsToggle(guiGraphics, config.isHudAutoHide(), posBtnX, row9Y, btnW, btnH, autoHideHover);

        // 10. MC Sound Ducking & Music Mute (Row 10)
        int row10Y = contentY + 171;
        guiGraphics.drawString(this.font, "MC Sound Ducking:", contentX, row10Y + 4, 0xFFFFD700, true);
        boolean duckHover = isMouseOver(mouseX, mouseY, posBtnX, row10Y, btnW, btnH);
        renderSettingsToggle(guiGraphics, config.isAudioDuckingEnabled(), posBtnX, row10Y, btnW, btnH, duckHover);

        // 11. Actions: Refresh Bridge & GitHub Update Checker (Row 11)
        int row11Y = contentY + 193;
        guiGraphics.fill(contentX, row11Y - 3, modalX + modalWidth - 16, row11Y - 2, 0x18FFFFFF);

        int btnWidth = 174;
        int btn1X = contentX;
        int btn2X = contentX + 184;

        boolean reloadHover = isMouseOver(mouseX, mouseY, btn1X, row11Y + 2, btnWidth, 20);
        guiGraphics.fill(btn1X, row11Y + 2, btn1X + btnWidth, row11Y + 22, reloadHover ? 0x40FFD700 : 0x22FFFFFF);
        guiGraphics.fill(btn1X, row11Y + 2, btn1X + btnWidth, row11Y + 3, reloadHover ? 0xFFFFD700 : 0x66FFD700);
        guiGraphics.fill(btn1X, row11Y + 21, btn1X + btnWidth, row11Y + 22, reloadHover ? 0xFFFFD700 : 0x66FFD700);
        guiGraphics.drawCenteredString(this.font, "Reload Bridge", btn1X + (btnWidth / 2), row11Y + 8, reloadHover ? 0xFFFFF59D : 0xFFFFD700);

        com.espoty.update.UpdateChecker updateChecker = com.espoty.update.UpdateChecker.getInstance();
        boolean hasUpdate = updateChecker.isUpdateAvailable();
        boolean updateHover = isMouseOver(mouseX, mouseY, btn2X, row11Y + 2, btnWidth, 20);

        int updateBg = hasUpdate ? (updateHover ? 0x4400E676 : 0x2800E676) : (updateHover ? 0x40FFD700 : 0x22FFFFFF);
        int updateBorder = hasUpdate ? (updateHover ? 0xFF00E676 : 0xAA00E676) : (updateHover ? 0xFFFFD700 : 0x66FFD700);
        int updateTextColor = hasUpdate ? (updateHover ? 0xFFB9F6CA : 0xFF00E676) : (updateHover ? 0xFFFFF59D : 0xFFFFD700);

        guiGraphics.fill(btn2X, row11Y + 2, btn2X + btnWidth, row11Y + 22, updateBg);
        guiGraphics.fill(btn2X, row11Y + 2, btn2X + btnWidth, row11Y + 3, updateBorder);
        guiGraphics.fill(btn2X, row11Y + 21, btn2X + btnWidth, row11Y + 22, updateBorder);

        String updateLabel = hasUpdate ? "Update v" + updateChecker.getLatestVersion() + " Available!" : "Check for Updates";
        guiGraphics.drawCenteredString(this.font, updateLabel, btn2X + (btnWidth / 2), row11Y + 8, updateTextColor);

        // 12. Author credits & website (itzCrih • itzcrih.it) (Row 12)
        String credit = "§7eSpoty v" + com.espoty.update.UpdateChecker.getCurrentVersion() + " by §6itzCrih §8• §ehttps://itzcrih.it" + (hasUpdate ? " §a(New v" + updateChecker.getLatestVersion() + "!)" : "");
        boolean creditHover = isMouseOver(mouseX, mouseY, contentX, row11Y + 25, 300, 14);
        guiGraphics.drawString(this.font, credit, contentX, row11Y + 27, creditHover ? 0xFFFFFFFF : 0xFFAAAAAA, false);
    }

    private void renderSettingsPill(GuiGraphics guiGraphics, String text, int x, int y, int w, int h, boolean hover) {
        guiGraphics.fill(x + 1, y, x + w - 1, y + h, hover ? 0x35FFD700 : 0x1AFFFFFF);
        guiGraphics.fill(x, y + 1, x + w, y + h - 1, hover ? 0x35FFD700 : 0x1AFFFFFF);
        guiGraphics.fill(x + 2, y, x + w - 2, y + 1, hover ? 0xAAFFD700 : 0x33FFD700);
        guiGraphics.fill(x + 2, y + h - 1, x + w - 2, y + h, hover ? 0xAAFFD700 : 0x33FFD700);
        guiGraphics.drawCenteredString(this.font, text, x + (w / 2), y + (h / 2) - 4, hover ? 0xFFFFF59D : 0xFFFFF066);
    }

    private void renderSettingsToggle(GuiGraphics guiGraphics, boolean enabled, int x, int y, int w, int h, boolean hover) {
        int bg = enabled ? (hover ? 0x4000E676 : 0x2200E676) : (hover ? 0x35FF5252 : 0x1AFFFFFF);
        guiGraphics.fill(x + 1, y, x + w - 1, y + h, bg);
        guiGraphics.fill(x, y + 1, x + w, y + h - 1, bg);

        int border = enabled ? 0x8800E676 : 0x33FFFFFF;
        guiGraphics.fill(x + 2, y, x + w - 2, y + 1, border);
        guiGraphics.fill(x + 2, y + h - 1, x + w - 2, y + h, border);

        String text = enabled ? "● Enabled" : "○ Disabled";
        int textColor = enabled ? 0xFF00E676 : 0xFFAAAAAA;
        guiGraphics.drawCenteredString(this.font, text, x + (w / 2), y + (h / 2) - 4, textColor);
    }

    private void renderStatusBanner(GuiGraphics guiGraphics) {
        int bannerW = this.font.width(statusMessage) + 24;
        int bannerH = 18;
        int bx = modalX + (modalWidth - bannerW) / 2;
        int by = modalY + modalHeight - 24;

        guiGraphics.fill(bx + 1, by, bx + bannerW - 1, by + bannerH, 0xEE11111A);
        guiGraphics.fill(bx, by + 1, bx + bannerW, by + bannerH - 1, 0xEE11111A);
        guiGraphics.fill(bx + 2, by, bx + bannerW - 2, by + 1, 0xFFFFD700);
        guiGraphics.drawCenteredString(this.font, statusMessage, bx + (bannerW / 2), by + 5, 0xFFFFFDE7);
    }

    private void setStatus(String msg) {
        this.statusMessage = msg;
        this.statusMessageTime = System.currentTimeMillis();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }

        int mx = (int) event.x();
        int my = (int) event.y();

        // 1. Close Button (✕)
        int closeX = modalX + modalWidth - 18;
        int closeY = modalY + 10;
        if (isMouseOver(mx, my, closeX - 4, closeY, 16, 14)) {
            this.startCloseAnimation();
            return true;
        }

        // 2. Navigation Tabs
        int tabY = modalY + 28;
        int tabX = modalX + 16;
        for (Tab tab : Tab.values()) {
            int tabW = this.font.width(tab.getTitle()) + 16;
            int tabH = 18;
            if (isMouseOver(mx, my, tabX, tabY, tabW, tabH)) {
                if (this.currentTab != tab) {
                    this.currentTab = tab;
                    this.tabChangeTime = System.currentTimeMillis();
                    if (tab == Tab.QUEUE) {
                        stateManager.refreshQueue(null);
                    }
                }
                return true;
            }
            tabX += tabW + 4;
        }

        // 3. Tab Interactions
        switch (currentTab) {
            case PLAYER -> handlePlayerClick(mx, my);
            case QUEUE -> handleQueueClick(mx, my);
            case SETTINGS -> handleSettingsClick(mx, my);
            default -> {}
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void handlePlayerClick(int mx, int my) {
        PlaybackState state = stateManager.getCurrentState();
        int contentX = modalX + 16;
        int contentY = modalY + 54;

        if (state == null) {
            int cx = modalX + (modalWidth / 2);
            if (isMouseOver(mx, my, cx - 75, modalY + 138, 150, 24)) {
                SpotifyProcessDetector.launchSpotify();
                setStatus("Launching Spotify...");
            }
            return;
        }

        // Like Song Button
        int heartX = modalX + modalWidth - 30;
        int heartY = contentY + 3;
        if (isMouseOver(mx, my, heartX - 3, heartY - 3, 16, 16)) {
            boolean liked = stateManager.toggleCurrentTrackLiked();
            setStatus(liked ? "Added to Liked Songs ❤" : "Removed from Liked Songs ♡");
            return;
        }

        // 2. Seek Bar Scrubbing
        int seekY = contentY + 84;
        int seekW = modalWidth - 32;
        if (state.canSeek() && isMouseOver(mx, my, contentX - 2, seekY - 4, seekW + 4, 12)) {
            this.isDraggingSeek = true;
            float ratio = Math.max(0f, Math.min(1f, (float) (mx - contentX) / seekW));
            this.dragSeekRatio = ratio;
            return;
        }

        // 3. Playback Controls
        int controlsY = seekY + 25;
        int centerX = modalX + (modalWidth / 2);
        int btnSize = 22;

        // A) Shuffle (🔀)
        if (state.canShuffle() && isMouseOver(mx, my, centerX - 90, controlsY, btnSize, btnSize)) {
            stateManager.toggleShuffle();
            setStatus("Toggled Shuffle");
            return;
        }

        // B) Prev (⏮)
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
        if (isMouseOver(mx, my, refBtnX, refBtnY, 150, 22)) {
            stateManager.fullReload();
            setStatus("Bridge Reloaded!");
        }
    }

    private void handleSettingsClick(int mx, int my) {
        int contentX = modalX + 16;
        int contentY = modalY + 52;
        int posBtnX = contentX + 120;
        int btnW = 160;
        int btnH = 16;

        // 1. HUD Position
        if (isMouseOver(mx, my, posBtnX, contentY, btnW, btnH)) {
            config.setHudPosition(config.getHudPosition().next());
            return;
        }

        // 2. HUD Opacity Slider Click
        int row2Y = contentY + 19;
        if (isMouseOver(mx, my, posBtnX - 2, row2Y + 2, btnW + 4, 14)) {
            this.isDraggingHudOpacity = true;
            float ratio = Math.max(0f, Math.min(1f, (float) (mx - posBtnX) / btnW));
            this.dragHudOpacityRatio = ratio;
            config.setHudOpacity(ratio);
            setStatus("HUD Opacity: " + config.getHudOpacityPercent() + "%");
            return;
        }

        // 3. HUD Scale Slider Click
        int row3Y = contentY + 38;
        if (isMouseOver(mx, my, posBtnX - 2, row3Y + 2, btnW + 4, 14)) {
            this.isDraggingHudScale = true;
            float ratio = Math.max(0f, Math.min(1f, (float) (mx - posBtnX) / btnW));
            this.dragHudScaleRatio = ratio;
            config.setHudScalePercent(Math.round(50 + ratio * 100));
            setStatus("HUD Scale: " + config.getHudScalePercent() + "%");
            return;
        }

        // 4. HUD Lyrics toggle
        int row4Y = contentY + 57;
        if (isMouseOver(mx, my, posBtnX, row4Y, btnW, btnH)) {
            config.setHudShowLyrics(!config.isHudShowLyrics());
            return;
        }

        // 5. Sky Lyrics (Cielo) toggle
        int row5Y = contentY + 76;
        if (isMouseOver(mx, my, posBtnX, row5Y, btnW, btnH)) {
            config.setSkyLyricsEnabled(!config.isSkyLyricsEnabled());
            setStatus(config.isSkyLyricsEnabled() ? "Sky Lyrics: Enabled" : "Sky Lyrics: Disabled");
            return;
        }

        // 6. Sky Lyrics Scale Slider Click
        int row6Y = contentY + 95;
        if (isMouseOver(mx, my, posBtnX - 2, row6Y + 2, btnW + 4, 14)) {
            this.isDraggingSkyScale = true;
            float ratio = Math.max(0f, Math.min(1f, (float) (mx - posBtnX) / btnW));
            this.dragSkyScaleRatio = ratio;
            config.setSkyLyricsScalePercent(Math.round(50 + ratio * 150));
            setStatus("Sky Scale: " + config.getSkyLyricsScalePercent() + "%");
            return;
        }

        // 7. Sky Lyrics Lines Slider Click
        int row7Y = contentY + 114;
        if (isMouseOver(mx, my, posBtnX - 2, row7Y + 2, btnW + 4, 14)) {
            this.isDraggingSkyLines = true;
            float ratio = Math.max(0f, Math.min(1f, (float) (mx - posBtnX) / btnW));
            this.dragSkyLinesRatio = ratio;
            config.setSkyLyricsLength(Math.round(3 + ratio * 22));
            setStatus("Sky Lines: " + config.getSkyLyricsLength() + " lines");
            return;
        }

        // 8. Compact toggle
        int row8Y = contentY + 133;
        if (isMouseOver(mx, my, posBtnX, row8Y, btnW, btnH)) {
            config.setHudCompact(!config.isHudCompact());
            return;
        }

        // 9. Auto-Hide toggle
        int row9Y = contentY + 152;
        if (isMouseOver(mx, my, posBtnX, row9Y, btnW, btnH)) {
            config.setHudAutoHide(!config.isHudAutoHide());
            return;
        }

        // 10. MC Sound Ducking toggle
        int row10Y = contentY + 171;
        if (isMouseOver(mx, my, posBtnX, row10Y, btnW, btnH)) {
            config.setAudioDuckingEnabled(!config.isAudioDuckingEnabled());
            return;
        }

        // 11. Force Refresh Bridge Button
        int row11Y = contentY + 193;
        int btnWidth = 174;
        int btn1X = contentX;
        int btn2X = contentX + 184;

        if (isMouseOver(mx, my, btn1X, row11Y + 2, btnWidth, 20)) {
            stateManager.fullReload();
            setStatus("Bridge Reloaded!");
            return;
        }

        // 12. GitHub Updates Button
        if (isMouseOver(mx, my, btn2X, row11Y + 2, btnWidth, 20)) {
            com.espoty.update.UpdateChecker checker = com.espoty.update.UpdateChecker.getInstance();
            if (checker.isUpdateAvailable()) {
                com.espoty.update.UpdateChecker.openWebpage(checker.getReleaseUrl());
                setStatus("Opening release page...");
            } else {
                setStatus("Checking for updates...");
                checker.checkForUpdatesAsync().thenAccept(avail -> {
                    setStatus(avail ? "Update available: v" + checker.getLatestVersion() : "eSpoty is up to date (v" + com.espoty.update.UpdateChecker.getCurrentVersion() + ")");
                });
            }
            return;
        }

        // 13. Author Website Link
        if (isMouseOver(mx, my, contentX, row11Y + 25, 300, 14)) {
            com.espoty.update.UpdateChecker.openWebpage("https://itzcrih.it");
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

            if (isDraggingHudOpacity) {
                int posBtnX = contentX + 120;
                int btnW = 160;
                this.dragHudOpacityRatio = Math.max(0f, Math.min(1f, (float) (mx - posBtnX) / btnW));
                config.setHudOpacity(dragHudOpacityRatio);
                setStatus("HUD Opacity: " + config.getHudOpacityPercent() + "%");
                return true;
            }

            if (isDraggingHudScale) {
                int posBtnX = contentX + 120;
                int btnW = 160;
                this.dragHudScaleRatio = Math.max(0f, Math.min(1f, (float) (mx - posBtnX) / btnW));
                config.setHudScalePercent(Math.round(50 + dragHudScaleRatio * 100));
                setStatus("HUD Scale: " + config.getHudScalePercent() + "%");
                return true;
            }

            if (isDraggingSkyScale) {
                int posBtnX = contentX + 120;
                int btnW = 160;
                this.dragSkyScaleRatio = Math.max(0f, Math.min(1f, (float) (mx - posBtnX) / btnW));
                config.setSkyLyricsScalePercent(Math.round(50 + dragSkyScaleRatio * 150));
                setStatus("Sky Scale: " + config.getSkyLyricsScalePercent() + "%");
                return true;
            }

            if (isDraggingSkyLines) {
                int posBtnX = contentX + 120;
                int btnW = 160;
                this.dragSkyLinesRatio = Math.max(0f, Math.min(1f, (float) (mx - posBtnX) / btnW));
                config.setSkyLyricsLength(Math.round(3 + dragSkyLinesRatio * 22));
                setStatus("Sky Lines: " + config.getSkyLyricsLength() + " lines");
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
            if (isDraggingHudOpacity) {
                config.setHudOpacity(dragHudOpacityRatio);
                isDraggingHudOpacity = false;
            }
            if (isDraggingHudScale) {
                config.setHudScalePercent(Math.round(50 + dragHudScaleRatio * 100));
                isDraggingHudScale = false;
            }
            if (isDraggingSkyScale) {
                config.setSkyLyricsScalePercent(Math.round(50 + dragSkyScaleRatio * 150));
                isDraggingSkyScale = false;
            }
            if (isDraggingSkyLines) {
                config.setSkyLyricsLength(Math.round(3 + dragSkyLinesRatio * 22));
                isDraggingSkyLines = false;
            }
        }
        return super.mouseReleased(event);
    }

    public void startCloseAnimation() {
        if (!isClosing) {
            this.isClosing = true;
            this.closeStartTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onClose() {
        startCloseAnimation();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            startCloseAnimation();
            return true;
        }

        if (com.espoty.client.ESpotyModClient.getKeyOpenGui() != null
                && com.espoty.client.ESpotyModClient.getKeyOpenGui().matches(event)) {
            startCloseAnimation();
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_SPACE && currentTab == Tab.PLAYER) {
            stateManager.togglePlayPause();
            return true;
        }

        return super.keyPressed(event);
    }

    private boolean isMouseOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private String trimWithEllipsis(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) return text;
        while (!text.isEmpty() && this.font.width(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }
}
