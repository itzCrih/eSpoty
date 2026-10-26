package com.espoty.config;

import com.espoty.ESpotyMod;
import com.espoty.gui.theme.ThemeManager.ThemeAccent;
import com.espoty.gui.theme.ThemeManager.ThemeLook;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("espoty/config.json").toFile();
    private static ModConfig INSTANCE;

    public enum HudPosition {
        TOP_LEFT("Top Left"),
        TOP_RIGHT("Top Right"),
        BOTTOM_LEFT("Bottom Left"),
        BOTTOM_RIGHT("Bottom Right"),
        OFF("Disabled");

        private final String displayName;

        HudPosition(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public HudPosition next() {
            HudPosition[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum SkyLyricsMode {
        FOLLOW_LOOK("Dynamic Gaze"),
        NORTH_SOUTH("Fixed North & South");

        private final String displayName;

        SkyLyricsMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public SkyLyricsMode next() {
            SkyLyricsMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum LyricFont {
        MODERN("Modern Sans", Identifier.fromNamespaceAndPath("espoty", "modern")),
        SPOTIFY_BOLD("Spotify Bold", Identifier.fromNamespaceAndPath("espoty", "bold")),
        MINECRAFT("Minecraft Pixel", Identifier.fromNamespaceAndPath("minecraft", "default")),
        UNIFORM("Vanilla Smooth", Identifier.fromNamespaceAndPath("minecraft", "uniform")),
        MONOSPACE("Monospace", Identifier.fromNamespaceAndPath("espoty", "mono"));

        private final String displayName;
        private final Identifier fontId;

        LyricFont(String displayName, Identifier fontId) {
            this.displayName = displayName;
            this.fontId = fontId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Identifier getFontId() {
            return fontId;
        }

        public Style getStyle() {
            if ("minecraft".equals(fontId.getNamespace()) && "default".equals(fontId.getPath())) {
                return Style.EMPTY;
            }
            return Style.EMPTY.withFont(new FontDescription.Resource(fontId));
        }

        public LyricFont next() {
            LyricFont[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    // Settings
    private boolean enabled = true;
    private HudPosition hudPosition = HudPosition.TOP_RIGHT;
    private boolean hudShowLyrics = true;
    private boolean hudCompact = false;
    private boolean hudAutoHide = false;
    private int hudAutoHideSeconds = 8;
    private boolean skyLyricsEnabled = true;
    private SkyLyricsMode skyLyricsMode = SkyLyricsMode.FOLLOW_LOOK;
    private float skyLyricsScale = 1.0f;
    private int skyLyricsLength = 5;
    private LyricFont lyricFont = LyricFont.MODERN;
    private ThemeLook themeLook = ThemeLook.AURORA;
    private ThemeAccent themeAccent = ThemeAccent.MATCH_THEME;
    private float glassOpacity = 0.90f;
    private boolean syncHudTheme = true;
    private float hudScale = 1.0f;
    private float hudOpacity = 0.85f;
    private String customClientId = "";
    private int volume = 80;
    private int volumeStep = 5;
    private boolean showChatFeedback = true;
    private boolean audioDuckingEnabled = true;
    private float duckingMultiplier = 0.35f;
    private boolean checkForUpdates = true;
    private Set<String> likedTracks = new HashSet<>();

    // Theme Color Palette (Royal Gold & Neon Yellow)
    public static final int COLOR_GOLD_PRIMARY = 0xFFFFD700;   // Bright Electric Gold
    public static final int COLOR_GOLD_SECONDARY = 0xFFE5A910; // Warm Amber Gold
    public static final int COLOR_YELLOW_ACCENT = 0xFFFFF066;  // Neon Light Yellow
    public static final int COLOR_YELLOW_BRIGHT = 0xFFFFF59D;  // Light Radiant Yellow
    public static final int COLOR_BG_DARK = 0xF20D0D14;        // Deep Obsidian Glass
    public static final int COLOR_BG_CARD = 0xF411111A;        // Sleek Surface Dark
    public static final int COLOR_BORDER_GOLD = 0x88FFD700;    // Gold Border Glow
    public static final int COLOR_TEXT_MUTED = 0xFFAAAAAA;     // Muted Gray
    public static final int COLOR_DISABLED = 0xFF555566;       // Disabled Gray
    public static final int COLOR_SUCCESS = 0xFF00E676;        // Neon Green
    public static final int COLOR_ERROR = 0xFFFF5252;          // Bright Red

    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static ModConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ModConfig config = GSON.fromJson(reader, ModConfig.class);
                if (config != null) {
                    if (config.likedTracks == null) config.likedTracks = new HashSet<>();
                    if (config.lyricFont == null) config.lyricFont = LyricFont.MODERN;
                    if (config.skyLyricsMode == null) config.skyLyricsMode = SkyLyricsMode.FOLLOW_LOOK;
                    if (config.themeLook == null) config.themeLook = ThemeLook.AURORA;
                    if (config.themeAccent == null) config.themeAccent = ThemeAccent.MATCH_THEME;
                    if (config.glassOpacity <= 0.05f) config.glassOpacity = 0.90f;
                    return config;
                }
            } catch (Exception e) {
                ESpotyMod.LOGGER.error("Failed to load config, falling back to defaults", e);
            }
        }
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            ESpotyMod.LOGGER.error("Failed to save config", e);
        }
    }

    public boolean isTrackLiked(String trackKey) {
        return trackKey != null && likedTracks.contains(trackKey);
    }

    public boolean toggleTrackLiked(String trackKey) {
        if (trackKey == null || trackKey.isEmpty()) return false;
        boolean nowLiked;
        if (likedTracks.contains(trackKey)) {
            likedTracks.remove(trackKey);
            nowLiked = false;
        } else {
            likedTracks.add(trackKey);
            nowLiked = true;
        }
        save();
        return nowLiked;
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public HudPosition getHudPosition() {
        return hudPosition;
    }

    public void setHudPosition(HudPosition hudPosition) {
        this.hudPosition = hudPosition;
        save();
    }

    public boolean isHudShowLyrics() {
        return hudShowLyrics;
    }

    public void setHudShowLyrics(boolean hudShowLyrics) {
        this.hudShowLyrics = hudShowLyrics;
        save();
    }

    public boolean isHudCompact() {
        return hudCompact;
    }

    public void setHudCompact(boolean hudCompact) {
        this.hudCompact = hudCompact;
        save();
    }

    public boolean isHudAutoHide() {
        return hudAutoHide;
    }

    public void setHudAutoHide(boolean hudAutoHide) {
        this.hudAutoHide = hudAutoHide;
        save();
    }

    public int getHudAutoHideSeconds() {
        return hudAutoHideSeconds;
    }

    public void setHudAutoHideSeconds(int hudAutoHideSeconds) {
        this.hudAutoHideSeconds = hudAutoHideSeconds;
        save();
    }

    public boolean isSkyLyricsEnabled() {
        return skyLyricsEnabled;
    }

    public void setSkyLyricsEnabled(boolean skyLyricsEnabled) {
        this.skyLyricsEnabled = skyLyricsEnabled;
        save();
    }

    public SkyLyricsMode getSkyLyricsMode() {
        return skyLyricsMode != null ? skyLyricsMode : SkyLyricsMode.FOLLOW_LOOK;
    }

    public void setSkyLyricsMode(SkyLyricsMode skyLyricsMode) {
        this.skyLyricsMode = skyLyricsMode != null ? skyLyricsMode : SkyLyricsMode.FOLLOW_LOOK;
        save();
    }

    public float getSkyLyricsScale() {
        return Math.max(0.5f, Math.min(2.0f, skyLyricsScale));
    }

    public void setSkyLyricsScale(float scale) {
        this.skyLyricsScale = Math.max(0.5f, Math.min(2.0f, scale));
        save();
    }

    public int getSkyLyricsScalePercent() {
        return Math.round(getSkyLyricsScale() * 100.0f);
    }

    public void setSkyLyricsScalePercent(int percent) {
        setSkyLyricsScale(Math.max(50, Math.min(200, percent)) / 100.0f);
    }

    public int getSkyLyricsLength() {
        return Math.max(3, Math.min(25, skyLyricsLength));
    }

    public void setSkyLyricsLength(int length) {
        this.skyLyricsLength = Math.max(3, Math.min(25, length));
        save();
    }

    public LyricFont getLyricFont() {
        return lyricFont != null ? lyricFont : LyricFont.MODERN;
    }

    public void setLyricFont(LyricFont lyricFont) {
        this.lyricFont = lyricFont != null ? lyricFont : LyricFont.MODERN;
        save();
    }

    public float getHudScale() {
        return Math.max(0.5f, Math.min(1.5f, hudScale));
    }

    public void setHudScale(float hudScale) {
        this.hudScale = Math.max(0.5f, Math.min(1.5f, hudScale));
        save();
    }

    public int getHudScalePercent() {
        return Math.round(getHudScale() * 100.0f);
    }

    public void setHudScalePercent(int percent) {
        setHudScale(Math.max(50, Math.min(150, percent)) / 100.0f);
    }

    public float getHudOpacity() {
        return Math.max(0.0f, Math.min(1.0f, hudOpacity));
    }

    public void setHudOpacity(float hudOpacity) {
        this.hudOpacity = Math.max(0.0f, Math.min(1.0f, hudOpacity));
        save();
    }

    public int getHudOpacityPercent() {
        return Math.round(getHudOpacity() * 100.0f);
    }

    public void setHudOpacityPercent(int percent) {
        setHudOpacity(Math.max(0, Math.min(100, percent)) / 100.0f);
    }

    public String getCustomClientId() {
        return customClientId != null ? customClientId.trim() : "";
    }

    public void setCustomClientId(String customClientId) {
        this.customClientId = customClientId != null ? customClientId.trim() : "";
        save();
    }

    public int getVolume() {
        return volume;
    }

    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
        save();
    }

    public int getVolumeStep() {
        return volumeStep;
    }

    public void setVolumeStep(int volumeStep) {
        this.volumeStep = volumeStep;
        save();
    }

    public boolean isShowChatFeedback() {
        return showChatFeedback;
    }

    public void setShowChatFeedback(boolean showChatFeedback) {
        this.showChatFeedback = showChatFeedback;
        save();
    }

    public boolean isAudioDuckingEnabled() {
        return audioDuckingEnabled;
    }

    public void setAudioDuckingEnabled(boolean audioDuckingEnabled) {
        this.audioDuckingEnabled = audioDuckingEnabled;
        save();
    }

    public boolean isCheckForUpdates() {
        return checkForUpdates;
    }

    public void setCheckForUpdates(boolean checkForUpdates) {
        this.checkForUpdates = checkForUpdates;
        save();
    }

    public float getDuckingMultiplier() {
        return duckingMultiplier;
    }

    public void setDuckingMultiplier(float duckingMultiplier) {
        this.duckingMultiplier = Math.max(0.05f, Math.min(1.0f, duckingMultiplier));
        save();
    }

    public Set<String> getLikedTracks() {
        return likedTracks;
    }

    public ThemeLook getThemeLook() {
        return themeLook != null ? themeLook : ThemeLook.AURORA;
    }

    public void setThemeLook(ThemeLook themeLook) {
        this.themeLook = themeLook != null ? themeLook : ThemeLook.AURORA;
        save();
    }

    public ThemeAccent getThemeAccent() {
        return themeAccent != null ? themeAccent : ThemeAccent.MATCH_THEME;
    }

    public void setThemeAccent(ThemeAccent themeAccent) {
        this.themeAccent = themeAccent != null ? themeAccent : ThemeAccent.MATCH_THEME;
        save();
    }

    public float getGlassOpacity() {
        return Math.max(0.40f, Math.min(1.0f, glassOpacity));
    }

    public void setGlassOpacity(float glassOpacity) {
        this.glassOpacity = Math.max(0.40f, Math.min(1.0f, glassOpacity));
        save();
    }

    public int getGlassOpacityPercent() {
        return Math.round(getGlassOpacity() * 100.0f);
    }

    public void setGlassOpacityPercent(int percent) {
        setGlassOpacity(Math.max(40, Math.min(100, percent)) / 100.0f);
    }

    public boolean isSyncHudTheme() {
        return syncHudTheme;
    }

    public void setSyncHudTheme(boolean syncHudTheme) {
        this.syncHudTheme = syncHudTheme;
        save();
    }
}
