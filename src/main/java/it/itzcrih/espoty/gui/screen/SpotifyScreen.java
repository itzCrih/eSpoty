package it.itzcrih.espoty.gui.screen;

import it.itzcrih.espoty.config.ModConfig;
import it.itzcrih.espoty.gui.texture.AlbumArtManager;
import it.itzcrih.espoty.gui.theme.ThemeManager;
import it.itzcrih.espoty.gui.theme.ThemeManager.ThemeAccent;
import it.itzcrih.espoty.gui.theme.ThemeManager.ThemeLook;
import it.itzcrih.espoty.gui.widget.AudioWaveformRenderer;
import it.itzcrih.espoty.lyrics.LyricLine;
import it.itzcrih.espoty.lyrics.LyricsManager;
import it.itzcrih.espoty.spotify.SpotifyProcessDetector;
import it.itzcrih.espoty.spotify.SpotifyStateManager;
import it.itzcrih.espoty.spotify.models.PlaybackState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Redesigned Spotify Hub Screen inspired by the Aurora Client liquid glass aesthetic.
 * Features 5 categories: Player, Lyrics, Queue, Themes (New!), and Settings.
 */
public class SpotifyScreen extends Screen {

    public enum Tab {
        PLAYER("🎵 Player"),
        LYRICS("📜 Lyrics"),
        QUEUE("📑 Queue"),
        THEMES("🎨 Themes"),
        SETTINGS("⚙ Settings");

        private final String title;
        Tab(String title) { this.title = title; }
        public String getTitle() { return title; }
    }

    private final SpotifyStateManager stateManager;
    private final ModConfig config;
    private Tab currentTab = Tab.PLAYER;

    // Responsive Modal Frame (Aurora Proportions)
    private int modalX;
    private int modalY;
    private int modalWidth = 420;
    private int modalHeight = 320;

    // Interactive slider dragging states
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
    private boolean isDraggingGlassOpacity = false;
    private float dragGlassOpacityRatio = 0.90f;
    private boolean isDraggingDucking = false;
    private float dragDuckingRatio = 0.35f;

    // Scroll states for each tab
    private int lyricsScrollY = 0;
    private int queueScrollY = 0;
    private int themesScrollY = 0;
    private int settingsScrollY = 0;

    // Animations & timing
    private boolean isClosing = false;
    private long closeStartTime = 0;
    private long tabChangeTime = System.currentTimeMillis();
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
        this.modalWidth = Math.min(420, this.width - 16);
        this.modalHeight = Math.min(320, this.height - 16);
        this.modalX = (this.width - this.modalWidth) / 2;
        this.modalY = (this.height - this.modalHeight) / 2;

        this.dragSkyLinesRatio = (config.getSkyLyricsLength() - 3) / 22.0f;
        this.dragSkyScaleRatio = (config.getSkyLyricsScalePercent() - 50) / 150.0f;
        this.dragHudOpacityRatio = config.getHudOpacity();
        this.dragHudScaleRatio = (config.getHudScalePercent() - 50) / 100.0f;
        this.dragGlassOpacityRatio = (config.getGlassOpacity() - 0.40f) / 0.60f;
        this.dragDuckingRatio = (config.getDuckingMultiplier() - 0.05f) / 0.95f;

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

        // Calculate smooth opening & closing ease
        float animScale;
        float backdropAlpha;

        if (isClosing) {
            long closeElapsed = now - closeStartTime;
            float p = Math.min(1.0f, closeElapsed / 150.0f);
            float ease = p * p;
            animScale = 1.0f - (ease * 0.10f);
            backdropAlpha = (1.0f - p) * 0.65f;
        } else {
            long elapsed = now - openTime;
            float animProgress = Math.min(1.0f, elapsed / 200.0f);
            float ease = 1.0f - (float) Math.pow(1.0f - animProgress, 3);
            animScale = 0.90f + (0.10f * ease);
            backdropAlpha = ease * 0.65f;
        }

        // 1. Scrim / Ambient Backdrop Vignette
        int alphaHex = Math.max(0, Math.min(255, (int) (backdropAlpha * 255)));
        guiGraphics.fill(0, 0, this.width, this.height, (alphaHex << 24));

        int cx = modalX + (modalWidth / 2);
        int cy = modalY + (modalHeight / 2);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate((float) cx, (float) cy);
        guiGraphics.pose().scale(animScale, animScale);
        guiGraphics.pose().translate((float) -cx, (float) -cy);

        // 2. Liquid Glass Aurora Modal Frame
        renderModernModalFrame(guiGraphics);

        // 3. Header Bar with Tabs
        renderHeader(guiGraphics, mouseX, mouseY, now);

        // 4. Tab Content with Snappy Vertical Glide Transition
        long tabElapsed = now - tabChangeTime;
        float tabProgress = Math.min(1.0f, tabElapsed / 150.0f);
        float tabEase = 1.0f - (float) Math.pow(1.0f - tabProgress, 3);
        float tabGlideY = (1.0f - tabEase) * 5.0f;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(0f, tabGlideY);

        switch (currentTab) {
            case PLAYER -> renderPlayerTab(guiGraphics, mouseX, mouseY, now);
            case LYRICS -> renderLyricsTab(guiGraphics, mouseX, mouseY);
            case QUEUE -> renderQueueTab(guiGraphics, mouseX, mouseY);
            case THEMES -> renderThemesTab(guiGraphics, mouseX, mouseY);
            case SETTINGS -> renderSettingsTab(guiGraphics, mouseX, mouseY);
        }

        guiGraphics.pose().popMatrix();

        // 5. Status Toast Banner
        if (!statusMessage.isEmpty() && now - statusMessageTime < 3200) {
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

        // 1. Aurora Multi-Stage Drop Shadows (Soft Blur Simulation)
        guiGraphics.fill(x + 2, y + 4, x + w + 2, y + h + 6, 0x35000000);
        guiGraphics.fill(x + 4, y + 7, x + w + 4, y + h + 9, 0x22000000);
        guiGraphics.fill(x + 7, y + 10, x + w + 7, y + h + 13, 0x12000000);

        // 2. Rounded Liquid Glass Body
        int winBg = ThemeManager.getWindowBg();
        guiGraphics.fill(x + 3, y, x + w - 3, y + h, winBg);
        guiGraphics.fill(x, y + 3, x + 3, y + h - 3, winBg);
        guiGraphics.fill(x + w - 3, y + 3, x + w, y + h - 3, winBg);

        // 3. Top Specular Sheen Line with Gradient
        int sheen1 = ThemeManager.getSheen();
        int sheen2 = ThemeManager.applyAlpha(ThemeManager.getAccent2(), 0.08f);
        guiGraphics.fillGradient(x + 4, y, x + w - 4, y + 2, sheen1, sheen2);

        // 4. Delicate Glowing Glass Border
        int border = ThemeManager.getGlassEdge();
        guiGraphics.fill(x + 3, y + h - 1, x + w - 3, y + h, border);
        guiGraphics.fill(x, y + 3, x + 1, y + h - 3, border);
        guiGraphics.fill(x + w - 1, y + 3, x + w, y + h - 3, border);

        // Rounded Chamfer Corner Connectors
        int cornerColor = ThemeManager.applyAlpha(border, 0.60f);
        guiGraphics.fill(x + 1, y + 1, x + 3, y + 3, cornerColor);
        guiGraphics.fill(x + w - 3, y + 1, x + w - 1, y + 3, cornerColor);
        guiGraphics.fill(x + 1, y + h - 3, x + 3, y + h - 1, cornerColor);
        guiGraphics.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, cornerColor);
    }

    private void renderHeader(GuiGraphics guiGraphics, int mouseX, int mouseY, long now) {
        int x = modalX + 14;
        int y = modalY + 9;

        // Brand Pill: "✦ eSpoty" + Version Tag
        int accent = ThemeManager.getAccent();
        guiGraphics.drawString(this.font, "✦ eSpoty", x, y + 1, accent, true);
        guiGraphics.drawString(this.font, "v" + it.itzcrih.espoty.update.UpdateChecker.getCurrentVersion(), x + 58, y + 2, ThemeManager.getTextMuted(), false);

        // Close Button (Top-Right Circular Glass Disc "✕")
        int closeX = modalX + modalWidth - 18;
        boolean closeHover = mouseX >= closeX - 4 && mouseX <= closeX + 12 && mouseY >= y && mouseY <= y + 14;
        if (closeHover) {
            guiGraphics.fill(closeX - 3, y - 1, closeX + 11, y + 13, 0x30FF4B4B);
        }
        guiGraphics.drawString(this.font, "✕", closeX, y + 1, closeHover ? 0xFFFF4B4B : 0xFF71717A, true);

        // Segmented Tab Bar Container (Aurora Capsule Style)
        int tabY = modalY + 25;
        int tabBarW = modalWidth - 28;
        guiGraphics.fill(modalX + 14, tabY, modalX + 14 + tabBarW, tabY + 20, 0x2A000000);
        guiGraphics.fill(modalX + 14, tabY + 19, modalX + 14 + tabBarW, tabY + 20, 0x14FFFFFF);

        int tabX = modalX + 16;
        for (Tab tab : Tab.values()) {
            boolean active = (tab == currentTab);
            String title = tab.getTitle();
            int tabW = this.font.width(title) + 12;
            int tabH = 18;
            boolean hover = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH;

            if (active) {
                // Illuminated Theme Accent Capsule
                int activeBg = ThemeManager.getAccentDim(1.0f);
                guiGraphics.fill(tabX + 1, tabY + 1, tabX + tabW - 1, tabY + tabH - 1, activeBg);
                guiGraphics.fill(tabX + 2, tabY + tabH - 2, tabX + tabW - 2, tabY + tabH, accent);
                guiGraphics.drawCenteredString(this.font, title, tabX + (tabW / 2), tabY + 5, accent);
            } else {
                if (hover) {
                    guiGraphics.fill(tabX + 1, tabY + 1, tabX + tabW - 1, tabY + tabH - 1, 0x18FFFFFF);
                }
                int textCol = hover ? ThemeManager.getTextPrimary() : ThemeManager.getTextSecondary();
                guiGraphics.drawCenteredString(this.font, title, tabX + (tabW / 2), tabY + 5, textCol);
            }

            tabX += tabW + 3;
        }
    }

    private void renderPlayerTab(GuiGraphics guiGraphics, int mouseX, int mouseY, long now) {
        int contentX = modalX + 16;
        int contentY = modalY + 52;
        int accent = ThemeManager.getAccent();
        int accent2 = ThemeManager.getAccent2();

        PlaybackState state = stateManager.getCurrentState();

        if (state == null) {
            int cx = modalX + (modalWidth / 2);
            renderGlassCard(guiGraphics, cx - 110, modalY + 80, 220, 110, false, false);
            guiGraphics.drawCenteredString(this.font, "No active Spotify track detected.", cx, modalY + 98, ThemeManager.getTextSecondary());
            guiGraphics.drawCenteredString(this.font, "Start playing music in your Spotify Desktop app!", cx, modalY + 114, accent);

            int btnX = cx - 75;
            int btnY = modalY + 144;
            boolean hover = isMouseOver(mouseX, mouseY, btnX, btnY, 150, 24);
            guiGraphics.fill(btnX, btnY, btnX + 150, btnY + 24, hover ? accent2 : accent);
            guiGraphics.drawCenteredString(this.font, "Launch Spotify App", cx, btnY + 8, 0xFF0B0F19);
            return;
        }

        // 1. High-Res Album Art with Rounded Glass Card
        int artSize = 74;
        Identifier texture = AlbumArtManager.getInstance().getAlbumTexture(state.getAlbumArtUrl());

        renderGlassCard(guiGraphics, contentX, contentY, artSize, artSize, false, state.isPlaying());

        if (texture != null) {
            guiGraphics.blit(texture, contentX + 2, contentY + 2, contentX + artSize - 2, contentY + artSize - 2, 0.0f, 1.0f, 0.0f, 1.0f);
        } else {
            guiGraphics.drawCenteredString(this.font, "♫", contentX + (artSize / 2), contentY + (artSize / 2) - 6, accent);
        }

        // 2. Track Metadata in Glass Panel
        int infoX = contentX + artSize + 14;
        int infoW = modalWidth - (infoX - modalX) - 34;

        String title = state.getTrackName();
        if (this.font.width(title) > infoW) {
            title = trimWithEllipsis(title, infoW);
        }
        guiGraphics.drawString(this.font, title, infoX, contentY + 4, accent, true);

        // Heart "Liked Songs" Button (❤ / ♡)
        int heartX = modalX + modalWidth - 28;
        int heartY = contentY + 3;
        boolean isLiked = stateManager.isCurrentTrackLiked();
        boolean heartHover = isMouseOver(mouseX, mouseY, heartX - 3, heartY - 3, 16, 16);
        String heartIcon = isLiked ? "❤" : "♡";
        int heartColor = isLiked ? accent : (heartHover ? accent2 : ThemeManager.getTextMuted());
        guiGraphics.drawString(this.font, heartIcon, heartX, heartY, heartColor, true);

        String artist = state.getArtistNames();
        if (this.font.width(artist) > infoW) {
            artist = trimWithEllipsis(artist, infoW);
        }
        guiGraphics.drawString(this.font, artist, infoX, contentY + 18, ThemeManager.getTextPrimary(), false);

        String album = state.getAlbumName();
        if (!album.isEmpty()) {
            if (this.font.width(album) > infoW) {
                album = trimWithEllipsis(album, infoW);
            }
            guiGraphics.drawString(this.font, album, infoX, contentY + 32, ThemeManager.getTextSecondary(), false);
        }

        // Device Pill Tag
        guiGraphics.drawString(this.font, "💻 Spotify Desktop", infoX, contentY + 54, accent2, false);

        // Dynamic Audio Waveform Visualizer
        AudioWaveformRenderer.renderSyncedWaveform(guiGraphics, infoX + 115, contentY + 66, 125, 14, 20,
                state.isPlaying(), stateManager.getInterpolatedProgressMs(), 1.0f, accent, accent2,
                state.getTrackName(), state.getArtistNames(), true);

        // 3. Modern Interactive Seek Bar
        int seekY = contentY + 86;
        int seekW = modalWidth - 32;
        int seekH = 4;
        long progressMs = isDraggingSeek ? (long) (state.getDurationMs() * dragSeekRatio) : stateManager.getInterpolatedProgressMs();
        long durationMs = state.getDurationMs();
        float ratio = durationMs > 0 ? Math.min(1.0f, (float) progressMs / durationMs) : 0f;

        // Background track (subtle translucent slate)
        guiGraphics.fill(contentX, seekY, contentX + seekW, seekY + seekH, 0x30FFFFFF);

        // Filled Theme Accent gradient bar
        int fillW = (int) (seekW * ratio);
        if (fillW > 0) {
            guiGraphics.fillGradient(contentX, seekY, contentX + fillW, seekY + seekH, accent, accent2);
        }

        // Circular Knob
        int knobX = contentX + fillW;
        boolean canSeek = state.canSeek();
        int knobColor = canSeek ? accent : 0xFF555566;
        guiGraphics.fill(knobX - 3, seekY - 2, knobX + 4, seekY + seekH + 2, knobColor);
        guiGraphics.fill(knobX - 1, seekY, knobX + 2, seekY + seekH, canSeek ? 0xFFFFFFFF : 0xFF888899);

        // Time labels
        String curTime = PlaybackState.formatTime(progressMs);
        String totTime = PlaybackState.formatTime(durationMs);
        guiGraphics.drawString(this.font, curTime, contentX, seekY + 8, accent, false);
        int totX = contentX + seekW - this.font.width(totTime);
        guiGraphics.drawString(this.font, totTime, totX, seekY + 8, ThemeManager.getTextSecondary(), false);

        // 4. Playback Controls Bar
        int controlsY = seekY + 25;
        int centerX = modalX + (modalWidth / 2);
        int btnSize = 22;

        // A) Shuffle (🔀)
        int shufX = centerX - 90;
        boolean canShuf = state.canShuffle();
        boolean shufHover = isMouseOver(mouseX, mouseY, shufX, controlsY, btnSize, btnSize) && canShuf;
        boolean isShuf = state.isShuffleState();
        int shufColor = canShuf ? (isShuf ? accent : ThemeManager.getTextSecondary()) : 0xFF555566;
        renderModernIconButton(guiGraphics, "🔀", shufX, controlsY, btnSize, btnSize, shufHover, shufColor, isShuf && canShuf, canShuf);

        // B) Prev (⏮)
        int prevX = centerX - 50;
        boolean canPrev = state.canPrev();
        boolean prevHover = isMouseOver(mouseX, mouseY, prevX, controlsY, btnSize, btnSize) && canPrev;
        int prevColor = canPrev ? ThemeManager.getTextPrimary() : 0xFF555566;
        renderModernIconButton(guiGraphics, "⏮", prevX, controlsY, btnSize, btnSize, prevHover, prevColor, false, canPrev);

        // C) Central Play/Pause Disc
        int playX = centerX - 18;
        int playW = 36;
        int playH = 26;
        boolean canPlay = state.canPlayPause();
        boolean playHover = isMouseOver(mouseX, mouseY, playX, controlsY - 2, playW, playH) && canPlay;
        String playIcon = state.isPlaying() ? "❚❚" : "▶";

        if (canPlay) {
            if (state.isPlaying()) {
                double pulse = Math.sin(now / 180.0) * 0.5 + 0.5;
                int haloAlpha = (int) (35 + (pulse * 50));
                guiGraphics.fill(playX - 3, controlsY - 5, playX + playW + 3, controlsY + playH + 3, ThemeManager.applyAlpha(accent, haloAlpha / 255.0f));
            }
            guiGraphics.fill(playX + 2, controlsY - 3, playX + playW - 2, controlsY + playH + 1, playHover ? accent2 : accent);
            guiGraphics.fill(playX, controlsY - 1, playX + playW, controlsY + playH - 1, playHover ? accent2 : accent);
            guiGraphics.drawCenteredString(this.font, playIcon, playX + (playW / 2), controlsY + 6, 0xFF0B0F19);
        } else {
            guiGraphics.fill(playX, controlsY, playX + playW, controlsY + playH, 0xFF1C1C24);
            guiGraphics.drawCenteredString(this.font, playIcon, playX + (playW / 2), controlsY + 6, 0xFF555566);
        }

        // D) Next (⏭)
        int nextX = centerX + 28;
        boolean canNext = state.canNext();
        boolean nextHover = isMouseOver(mouseX, mouseY, nextX, controlsY, btnSize, btnSize) && canNext;
        int nextColor = canNext ? ThemeManager.getTextPrimary() : 0xFF555566;
        renderModernIconButton(guiGraphics, "⏭", nextX, controlsY, btnSize, btnSize, nextHover, nextColor, false, canNext);

        // E) Loop / Repeat (🔁)
        int repX = centerX + 68;
        boolean canRep = state.canRepeat();
        boolean repHover = isMouseOver(mouseX, mouseY, repX, controlsY, btnSize, btnSize) && canRep;
        String repMode = state.getRepeatState();
        boolean isRep = !"none".equalsIgnoreCase(repMode) && !"off".equalsIgnoreCase(repMode);
        String repIcon = "track".equalsIgnoreCase(repMode) ? "🔂" : "🔁";
        int repColor = canRep ? (isRep ? accent : ThemeManager.getTextSecondary()) : 0xFF555566;
        renderModernIconButton(guiGraphics, repIcon, repX, controlsY, btnSize, btnSize, repHover, repColor, isRep && canRep, canRep);

        // 5. Volume Slider Row
        int volY = controlsY + 32;
        int volBarW = 110;
        int volBarH = 4;
        int volStartX = centerX - 80;
        int volBarX = volStartX + 18;

        int curVol = isDraggingVolume ? (int) (dragVolumeRatio * 100) : (state.getVolumePercent() >= 0 ? state.getVolumePercent() : config.getVolume());
        curVol = Math.max(0, Math.min(100, curVol));

        String speakerIcon = curVol == 0 ? "🔇" : (curVol < 50 ? "🔉" : "🔊");
        boolean speakerHover = isMouseOver(mouseX, mouseY, volStartX - 4, volY - 3, 16, 14);
        guiGraphics.drawString(this.font, speakerIcon, volStartX, volY - 2, speakerHover ? accent2 : accent, true);

        guiGraphics.fill(volBarX, volY, volBarX + volBarW, volY + volBarH, 0x30FFFFFF);
        float volRatio = curVol / 100.0f;
        int volFillW = (int) (volBarW * volRatio);
        if (volFillW > 0) {
            guiGraphics.fillGradient(volBarX, volY, volBarX + volFillW, volY + volBarH, accent, accent2);
        }

        int volKnobX = volBarX + volFillW;
        guiGraphics.fill(volKnobX - 2, volY - 2, volKnobX + 3, volY + volBarH + 2, accent);
        guiGraphics.fill(volKnobX - 1, volY - 1, volKnobX + 2, volY + volBarH + 1, 0xFFFFFFFF);

        String volText = curVol + "%";
        guiGraphics.drawString(this.font, volText, volBarX + volBarW + 8, volY - 2, ThemeManager.getTextSecondary(), false);
    }

    private void renderLyricsTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + 16;
        int contentY = modalY + 52;
        int contentW = modalWidth - 32;
        int contentH = modalHeight - 64;

        LyricsManager.SongLyrics lyrics = LyricsManager.getInstance().getCurrentLyrics();
        PlaybackState state = stateManager.getCurrentState();

        if (state == null) {
            guiGraphics.drawCenteredString(this.font, "No track currently playing.", modalX + (modalWidth / 2), modalY + 110, ThemeManager.getTextSecondary());
            return;
        }

        if (lyrics == null || (lyrics.getLines().isEmpty() && lyrics.getPlainLyrics().isEmpty())) {
            guiGraphics.drawCenteredString(this.font, "Searching lyrics for " + state.getTrackName() + "...",
                    modalX + (modalWidth / 2), modalY + 110, ThemeManager.getAccent());
            return;
        }

        renderGlassCard(guiGraphics, contentX, contentY, contentW, contentH, false, false);

        guiGraphics.enableScissor(contentX + 2, contentY + 2, contentX + contentW - 2, contentY + contentH - 2);

        long progressMs = stateManager.getInterpolatedProgressMs();
        int activeIdx = lyrics.getActiveIndex(progressMs);

        if (lyrics.isSynced()) {
            List<LyricLine> lines = lyrics.getLines();
            int lineHeight = 22;
            int totalHeight = lines.size() * lineHeight;

            int targetScroll = (activeIdx * lineHeight) - (contentH / 2) + 12;
            targetScroll = Math.max(0, Math.min(totalHeight - contentH + 30, targetScroll));

            lyricsScrollY += (targetScroll - lyricsScrollY) * 0.18f;
            int lineY = contentY + 12 - lyricsScrollY;
            int maxTextW = contentW - 38;

            for (int i = 0; i < lines.size(); i++) {
                LyricLine line = lines.get(i);
                boolean isActive = (i == activeIdx);

                if (lineY + lineHeight >= contentY - 4 && lineY <= contentY + contentH + 4) {
                    String clean = line.getText().replaceAll("(?i)§[0-9a-fk-orx]", "").trim();
                    Component lineComp = Component.literal(clean).withStyle(config.getLyricFont().getStyle());
                    int textW = this.font.width(lineComp);

                    if (isActive) {
                        // Glowing Pill Capsule
                        guiGraphics.fill(contentX + 6, lineY - 2, contentX + contentW - 6, lineY + 17, ThemeManager.getAccentDim(1.0f));
                        guiGraphics.fill(contentX + 6, lineY - 2, contentX + 9, lineY + 17, ThemeManager.getAccent());
                        guiGraphics.drawString(this.font, "♪", contentX + 13, lineY + 2, ThemeManager.getAccent(), true);

                        int textStartX = contentX + 25;
                        if (textW > maxTextW) {
                            float textScale = (float) maxTextW / (float) textW;
                            guiGraphics.pose().pushMatrix();
                            guiGraphics.pose().translate((float) textStartX, (float) (lineY + 2));
                            guiGraphics.pose().scale(textScale, textScale);
                            guiGraphics.drawString(this.font, lineComp, 0, 0, 0xFFFFFFFF, true);
                            guiGraphics.pose().popMatrix();
                        } else {
                            guiGraphics.drawString(this.font, lineComp, textStartX, lineY + 2, 0xFFFFFFFF, true);
                        }
                    } else {
                        int color = (i < activeIdx) ? ThemeManager.getTextMuted() : ThemeManager.getTextSecondary();
                        int textStartX = contentX + 25;
                        if (textW > maxTextW) {
                            float textScale = (float) maxTextW / (float) textW;
                            guiGraphics.pose().pushMatrix();
                            guiGraphics.pose().translate((float) textStartX, (float) (lineY + 2));
                            guiGraphics.pose().scale(textScale, textScale);
                            guiGraphics.drawString(this.font, lineComp, 0, 0, color, true);
                            guiGraphics.pose().popMatrix();
                        } else {
                            guiGraphics.drawString(this.font, lineComp, textStartX, lineY + 2, color, true);
                        }
                    }
                }
                lineY += lineHeight;
            }
        } else {
            String[] plainLines = lyrics.getPlainLyrics().split("\\r?\\n");
            int lineY = contentY + 12 - lyricsScrollY;
            int maxTextW = contentW - 24;
            for (String pl : plainLines) {
                String clean = pl.replaceAll("(?i)§[0-9a-fk-orx]", "").trim();
                if (lineY + 18 >= contentY && lineY <= contentY + contentH) {
                    Component plComp = Component.literal(clean).withStyle(config.getLyricFont().getStyle());
                    int textW = this.font.width(plComp);
                    if (textW > maxTextW) {
                        float textScale = (float) maxTextW / (float) textW;
                        guiGraphics.pose().pushMatrix();
                        guiGraphics.pose().translate((float) (contentX + 12), (float) lineY);
                        guiGraphics.pose().scale(textScale, textScale);
                        guiGraphics.drawString(this.font, plComp, 0, 0, 0xFFFFFFFF, true);
                        guiGraphics.pose().popMatrix();
                    } else {
                        guiGraphics.drawString(this.font, plComp, contentX + 12, lineY, 0xFFFFFFFF, true);
                    }
                }
                lineY += 18;
            }
        }

        guiGraphics.disableScissor();
    }

    private void renderQueueTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + 16;
        int contentY = modalY + 52;
        int contentW = modalWidth - 32;
        int contentH = modalHeight - 64;

        PlaybackState curState = stateManager.getCurrentState();

        renderGlassCard(guiGraphics, contentX, contentY, contentW, contentH, false, false);

        guiGraphics.enableScissor(contentX + 4, contentY + 4, contentX + contentW - 4, contentY + contentH - 4);
        int currentY = contentY + 10 - queueScrollY;

        if (curState != null) {
            guiGraphics.drawString(this.font, "CURRENTLY PLAYING", contentX + 10, currentY, ThemeManager.getAccent(), true);
            currentY += 14;

            // Rounded Mini Card
            renderGlassCard(guiGraphics, contentX + 8, currentY, contentW - 16, 36, false, true);

            guiGraphics.drawString(this.font, curState.getTrackName(), contentX + 16, currentY + 6, ThemeManager.getAccent(), true);
            String dur = PlaybackState.formatTime(curState.getDurationMs());
            guiGraphics.drawString(this.font, dur, contentX + contentW - this.font.width(dur) - 16, currentY + 6, ThemeManager.getTextSecondary(), false);

            guiGraphics.drawString(this.font, curState.getArtistNames() + " • " + curState.getAlbumName(), contentX + 16, currentY + 19, ThemeManager.getTextPrimary(), false);
            currentY += 46;
        }

        guiGraphics.drawString(this.font, "DIRECT SPOTIFY QUEUE", contentX + 10, currentY, ThemeManager.getAccent(), true);
        currentY += 14;
        guiGraphics.drawString(this.font, "Playback is synchronized in real-time with your desktop Spotify application.", contentX + 10, currentY, ThemeManager.getTextSecondary(), false);
        currentY += 14;
        guiGraphics.drawString(this.font, "Use Next (⏭) and Prev (⏮) controls to navigate your playlist queue.", contentX + 10, currentY, ThemeManager.getTextPrimary(), false);

        // Modern Force Refresh Button
        int refBtnX = contentX + 10;
        int refBtnY = currentY + 20;
        boolean refHover = isMouseOver(mouseX, mouseY, refBtnX, refBtnY, 160, 24);
        renderGlassCard(guiGraphics, refBtnX, refBtnY, 160, 24, refHover, false);
        guiGraphics.drawCenteredString(this.font, "🔄 Force Refresh Bridge", refBtnX + 80, refBtnY + 8, refHover ? ThemeManager.getAccent2() : ThemeManager.getAccent());

        guiGraphics.disableScissor();
    }

    /**
     * Renders the brand-new THEMES category requested by the user.
     * Offers Aurora Liquid Glass, Midnight, OLED, Daylight, Sunset, and Cyber Gold looks,
     * along with accent color palette swatches and glass opacity controls.
     */
    private void renderThemesTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + 16;
        int contentY = modalY + 52;
        int contentW = modalWidth - 32;
        int contentH = modalHeight - 64;

        guiGraphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        int curY = contentY + 2 - themesScrollY;
        int accent = ThemeManager.getAccent();
        ThemeLook currentLook = config.getThemeLook();
        ThemeAccent currentAccent = config.getThemeAccent();

        // 1. SECTION: THEME PRESETS (LOOKS)
        guiGraphics.drawString(this.font, "✦ THEME ATMOSPHERE", contentX + 2, curY, accent, true);
        guiGraphics.drawString(this.font, "Select liquid glass style & atmosphere", contentX + 140, curY + 1, ThemeManager.getTextMuted(), false);
        curY += 14;

        ThemeLook[] looks = ThemeLook.values();
        int cardW = (contentW - 6) / 2;
        int cardH = 34;

        for (int i = 0; i < looks.length; i++) {
            ThemeLook look = looks[i];
            int col = i % 2;
            int row = i / 2;
            int cardX = contentX + (col * (cardW + 6));
            int cardY = curY + (row * (cardH + 5));

            boolean isActive = (look == currentLook);
            boolean hover = isMouseOver(mouseX, mouseY, cardX, cardY, cardW, cardH);

            renderGlassCard(guiGraphics, cardX, cardY, cardW, cardH, hover, isActive);

            // Left active indicator pill
            if (isActive) {
                guiGraphics.fill(cardX + 1, cardY + 3, cardX + 3, cardY + cardH - 3, look.getAccentColor());
            }

            // Theme Name & Subtitle
            guiGraphics.drawString(this.font, look.getTitle(), cardX + 8, cardY + 5, isActive ? look.getAccentColor() : ThemeManager.getTextPrimary(), true);
            guiGraphics.drawString(this.font, look.getSubtitle(), cardX + 8, cardY + 18, ThemeManager.getTextMuted(), false);

            // 3 Color Swatch Preview Dots (Window, Card, Accent)
            int dotRight = cardX + cardW - 10;
            int dotY = cardY + 12;
            guiGraphics.fill(dotRight - 28, dotY, dotRight - 20, dotY + 8, 0xFF000000 | look.getWindowRgb());
            guiGraphics.fill(dotRight - 18, dotY, dotRight - 10, dotY + 8, 0xFF000000 | look.getCardRgb());
            guiGraphics.fill(dotRight - 8, dotY, dotRight, dotY + 8, look.getAccentColor());

            // Active badge
            if (isActive) {
                guiGraphics.drawString(this.font, "●", dotRight - 38, cardY + 6, look.getAccentColor(), true);
            }
        }

        curY += (looks.length / 2) * (cardH + 5) + 8;

        // 2. SECTION: ACCENT PALETTE
        guiGraphics.drawString(this.font, "✦ ACCENT OVERRIDE", contentX + 2, curY, accent, true);
        guiGraphics.drawString(this.font, "Customize the primary glowing accent", contentX + 130, curY + 1, ThemeManager.getTextMuted(), false);
        curY += 14;

        ThemeAccent[] accents = ThemeAccent.values();
        int swatchSize = 20;
        int swatchSpacing = (contentW - (accents.length * swatchSize)) / (accents.length - 1);

        for (int i = 0; i < accents.length; i++) {
            ThemeAccent a = accents[i];
            int sx = contentX + (i * (swatchSize + swatchSpacing));
            int sy = curY;

            boolean isCurrent = (a == currentAccent);
            boolean hover = isMouseOver(mouseX, mouseY, sx, sy, swatchSize, swatchSize);

            // Swatch background disc
            int col = (a == ThemeAccent.MATCH_THEME) ? currentLook.getAccentColor() : a.getColor();

            if (hover || isCurrent) {
                guiGraphics.fill(sx - 1, sy - 1, sx + swatchSize + 1, sy + swatchSize + 1, isCurrent ? accent : 0x88FFFFFF);
            }

            guiGraphics.fill(sx, sy, sx + swatchSize, sy + swatchSize, col);

            if (a == ThemeAccent.MATCH_THEME) {
                guiGraphics.drawCenteredString(this.font, "↺", sx + (swatchSize / 2), sy + 5, 0xFF0B0F19);
            } else if (isCurrent) {
                guiGraphics.drawCenteredString(this.font, "✓", sx + (swatchSize / 2), sy + 5, 0xFF0B0F19);
            }

            if (hover) {
                // Tooltip label above swatch
                guiGraphics.drawCenteredString(this.font, a.getTitle(), sx + (swatchSize / 2), sy - 10, accent);
            }
        }

        curY += swatchSize + 14;

        // 3. SECTION: GLASS & HUD INTEGRATION CARDS
        guiGraphics.drawString(this.font, "✦ GLASS & HUD INTEGRATION", contentX + 2, curY, accent, true);
        curY += 13;

        // Card A: Glass Opacity Slider
        int cardRowH = 26;
        renderGlassCard(guiGraphics, contentX, curY, contentW, cardRowH, false, false);
        guiGraphics.drawString(this.font, "Window Glass Opacity", contentX + 10, curY + 5, ThemeManager.getTextPrimary(), false);
        guiGraphics.drawString(this.font, "Adjust window transparency & blur", contentX + 10, curY + 15, ThemeManager.getTextMuted(), false);

        int sliderW = 120;
        int sliderX = contentX + contentW - sliderW - 10;
        int opacityPercent = isDraggingGlassOpacity ? Math.round(40 + dragGlassOpacityRatio * 60) : config.getGlassOpacityPercent();
        float opacRatio = (opacityPercent - 40) / 60.0f;
        boolean opacHover = isMouseOver(mouseX, mouseY, sliderX, curY + 6, sliderW, 14);
        renderSlider(guiGraphics, sliderX, curY + 8, sliderW, 4, opacRatio, opacityPercent + "%", opacHover);

        curY += cardRowH + 6;

        // Card B: Sync HUD & Sky Lyrics Toggle
        renderGlassCard(guiGraphics, contentX, curY, contentW, cardRowH, false, false);
        guiGraphics.drawString(this.font, "Sync HUD & 3D Celestial Sky", contentX + 10, curY + 5, ThemeManager.getTextPrimary(), false);
        guiGraphics.drawString(this.font, "Apply active theme colors to In-Game HUD & Sky Lyrics", contentX + 10, curY + 15, ThemeManager.getTextMuted(), false);

        int switchW = 44;
        int switchH = 14;
        int switchX = contentX + contentW - switchW - 10;
        boolean switchHover = isMouseOver(mouseX, mouseY, switchX, curY + 6, switchW, switchH);
        renderPillSwitch(guiGraphics, config.isSyncHudTheme(), switchX, curY + 6, switchW, switchH, switchHover);

        guiGraphics.disableScissor();
    }

    private void renderSettingsTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + 16;
        int contentY = modalY + 52;
        int contentW = modalWidth - 32;
        int contentH = modalHeight - 64;

        guiGraphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        int curY = contentY + 2 - settingsScrollY;
        int rowH = 26;
        int controlW = 120;
        int controlX = contentX + contentW - controlW - 10;
        int switchW = 44;
        int switchH = 14;
        int switchX = contentX + contentW - switchW - 10;

        // 1. In-Game HUD Card
        renderGlassCard(guiGraphics, contentX, curY, contentW, rowH, false, false);
        guiGraphics.drawString(this.font, "In-Game HUD Overlay", contentX + 10, curY + 5, ThemeManager.getTextPrimary(), false);
        guiGraphics.drawString(this.font, "Position: " + config.getHudPosition().getDisplayName(), contentX + 10, curY + 15, ThemeManager.getTextMuted(), false);
        boolean hudHover = isMouseOver(mouseX, mouseY, switchX, curY + 6, switchW, switchH);
        renderPillSwitch(guiGraphics, config.getHudPosition() != ModConfig.HudPosition.OFF, switchX, curY + 6, switchW, switchH, hudHover);
        curY += rowH + 4;

        // 1b. HUD Lyrics Line Card
        renderGlassCard(guiGraphics, contentX, curY, contentW, rowH, false, false);
        guiGraphics.drawString(this.font, "HUD Lyrics Line", contentX + 10, curY + 5, ThemeManager.getTextPrimary(), false);
        guiGraphics.drawString(this.font, "Display active lyric line under the HUD card", contentX + 10, curY + 15, ThemeManager.getTextMuted(), false);
        boolean hudLyricsHover = isMouseOver(mouseX, mouseY, switchX, curY + 6, switchW, switchH);
        renderPillSwitch(guiGraphics, config.isHudShowLyrics(), switchX, curY + 6, switchW, switchH, hudLyricsHover);
        curY += rowH + 4;

        // 2. HUD Opacity Slider Card
        renderGlassCard(guiGraphics, contentX, curY, contentW, rowH, false, false);
        guiGraphics.drawString(this.font, "HUD Opacity", contentX + 10, curY + 5, ThemeManager.getTextPrimary(), false);
        guiGraphics.drawString(this.font, "Translucency of overlay", contentX + 10, curY + 15, ThemeManager.getTextMuted(), false);
        int opacPercent = isDraggingHudOpacity ? Math.round(dragHudOpacityRatio * 100) : config.getHudOpacityPercent();
        float opacRatio = opacPercent / 100.0f;
        boolean opacHover = isMouseOver(mouseX, mouseY, controlX, curY + 6, controlW, 14);
        String opacLabel = (opacPercent == 0) ? "0% (Hidden)" : (opacPercent + "%");
        renderSlider(guiGraphics, controlX, curY + 8, controlW, 4, opacRatio, opacLabel, opacHover);
        curY += rowH + 4;

        // 3. HUD Scale Slider Card
        renderGlassCard(guiGraphics, contentX, curY, contentW, rowH, false, false);
        guiGraphics.drawString(this.font, "HUD Scale", contentX + 10, curY + 5, ThemeManager.getTextPrimary(), false);
        guiGraphics.drawString(this.font, "Size of the in-game overlay", contentX + 10, curY + 15, ThemeManager.getTextMuted(), false);
        int scalePercent = isDraggingHudScale ? Math.round(50 + dragHudScaleRatio * 100) : config.getHudScalePercent();
        float scaleRatio = (scalePercent - 50) / 100.0f;
        boolean scaleHover = isMouseOver(mouseX, mouseY, controlX, curY + 6, controlW, 14);
        renderSlider(guiGraphics, controlX, curY + 8, controlW, 4, scaleRatio, scalePercent + "%", scaleHover);
        curY += rowH + 4;

        // 4. 360° Sky Lyrics (Cielo) Card
        renderGlassCard(guiGraphics, contentX, curY, contentW, rowH, false, false);
        guiGraphics.drawString(this.font, "360° Celestial Sky Lyrics (Cielo)", contentX + 10, curY + 5, ThemeManager.getTextPrimary(), false);
        guiGraphics.drawString(this.font, "Display synced lyrics across the sky", contentX + 10, curY + 15, ThemeManager.getTextMuted(), false);
        boolean skyHover = isMouseOver(mouseX, mouseY, switchX, curY + 6, switchW, switchH);
        renderPillSwitch(guiGraphics, config.isSkyLyricsEnabled(), switchX, curY + 6, switchW, switchH, skyHover);
        curY += rowH + 4;

        // 4b. Sky Lyrics Direction / Mode Card
        renderGlassCard(guiGraphics, contentX, curY, contentW, rowH, false, false);
        guiGraphics.drawString(this.font, "Sky Lyrics Direction", contentX + 10, curY + 5, ThemeManager.getTextPrimary(), false);
        guiGraphics.drawString(this.font, "Orientation: " + config.getSkyLyricsMode().getDisplayName(), contentX + 10, curY + 15, ThemeManager.getTextMuted(), false);
        boolean skyDirHover = isMouseOver(mouseX, mouseY, controlX, curY + 4, controlW, 18);
        renderPillButton(guiGraphics, config.getSkyLyricsMode().getDisplayName(), controlX, curY + 4, controlW, 18, skyDirHover);
        curY += rowH + 4;

        // 5. Sky Lyrics Scale Slider Card
        renderGlassCard(guiGraphics, contentX, curY, contentW, rowH, false, false);
        guiGraphics.drawString(this.font, "Sky Lyrics Scale", contentX + 10, curY + 5, ThemeManager.getTextPrimary(), false);
        guiGraphics.drawString(this.font, "Celestial text size in sky dome", contentX + 10, curY + 15, ThemeManager.getTextMuted(), false);
        int skyScalePercent = isDraggingSkyScale ? Math.round(50 + dragSkyScaleRatio * 150) : config.getSkyLyricsScalePercent();
        float skyScaleRatio = (skyScalePercent - 50) / 150.0f;
        boolean skyScaleHover = isMouseOver(mouseX, mouseY, controlX, curY + 6, controlW, 14);
        renderSlider(guiGraphics, controlX, curY + 8, controlW, 4, skyScaleRatio, skyScalePercent + "%", skyScaleHover);
        curY += rowH + 4;

        // 6. Sky Visible Lines Slider Card
        renderGlassCard(guiGraphics, contentX, curY, contentW, rowH, false, false);
        guiGraphics.drawString(this.font, "Sky Visible Lines", contentX + 10, curY + 5, ThemeManager.getTextPrimary(), false);
        guiGraphics.drawString(this.font, "Number of lyric lines shown simultaneously", contentX + 10, curY + 15, ThemeManager.getTextMuted(), false);
        int skyLines = isDraggingSkyLines ? Math.round(3 + dragSkyLinesRatio * 22) : config.getSkyLyricsLength();
        float skyLinesRatio = (skyLines - 3) / 22.0f;
        boolean skyLinesHover = isMouseOver(mouseX, mouseY, controlX, curY + 6, controlW, 14);
        renderSlider(guiGraphics, controlX, curY + 8, controlW, 4, skyLinesRatio, skyLines + " lines", skyLinesHover);
        curY += rowH + 4;

        // 7. Lyrics Typography Card
        renderGlassCard(guiGraphics, contentX, curY, contentW, rowH, false, false);
        guiGraphics.drawString(this.font, "Lyrics Typography", contentX + 10, curY + 5, ThemeManager.getTextPrimary(), false);
        guiGraphics.drawString(this.font, "Font family for lyrics & UI elements", contentX + 10, curY + 15, ThemeManager.getTextMuted(), false);
        boolean fontHover = isMouseOver(mouseX, mouseY, controlX, curY + 4, controlW, 18);
        renderPillButton(guiGraphics, config.getLyricFont().getDisplayName(), controlX, curY + 4, controlW, 18, fontHover);
        curY += rowH + 4;

        // 8. Minecraft Audio Ducking Card
        renderGlassCard(guiGraphics, contentX, curY, contentW, rowH, false, false);
        guiGraphics.drawString(this.font, "Minecraft Audio Ducking", contentX + 10, curY + 5, ThemeManager.getTextPrimary(), false);
        guiGraphics.drawString(this.font, "Mute MC music & lower sounds during playback", contentX + 10, curY + 15, ThemeManager.getTextMuted(), false);
        boolean duckHover = isMouseOver(mouseX, mouseY, switchX, curY + 6, switchW, switchH);
        renderPillSwitch(guiGraphics, config.isAudioDuckingEnabled(), switchX, curY + 6, switchW, switchH, duckHover);
        curY += rowH + 4;

        // 9. Ducking Multiplier Slider Card
        renderGlassCard(guiGraphics, contentX, curY, contentW, rowH, false, false);
        guiGraphics.drawString(this.font, "Ducking Volume Multiplier", contentX + 10, curY + 5, ThemeManager.getTextPrimary(), false);
        guiGraphics.drawString(this.font, "Game volume level while Spotify is playing", contentX + 10, curY + 15, ThemeManager.getTextMuted(), false);
        int duckPercent = isDraggingDucking ? Math.round(5 + dragDuckingRatio * 95) : Math.round(config.getDuckingMultiplier() * 100);
        float duckRatio = (duckPercent - 5) / 95.0f;
        boolean duckSliderHover = isMouseOver(mouseX, mouseY, controlX, curY + 6, controlW, 14);
        renderSlider(guiGraphics, controlX, curY + 8, controlW, 4, duckRatio, duckPercent + "%", duckSliderHover);
        curY += rowH + 8;

        // 9b. Anonymous Diagnostics Card
        renderGlassCard(guiGraphics, contentX, curY, contentW, rowH, false, false);
        guiGraphics.drawString(this.font, "Anonymous Diagnostics", contentX + 10, curY + 5, ThemeManager.getTextPrimary(), false);
        guiGraphics.drawString(this.font, "Share anonymized crash and stability metrics", contentX + 10, curY + 15, ThemeManager.getTextMuted(), false);
        boolean metricsHover = isMouseOver(mouseX, mouseY, switchX, curY + 6, switchW, switchH);
        renderPillSwitch(guiGraphics, config.isMetricsEnabled(), switchX, curY + 6, switchW, switchH, metricsHover);
        curY += rowH + 4;

        // 10. Utility Actions Card (Reload Bridge & Updates)
        int btnW = (contentW - 6) / 2;
        boolean reloadHover = isMouseOver(mouseX, mouseY, contentX, curY, btnW, 22);
        renderGlassCard(guiGraphics, contentX, curY, btnW, 22, reloadHover, false);
        guiGraphics.drawCenteredString(this.font, "↺ Reload Bridge", contentX + (btnW / 2), curY + 7, reloadHover ? ThemeManager.getAccent2() : ThemeManager.getAccent());

        it.itzcrih.espoty.update.UpdateChecker updateChecker = it.itzcrih.espoty.update.UpdateChecker.getInstance();
        boolean hasUpdate = updateChecker.isUpdateAvailable();
        int btn2X = contentX + btnW + 6;
        boolean updateHover = isMouseOver(mouseX, mouseY, btn2X, curY, btnW, 22);
        renderGlassCard(guiGraphics, btn2X, curY, btnW, 22, updateHover, hasUpdate);
        String updateLabel = hasUpdate ? "Update v" + updateChecker.getLatestVersion() + " Available!" : "Check for Updates";
        guiGraphics.drawCenteredString(this.font, updateLabel, btn2X + (btnW / 2), curY + 7, hasUpdate ? 0xFF00FFA3 : (updateHover ? ThemeManager.getAccent2() : ThemeManager.getTextPrimary()));

        curY += 28;

        // Author credits link
        String credit = "§7eSpoty v" + it.itzcrih.espoty.update.UpdateChecker.getCurrentVersion() + " by §6itzCrih §8• §ehttps://itzcrih.it";
        boolean creditHover = isMouseOver(mouseX, mouseY, contentX + 2, curY, 280, 12);
        guiGraphics.drawString(this.font, credit, contentX + 2, curY, creditHover ? 0xFFFFFFFF : ThemeManager.getTextMuted(), false);

        guiGraphics.disableScissor();
    }

    // =========================================================================
    // AURORA CLIENT REUSABLE UI WIDGETS
    // =========================================================================

    private void renderGlassCard(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean active) {
        int bg = hover ? ThemeManager.getCardHoverBg() : ThemeManager.getCardBg();
        int border = active ? ThemeManager.getAccent() : (hover ? ThemeManager.applyAlpha(ThemeManager.getAccent(), 0.40f) : ThemeManager.getGlassEdge());

        // Rounded body
        g.fill(x + 2, y, x + w - 2, y + h, bg);
        g.fill(x, y + 2, x + 2, y + h - 2, bg);
        g.fill(x + w - 2, y + 2, x + w, y + h - 2, bg);

        // 1px Subtle Glass Border
        g.fill(x + 2, y, x + w - 2, y + 1, border);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, border);
        g.fill(x, y + 2, x + 1, y + h - 2, border);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, border);

        // Active halo glow
        if (active) {
            int glow = ThemeManager.getAccentDim(0.50f);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, glow);
        }
    }

    private void renderPillSwitch(GuiGraphics g, boolean enabled, int x, int y, int w, int h, boolean hover) {
        int accent = ThemeManager.getAccent();
        int bg = enabled ? (hover ? ThemeManager.getAccent2() : accent) : (hover ? 0x40FFFFFF : 0x22FFFFFF);
        int border = enabled ? accent : 0x33FFFFFF;

        // Rounded capsule track
        g.fill(x + 2, y, x + w - 2, y + h, bg);
        g.fill(x, y + 2, x + 2, y + h - 2, bg);
        g.fill(x + w - 2, y + 2, x + w, y + h - 2, bg);

        g.fill(x + 2, y, x + w - 2, y + 1, border);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, border);

        // Smooth Knob (Circle on left if disabled, right if enabled)
        int knobSize = h - 4;
        int knobX = enabled ? (x + w - knobSize - 3) : (x + 3);
        int knobY = y + 2;
        int knobCol = enabled ? 0xFF0B0F19 : 0xFFCBD5E1;

        g.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, knobCol);
    }

    private void renderSlider(GuiGraphics g, int x, int y, int w, int h, float ratio, String label, boolean hover) {
        int accent = ThemeManager.getAccent();
        int accent2 = ThemeManager.getAccent2();

        // Background track
        g.fill(x, y, x + w, y + h, 0x30FFFFFF);

        // Filled track
        int fillW = (int) (w * Math.max(0f, Math.min(1f, ratio)));
        if (fillW > 0) {
            g.fillGradient(x, y, x + fillW, y + h, accent, accent2);
        }

        // Circular knob
        int knobX = x + fillW;
        int knobCol = hover ? accent2 : accent;
        g.fill(knobX - 3, y - 3, knobX + 4, y + h + 3, knobCol);
        g.fill(knobX - 1, y - 1, knobX + 2, y + h + 1, 0xFFFFFFFF);

        // Label pill on right
        if (label != null && !label.isEmpty()) {
            g.drawString(this.font, label, x + w + 8, y - 2, ThemeManager.getTextSecondary(), false);
        }
    }

    private void renderPillButton(GuiGraphics g, String text, int x, int y, int w, int h, boolean hover) {
        int bg = hover ? ThemeManager.getAccentDim(1.0f) : 0x18FFFFFF;
        int border = hover ? ThemeManager.getAccent() : ThemeManager.getGlassEdge();

        g.fill(x + 2, y, x + w - 2, y + h, bg);
        g.fill(x, y + 2, x + 2, y + h - 2, bg);
        g.fill(x + w - 2, y + 2, x + w, y + h - 2, bg);

        g.fill(x + 2, y, x + w - 2, y + 1, border);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, border);

        g.drawCenteredString(this.font, text, x + (w / 2), y + (h / 2) - 4, hover ? ThemeManager.getAccent() : ThemeManager.getTextPrimary());
    }

    private void renderModernIconButton(GuiGraphics g, String icon, int x, int y, int w, int h, boolean hover, int color, boolean active, boolean enabled) {
        if (!enabled) {
            g.drawCenteredString(this.font, icon, x + (w / 2), y + (h / 2) - 4, 0xFF444455);
            return;
        }

        int bg = hover ? ThemeManager.getAccentDim(0.85f) : 0x15FFFFFF;
        g.fill(x + 2, y, x + w - 2, y + h, bg);
        g.fill(x, y + 2, x + 2, y + h - 2, bg);
        g.fill(x + w - 2, y + 2, x + w, y + h - 2, bg);

        g.drawCenteredString(this.font, icon, x + (w / 2), y + (h / 2) - 4, hover ? ThemeManager.getAccent() : color);

        if (active) {
            g.fill(x + (w / 2) - 2, y + h - 2, x + (w / 2) + 2, y + h, ThemeManager.getAccent());
        }
    }

    private void renderStatusBanner(GuiGraphics guiGraphics) {
        int bannerW = this.font.width(statusMessage) + 24;
        int bannerH = 18;
        int bx = modalX + (modalWidth - bannerW) / 2;
        int by = modalY + modalHeight - 24;

        int bg = ThemeManager.getPanelBg();
        guiGraphics.fill(bx + 2, by, bx + bannerW - 2, by + bannerH, bg);
        guiGraphics.fill(bx, by + 2, bx + 2, by + bannerH - 2, bg);
        guiGraphics.fill(bx + bannerW - 2, by + 2, bx + bannerW, by + bannerH - 2, bg);

        guiGraphics.fill(bx + 2, by, bx + bannerW - 2, by + 1, ThemeManager.getAccent());
        guiGraphics.drawCenteredString(this.font, statusMessage, bx + (bannerW / 2), by + 5, 0xFFFFFFFF);
    }

    private void setStatus(String msg) {
        this.statusMessage = msg;
        this.statusMessageTime = System.currentTimeMillis();
    }

    // =========================================================================
    // INTERACTION HANDLING (CLICKS, DRAGS, SCROLLS, KEYS)
    // =========================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }

        int mx = (int) event.x();
        int my = (int) event.y();

        // 1. Close Button (✕)
        int closeX = modalX + modalWidth - 18;
        int closeY = modalY + 9;
        if (isMouseOver(mx, my, closeX - 4, closeY, 16, 14)) {
            this.startCloseAnimation();
            return true;
        }

        // 2. Navigation Tabs
        int tabY = modalY + 25;
        int tabX = modalX + 16;
        for (Tab tab : Tab.values()) {
            int tabW = this.font.width(tab.getTitle()) + 12;
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
            tabX += tabW + 3;
        }

        // 3. Category Interactions
        switch (currentTab) {
            case PLAYER -> handlePlayerClick(mx, my);
            case QUEUE -> handleQueueClick(mx, my);
            case THEMES -> handleThemesClick(mx, my);
            case SETTINGS -> handleSettingsClick(mx, my);
            default -> {}
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void handlePlayerClick(int mx, int my) {
        PlaybackState state = stateManager.getCurrentState();
        int contentX = modalX + 16;
        int contentY = modalY + 52;

        if (state == null) {
            int cx = modalX + (modalWidth / 2);
            if (isMouseOver(mx, my, cx - 75, modalY + 144, 150, 24)) {
                SpotifyProcessDetector.launchSpotify();
                setStatus("Launching Spotify...");
            }
            return;
        }

        // Like Song Button
        int heartX = modalX + modalWidth - 28;
        int heartY = contentY + 3;
        if (isMouseOver(mx, my, heartX - 3, heartY - 3, 16, 16)) {
            boolean liked = stateManager.toggleCurrentTrackLiked();
            setStatus(liked ? "Added to Liked Songs ❤" : "Removed from Liked Songs ♡");
            return;
        }

        // Seek Bar Scrubbing
        int seekY = contentY + 86;
        int seekW = modalWidth - 32;
        if (state.canSeek() && isMouseOver(mx, my, contentX - 2, seekY - 4, seekW + 4, 12)) {
            this.isDraggingSeek = true;
            this.dragSeekRatio = Math.max(0f, Math.min(1f, (float) (mx - contentX) / seekW));
            return;
        }

        // Controls
        int controlsY = seekY + 25;
        int centerX = modalX + (modalWidth / 2);
        int btnSize = 22;

        // Shuffle (🔀)
        if (state.canShuffle() && isMouseOver(mx, my, centerX - 90, controlsY, btnSize, btnSize)) {
            stateManager.toggleShuffle();
            setStatus("Toggled Shuffle");
            return;
        }

        // Prev (⏮)
        if (state.canPrev() && isMouseOver(mx, my, centerX - 50, controlsY, btnSize, btnSize)) {
            stateManager.previous();
            setStatus("Previous Track");
            return;
        }

        // Play/Pause (▶ / ❚❚)
        if (state.canPlayPause() && isMouseOver(mx, my, centerX - 18, controlsY - 2, 36, 26)) {
            stateManager.togglePlayPause();
            setStatus(state.isPlaying() ? "Paused" : "Playing");
            return;
        }

        // Next (⏭)
        if (state.canNext() && isMouseOver(mx, my, centerX + 28, controlsY, btnSize, btnSize)) {
            stateManager.next();
            setStatus("Next Track");
            return;
        }

        // Repeat (🔁)
        if (state.canRepeat() && isMouseOver(mx, my, centerX + 68, controlsY, btnSize, btnSize)) {
            stateManager.cycleRepeat();
            setStatus("Cycled Repeat Mode");
            return;
        }

        // Volume Controls
        int volY = controlsY + 32;
        int volBarW = 110;
        int volStartX = centerX - 80;
        int volBarX = volStartX + 18;

        // Speaker Mute Click
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
            this.dragVolumeRatio = Math.max(0f, Math.min(1f, (float) (mx - volBarX) / volBarW));
            int targetVol = (int) (dragVolumeRatio * 100);
            stateManager.setVolume(targetVol);
            setStatus("Volume: " + targetVol + "%");
        }
    }

    private void handleQueueClick(int mx, int my) {
        int contentX = modalX + 16;
        int contentY = modalY + 52;
        int currentY = contentY + 10 - queueScrollY;
        if (stateManager.getCurrentState() != null) {
            currentY += 60;
        }
        int refBtnX = contentX + 10;
        int refBtnY = currentY + 48;
        if (isMouseOver(mx, my, refBtnX, refBtnY, 160, 24)) {
            stateManager.fullReload();
            setStatus("Bridge Reloaded!");
        }
    }

    private void handleThemesClick(int mx, int my) {
        int contentX = modalX + 16;
        int contentY = modalY + 52;
        int contentW = modalWidth - 32;

        int curY = contentY + 2 - themesScrollY;
        curY += 14;

        // 1. Theme Look Cards
        ThemeLook[] looks = ThemeLook.values();
        int cardW = (contentW - 6) / 2;
        int cardH = 34;

        for (int i = 0; i < looks.length; i++) {
            ThemeLook look = looks[i];
            int col = i % 2;
            int row = i / 2;
            int cardX = contentX + (col * (cardW + 6));
            int cardY = curY + (row * (cardH + 5));

            if (isMouseOver(mx, my, cardX, cardY, cardW, cardH)) {
                config.setThemeLook(look);
                setStatus("Theme: " + look.getTitle());
                return;
            }
        }

        curY += (looks.length / 2) * (cardH + 5) + 8;
        curY += 14;

        // 2. Accent Color Palette Swatches
        ThemeAccent[] accents = ThemeAccent.values();
        int swatchSize = 20;
        int swatchSpacing = (contentW - (accents.length * swatchSize)) / (accents.length - 1);

        for (int i = 0; i < accents.length; i++) {
            ThemeAccent a = accents[i];
            int sx = contentX + (i * (swatchSize + swatchSpacing));
            int sy = curY;

            if (isMouseOver(mx, my, sx - 2, sy - 2, swatchSize + 4, swatchSize + 4)) {
                config.setThemeAccent(a);
                setStatus("Accent: " + a.getTitle());
                return;
            }
        }

        curY += swatchSize + 14;
        curY += 13;

        // 3. Glass Opacity Slider
        int cardRowH = 26;
        int sliderW = 120;
        int sliderX = contentX + contentW - sliderW - 10;
        if (isMouseOver(mx, my, sliderX - 4, curY + 4, sliderW + 8, 16)) {
            this.isDraggingGlassOpacity = true;
            float ratio = Math.max(0f, Math.min(1f, (float) (mx - sliderX) / sliderW));
            this.dragGlassOpacityRatio = ratio;
            config.setGlassOpacity(0.40f + (ratio * 0.60f));
            setStatus("Glass Opacity: " + config.getGlassOpacityPercent() + "%");
            return;
        }

        curY += cardRowH + 6;

        // 4. Sync HUD & Sky Switch
        int switchW = 44;
        int switchH = 14;
        int switchX = contentX + contentW - switchW - 10;
        if (isMouseOver(mx, my, switchX, curY + 6, switchW, switchH)) {
            config.setSyncHudTheme(!config.isSyncHudTheme());
            setStatus("Sync Theme: " + (config.isSyncHudTheme() ? "Enabled" : "Disabled"));
        }
    }

    private void handleSettingsClick(int mx, int my) {
        int contentX = modalX + 16;
        int contentY = modalY + 52;
        int contentW = modalWidth - 32;

        int curY = contentY + 2 - settingsScrollY;
        int rowH = 26;
        int controlW = 120;
        int controlX = contentX + contentW - controlW - 10;
        int switchW = 44;
        int switchH = 14;
        int switchX = contentX + contentW - switchW - 10;

        // 1. In-Game HUD Toggle
        if (isMouseOver(mx, my, switchX, curY + 6, switchW, switchH)) {
            config.setHudPosition(config.getHudPosition() == ModConfig.HudPosition.OFF ? ModConfig.HudPosition.TOP_RIGHT : ModConfig.HudPosition.OFF);
            setStatus("In-Game HUD: " + (config.getHudPosition() != ModConfig.HudPosition.OFF ? "Enabled" : "Disabled"));
            return;
        }
        curY += rowH + 4;

        // 1b. HUD Lyrics Line Toggle
        if (isMouseOver(mx, my, switchX, curY + 6, switchW, switchH)) {
            config.setHudShowLyrics(!config.isHudShowLyrics());
            setStatus("HUD Lyrics Line: " + (config.isHudShowLyrics() ? "Enabled" : "Disabled"));
            return;
        }
        curY += rowH + 4;

        // 2. HUD Opacity Slider Click
        if (isMouseOver(mx, my, controlX - 2, curY + 4, controlW + 4, 14)) {
            this.isDraggingHudOpacity = true;
            float ratio = Math.max(0f, Math.min(1f, (float) (mx - controlX) / controlW));
            this.dragHudOpacityRatio = ratio;
            config.setHudOpacity(ratio);
            setStatus("HUD Opacity: " + config.getHudOpacityPercent() + "%");
            return;
        }
        curY += rowH + 4;

        // 3. HUD Scale Slider Click
        if (isMouseOver(mx, my, controlX - 2, curY + 4, controlW + 4, 14)) {
            this.isDraggingHudScale = true;
            float ratio = Math.max(0f, Math.min(1f, (float) (mx - controlX) / controlW));
            this.dragHudScaleRatio = ratio;
            config.setHudScalePercent(Math.round(50 + ratio * 100));
            setStatus("HUD Scale: " + config.getHudScalePercent() + "%");
            return;
        }
        curY += rowH + 4;

        // 4. 360° Sky Lyrics Toggle
        if (isMouseOver(mx, my, switchX, curY + 6, switchW, switchH)) {
            config.setSkyLyricsEnabled(!config.isSkyLyricsEnabled());
            setStatus(config.isSkyLyricsEnabled() ? "Sky Lyrics: Enabled" : "Sky Lyrics: Disabled");
            return;
        }
        curY += rowH + 4;

        // 4b. Sky Lyrics Direction Button Click
        if (isMouseOver(mx, my, controlX, curY + 4, controlW, 18)) {
            config.setSkyLyricsMode(config.getSkyLyricsMode().next());
            setStatus("Sky Direction: " + config.getSkyLyricsMode().getDisplayName());
            return;
        }
        curY += rowH + 4;

        // 5. Sky Scale Slider Click
        if (isMouseOver(mx, my, controlX - 2, curY + 4, controlW + 4, 14)) {
            this.isDraggingSkyScale = true;
            float ratio = Math.max(0f, Math.min(1f, (float) (mx - controlX) / controlW));
            this.dragSkyScaleRatio = ratio;
            config.setSkyLyricsScalePercent(Math.round(50 + ratio * 150));
            setStatus("Sky Scale: " + config.getSkyLyricsScalePercent() + "%");
            return;
        }
        curY += rowH + 4;

        // 6. Sky Visible Lines Slider Click
        if (isMouseOver(mx, my, controlX - 2, curY + 4, controlW + 4, 14)) {
            this.isDraggingSkyLines = true;
            float ratio = Math.max(0f, Math.min(1f, (float) (mx - controlX) / controlW));
            this.dragSkyLinesRatio = ratio;
            config.setSkyLyricsLength(Math.round(3 + ratio * 22));
            setStatus("Sky Lines: " + config.getSkyLyricsLength() + " lines");
            return;
        }
        curY += rowH + 4;

        // 7. Typography Button Click
        if (isMouseOver(mx, my, controlX, curY + 4, controlW, 18)) {
            config.setLyricFont(config.getLyricFont().next());
            setStatus("Font: " + config.getLyricFont().getDisplayName());
            return;
        }
        curY += rowH + 4;

        // 8. Audio Ducking Toggle
        if (isMouseOver(mx, my, switchX, curY + 6, switchW, switchH)) {
            config.setAudioDuckingEnabled(!config.isAudioDuckingEnabled());
            setStatus("Ducking: " + (config.isAudioDuckingEnabled() ? "Enabled" : "Disabled"));
            return;
        }
        curY += rowH + 4;

        // 9. Ducking Multiplier Slider Click
        if (isMouseOver(mx, my, controlX - 2, curY + 4, controlW + 4, 14)) {
            this.isDraggingDucking = true;
            float ratio = Math.max(0f, Math.min(1f, (float) (mx - controlX) / controlW));
            this.dragDuckingRatio = ratio;
            config.setDuckingMultiplier(0.05f + (ratio * 0.95f));
            setStatus("Ducking Volume: " + Math.round(config.getDuckingMultiplier() * 100) + "%");
            return;
        }
        curY += rowH + 8;

        // 9b. Anonymous Diagnostics Toggle
        if (isMouseOver(mx, my, switchX, curY + 6, switchW, switchH)) {
            config.setMetricsEnabled(!config.isMetricsEnabled());
            setStatus("Diagnostics: " + (config.isMetricsEnabled() ? "Enabled" : "Disabled"));
            return;
        }
        curY += rowH + 4;

        // 10. Reload Bridge
        int btnW = (contentW - 6) / 2;
        if (isMouseOver(mx, my, contentX, curY, btnW, 22)) {
            stateManager.fullReload();
            setStatus("Bridge Reloaded!");
            return;
        }

        // 11. GitHub Updates
        int btn2X = contentX + btnW + 6;
        if (isMouseOver(mx, my, btn2X, curY, btnW, 22)) {
            it.itzcrih.espoty.update.UpdateChecker checker = it.itzcrih.espoty.update.UpdateChecker.getInstance();
            if (checker.isUpdateAvailable()) {
                it.itzcrih.espoty.update.UpdateChecker.openWebpage(checker.getReleaseUrl());
                setStatus("Opening release page...");
            } else {
                setStatus("Checking for updates...");
                checker.checkForUpdatesAsync().thenAccept(avail -> {
                    setStatus(avail ? "Update available: v" + checker.getLatestVersion() : "eSpoty is up to date (v" + it.itzcrih.espoty.update.UpdateChecker.getCurrentVersion() + ")");
                });
            }
            return;
        }

        curY += 28;

        // 12. Author Website
        if (isMouseOver(mx, my, contentX + 2, curY, 280, 12)) {
            it.itzcrih.espoty.update.UpdateChecker.openWebpage("https://itzcrih.it");
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0) {
            int mx = (int) event.x();
            int contentX = modalX + 16;
            int contentW = modalWidth - 32;

            if (isDraggingSeek) {
                this.dragSeekRatio = Math.max(0f, Math.min(1f, (float) (mx - contentX) / contentW));
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

            int controlW = 120;
            int controlX = contentX + contentW - controlW - 10;

            if (isDraggingHudOpacity) {
                this.dragHudOpacityRatio = Math.max(0f, Math.min(1f, (float) (mx - controlX) / controlW));
                config.setHudOpacity(dragHudOpacityRatio);
                setStatus("HUD Opacity: " + config.getHudOpacityPercent() + "%");
                return true;
            }

            if (isDraggingHudScale) {
                this.dragHudScaleRatio = Math.max(0f, Math.min(1f, (float) (mx - controlX) / controlW));
                config.setHudScalePercent(Math.round(50 + dragHudScaleRatio * 100));
                setStatus("HUD Scale: " + config.getHudScalePercent() + "%");
                return true;
            }

            if (isDraggingSkyScale) {
                this.dragSkyScaleRatio = Math.max(0f, Math.min(1f, (float) (mx - controlX) / controlW));
                config.setSkyLyricsScalePercent(Math.round(50 + dragSkyScaleRatio * 150));
                setStatus("Sky Scale: " + config.getSkyLyricsScalePercent() + "%");
                return true;
            }

            if (isDraggingSkyLines) {
                this.dragSkyLinesRatio = Math.max(0f, Math.min(1f, (float) (mx - controlX) / controlW));
                config.setSkyLyricsLength(Math.round(3 + dragSkyLinesRatio * 22));
                setStatus("Sky Lines: " + config.getSkyLyricsLength() + " lines");
                return true;
            }

            if (isDraggingGlassOpacity) {
                this.dragGlassOpacityRatio = Math.max(0f, Math.min(1f, (float) (mx - controlX) / controlW));
                config.setGlassOpacity(0.40f + (dragGlassOpacityRatio * 0.60f));
                setStatus("Glass Opacity: " + config.getGlassOpacityPercent() + "%");
                return true;
            }

            if (isDraggingDucking) {
                this.dragDuckingRatio = Math.max(0f, Math.min(1f, (float) (mx - controlX) / controlW));
                config.setDuckingMultiplier(0.05f + (dragDuckingRatio * 0.95f));
                setStatus("Ducking Volume: " + Math.round(config.getDuckingMultiplier() * 100) + "%");
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
            if (isDraggingGlassOpacity) {
                config.setGlassOpacity(0.40f + (dragGlassOpacityRatio * 0.60f));
                isDraggingGlassOpacity = false;
            }
            if (isDraggingDucking) {
                config.setDuckingMultiplier(0.05f + (dragDuckingRatio * 0.95f));
                isDraggingDucking = false;
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            int delta = (int) (-verticalAmount * 16);
            switch (currentTab) {
                case SETTINGS -> settingsScrollY = Math.max(0, settingsScrollY + delta);
                case THEMES -> themesScrollY = Math.max(0, themesScrollY + delta);
                case LYRICS -> lyricsScrollY = Math.max(0, lyricsScrollY + delta);
                case QUEUE -> queueScrollY = Math.max(0, queueScrollY + delta);
                default -> {}
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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

        if (it.itzcrih.espoty.client.ESpotyModClient.getKeyOpenGui() != null
                && it.itzcrih.espoty.client.ESpotyModClient.getKeyOpenGui().matches(event)) {
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
