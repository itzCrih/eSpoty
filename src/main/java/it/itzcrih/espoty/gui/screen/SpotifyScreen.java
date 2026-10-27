package it.itzcrih.espoty.gui.screen;

import it.itzcrih.espoty.config.ModConfig;
import it.itzcrih.espoty.gui.render.AaRoundRect;
import it.itzcrih.espoty.gui.theme.ThemeManager;
import it.itzcrih.espoty.gui.theme.ThemeManager.ThemeAccent;
import it.itzcrih.espoty.gui.theme.ThemeManager.ThemeLook;
import it.itzcrih.espoty.gui.widget.AudioWaveformRenderer;
import it.itzcrih.espoty.lyrics.LyricLine;
import it.itzcrih.espoty.lyrics.LyricsManager;
import it.itzcrih.espoty.spotify.ArtworkFinder;
import it.itzcrih.espoty.spotify.SpotifyProcessDetector;
import it.itzcrih.espoty.spotify.SpotifyStateManager;
import it.itzcrih.espoty.spotify.models.PlaybackState;
import it.itzcrih.espoty.spotify.models.QueueItem;
import it.itzcrih.espoty.spotify.models.QueueResponse;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.util.Collections;
import java.util.List;

/**
 * In-game control hub for eSpoty.
 * Complete with:
 * - Left Navigation Rail (110px) with custom HD logo, active glowing accent bar, and connection status pill.
 * - Group Container Cards with 9px/10px SDF rounded corners and subtle hairline dividers.
 * - Circular Thumb Sliders (4px pill track, 10px white circular knob, dedicated numeric tag badges).
 * - Full-pill Toggle Switches (32x16px, 10px circular knob).
 * - Smooth SDF Anti-Aliasing (AaRoundRect) across all borders and elements.
 * - Guaranteed zero text cut-off: 25px safe margin between slider badges and scrollbars.
 */
public class SpotifyScreen extends Screen {

    public enum Tab {
        PLAYER("Player", ">", "Desktop media playback & controls"),
        LYRICS("Lyrics", "=", "Synchronized real-time song lyrics"),
        QUEUE("Queue", "#", "Next tracks & queue management"),
        HUD_EDITOR("Hud Editor", "+", "Live in-game HUD positioning & scaling"),
        THEMES("Themes", "*", "Liquid glass atmosphere & accents"),
        SETTINGS("Settings", "@", "In-game HUD, 3D celestial sky & ducking");

        private final String title;
        private final String icon;
        private final String subtitle;

        Tab(String title, String icon, String subtitle) {
            this.title = title;
            this.icon = icon;
            this.subtitle = subtitle;
        }

        public String getTitle() { return title; }
        public String getIcon() { return icon; }
        public String getSubtitle() { return subtitle; }
    }

    private Tab activeTab = Tab.PLAYER;
    private final ModConfig config;
    private final SpotifyStateManager stateManager;

    // Window Dimensions
    private int modalX;
    private int modalY;
    private int modalWidth = 480;
    private int modalHeight = 315;
    private int sidebarWidth = 110;

    // Slider constants
    private static final int SLIDER_TRACK_W = 80;
    private static final int BADGE_TAG_W = 38;

    // Content total heights for exact scrolling boundaries
    private static final int THEMES_CONTENT_HEIGHT = 414;
    private static final int SETTINGS_CONTENT_HEIGHT = 612;

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

    // Scroll states
    private int lyricsScrollY = 0;
    private int queueScrollY = 0;
    private int themesScrollY = 0;
    private int settingsScrollY = 0;

    // Animations & timing
    private long openStartTime;
    private long closeStartTime = 0;
    private boolean isClosing = false;
    private static final int OPEN_ANIM_MS = 200;
    private static final int CLOSE_ANIM_MS = 150;
    private long tabSwitchStartTime = 0;

    // Ephemeral Toast Banner
    private String statusMessage = "";
    private long statusMessageTime = 0;

    // Mod logo identifier
    private static final Identifier LOGO_ICON = Identifier.fromNamespaceAndPath("espoty", "icon.png");

    public SpotifyScreen() {
        super(Component.literal("eSpoty Hub"));
        this.config = ModConfig.getInstance();
        this.stateManager = SpotifyStateManager.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        this.openStartTime = System.currentTimeMillis();
        this.closeStartTime = 0;
        this.isClosing = false;

        this.modalX = (this.width - this.modalWidth) / 2;
        this.modalY = (this.height - this.modalHeight) / 2;

        this.dragSkyLinesRatio = (config.getSkyLyricsLength() - 3) / 22.0f;
        this.dragSkyScaleRatio = (config.getSkyLyricsScalePercent() - 50) / 150.0f;
        this.dragHudOpacityRatio = config.getHudOpacity();
        this.dragHudScaleRatio = (config.getHudScalePercent() - 50) / 100.0f;
        this.dragGlassOpacityRatio = (config.getGlassOpacity() - 0.40f) / 0.60f;
        this.dragDuckingRatio = (config.getDuckingMultiplier() - 0.05f) / 0.95f;
        this.stateManager.requestQueueRefresh();

        PlaybackState state = stateManager.getCurrentState();
        if (state != null && state.getVolumePercent() >= 0) {
            this.dragVolumeRatio = state.getVolumePercent() / 100.0f;
        } else {
            this.dragVolumeRatio = config.getVolume() / 100.0f;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();

        // 1. Subtle dark background backdrop blur & dim
        guiGraphics.fill(0, 0, this.width, this.height, 0x88000000);

        // Smooth entry/exit animation
        float animProgress = isClosing
                ? Math.max(0f, 1f - ((now - closeStartTime) / (float) CLOSE_ANIM_MS))
                : Math.min(1f, (now - openStartTime) / (float) OPEN_ANIM_MS);

        if (isClosing && animProgress <= 0.001f) {
            this.minecraft.setScreen(null);
            return;
        }

        // Cubic ease-out
        float scale = isClosing
                ? 0.92f + 0.08f * animProgress
                : 0.92f + 0.08f * (1f - (float) Math.pow(1f - animProgress, 3));

        guiGraphics.pose().pushMatrix();
        float centerX = this.width / 2.0f;
        float centerY = this.height / 2.0f;
        guiGraphics.pose().translate(centerX, centerY);
        guiGraphics.pose().scale(scale, scale);
        guiGraphics.pose().translate(-centerX, -centerY);

        // 2. Render Main Window Frame
        renderWindowFrame(guiGraphics, modalX, modalY, modalWidth, modalHeight);

        // 3. Render Left Navigation Rail
        renderSidebar(guiGraphics, modalX, modalY, sidebarWidth, modalHeight, mouseX, mouseY);

        // 4. Render Content Header & Close Button
        renderContentHeader(guiGraphics, mouseX, mouseY);

        // 5. Render Active Content Tab with smooth lightweight transition
        long tabElapsed = now - tabSwitchStartTime;
        float tabAnimProgress = Math.min(1.0f, tabElapsed / 140.0f);
        float tabEase = 1.0f - (float) Math.pow(1.0f - tabAnimProgress, 3);
        float tabSlideY = (1.0f - tabEase) * 4.0f;

        guiGraphics.pose().pushMatrix();
        if (tabAnimProgress < 1.0f) {
            guiGraphics.pose().translate(0, tabSlideY);
        }

        switch (activeTab) {
            case PLAYER -> renderPlayerTab(guiGraphics, mouseX, mouseY);
            case LYRICS -> renderLyricsTab(guiGraphics, mouseX, mouseY);
            case QUEUE -> renderQueueTab(guiGraphics, mouseX, mouseY);
            case HUD_EDITOR -> renderHudEditorTab(guiGraphics, mouseX, mouseY);
            case THEMES -> renderThemesTab(guiGraphics, mouseX, mouseY);
            case SETTINGS -> renderSettingsTab(guiGraphics, mouseX, mouseY);
        }

        guiGraphics.pose().popMatrix();

        // 6. Ephemeral Toast Status Banner
        if (now - statusMessageTime < 2400 && !statusMessage.isEmpty()) {
            renderStatusBanner(guiGraphics);
        }

        guiGraphics.pose().popMatrix();
    }

    // =========================================================================
    // SHELL & NAVIGATION RAIL
    // =========================================================================

    private void renderWindowFrame(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        // Multi-stage soft drop shadow
        AaRoundRect.draw(guiGraphics, x - 6, y - 4, w + 12, h + 14, 16, 0x14000000, 0);
        AaRoundRect.draw(guiGraphics, x - 3, y - 2, w + 6, h + 8, 14, 0x22000000, 0);

        // Main Liquid Glass Window Body
        AaRoundRect.draw(guiGraphics, x, y, w, h, 12, ThemeManager.getWindowBg(), ThemeManager.getGlassEdge());

        // Specular top highlight sheen
        int sheen1 = ThemeManager.getSheen();
        int sheen2 = ThemeManager.applyAlpha(ThemeManager.getAccent2(), 0.08f);
        guiGraphics.fillGradient(x + 12, y + 1, x + w - 12, y + 2, sheen1, sheen2);
    }

    private void renderSidebar(GuiGraphics guiGraphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        // Subtle darker tinted sidebar backdrop
        AaRoundRect.draw(guiGraphics, x + 2, y + 2, w - 2, h - 4, 10, 0x26000000, 0);

        // Header Logo & Title
        int logoX = x + 10;
        int logoY = y + 12;
        int logoSize = 26;
        int logoRadius = 6;

        AaRoundRect.draw(guiGraphics, logoX, logoY, logoSize, logoSize, logoRadius, 0x33000000, 0);

        try {
            guiGraphics.blit(LOGO_ICON, logoX, logoY, logoX + logoSize, logoY + logoSize, 0f, 1f, 0f, 1f);
        } catch (Exception ignored) {
            guiGraphics.drawString(this.font, "*", logoX + 2, logoY + 4, ThemeManager.getAccent(), true);
        }

        AaRoundRect.draw(guiGraphics, logoX, logoY, logoSize, logoSize, logoRadius, 0, ThemeManager.applyAlpha(ThemeManager.getGlassEdge(), 0.85f));

guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(x + 40, y + 19);
        guiGraphics.pose().scale(1.35f, 1.35f);
        guiGraphics.drawString(this.font, "eSpoty", 0, 0, 0xFFFFFFFF, true);
        guiGraphics.pose().popMatrix();

        // Navigation Tabs Stack
        Tab[] tabs = Tab.values();
        int startY = y + 54;
        int tabH = 30;
        int tabGap = 3;
        int tabX = x + 8;
        int tabW = w - 16;

        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            int itemY = startY + (i * (tabH + tabGap));
            boolean isActive = (tab == activeTab);
            boolean hover = isMouseOver(mouseX, mouseY, tabX, itemY, tabW, tabH);

            int bg = isActive ? ThemeManager.getAccentDim(0.24f) : (hover ? 0x1AFFFFFF : 0);
            int border = isActive ? ThemeManager.applyAlpha(ThemeManager.getAccent(), 0.65f) : (hover ? 0x24FFFFFF : 0);

            AaRoundRect.draw(guiGraphics, tabX, itemY, tabW, tabH, 6, bg, border);

            // Left Active Glow Bar
            if (isActive) {
                AaRoundRect.draw(guiGraphics, tabX + 2, itemY + 5, 3, tabH - 10, 1, ThemeManager.getAccent(), 0);
            }

            // Tab Icon & Label
            int textColor = isActive ? ThemeManager.getAccent() : (hover ? 0xFFFFFFFF : ThemeManager.getTextSecondary());
            guiGraphics.drawString(this.font, tab.getIcon(), tabX + 9, itemY + 8, textColor, true);
            guiGraphics.drawString(this.font, tab.getTitle(), tabX + 27, itemY + 8, textColor, isActive);
        }



        // Vertical Glass Divider Seam between Sidebar and Content
        guiGraphics.fill(x + w, y + 8, x + w + 1, y + h - 8, 0x18FFFFFF);
    }

    private void renderContentHeader(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + sidebarWidth + 14;
        int contentW = modalWidth - sidebarWidth - 26;

        // Active Section Title with Star Glyph (Centered vertically with close button)
        guiGraphics.drawString(this.font, activeTab.getTitle().toUpperCase(), contentX, modalY + 16, ThemeManager.getAccent(), true);

        // Sleek Circular Glass Close Button (turns smooth red on hover)
        int closeSize = 16;
        int closeX = modalX + modalWidth - closeSize - 12;
        int closeY = modalY + 12;
        boolean closeHover = isMouseOver(mouseX, mouseY, closeX - 2, closeY - 2, closeSize + 4, closeSize + 4);

        int closeBg = closeHover ? 0x44FF3333 : 0x14FFFFFF;
        int closeBorder = closeHover ? 0xFFFF4444 : 0x28FFFFFF;
        int closeText = closeHover ? 0xFFFF5555 : 0xFFCBD5E1;

        AaRoundRect.draw(guiGraphics, closeX, closeY, closeSize, closeSize, closeSize / 2, closeBg, closeBorder);
        guiGraphics.drawCenteredString(this.font, "x", closeX + (closeSize / 2), closeY + 4, closeText);

        // Hairline separator below header
        guiGraphics.fillGradient(contentX, modalY + 38, modalX + modalWidth - 12, modalY + 39, ThemeManager.getGlassEdge(), 0x05FFFFFF);
    }

    // =========================================================================
    // TAB 1: PLAYER
    // =========================================================================

    private void renderPlayerTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + sidebarWidth + 14;
        int contentW = modalWidth - sidebarWidth - 28;
        int contentY = modalY + 46;
        int accent = ThemeManager.getAccent();
        int accent2 = ThemeManager.getAccent2();

        PlaybackState state = stateManager.getCurrentState();

        if (state == null || !state.isPlaying() && (state.getTrackName() == null || state.getTrackName().isEmpty())) {
            int cx = contentX + (contentW / 2);
            renderGlassCard(guiGraphics, contentX, contentY + 24, contentW, 110, false, false);
            guiGraphics.drawCenteredString(this.font, "No active Spotify track detected.", cx, contentY + 54, ThemeManager.getTextSecondary());
            guiGraphics.drawCenteredString(this.font, "Start Spotify and play any song to begin listening.", cx, contentY + 70, accent);

            int btnW = 120;
            int btnH = 22;
            int btnX = cx - (btnW / 2);
            int btnY = contentY + 92;
            boolean btnHover = isMouseOver(mouseX, mouseY, btnX, btnY, btnW, btnH);
            renderPillButton(guiGraphics, "Launch Spotify Desktop", btnX, btnY, btnW, btnH, btnHover);
            return;
        }

        // 1. Album Artwork Card
        int artSize = 64;
        int artX = contentX + 6;
        int artY = contentY + 6;
        AaRoundRect.draw(guiGraphics, artX, artY, artSize, artSize, 6, 0x33000000, ThemeManager.getGlassEdge());

        Identifier texture = (state.getAlbumArtUrl() != null && !state.getAlbumArtUrl().isEmpty()) ? it.itzcrih.espoty.gui.texture.AlbumArtManager.getInstance().getAlbumTexture(state.getAlbumArtUrl()) : null;
        if (texture != null) {
            guiGraphics.blit(texture, artX + 1, artY + 1, artX + artSize - 1, artY + artSize - 1, 0.0f, 1.0f, 0.0f, 1.0f);
        } else {
            guiGraphics.drawCenteredString(this.font, "*", artX + (artSize / 2), artY + (artSize / 2) - 4, accent);
        }

        // 2. Track Title, Artist, Album Info
        int infoX = artX + artSize + 12;
        int infoMaxW = contentW - artSize - 42;

        String trackTitle = trimWithEllipsis(state.getTrackName(), infoMaxW);
        guiGraphics.drawString(this.font, trackTitle, infoX, contentY + 8, 0xFFFFFFFF, true);

        String artist = trimWithEllipsis(state.getArtistNames(), infoMaxW);
        guiGraphics.drawString(this.font, artist, infoX, contentY + 23, ThemeManager.getTextPrimary(), false);

        String album = state.getAlbumName();
        if (album != null && !album.isEmpty()) {
            album = trimWithEllipsis(album, infoMaxW);
            guiGraphics.drawString(this.font, album, infoX, contentY + 38, ThemeManager.getTextSecondary(), false);
        }

        // Like Heart Icon
        boolean isLiked = stateManager.isCurrentTrackLiked();
        int heartX = contentX + contentW - 18;
        int heartY = contentY + 8;
        boolean heartHover = isMouseOver(mouseX, mouseY, heartX - 4, heartY - 4, 18, 18);
        int heartColor = isLiked ? accent : (heartHover ? accent2 : ThemeManager.getTextMuted());
        guiGraphics.drawString(this.font, isLiked ? "[+]" : "[-]", heartX, heartY, heartColor, isLiked);

        // Device indicator tag
        if (state.getDeviceName() != null && !state.getDeviceName().isEmpty()) {
            String devStr = "Device: " + state.getDeviceName();
            guiGraphics.drawString(this.font, trimWithEllipsis(devStr, 130), infoX, contentY + 54, ThemeManager.getTextMuted(), false);
        }

        // 3. Progress Seek Bar
        int seekY = contentY + 82;
        int seekW = contentW - 8;
        int seekX = contentX + 4;
        long progressMs = isDraggingSeek ? (long) (state.getDurationMs() * dragSeekRatio) : stateManager.getInterpolatedProgressMs();
        float progressRatio = (state.getDurationMs() > 0) ? (progressMs / (float) state.getDurationMs()) : 0f;
        boolean seekHover = isMouseOver(mouseX, mouseY, seekX - 2, seekY - 4, seekW + 4, 12);

        // Seek background track (4px thin pill)
        AaRoundRect.draw(guiGraphics, seekX, seekY, seekW, 4, 2, 0x24FFFFFF, 0);

        // Filled progress track
        int seekFillW = (int) (seekW * Math.max(0f, Math.min(1f, progressRatio)));
        if (seekFillW > 0) {
            AaRoundRect.draw(guiGraphics, seekX, seekY, Math.max(4, seekFillW), 4, 2, accent, 0);
        }

        // Pure white circular knob
        int seekKnobX = seekX + seekFillW;
        AaRoundRect.draw(guiGraphics, seekKnobX - 5, seekY - 3, 10, 10, 5, 0xFFFFFFFF, seekHover ? accent2 : 0x40000000);

        // Time timestamps below seek bar
        String curTime = PlaybackState.formatTime(progressMs);
        String totTime = PlaybackState.formatTime(state.getDurationMs());
        guiGraphics.drawString(this.font, curTime, seekX, seekY + 7, ThemeManager.getTextSecondary(), false);
        int totX = seekX + seekW - this.font.width(totTime);
        guiGraphics.drawString(this.font, totTime, totX, seekY + 7, ThemeManager.getTextSecondary(), false);

        // 4. Media Playback Controls (Perfect Circles, Symmetric & Vector Icons)
        int controlsY = seekY + 22;
        int centerX = contentX + (contentW / 2);
        int centerY = controlsY + 14;
        int btnSize = 22;
        int btnCenterY = centerY - (btnSize / 2);

        int playSize = 30;
        int playX = centerX - (playSize / 2);
        int playY = centerY - (playSize / 2);

        int prevX = playX - 10 - btnSize;
        int nextX = playX + playSize + 10;

        int shufX = prevX - 10 - btnSize;
        int repX = nextX + btnSize + 10;

        // Shuffle
        boolean canShuf = state.canShuffle();
        boolean isShuf = state.isShuffleState();
        boolean shufHover = isMouseOver(mouseX, mouseY, shufX, btnCenterY, btnSize, btnSize);
        int shufColor = canShuf ? (isShuf ? accent : ThemeManager.getTextSecondary()) : 0xFF555566;
        renderModernIconButton(guiGraphics, "SHUF", shufX, btnCenterY, btnSize, btnSize, shufHover, shufColor, isShuf, canShuf);

        // Previous (Vector Stop Bar + Left Triangle)
        boolean canPrev = state.canPrev();
        boolean prevHover = isMouseOver(mouseX, mouseY, prevX, btnCenterY, btnSize, btnSize);
        int prevColor = canPrev ? (prevHover ? 0xFFFFFFFF : ThemeManager.getTextPrimary()) : 0xFF555566;
        renderModernIconButton(guiGraphics, "PREV", prevX, btnCenterY, btnSize, btnSize, prevHover, prevColor, false, canPrev);

        // Play / Pause (Large Circular Button 30x30 with Precision Vector Glyphs)
        boolean canPlay = state.canPlayPause();
        boolean playHover = isMouseOver(mouseX, mouseY, playX, playY, playSize, playSize);
        int playBg = playHover ? accent2 : accent;
        AaRoundRect.draw(guiGraphics, playX, playY, playSize, playSize, playSize / 2, playBg, 0);
        String playIconType = state.isPlaying() ? "PAUSE" : "PLAY";
        renderControlIcon(guiGraphics, playIconType, centerX, centerY, 0xFF0B0F19);

        // Next (Vector Right Triangle + Stop Bar)
        boolean canNext = state.canNext();
        boolean nextHover = isMouseOver(mouseX, mouseY, nextX, btnCenterY, btnSize, btnSize);
        int nextColor = canNext ? (nextHover ? 0xFFFFFFFF : ThemeManager.getTextPrimary()) : 0xFF555566;
        renderModernIconButton(guiGraphics, "NEXT", nextX, btnCenterY, btnSize, btnSize, nextHover, nextColor, false, canNext);

        // Repeat
        boolean canRep = state.canRepeat();
        String repState = state.getRepeatState();
        boolean isRep = !"off".equalsIgnoreCase(repState);
        boolean repHover = isMouseOver(mouseX, mouseY, repX, btnCenterY, btnSize, btnSize);
        int repColor = canRep ? (isRep ? accent : ThemeManager.getTextSecondary()) : 0xFF555566;
        String repIcon = "track".equalsIgnoreCase(repState) ? "REP1" : "REP";
        renderModernIconButton(guiGraphics, repIcon, repX, btnCenterY, btnSize, btnSize, repHover, repColor, isRep, canRep);

        // 5. Volume Slider
        if (!isDraggingVolume && state.getVolumePercent() >= 0) {
            this.dragVolumeRatio = Math.max(0f, Math.min(1f, state.getVolumePercent() / 100.0f));
        }
        int volY = controlsY + 36;
        int volBarW = 100;
        int volBarX = centerX - (volBarW / 2);
        boolean volHover = isMouseOver(mouseX, mouseY, volBarX, volY, volBarW, 12);
        int curVol = Math.round(dragVolumeRatio * 100);

        guiGraphics.drawString(this.font, "VOL", volBarX - 22, volY - 2, volHover ? 0xFFFFFFFF : ThemeManager.getTextSecondary(), false);

        // Volume track
        AaRoundRect.draw(guiGraphics, volBarX, volY, volBarW, 4, 2, 0x24FFFFFF, 0);
        int volFill = (int) (volBarW * dragVolumeRatio);
        if (volFill > 0) {
            AaRoundRect.draw(guiGraphics, volBarX, volY, Math.max(4, volFill), 4, 2, accent, 0);
        }
        AaRoundRect.draw(guiGraphics, volBarX + volFill - 4, volY - 2, 8, 8, 4, 0xFFFFFFFF, volHover ? accent : 0);

        String volText = curVol + "%";
        guiGraphics.drawString(this.font, volText, volBarX + volBarW + 8, volY - 2, ThemeManager.getTextSecondary(), false);

        // 6. Beat-synced Audio Waveform Visualizer (tall, vibrant & volume-scaled)
        int visualBaselineY = modalY + modalHeight - 12;
        int visualMaxHeight = 46;
        boolean hasLyric = LyricsManager.getInstance().getCurrentLyrics() != null
                && LyricsManager.getInstance().getCurrentLyrics().getActiveIndex(stateManager.getInterpolatedProgressMs()) >= 0;
        AudioWaveformRenderer.renderSyncedWaveform(
                guiGraphics, contentX + 6, visualBaselineY, contentW - 12, visualMaxHeight,
                30, state.isPlaying(), stateManager.getInterpolatedProgressMs(), 0.85f, accent, accent2,
                state.getTrackName(), state.getArtistNames(), hasLyric, dragVolumeRatio
        );
    }

    // =========================================================================
    // TAB 2: LYRICS
    // =========================================================================

    private void renderLyricsTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + sidebarWidth + 14;
        int contentW = modalWidth - sidebarWidth - 28;
        int contentY = modalY + 46;
        int contentH = modalHeight - 54;
        int accent = ThemeManager.getAccent();

        LyricsManager lyricsManager = LyricsManager.getInstance();
        LyricsManager.SongLyrics songLyrics = lyricsManager.getCurrentLyrics();
        List<LyricLine> lyrics = (songLyrics != null && songLyrics.isSynced()) ? songLyrics.getLines() : null;

        if (lyrics == null || lyrics.isEmpty()) {
            int cx = contentX + (contentW / 2);
            renderGlassCard(guiGraphics, contentX, contentY + 20, contentW, 110, false, false);
            guiGraphics.drawCenteredString(this.font, "No synced lyrics available for this song.", cx, contentY + 48, ThemeManager.getTextSecondary());
            guiGraphics.drawCenteredString(this.font, "Checking LRCLib database automatically...", cx, contentY + 64, accent);
            return;
        }

        long progressMs = stateManager.getInterpolatedProgressMs();
        int activeIdx = songLyrics.getActiveIndex(progressMs);

        guiGraphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        int lineHeight = 22;
        int totalHeight = lyrics.size() * lineHeight;

        int targetScroll = (activeIdx * lineHeight) - (contentH / 2) + 12;
        targetScroll = Math.max(0, Math.min(totalHeight - contentH + 30, targetScroll));

        lyricsScrollY += (targetScroll - lyricsScrollY) * 0.18f;
        int lineY = contentY + 12 - lyricsScrollY;
        int maxTextW = contentW - 32;

        for (int i = 0; i < lyrics.size(); i++) {
            LyricLine line = lyrics.get(i);
            boolean isActive = (i == activeIdx);

            if (lineY >= contentY - lineHeight && lineY <= contentY + contentH + lineHeight) {
                String text = line.getText();
                if (this.font.width(text) > maxTextW) {
                    text = trimWithEllipsis(text, maxTextW);
                }

                if (isActive) {
                    // Glowing active line card
                    AaRoundRect.draw(guiGraphics, contentX + 2, lineY - 2, contentW - 4, 20, 6, ThemeManager.getAccentDim(0.24f), ThemeManager.applyAlpha(accent, 0.55f));
                    guiGraphics.drawCenteredString(this.font, text, contentX + (contentW / 2), lineY + 3, 0xFFFFFFFF);
                } else {
                    int dist = Math.abs(i - activeIdx);
                    int alpha = Math.max(50, 210 - (dist * 40));
                    int color = ThemeManager.applyAlpha(ThemeManager.getTextSecondary(), alpha / 255.0f);
                    guiGraphics.drawCenteredString(this.font, text, contentX + (contentW / 2), lineY + 3, color);
                }
            }
            lineY += lineHeight;
        }

        guiGraphics.disableScissor();

        // Scrollbar
        renderScrollbar(guiGraphics, contentX + contentW - 6, contentY + 4, contentH - 8, totalHeight, lyricsScrollY);
    }

    // =========================================================================
    // TAB 3: QUEUE
    // =========================================================================

    private void renderQueueTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + sidebarWidth + 14;
        int contentW = modalWidth - sidebarWidth - 28;
        int contentY = modalY + 46;
        int contentH = modalHeight - 54;
        int accent = ThemeManager.getAccent();
        int accent2 = ThemeManager.getAccent2();

        QueueResponse queue = stateManager.getCurrentQueue();
        List<QueueItem> items = (queue != null && queue.getQueue() != null) ? queue.getQueue() : Collections.emptyList();

        // 1. Top Action Bar: Header + Refresh Button
        int headerY = contentY;
        int refreshW = 58;
        int refreshH = 18;
        int refreshX = contentX + contentW - refreshW - 14;
        boolean refreshHover = isMouseOver(mouseX, mouseY, refreshX, headerY, refreshW, refreshH);
        renderButton(guiGraphics, "↺ Refresh", refreshX, headerY, refreshW, refreshH, refreshHover);

        String countText = items.isEmpty() ? "NO UPCOMING TRACKS" : "UPCOMING QUEUE (" + items.size() + ")";
        guiGraphics.drawString(this.font, countText, contentX + 2, headerY + 5, accent, true);

        int listTopY = headerY + 22;
        int listH = contentH - 22;

        if (items.isEmpty() && (queue == null || queue.getCurrentlyPlaying() == null)) {
            int cx = contentX + (contentW / 2);
            renderGlassCard(guiGraphics, contentX, listTopY + 12, contentW - 14, 110, false, false);
            guiGraphics.drawCenteredString(this.font, "Spotify queue is currently empty.", cx, listTopY + 42, ThemeManager.getTextSecondary());
            guiGraphics.drawCenteredString(this.font, "Queue tracks in Spotify or click '↺ Refresh'.", cx, listTopY + 58, accent);
            return;
        }

        guiGraphics.enableScissor(contentX, listTopY, contentX + contentW, listTopY + listH);

        int curY = listTopY + 2 - queueScrollY;
        int cardW = contentW - 14;

        // Currently Playing Card (if available)
        QueueItem cp = (queue != null) ? queue.getCurrentlyPlaying() : null;
        if (cp == null && stateManager.getCurrentState() != null) {
            PlaybackState s = stateManager.getCurrentState();
            if (s.getTrackName() != null && !s.getTrackName().isEmpty()) {
                cp = new QueueItem(s.getTrackId(), s.getTrackName(), s.getArtistNames(), s.getAlbumName(), s.getAlbumArtUrl(), s.getDurationMs());
            }
        }

        if (cp != null && cp.getName() != null && !cp.getName().isEmpty()) {
            int cpH = 34;
            AaRoundRect.draw(guiGraphics, contentX, curY, cardW, cpH, 7, ThemeManager.getAccentDim(0.20f), accent);
            guiGraphics.drawString(this.font, "▶ NOW PLAYING", contentX + 8, curY + 5, accent, true);
            String npText = trimWithEllipsis(cp.getName() + " — " + cp.getArtists(), cardW - 20);
            guiGraphics.drawString(this.font, npText, contentX + 8, curY + 18, 0xFFFFFFFF, false);
            curY += cpH + 6;
        }

        int rowH = 30;
        int btnSize = 16;
        int totalItems = items.size();

        for (int i = 0; i < totalItems; i++) {
            QueueItem item = items.get(i);
            int rowY = curY;

            if (rowY >= listTopY - rowH && rowY <= listTopY + listH + rowH) {
                boolean rowHover = isMouseOver(mouseX, mouseY, contentX, rowY, cardW, rowH);

                int bg = rowHover ? ThemeManager.getCardHoverBg() : ThemeManager.getCardBg();
                int border = rowHover ? ThemeManager.applyAlpha(accent, 0.65f) : ThemeManager.getGlassEdge();
                AaRoundRect.draw(guiGraphics, contentX, rowY, cardW, rowH, 6, bg, border);

                // Index Tag
                String idxStr = "#" + (i + 1);
                guiGraphics.drawString(this.font, idxStr, contentX + 8, rowY + 10, ThemeManager.getTextMuted(), false);

                // Action Buttons on Right: [^] [v] [x]
                int actionX = contentX + cardW - 6 - (btnSize * 3 + 8);
                int btnY = rowY + 7;

                // Move Up Button (▲)
                boolean canMoveUp = (i > 0);
                int upX = actionX;
                boolean upHover = canMoveUp && isMouseOver(mouseX, mouseY, upX, btnY, btnSize, btnSize);
                if (canMoveUp) {
                    int upBg = upHover ? ThemeManager.getAccentDim(0.35f) : 0x14FFFFFF;
                    int upBorder = upHover ? accent : 0x22FFFFFF;
                    AaRoundRect.draw(guiGraphics, upX, btnY, btnSize, btnSize, 4, upBg, upBorder);
                    guiGraphics.drawCenteredString(this.font, "▲", upX + (btnSize / 2), btnY + 3, upHover ? accent : ThemeManager.getTextSecondary());
                }

                // Move Down Button (▼)
                boolean canMoveDown = (i < totalItems - 1);
                int downX = upX + btnSize + 4;
                boolean downHover = canMoveDown && isMouseOver(mouseX, mouseY, downX, btnY, btnSize, btnSize);
                if (canMoveDown) {
                    int downBg = downHover ? ThemeManager.getAccentDim(0.35f) : 0x14FFFFFF;
                    int downBorder = downHover ? accent : 0x22FFFFFF;
                    AaRoundRect.draw(guiGraphics, downX, btnY, btnSize, btnSize, 4, downBg, downBorder);
                    guiGraphics.drawCenteredString(this.font, "▼", downX + (btnSize / 2), btnY + 3, downHover ? accent : ThemeManager.getTextSecondary());
                }

                // Remove Button (x)
                int remX = downX + btnSize + 4;
                boolean remHover = isMouseOver(mouseX, mouseY, remX, btnY, btnSize, btnSize);
                int remBg = remHover ? 0x44FF3333 : 0x14FFFFFF;
                int remBorder = remHover ? 0xFFFF4444 : 0x22FFFFFF;
                AaRoundRect.draw(guiGraphics, remX, btnY, btnSize, btnSize, 4, remBg, remBorder);
                guiGraphics.drawCenteredString(this.font, "x", remX + (btnSize / 2), btnY + 3, remHover ? 0xFFFF5555 : ThemeManager.getTextMuted());

                // Track Line: Title in bold white, artist in secondary
                int textX = contentX + 28;
                int textMaxW = actionX - textX - 6;
                String trackStr = item.getName() + " — " + item.getArtists();
                String line = trimWithEllipsis(trackStr, textMaxW);
                guiGraphics.drawString(this.font, line, textX, rowY + 10, rowHover ? 0xFFFFFFFF : ThemeManager.getTextPrimary(), false);
            }

            curY += rowH + 4;
        }

        guiGraphics.disableScissor();

        int totalH = (cp != null ? 40 : 0) + (totalItems * (rowH + 4)) + 12;
        int maxScroll = Math.max(0, totalH - listH);
        if (queueScrollY > maxScroll) queueScrollY = maxScroll;

        renderScrollbar(guiGraphics, contentX + contentW - 6, listTopY, listH, totalH, queueScrollY);
    }

    // =========================================================================
    // TAB 4: THEMES
    // =========================================================================

    // =========================================================================
    // TAB: HUD EDITOR
    // =========================================================================

    private void renderHudEditorTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + sidebarWidth + 14;
        int curY = modalY + 44;
        int cardW = modalWidth - sidebarWidth - 28;

        int mainCardH = 248;
        renderGroupCardBackground(guiGraphics, contentX, curY, cardW, mainCardH);

        int accent = ThemeManager.getAccent();
        guiGraphics.drawString(this.font, "Interactive In-Game HUD Canvas", contentX + 14, curY + 12, accent, true);
        guiGraphics.drawString(this.font, "Reposition and scale the Spotify HUD overlay anywhere on your screen.", contentX + 14, curY + 24, ThemeManager.getTextSecondary(), false);

        renderHairline(guiGraphics, contentX + 12, curY + 38, cardW - 24);

        int badgeY = curY + 46;
        int badgeW = 92;
        int badgeH = 18;
        int gap = 5;

        renderInfoPill(guiGraphics, "Drag & Drop", contentX + 14, badgeY, badgeW, badgeH);
        renderInfoPill(guiGraphics, "Corner Scale", contentX + 14 + badgeW + gap, badgeY, badgeW, badgeH);
        renderInfoPill(guiGraphics, "Magnetic Snapping", contentX + 14 + (badgeW + gap) * 2, badgeY, badgeW + 20, badgeH);

        int statusBoxY = curY + 72;
        int statusBoxH = 68;
        AaRoundRect.draw(guiGraphics, contentX + 14, statusBoxY, cardW - 28, statusBoxH, 6, 0x1AFFFFFF, 0x24FFFFFF);

        String posStr = config.getHudPosition().getDisplayName();
        String coordStr = (config.getHudCustomX() >= 0 && config.getHudCustomY() >= 0)
                ? (config.getHudCustomX() + ", " + config.getHudCustomY() + " px")
                : "Automatic Anchor";
        String scaleStr = config.getHudScalePercent() + "%";

        guiGraphics.drawString(this.font, "Placement:", contentX + 22, statusBoxY + 10, ThemeManager.getTextSecondary());
        guiGraphics.drawString(this.font, posStr, contentX + 115, statusBoxY + 10, accent, true);

        guiGraphics.drawString(this.font, "Coordinates (X, Y):", contentX + 22, statusBoxY + 28, ThemeManager.getTextSecondary());
        guiGraphics.drawString(this.font, coordStr, contentX + 115, statusBoxY + 28, 0xFFFFFFFF, false);

        guiGraphics.drawString(this.font, "Scale:", contentX + 22, statusBoxY + 46, ThemeManager.getTextSecondary());
        guiGraphics.drawString(this.font, scaleStr, contentX + 115, statusBoxY + 46, ThemeManager.getAccent2(), true);

        int btnW = 180;
        int btnH = 28;
        int btnX = contentX + (cardW - btnW) / 2;
        int btnY = curY + 152;
        boolean btnHover = isMouseOver(mouseX, mouseY, btnX, btnY, btnW, btnH);

        int btnBg = btnHover ? accent : ThemeManager.getAccentDim(0.85f);
        int btnBorder = ThemeManager.getAccent();
        AaRoundRect.draw(guiGraphics, btnX, btnY, btnW, btnH, 6, btnBg, btnBorder);
        guiGraphics.fill(btnX + 4, btnY + 1, btnX + btnW - 4, btnY + 2, 0x55FFFFFF);

        int textCol = btnHover ? 0xFF0D0D14 : 0xFF14141E;
        guiGraphics.drawCenteredString(this.font, "Launch HUD Editor", btnX + (btnW / 2), btnY + 10, textCol);

        String tip = "Click 'Save' at the top center of the editor to apply coordinates.";
        guiGraphics.drawCenteredString(this.font, tip, contentX + (cardW / 2), curY + 194, ThemeManager.getTextMuted());
    }

    private void renderInfoPill(GuiGraphics g, String text, int x, int y, int w, int h) {
        AaRoundRect.draw(g, x, y, w, h, 4, 0x220D0D14, 0x24FFFFFF);
        g.drawCenteredString(this.font, text, x + (w / 2), y + 5, 0xFFCCCCCC);
    }

    private void renderThemesTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + sidebarWidth + 14;
        int contentW = modalWidth - sidebarWidth - 28;
        int contentY = modalY + 46;
        int contentH = modalHeight - 54;
        int accent = ThemeManager.getAccent();
        int cardW = contentW - 14; // leaves clean 8px gutter before scrollbar

        int maxScroll = Math.max(0, THEMES_CONTENT_HEIGHT - contentH);
        if (this.themesScrollY > maxScroll) {
            this.themesScrollY = maxScroll;
        }

        guiGraphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        int curY = contentY + 4 - themesScrollY;

        guiGraphics.drawString(this.font, "LIQUID GLASS THEMES", contentX + 2, curY, accent, true);
        curY += 16;

        ThemeLook currentLook = config.getThemeLook();
        ThemeLook[] looks = ThemeLook.values();
        int cardH = 34;

        for (ThemeLook look : looks) {
            boolean isActive = (look == currentLook);
            boolean hover = isMouseOver(mouseX, mouseY, contentX, curY, cardW, cardH);

            int bg = hover ? ThemeManager.getCardHoverBg() : ThemeManager.getCardBg();
            int border = isActive ? look.getAccentColor() : (hover ? ThemeManager.applyAlpha(look.getAccentColor(), 0.55f) : ThemeManager.getGlassEdge());

            AaRoundRect.draw(guiGraphics, contentX, curY, cardW, cardH, 7, bg, border);

            // Palette color swatch (perfectly centered 12x12 inner swatch in 22x22 outer box)
            AaRoundRect.draw(guiGraphics, contentX + 8, curY + 6, 22, 22, 5, 0xFF000000 | look.getWindowRgb(), 0x33FFFFFF);
            AaRoundRect.draw(guiGraphics, contentX + 13, curY + 11, 12, 12, 3, look.getAccentColor(), 0x30000000);

            // Title & Subtitle
            guiGraphics.drawString(this.font, look.getTitle(), contentX + 38, curY + 6, isActive ? look.getAccentColor() : ThemeManager.getTextPrimary(), isActive);
            guiGraphics.drawString(this.font, look.getSubtitle(), contentX + 38, curY + 18, ThemeManager.getTextMuted(), false);

            if (isActive) {
                guiGraphics.drawString(this.font, "Active", contentX + cardW - 55, curY + 12, look.getAccentColor(), true);
            }

            curY += cardH + 5;
        }

        curY += 8;

        // Glass Opacity Slider Card
        AaRoundRect.draw(guiGraphics, contentX, curY, cardW, 40, 8, ThemeManager.getCardBg(), ThemeManager.getGlassEdge());
        guiGraphics.drawString(this.font, "Liquid Glass Translucency", contentX + 12, curY + 8, 0xFFFFFFFF, false);
        guiGraphics.drawString(this.font, "Window background opacity", contentX + 12, curY + 22, ThemeManager.getTextMuted(), false);

        int sliderW = 85;
        int badgeW = 38;
        int badgeX = contentX + cardW - 12 - badgeW;
        int sliderX = badgeX - 8 - sliderW;
        int opacPercent = Math.round(40 + dragGlassOpacityRatio * 60);
        boolean opacHover = isMouseOver(mouseX, mouseY, sliderX, curY + 12, sliderW, 16);
        renderSlider(guiGraphics, sliderX, curY + 18, sliderW, dragGlassOpacityRatio, opacHover);
        renderBadge(guiGraphics, badgeX, curY + 11, badgeW, 18, opacPercent + "%", opacHover);

        curY += 48;

        // Accent Palette Overrides
        guiGraphics.drawString(this.font, "ACCENT PALETTE OVERRIDE", contentX + 2, curY, accent, true);
        curY += 16;

        ThemeAccent currentAccent = config.getThemeAccent();
        ThemeAccent[] accents = ThemeAccent.values();
        int gridCols = 3;
        int cellW = (cardW - ((gridCols - 1) * 6)) / gridCols;
        int cellH = 24;

        for (int i = 0; i < accents.length; i++) {
            ThemeAccent acc = accents[i];
            int col = i % gridCols;
            int row = i / gridCols;
            int cellX = contentX + (col * (cellW + 6));
            int cellY = curY + (row * (cellH + 5));

            boolean isActive = (acc == currentAccent);
            boolean hover = isMouseOver(mouseX, mouseY, cellX, cellY, cellW, cellH);

            int cellBg = isActive ? ThemeManager.getAccentDim(0.24f) : (hover ? ThemeManager.getCardHoverBg() : ThemeManager.getCardBg());
            int cellBorder = isActive ? (acc.getColor() != 0 ? acc.getColor() : accent) : (hover ? 0x44FFFFFF : ThemeManager.getGlassEdge());

            AaRoundRect.draw(guiGraphics, cellX, cellY, cellW, cellH, 6, cellBg, cellBorder);

            if (acc.getColor() != 0) {
                AaRoundRect.draw(guiGraphics, cellX + 6, cellY + 6, 12, 12, 3, acc.getColor(), 0);
            } else {
                AaRoundRect.draw(guiGraphics, cellX + 6, cellY + 6, 12, 12, 3, currentLook.getAccentColor(), 0);
            }

            guiGraphics.drawString(this.font, acc.getTitle(), cellX + 22, cellY + 7, isActive ? 0xFFFFFFFF : ThemeManager.getTextSecondary(), false);
        }

        guiGraphics.disableScissor();

        // Scrollbar with dedicated 8px gutter
        renderScrollbar(guiGraphics, contentX + contentW - 6, contentY + 4, contentH - 8, THEMES_CONTENT_HEIGHT, themesScrollY);
    }

    // =========================================================================
    // TAB 5: SETTINGS
    // =========================================================================

    private void renderSettingsTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentX = modalX + sidebarWidth + 14;
        int contentW = modalWidth - sidebarWidth - 28;
        int contentY = modalY + 46;
        int contentH = modalHeight - 54;
        int accent = ThemeManager.getAccent();

        int maxScroll = Math.max(0, SETTINGS_CONTENT_HEIGHT - contentH);
        if (this.settingsScrollY > maxScroll) {
            this.settingsScrollY = maxScroll;
        }

        guiGraphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        int curY = contentY + 4 - settingsScrollY;
        int cardW = contentW - 14;
        int rowH = 38;

        // Layout horizontal offsets inside cards (balanced spacing & zero clipping)
        int sliderW = 76;
        int badgeW = 36;
        int badgeX = contentX + cardW - 12 - badgeW;
        int sliderX = badgeX - 6 - sliderW;
        int switchX = badgeX + (badgeW - 32); // right-aligned with the badge column
        int buttonW = 105;
        int buttonX = contentX + cardW - 12 - buttonW;

        // ---------------------------------------------------------------------
        // GROUP 1: CELESTIAL SKY LYRICS (The exact section in user's Screen 1!)
        // ---------------------------------------------------------------------
        renderGroupHeader(guiGraphics, "CELESTIAL SKY LYRICS", contentX + 2, curY);
        curY += 15;

        int skyGroupRows = 5;
        int skyCardH = skyGroupRows * rowH;
        renderGroupCardBackground(guiGraphics, contentX, curY, cardW, skyCardH);

        // Row 1: Sky Lyrics Enable
        int r1Y = curY;
        renderRowHover(guiGraphics, contentX, r1Y, cardW, rowH, isMouseOver(mouseX, mouseY, contentX, r1Y, cardW, rowH));
        renderSettingLabel(guiGraphics, "Sky Lyrics Overlay", "Display synced lyrics across the sky dome", contentX + 14, r1Y, switchX);
        boolean skyHover = isMouseOver(mouseX, mouseY, switchX, r1Y + 11, 32, 16);
        renderSwitch(guiGraphics, config.isSkyLyricsEnabled(), switchX, r1Y + 11, skyHover);
        renderHairline(guiGraphics, contentX + 12, r1Y + rowH, cardW - 24);

        // Row 2: Sky Lyrics Direction (Fixed removed completely!)
        int r2Y = curY + rowH;
        renderRowHover(guiGraphics, contentX, r2Y, cardW, rowH, isMouseOver(mouseX, mouseY, contentX, r2Y, cardW, rowH));
        renderSettingLabel(guiGraphics, "Sky Lyrics Direction", "Direction: " + config.getSkyLyricsMode().getDisplayName(), contentX + 14, r2Y, buttonX);
        boolean skyDirHover = isMouseOver(mouseX, mouseY, buttonX, r2Y + 9, buttonW, 20);
        renderButton(guiGraphics, config.getSkyLyricsMode().getDisplayName() + " ▾", buttonX, r2Y + 9, buttonW, 20, skyDirHover);
        renderHairline(guiGraphics, contentX + 12, r2Y + rowH, cardW - 24);

        // Row 3: Sky Lyrics Scale (Slider + Badge)
        int r3Y = curY + (rowH * 2);
        renderRowHover(guiGraphics, contentX, r3Y, cardW, rowH, isMouseOver(mouseX, mouseY, contentX, r3Y, cardW, rowH));
        renderSettingLabel(guiGraphics, "Sky Lyrics Scale", "Text size in sky dome", contentX + 14, r3Y, sliderX);
        int skyScalePercent = isDraggingSkyScale ? Math.round(50 + dragSkyScaleRatio * 150) : config.getSkyLyricsScalePercent();
        float skyScaleRatio = (skyScalePercent - 50) / 150.0f;
        boolean skyScaleHover = isMouseOver(mouseX, mouseY, sliderX, r3Y + 11, sliderW, 16);
        renderSlider(guiGraphics, sliderX, r3Y + 17, sliderW, skyScaleRatio, skyScaleHover);
        renderBadge(guiGraphics, badgeX, r3Y + 10, badgeW, 18, skyScalePercent + "%", skyScaleHover);
        renderHairline(guiGraphics, contentX + 12, r3Y + rowH, cardW - 24);

        // Row 4: Sky Visible Lines (Slider + Badge)
        int r4Y = curY + (rowH * 3);
        renderRowHover(guiGraphics, contentX, r4Y, cardW, rowH, isMouseOver(mouseX, mouseY, contentX, r4Y, cardW, rowH));
        renderSettingLabel(guiGraphics, "Sky Visible Lines", "Visible lyric line count", contentX + 14, r4Y, sliderX);
        int skyLines = isDraggingSkyLines ? Math.round(3 + dragSkyLinesRatio * 22) : config.getSkyLyricsLength();
        float skyLinesRatio = (skyLines - 3) / 22.0f;
        boolean skyLinesHover = isMouseOver(mouseX, mouseY, sliderX, r4Y + 11, sliderW, 16);
        renderSlider(guiGraphics, sliderX, r4Y + 17, sliderW, skyLinesRatio, skyLinesHover);
        renderBadge(guiGraphics, badgeX, r4Y + 10, badgeW, 18, skyLines + "x", skyLinesHover);
        renderHairline(guiGraphics, contentX + 12, r4Y + rowH, cardW - 24);

        // Row 5: Lyrics Typography
        int r5Y = curY + (rowH * 4);
        renderRowHover(guiGraphics, contentX, r5Y, cardW, rowH, isMouseOver(mouseX, mouseY, contentX, r5Y, cardW, rowH));
        renderSettingLabel(guiGraphics, "Lyrics Typography", "Font for lyrics & UI", contentX + 14, r5Y, buttonX);
        boolean fontHover = isMouseOver(mouseX, mouseY, buttonX, r5Y + 9, buttonW, 20);
        renderButton(guiGraphics, config.getLyricFont().getDisplayName() + " ▾", buttonX, r5Y + 9, buttonW, 20, fontHover);

        curY += skyCardH + 16;

        // ---------------------------------------------------------------------
        // GROUP 2: IN-GAME HUD OVERLAY
        // ---------------------------------------------------------------------
        renderGroupHeader(guiGraphics, "IN-GAME HUD OVERLAY", contentX + 2, curY);
        curY += 15;

        int hudGroupRows = 4;
        int hudCardH = hudGroupRows * rowH;
        renderGroupCardBackground(guiGraphics, contentX, curY, cardW, hudCardH);

        // Row 1: HUD Enabled
        int h1Y = curY;
        renderRowHover(guiGraphics, contentX, h1Y, cardW, rowH, isMouseOver(mouseX, mouseY, contentX, h1Y, cardW, rowH));
        renderSettingLabel(guiGraphics, "HUD Overlay Card", "Position: " + config.getHudPosition().getDisplayName(), contentX + 14, h1Y, switchX);
        boolean hudHover = isMouseOver(mouseX, mouseY, switchX, h1Y + 11, 32, 16);
        renderSwitch(guiGraphics, config.getHudPosition() != ModConfig.HudPosition.OFF, switchX, h1Y + 11, hudHover);
        renderHairline(guiGraphics, contentX + 12, h1Y + rowH, cardW - 24);

        // Row 2: HUD Lyrics Line
        int h2Y = curY + rowH;
        renderRowHover(guiGraphics, contentX, h2Y, cardW, rowH, isMouseOver(mouseX, mouseY, contentX, h2Y, cardW, rowH));
        renderSettingLabel(guiGraphics, "HUD Lyrics Line", "Display active lyric line under HUD card", contentX + 14, h2Y, switchX);
        boolean hudLyricsHover = isMouseOver(mouseX, mouseY, switchX, h2Y + 11, 32, 16);
        renderSwitch(guiGraphics, config.isHudShowLyrics(), switchX, h2Y + 11, hudLyricsHover);
        renderHairline(guiGraphics, contentX + 12, h2Y + rowH, cardW - 24);

        // Row 3: HUD Opacity (Slider + Badge)
        int h3Y = curY + (rowH * 2);
        renderRowHover(guiGraphics, contentX, h3Y, cardW, rowH, isMouseOver(mouseX, mouseY, contentX, h3Y, cardW, rowH));
        renderSettingLabel(guiGraphics, "HUD Opacity", "Translucency of overlay", contentX + 14, h3Y, sliderX);
        int opacPercent = isDraggingHudOpacity ? Math.round(dragHudOpacityRatio * 100) : config.getHudOpacityPercent();
        float opacRatio = opacPercent / 100.0f;
        boolean opacHover = isMouseOver(mouseX, mouseY, sliderX, h3Y + 11, sliderW, 16);
        renderSlider(guiGraphics, sliderX, h3Y + 17, sliderW, opacRatio, opacHover);
        renderBadge(guiGraphics, badgeX, h3Y + 10, badgeW, 18, (opacPercent == 0) ? "Off" : (opacPercent + "%"), opacHover);
        renderHairline(guiGraphics, contentX + 12, h3Y + rowH, cardW - 24);

        // Row 4: HUD Scale (Slider + Badge)
        int h4Y = curY + (rowH * 3);
        renderRowHover(guiGraphics, contentX, h4Y, cardW, rowH, isMouseOver(mouseX, mouseY, contentX, h4Y, cardW, rowH));
        renderSettingLabel(guiGraphics, "HUD Scale", "Size of the in-game overlay", contentX + 14, h4Y, sliderX);
        int scalePercent = isDraggingHudScale ? Math.round(50 + dragHudScaleRatio * 100) : config.getHudScalePercent();
        float scaleRatio = (scalePercent - 50) / 100.0f;
        boolean scaleHover = isMouseOver(mouseX, mouseY, sliderX, h4Y + 11, sliderW, 16);
        renderSlider(guiGraphics, sliderX, h4Y + 17, sliderW, scaleRatio, scaleHover);
        renderBadge(guiGraphics, badgeX, h4Y + 10, badgeW, 18, scalePercent + "%", scaleHover);

        curY += hudCardH + 16;

        // ---------------------------------------------------------------------
        // GROUP 3: AUDIO DUCKING & SYSTEM
        // ---------------------------------------------------------------------
        renderGroupHeader(guiGraphics, "AUDIO DUCKING & UTILITIES", contentX + 2, curY);
        curY += 15;

        int audioGroupRows = 4;
        int audioCardH = audioGroupRows * rowH;
        renderGroupCardBackground(guiGraphics, contentX, curY, cardW, audioCardH);

        // Row 1: Audio Ducking Enable
        int a1Y = curY;
        renderRowHover(guiGraphics, contentX, a1Y, cardW, rowH, isMouseOver(mouseX, mouseY, contentX, a1Y, cardW, rowH));
        renderSettingLabel(guiGraphics, "Minecraft Audio Ducking", "Lower game volume during music", contentX + 14, a1Y, switchX);
        boolean duckHover = isMouseOver(mouseX, mouseY, switchX, a1Y + 11, 32, 16);
        renderSwitch(guiGraphics, config.isAudioDuckingEnabled(), switchX, a1Y + 11, duckHover);
        renderHairline(guiGraphics, contentX + 12, a1Y + rowH, cardW - 24);

        // Row 2: Ducking Multiplier (Slider + Badge)
        int a2Y = curY + rowH;
        renderRowHover(guiGraphics, contentX, a2Y, cardW, rowH, isMouseOver(mouseX, mouseY, contentX, a2Y, cardW, rowH));
        renderSettingLabel(guiGraphics, "Ducking Multiplier", "Game volume level while playing", contentX + 14, a2Y, sliderX);
        int duckPercent = isDraggingDucking ? Math.round(5 + dragDuckingRatio * 95) : Math.round(config.getDuckingMultiplier() * 100);
        float duckRatio = (duckPercent - 5) / 95.0f;
        boolean duckHoverSlider = isMouseOver(mouseX, mouseY, sliderX, a2Y + 11, sliderW, 16);
        renderSlider(guiGraphics, sliderX, a2Y + 17, sliderW, duckRatio, duckHoverSlider);
        renderBadge(guiGraphics, badgeX, a2Y + 10, badgeW, 18, duckPercent + "%", duckHoverSlider);
        renderHairline(guiGraphics, contentX + 12, a2Y + rowH, cardW - 24);

        // Row 3: Diagnostics
        int a3Y = curY + (rowH * 2);
        renderRowHover(guiGraphics, contentX, a3Y, cardW, rowH, isMouseOver(mouseX, mouseY, contentX, a3Y, cardW, rowH));
        renderSettingLabel(guiGraphics, "Anonymous Diagnostics", "Crash & stability metrics", contentX + 14, a3Y, switchX);
        boolean diagHover = isMouseOver(mouseX, mouseY, switchX, a3Y + 11, 32, 16);
        renderSwitch(guiGraphics, config.isMetricsEnabled(), switchX, a3Y + 11, diagHover);
        renderHairline(guiGraphics, contentX + 12, a3Y + rowH, cardW - 24);

        // Row 4: Utility Action Buttons
        int a4Y = curY + (rowH * 3);
        renderRowHover(guiGraphics, contentX, a4Y, cardW, rowH, isMouseOver(mouseX, mouseY, contentX, a4Y, cardW, rowH));
        int btnW = (cardW - 32) / 2;
        boolean reloadHover = isMouseOver(mouseX, mouseY, contentX + 12, a4Y + 8, btnW, 22);
        renderButton(guiGraphics, "↺ Reload Bridge", contentX + 12, a4Y + 8, btnW, 22, reloadHover);

        it.itzcrih.espoty.update.UpdateChecker updateChecker = it.itzcrih.espoty.update.UpdateChecker.getInstance();
        boolean hasUpdate = updateChecker.isUpdateAvailable();
        int btn2X = contentX + 12 + btnW + 8;
        boolean updateHover = isMouseOver(mouseX, mouseY, btn2X, a4Y + 8, btnW, 22);
        String updateLabel = hasUpdate ? "Update v" + updateChecker.getLatestVersion() + " Available!" : "Check for Updates";
        renderButton(guiGraphics, updateLabel, btn2X, a4Y + 8, btnW, 22, updateHover);

        curY += audioCardH + 20;

        // Author credits
        String credit = "§7eSpoty v" + it.itzcrih.espoty.update.UpdateChecker.getCurrentVersion() + " by §6itzCrih §8• §ehttps://itzcrih.it";
        boolean creditHover = isMouseOver(mouseX, mouseY, contentX + 2, curY, 260, 12);
        guiGraphics.drawString(this.font, credit, contentX + 2, curY, creditHover ? 0xFFFFFFFF : ThemeManager.getTextMuted(), false);

        curY += 20;
        guiGraphics.disableScissor();

        // Scrollbar with dedicated 8px gutter
        renderScrollbar(guiGraphics, contentX + contentW - 6, contentY + 4, contentH - 8, SETTINGS_CONTENT_HEIGHT, settingsScrollY);
    }

    // =========================================================================
    // UI WIDGETS
    // =========================================================================

    private void renderGroupHeader(GuiGraphics g, String title, int x, int y) {
        g.drawString(this.font, title, x, y, ThemeManager.getAccent(), true);
    }

    private void renderGroupCardBackground(GuiGraphics g, int x, int y, int w, int h) {
        // Deep obsidian glass panel with 9px rounded corners and 1px delicate border
        AaRoundRect.draw(g, x, y, w, h, 9, 0xD00E1422, 0x24FFFFFF);
    }

    private void renderRowHover(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        if (hover) {
            AaRoundRect.draw(g, x + 2, y + 1, w - 4, h - 2, 6, 0x0CFFFFFF, 0);
        }
    }

    private void renderHairline(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, 0x0EFFFFFF);
    }

    private void renderSettingLabel(GuiGraphics g, String title, String subtitle, int x, int y, int controlX) {
        int maxW = Math.max(120, controlX - x - 8);
        String t = trimWithEllipsis(title, maxW);
        String s = trimWithEllipsis(subtitle, maxW);
        g.drawString(this.font, t, x, y + 6, 0xFFFFFFFF, false);
        g.drawString(this.font, s, x, y + 20, ThemeManager.getTextMuted(), false);
    }

    private void renderSettingLabel(GuiGraphics g, String title, String subtitle, int x, int y) {
        renderSettingLabel(g, title, subtitle, x, y, x + 180);
    }

    /**
     * Slider: 4px thin pill track + white 10px circular knob.
     */
    private void renderSlider(GuiGraphics g, int x, int y, int w, float ratio, boolean hover) {
        int accent = ThemeManager.getAccent();
        int accent2 = ThemeManager.getAccent2();

        // 4px background track
        AaRoundRect.draw(g, x, y, w, 4, 2, 0x28FFFFFF, 0);

        // 4px filled accent track
        int fillW = (int) (w * Math.max(0f, Math.min(1f, ratio)));
        if (fillW > 0) {
            AaRoundRect.draw(g, x, y, Math.max(4, fillW), 4, 2, accent, 0);
        }

        // Pure white circular knob (10px diameter, radius 5px)
        int knobX = x + fillW;
        int knobY = y + 2;
        AaRoundRect.draw(g, knobX - 5, knobY - 5, 10, 10, 5, 0xFFFFFFFF, hover ? accent2 : 0x40000000);
    }

    /**
     * Numeric Badge Tag: rounded mini-pill with clear centered text.
     */
    private void renderBadge(GuiGraphics g, int x, int y, int w, int h, String text, boolean hover) {
        int bg = hover ? ThemeManager.getAccentDim(0.30f) : 0x14FFFFFF;
        int border = hover ? ThemeManager.getAccent() : 0x24FFFFFF;
        AaRoundRect.draw(g, x, y, w, h, 4, bg, border);
        g.drawCenteredString(this.font, text, x + (w / 2), y + 5, hover ? 0xFFFFFFFF : ThemeManager.getTextPrimary());
    }

    /**
     * Toggle Switch: 32x16px full pill with 10px circular knob.
     */
    private void renderSwitch(GuiGraphics g, boolean enabled, int x, int y, boolean hover) {
        int w = 32;
        int h = 16;
        int accent = ThemeManager.getAccent();

        int bg = enabled ? (hover ? ThemeManager.getAccent2() : accent) : (hover ? 0x28FFFFFF : 0x1EFFFFFF);
        int border = enabled ? accent : (hover ? 0x40FFFFFF : 0x28FFFFFF);

        // 8px radius pill capsule
        AaRoundRect.draw(g, x, y, w, h, 8, bg, border);

        // 10px diameter circular knob
        int knobSize = 10;
        int knobX = enabled ? (x + w - knobSize - 3) : (x + 3);
        int knobY = y + 3;
        int knobCol = enabled ? 0xFFFFFFFF : 0xFFB0B0C0;

        AaRoundRect.draw(g, knobX, knobY, knobSize, knobSize, 5, knobCol, 0);
    }

    /**
     * Pill / Dropdown Button: 20px height, 5px radius.
     */
    private void renderButton(GuiGraphics g, String text, int x, int y, int w, int h, boolean hover) {
        int bg = hover ? ThemeManager.getAccentDim(0.35f) : 0x14FFFFFF;
        int border = hover ? ThemeManager.getAccent() : 0x28FFFFFF;
        AaRoundRect.draw(g, x, y, w, h, 5, bg, border);
        g.drawCenteredString(this.font, text, x + (w / 2), y + (h / 2) - 4, hover ? ThemeManager.getAccent() : 0xFFFFFFFF);
    }

    /**
     * Scrollbar: thin 3px vertical pill with rounded tips.
     */
    private void renderScrollbar(GuiGraphics g, int x, int y, int h, int totalH, int scrollY) {
        if (totalH <= h) return;

        // Background track (3px)
        AaRoundRect.draw(g, x, y, 3, h, 1, 0x14FFFFFF, 0);

        int thumbH = Math.max(16, Math.min(h, (int) ((h / (float) totalH) * h)));
        int maxScroll = Math.max(1, totalH - h);
        float ratio = Math.max(0f, Math.min(1f, scrollY / (float) maxScroll));
        int thumbY = y + (int) (ratio * (h - thumbH));

        AaRoundRect.draw(g, x, thumbY, 3, thumbH, 1, 0x50FFFFFF, 0x20FFFFFF);
    }

    private void renderGlassCard(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean active) {
        int bg = hover ? ThemeManager.getCardHoverBg() : ThemeManager.getCardBg();
        int border = active ? ThemeManager.getAccent() : (hover ? ThemeManager.applyAlpha(ThemeManager.getAccent(), 0.55f) : ThemeManager.getGlassEdge());
        AaRoundRect.draw(g, x, y, w, h, 7, bg, border);

        if (active) {
            AaRoundRect.draw(g, x + 1, y + 1, w - 2, h - 2, 6, ThemeManager.getAccentDim(0.20f), 0);
        }
    }

    private void renderPillButton(GuiGraphics g, String text, int x, int y, int w, int h, boolean hover) {
        renderButton(g, text, x, y, w, h, hover);
    }

    private void renderModernIconButton(GuiGraphics g, String iconType, int x, int y, int w, int h, boolean hover, int color, boolean active, boolean enabled) {
        if (!enabled) {
            color = 0xFF444455;
        }

        int bg = active ? ThemeManager.getAccentDim(0.35f) : (hover ? 0x24FFFFFF : 0x0DFFFFFF);
        int border = active ? ThemeManager.getAccent() : (hover ? ThemeManager.applyAlpha(ThemeManager.getAccent(), 0.60f) : 0x22FFFFFF);

        AaRoundRect.draw(g, x, y, w, h, Math.min(w, h) / 2, bg, border);

        int cx = x + (w / 2);
        int cy = y + (h / 2);
        int iconColor = hover ? 0xFFFFFFFF : color;

        renderControlIcon(g, iconType, cx, cy, iconColor);
    }

    /**
     * Precision Vector Glyphs for Media Controls: perfectly sharp at all GUI scales.
     */
    private void renderControlIcon(GuiGraphics g, String type, int cx, int cy, int color) {
        switch (type) {
            case "PLAY" -> {
                // Right-pointing filled play triangle (width 7, height 11)
                for (int i = 0; i <= 6; i++) {
                    int h = 5 - (i * 5 / 7);
                    g.fill(cx - 2 + i, cy - h, cx - 1 + i, cy + h + 1, color);
                }
            }
            case "PAUSE" -> {
                // Two vertical pause bars (width 3, height 10, gap 2)
                g.fill(cx - 4, cy - 5, cx - 1, cy + 5, color);
                g.fill(cx + 1, cy - 5, cx + 4, cy + 5, color);
            }
            case "PREV" -> {
                // Previous: vertical stop bar + left triangle
                g.fill(cx - 5, cy - 4, cx - 3, cy + 5, color);
                for (int i = 0; i <= 5; i++) {
                    int h = i * 4 / 5;
                    g.fill(cx - 1 + i, cy - h, cx + i, cy + h + 1, color);
                }
            }
            case "NEXT" -> {
                // Next: right triangle + vertical stop bar
                for (int i = 0; i <= 5; i++) {
                    int h = 4 - (i * 4 / 5);
                    g.fill(cx - 4 + i, cy - h, cx - 3 + i, cy + h + 1, color);
                }
                g.fill(cx + 3, cy - 4, cx + 5, cy + 5, color);
            }
            case "SHUF" -> {
                g.drawCenteredString(this.font, "[SHUF]", cx, cy - 4, color);
            }
            case "REP" -> {
                g.drawCenteredString(this.font, "[REP]", cx, cy - 4, color);
            }
            case "REP1" -> {
                g.drawCenteredString(this.font, "[REP1]", cx, cy - 4, color);
            }
            default -> {
                g.drawCenteredString(this.font, type, cx, cy - 4, color);
            }
        }
    }

    private void renderStatusBanner(GuiGraphics guiGraphics) {
        int bannerW = this.font.width(statusMessage) + 24;
        int bannerH = 18;
        int bx = modalX + sidebarWidth + ((modalWidth - sidebarWidth - bannerW) / 2);
        int by = modalY + modalHeight - 28;

        int bannerBg = 0xEE000000 | ThemeManager.getLook().getWindowRgb();
        AaRoundRect.draw(guiGraphics, bx, by, bannerW, bannerH, 9, bannerBg, ThemeManager.getAccent());
        guiGraphics.drawCenteredString(this.font, statusMessage, bx + (bannerW / 2), by + 5, ThemeManager.getAccent());
    }

    public void setStatus(String msg) {
        this.statusMessage = msg;
        this.statusMessageTime = System.currentTimeMillis();
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f)
        );
    }

    private String trimWithEllipsis(String text, int maxWidth) {
        if (text == null) return "";
        if (this.font.width(text) <= maxWidth) return text;
        String ell = "…";
        int ellW = this.font.width(ell);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (this.font.width(sb.toString() + c) + ellW > maxWidth) {
                return sb.toString() + ell;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // =========================================================================
    // MOUSE & KEY EVENT HANDLING
    // =========================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();

        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }

        // Close button click
        int closeSize = 16;
        int closeX = modalX + modalWidth - closeSize - 12;
        int closeY = modalY + 12;
        if (isMouseOver(mx, my, closeX - 4, closeY - 4, closeSize + 8, closeSize + 8)) {
            triggerCloseAnimation();
            playClickSound();
            return true;
        }

        // Sidebar Tab selection
        if (mx >= modalX && mx <= modalX + sidebarWidth) {
            int startY = modalY + 54;
            int tabH = 30;
            int tabGap = 3;
            Tab[] tabs = Tab.values();
            for (int i = 0; i < tabs.length; i++) {
                int itemY = startY + (i * (tabH + tabGap));
                if (isMouseOver(mx, my, modalX + 8, itemY, sidebarWidth - 16, tabH)) {
                    if (tabs[i] == Tab.HUD_EDITOR) {
                        playClickSound();
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(new HudEditorScreen(this));
                        }
                        return true;
                    }
                    if (this.activeTab != tabs[i]) {
                        this.activeTab = tabs[i];
                        this.tabSwitchStartTime = System.currentTimeMillis();
                        if (this.activeTab == Tab.QUEUE) {
                            this.stateManager.requestQueueRefresh();
                        }
                        playClickSound();
                    }
                    return true;
                }
            }
            return true;
        }

        // Content Area Click Router
        switch (activeTab) {
            case PLAYER -> handlePlayerClick(mx, my);
            case LYRICS -> handleLyricsClick(mx, my);
            case QUEUE -> handleQueueClick(mx, my);
            case HUD_EDITOR -> handleHudEditorClick(mx, my);
            case THEMES -> handleThemesClick(mx, my);
            case SETTINGS -> handleSettingsClick(mx, my);
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void handlePlayerClick(int mx, int my) {
        int contentX = modalX + sidebarWidth + 14;
        int contentW = modalWidth - sidebarWidth - 28;
        int contentY = modalY + 46;

        PlaybackState state = stateManager.getCurrentState();
        if (state == null || !state.isPlaying() && (state.getTrackName() == null || state.getTrackName().isEmpty())) {
            int cx = contentX + (contentW / 2);
            int btnW = 120;
            int btnH = 22;
            if (isMouseOver(mx, my, cx - (btnW / 2), contentY + 92, btnW, btnH)) {
                SpotifyProcessDetector.launchSpotify();
                setStatus("Launching Spotify Desktop...");
                playClickSound();
            }
            return;
        }

        // Like Heart Click
        int heartX = contentX + contentW - 18;
        int heartY = contentY + 8;
        if (isMouseOver(mx, my, heartX - 4, heartY - 4, 18, 18)) {
            stateManager.toggleCurrentTrackLiked();
            setStatus(stateManager.isCurrentTrackLiked() ? "Saved to Liked Songs" : "Removed from Liked Songs");
            playClickSound();
            return;
        }

        // Seek Bar Click
        int seekY = contentY + 82;
        int seekW = contentW - 8;
        int seekX = contentX + 4;
        if (isMouseOver(mx, my, seekX - 2, seekY - 4, seekW + 4, 12)) {
            if (state.canSeek() && state.getDurationMs() > 0) {
                this.isDraggingSeek = true;
                this.dragSeekRatio = Math.max(0f, Math.min(1f, (float) (mx - seekX) / seekW));
                long targetMs = (long) (state.getDurationMs() * dragSeekRatio);
                stateManager.seek(targetMs);
                setStatus("Seek to: " + PlaybackState.formatTime(targetMs));
            }
            return;
        }

        // Media Controls
        int controlsY = seekY + 22;
        int centerX = contentX + (contentW / 2);
        int centerY = controlsY + 14;
        int btnSize = 22;
        int btnCenterY = centerY - (btnSize / 2);

        int playSize = 30;
        int playX = centerX - (playSize / 2);
        int playY = centerY - (playSize / 2);

        int prevX = playX - 10 - btnSize;
        int nextX = playX + playSize + 10;

        int shufX = prevX - 10 - btnSize;
        int repX = nextX + btnSize + 10;

        // Shuffle
        if (isMouseOver(mx, my, shufX, btnCenterY, btnSize, btnSize) && state.canShuffle()) {
            stateManager.toggleShuffle();
            playClickSound();
            return;
        }

        // Prev
        if (isMouseOver(mx, my, prevX, btnCenterY, btnSize, btnSize) && state.canPrev()) {
            stateManager.previous();
            playClickSound();
            return;
        }

        // Play/Pause
        if (isMouseOver(mx, my, playX, playY, playSize, playSize) && state.canPlayPause()) {
            stateManager.togglePlayPause();
            playClickSound();
            return;
        }

        // Next
        if (isMouseOver(mx, my, nextX, btnCenterY, btnSize, btnSize) && state.canNext()) {
            stateManager.next();
            playClickSound();
            return;
        }

        // Repeat
        if (isMouseOver(mx, my, repX, btnCenterY, btnSize, btnSize) && state.canRepeat()) {
            stateManager.cycleRepeat();
            playClickSound();
            return;
        }

        // Volume Bar Click
        int volY = controlsY + 36;
        int volBarW = 100;
        int volBarX = centerX - (volBarW / 2);
        if (isMouseOver(mx, my, volBarX - 16, volY - 4, 16, 16)) {
            // Mute / Unmute
            int curVol = Math.round(dragVolumeRatio * 100);
            stateManager.setVolume(curVol == 0 ? 50 : 0);
            this.dragVolumeRatio = (curVol == 0 ? 0.5f : 0f);
            playClickSound();
            return;
        }
        if (isMouseOver(mx, my, volBarX - 2, volY - 4, volBarW + 4, 12)) {
            this.isDraggingVolume = true;
            this.dragVolumeRatio = Math.max(0f, Math.min(1f, (float) (mx - volBarX) / volBarW));
            int targetVol = Math.round(dragVolumeRatio * 100);
            stateManager.setVolume(targetVol);
            config.setVolume(targetVol);
            return;
        }
    }

    private void handleLyricsClick(int mx, int my) {
        // Reserved for interactive lyric clicking
    }

    private void handleQueueClick(int mx, int my) {
        int contentX = modalX + sidebarWidth + 14;
        int contentW = modalWidth - sidebarWidth - 28;
        int contentY = modalY + 46;
        int contentH = modalHeight - 54;

        // 1. Refresh Button Click
        int headerY = contentY;
        int refreshW = 58;
        int refreshH = 18;
        int refreshX = contentX + contentW - refreshW - 14;
        if (isMouseOver(mx, my, refreshX, headerY, refreshW, refreshH)) {
            stateManager.requestQueueRefresh();
            playClickSound();
            setStatus("Queue refreshed");
            return;
        }

        QueueResponse queue = stateManager.getCurrentQueue();
        if (queue == null || queue.getQueue() == null) return;
        List<QueueItem> items = queue.getQueue();
        if (items.isEmpty()) return;

        int listTopY = headerY + 22;
        int curY = listTopY + 2 - queueScrollY;
        int cardW = contentW - 14;

        QueueItem cp = (queue != null) ? queue.getCurrentlyPlaying() : null;
        if (cp == null && stateManager.getCurrentState() != null) {
            PlaybackState s = stateManager.getCurrentState();
            if (s.getTrackName() != null && !s.getTrackName().isEmpty()) {
                curY += 34 + 6;
            }
        } else if (cp != null && cp.getName() != null && !cp.getName().isEmpty()) {
            curY += 34 + 6;
        }

        int rowH = 30;
        int btnSize = 16;

        for (int i = 0; i < items.size(); i++) {
            QueueItem item = items.get(i);
            int rowY = curY;

            if (rowY >= listTopY - rowH && rowY <= listTopY + (contentH - 22) + rowH) {
                int actionX = contentX + cardW - 6 - (btnSize * 3 + 8);
                int btnY = rowY + 7;
                int upX = actionX;
                int downX = upX + btnSize + 4;
                int remX = downX + btnSize + 4;

                // Move Up Button (▲)
                if (i > 0 && isMouseOver(mx, my, upX, btnY, btnSize, btnSize)) {
                    stateManager.moveQueueItem(i, i - 1);
                    playClickSound();
                    setStatus("Moved up: " + item.getName());
                    return;
                }

                // Move Down Button (▼)
                if (i < items.size() - 1 && isMouseOver(mx, my, downX, btnY, btnSize, btnSize)) {
                    stateManager.moveQueueItem(i, i + 1);
                    playClickSound();
                    setStatus("Moved down: " + item.getName());
                    return;
                }

                // Remove Button (x)
                if (isMouseOver(mx, my, remX, btnY, btnSize, btnSize)) {
                    stateManager.removeQueueItem(i);
                    playClickSound();
                    setStatus("Removed: " + item.getName());
                    return;
                }

                // Click row body to play immediately
                if (isMouseOver(mx, my, contentX, rowY, actionX - contentX, rowH)) {
                    stateManager.playQueueItem(item.getId());
                    playClickSound();
                    setStatus("Playing: " + item.getName());
                    return;
                }
            }

            curY += rowH + 4;
        }
    }

    private void handleHudEditorClick(int mx, int my) {
        int contentX = modalX + sidebarWidth + 14;
        int curY = modalY + 44;
        int cardW = modalWidth - sidebarWidth - 28;
        int btnW = 180;
        int btnH = 28;
        int btnX = contentX + (cardW - btnW) / 2;
        int btnY = curY + 152;

        if (isMouseOver(mx, my, btnX, btnY, btnW, btnH)) {
            playClickSound();
            if (this.minecraft != null) {
                this.minecraft.setScreen(new HudEditorScreen(this));
            }
        }
    }

    private void handleThemesClick(int mx, int my) {
        int contentX = modalX + sidebarWidth + 14;
        int contentW = modalWidth - sidebarWidth - 28;
        int contentY = modalY + 46;

        int curY = contentY + 20 - themesScrollY;
        ThemeLook[] looks = ThemeLook.values();
        int cardH = 34;
        int cardW = contentW - 14;

        for (ThemeLook look : looks) {
            if (isMouseOver(mx, my, contentX, curY, cardW, cardH)) {
                config.setThemeLook(look);
                config.setThemeAccent(ThemeAccent.MATCH_THEME);
                ThemeManager.invalidateCache();
                AaRoundRect.clearCache();
                setStatus("Applied Theme: " + look.getTitle());
                playClickSound();
                return;
            }
            curY += cardH + 5;
        }

        curY += 8;

        // Opacity Slider Click
        int sliderW = 85;
        int badgeW = 38;
        int badgeX = contentX + cardW - 12 - badgeW;
        int sliderX = badgeX - 8 - sliderW;
        if (isMouseOver(mx, my, sliderX - 4, curY + 8, sliderW + 8, 20)) {
            this.isDraggingGlassOpacity = true;
            this.dragGlassOpacityRatio = Math.max(0f, Math.min(1f, (float) (mx - sliderX) / sliderW));
            config.setGlassOpacity(0.40f + (dragGlassOpacityRatio * 0.60f));
            AaRoundRect.clearCache();
            return;
        }

        curY += 48 + 16;

        ThemeAccent[] accents = ThemeAccent.values();
        int gridCols = 3;
        int cellW = (cardW - ((gridCols - 1) * 6)) / gridCols;
        int cellH = 24;

        for (int i = 0; i < accents.length; i++) {
            int col = i % gridCols;
            int row = i / gridCols;
            int cellX = contentX + (col * (cellW + 6));
            int cellY = curY + (row * (cellH + 5));

            if (isMouseOver(mx, my, cellX, cellY, cellW, cellH)) {
                config.setThemeAccent(accents[i]);
                ThemeManager.invalidateCache();
                AaRoundRect.clearCache();
                setStatus("Accent Color: " + accents[i].getTitle());
                playClickSound();
                return;
            }
        }
    }

    private void handleSettingsClick(int mx, int my) {
        int contentX = modalX + sidebarWidth + 14;
        int contentW = modalWidth - sidebarWidth - 28;
        int contentY = modalY + 46;

        int curY = contentY + 4 - settingsScrollY;
        int cardW = contentW - 14;
        int rowH = 38;

        int sliderW = SLIDER_TRACK_W;
        int badgeW = BADGE_TAG_W;
        int badgeX = contentX + cardW - 14 - badgeW;
        int sliderX = badgeX - 8 - sliderW;
        int switchX = badgeX + (badgeW - 32);
        int buttonW = 115;
        int buttonX = contentX + cardW - 14 - buttonW;

        // GROUP 1: CELESTIAL SKY LYRICS
        curY += 15;

        // Row 1: Sky Lyrics Enable
        int r1Y = curY;
        if (isMouseOver(mx, my, switchX - 4, r1Y + 6, 40, 24)) {
            config.setSkyLyricsEnabled(!config.isSkyLyricsEnabled());
            setStatus(config.isSkyLyricsEnabled() ? "Sky Lyrics Enabled" : "Sky Lyrics Disabled");
            playClickSound();
            return;
        }

        // Row 2: Sky Lyrics Direction (Fixed removed!)
        int r2Y = curY + rowH;
        if (isMouseOver(mx, my, buttonX, r2Y + 9, buttonW, 20)) {
            config.setSkyLyricsMode(config.getSkyLyricsMode().next());
            setStatus("Sky Direction: " + config.getSkyLyricsMode().getDisplayName());
            playClickSound();
            return;
        }

        // Row 3: Sky Lyrics Scale
        int r3Y = curY + (rowH * 2);
        if (isMouseOver(mx, my, sliderX - 4, r3Y + 8, sliderW + 8, 20)) {
            this.isDraggingSkyScale = true;
            this.dragSkyScaleRatio = Math.max(0f, Math.min(1f, (float) (mx - sliderX) / sliderW));
            int percent = Math.round(50 + dragSkyScaleRatio * 150);
            config.setSkyLyricsScalePercent(percent);
            return;
        }

        // Row 4: Sky Visible Lines
        int r4Y = curY + (rowH * 3);
        if (isMouseOver(mx, my, sliderX - 4, r4Y + 8, sliderW + 8, 20)) {
            this.isDraggingSkyLines = true;
            this.dragSkyLinesRatio = Math.max(0f, Math.min(1f, (float) (mx - sliderX) / sliderW));
            int lines = Math.round(3 + dragSkyLinesRatio * 22);
            config.setSkyLyricsLength(lines);
            return;
        }

        // Row 5: Typography
        int r5Y = curY + (rowH * 4);
        if (isMouseOver(mx, my, buttonX, r5Y + 9, buttonW, 20)) {
            config.setLyricFont(config.getLyricFont().next());
            setStatus("Lyrics Font: " + config.getLyricFont().getDisplayName());
            playClickSound();
            return;
        }

        curY += (5 * rowH) + 16;

        // GROUP 2: IN-GAME HUD OVERLAY
        curY += 15;

        // Row 1: HUD Enabled
        int h1Y = curY;
        if (isMouseOver(mx, my, switchX - 4, h1Y + 6, 40, 24)) {
            if (config.getHudPosition() == ModConfig.HudPosition.OFF) {
                if (config.getHudCustomX() >= 0 && config.getHudCustomY() >= 0) {
                    config.setHudPosition(ModConfig.HudPosition.CUSTOM);
                } else {
                    config.setHudPosition(ModConfig.HudPosition.TOP_RIGHT);
                }
                setStatus("HUD Enabled (" + config.getHudPosition().getDisplayName() + ")");
            } else {
                config.setHudPosition(ModConfig.HudPosition.OFF);
                setStatus("HUD Disabled");
            }
            playClickSound();
            return;
        }

        // Row 2: HUD Lyrics Line
        int h2Y = curY + rowH;
        if (isMouseOver(mx, my, switchX - 4, h2Y + 6, 40, 24)) {
            config.setHudShowLyrics(!config.isHudShowLyrics());
            setStatus(config.isHudShowLyrics() ? "HUD Lyrics Line Enabled" : "HUD Lyrics Line Hidden");
            playClickSound();
            return;
        }

        // Row 3: HUD Opacity
        int h3Y = curY + (rowH * 2);
        if (isMouseOver(mx, my, sliderX - 4, h3Y + 8, sliderW + 8, 20)) {
            this.isDraggingHudOpacity = true;
            this.dragHudOpacityRatio = Math.max(0f, Math.min(1f, (float) (mx - sliderX) / sliderW));
            int percent = Math.round(dragHudOpacityRatio * 100);
            config.setHudOpacityPercent(percent);
            return;
        }

        // Row 4: HUD Scale
        int h4Y = curY + (rowH * 3);
        if (isMouseOver(mx, my, sliderX - 4, h4Y + 8, sliderW + 8, 20)) {
            this.isDraggingHudScale = true;
            this.dragHudScaleRatio = Math.max(0f, Math.min(1f, (float) (mx - sliderX) / sliderW));
            int percent = Math.round(50 + dragHudScaleRatio * 100);
            config.setHudScalePercent(percent);
            return;
        }

        curY += (4 * rowH) + 16;

        // GROUP 3: AUDIO DUCKING & UTILITIES
        curY += 15;

        // Row 1: Ducking
        int a1Y = curY;
        if (isMouseOver(mx, my, switchX - 4, a1Y + 6, 40, 24)) {
            config.setAudioDuckingEnabled(!config.isAudioDuckingEnabled());
            setStatus(config.isAudioDuckingEnabled() ? "Audio Ducking Active" : "Audio Ducking Disabled");
            playClickSound();
            return;
        }

        // Row 2: Ducking Multiplier
        int a2Y = curY + rowH;
        if (isMouseOver(mx, my, sliderX - 4, a2Y + 8, sliderW + 8, 20)) {
            this.isDraggingDucking = true;
            this.dragDuckingRatio = Math.max(0f, Math.min(1f, (float) (mx - sliderX) / sliderW));
            float mult = 0.05f + (dragDuckingRatio * 0.95f);
            config.setDuckingMultiplier(mult);
            return;
        }

        // Row 3: Diagnostics
        int a3Y = curY + (rowH * 2);
        if (isMouseOver(mx, my, switchX - 4, a3Y + 6, 40, 24)) {
            config.setMetricsEnabled(!config.isMetricsEnabled());
            setStatus(config.isMetricsEnabled() ? "Anonymous Diagnostics Enabled" : "Diagnostics Disabled");
            playClickSound();
            return;
        }

        // Row 4: Buttons
        int a4Y = curY + (rowH * 3);
        int btnW = (cardW - 32) / 2;
        if (isMouseOver(mx, my, contentX + 12, a4Y + 8, btnW, 22)) {
            SpotifyStateManager.getInstance().fullReload();
            setStatus("Reloading Windows Media Bridge...");
            playClickSound();
            return;
        }

        int btn2X = contentX + 12 + btnW + 8;
        if (isMouseOver(mx, my, btn2X, a4Y + 8, btnW, 22)) {
            it.itzcrih.espoty.update.UpdateChecker.getInstance().checkForUpdatesAsync();
            setStatus("Checking for updates online...");
            playClickSound();
            return;
        }

        curY += (4 * rowH) + 20;

        // Author credits link
        if (isMouseOver(mx, my, contentX + 2, curY, 260, 12)) {
            try {
                net.minecraft.util.Util.getPlatform().openUri(new java.net.URI("https://itzcrih.it"));
            } catch (Exception ignored) {}
            playClickSound();
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int mx = (int) event.x();
        boolean hasAnyDrag = isDraggingSeek || isDraggingVolume || isDraggingHudOpacity
                || isDraggingHudScale || isDraggingSkyScale || isDraggingSkyLines
                || isDraggingGlassOpacity || isDraggingDucking;

        if (!hasAnyDrag) {
            return super.mouseDragged(event, dragX, dragY);
        }

        int contentX = modalX + sidebarWidth + 14;
        int contentW = modalWidth - sidebarWidth - 28;
        int contentY = modalY + 46;
        int cardW = contentW - 10;
        int sliderW = SLIDER_TRACK_W;
        int badgeW = BADGE_TAG_W;
        int badgeX = contentX + cardW - 14 - badgeW;
        int sliderX = badgeX - 8 - sliderW;

        if (isDraggingSeek) {
            int seekW = contentW - 8;
            int seekX = contentX + 4;
            this.dragSeekRatio = Math.max(0f, Math.min(1f, (float) (mx - seekX) / seekW));
            PlaybackState state = stateManager.getCurrentState();
            if (state != null && state.getDurationMs() > 0) {
                long targetMs = (long) (state.getDurationMs() * dragSeekRatio);
                stateManager.seek(targetMs);
            }
            return true;
        }

        if (isDraggingVolume) {
            int centerX = contentX + (contentW / 2);
            int volBarW = 100;
            int volBarX = centerX - (volBarW / 2);
            this.dragVolumeRatio = Math.max(0f, Math.min(1f, (float) (mx - volBarX) / volBarW));
            int targetVol = Math.round(dragVolumeRatio * 100);
            stateManager.setVolume(targetVol);
            config.setVolume(targetVol);
            return true;
        }

        if (isDraggingGlassOpacity) {
            int themesBadgeX = contentX + contentW - 12 - badgeW;
            int themesSliderX = themesBadgeX - 8 - sliderW;
            this.dragGlassOpacityRatio = Math.max(0f, Math.min(1f, (float) (mx - themesSliderX) / sliderW));
            config.setGlassOpacity(0.40f + (dragGlassOpacityRatio * 0.60f));
            AaRoundRect.clearCache();
            return true;
        }

        if (isDraggingHudOpacity) {
            this.dragHudOpacityRatio = Math.max(0f, Math.min(1f, (float) (mx - sliderX) / sliderW));
            int percent = Math.round(dragHudOpacityRatio * 100);
            config.setHudOpacityPercent(percent);
            return true;
        }

        if (isDraggingHudScale) {
            this.dragHudScaleRatio = Math.max(0f, Math.min(1f, (float) (mx - sliderX) / sliderW));
            int percent = Math.round(50 + dragHudScaleRatio * 100);
            config.setHudScalePercent(percent);
            return true;
        }

        if (isDraggingSkyScale) {
            this.dragSkyScaleRatio = Math.max(0f, Math.min(1f, (float) (mx - sliderX) / sliderW));
            int percent = Math.round(50 + dragSkyScaleRatio * 150);
            config.setSkyLyricsScalePercent(percent);
            return true;
        }

        if (isDraggingSkyLines) {
            this.dragSkyLinesRatio = Math.max(0f, Math.min(1f, (float) (mx - sliderX) / sliderW));
            int lines = Math.round(3 + dragSkyLinesRatio * 22);
            config.setSkyLyricsLength(lines);
            return true;
        }

        if (isDraggingDucking) {
            this.dragDuckingRatio = Math.max(0f, Math.min(1f, (float) (mx - sliderX) / sliderW));
            float mult = 0.05f + (dragDuckingRatio * 0.95f);
            config.setDuckingMultiplier(mult);
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.isDraggingSeek = false;
        this.isDraggingVolume = false;
        this.isDraggingHudOpacity = false;
        this.isDraggingHudScale = false;
        this.isDraggingSkyScale = false;
        this.isDraggingSkyLines = false;
        this.isDraggingGlassOpacity = false;
        this.isDraggingDucking = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int delta = (int) (-verticalAmount * 16);
        int viewH = modalHeight - 54;
        switch (activeTab) {
            case LYRICS -> {
                LyricsManager.SongLyrics songLyrics = LyricsManager.getInstance().getCurrentLyrics();
                int lyricsTotalH = (songLyrics != null && songLyrics.getLines() != null) ? songLyrics.getLines().size() * 22 : 0;
                int maxScroll = Math.max(0, lyricsTotalH - viewH);
                this.lyricsScrollY = Math.max(0, Math.min(maxScroll, this.lyricsScrollY + delta));
                return true;
            }
            case QUEUE -> {
                QueueResponse queue = stateManager.getCurrentQueue();
                int itemsCount = (queue != null && queue.getQueue() != null) ? queue.getQueue().size() : 0;
                int queueTotalH = 40 + (itemsCount * 34) + 12;
                int maxScroll = Math.max(0, queueTotalH - (viewH - 22));
                this.queueScrollY = Math.max(0, Math.min(maxScroll, this.queueScrollY + delta));
                return true;
            }
            case THEMES -> {
                int maxScroll = Math.max(0, THEMES_CONTENT_HEIGHT - viewH);
                this.themesScrollY = Math.max(0, Math.min(maxScroll, this.themesScrollY + delta));
                return true;
            }
            case SETTINGS -> {
                int maxScroll = Math.max(0, SETTINGS_CONTENT_HEIGHT - viewH);
                this.settingsScrollY = Math.max(0, Math.min(maxScroll, this.settingsScrollY + delta));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        // Spacebar toggles playback on Player tab
        if (keyCode == 32 && activeTab == Tab.PLAYER) {
            PlaybackState state = stateManager.getCurrentState();
            if (state != null && state.canPlayPause()) {
                stateManager.togglePlayPause();
                playClickSound();
                return true;
            }
        }

        // Escape triggers smooth exit animation
        if (keyCode == 256) {
            triggerCloseAnimation();
            return true;
        }

        return super.keyPressed(event);
    }

    private void triggerCloseAnimation() {
        if (!isClosing) {
            this.isClosing = true;
            this.closeStartTime = System.currentTimeMillis();
        }
    }

    private boolean isMouseOver(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
