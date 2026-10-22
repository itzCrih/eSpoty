package com.espoty.config;

import com.espoty.ESpotyMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

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

    // Settings
    private boolean enabled = true;
    private HudPosition hudPosition = HudPosition.TOP_RIGHT;
    private boolean hudShowLyrics = true;
    private boolean hudCompact = false;
    private boolean hudAutoHide = false;
    private int hudAutoHideSeconds = 5;
    private float hudScale = 1.0f;
    private String customClientId = "";
    private int volume = 80;
    private int volumeStep = 5;
    private boolean showChatFeedback = true;
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

    public float getHudScale() {
        return hudScale;
    }

    public void setHudScale(float hudScale) {
        this.hudScale = hudScale;
        save();
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

    public Set<String> getLikedTracks() {
        return likedTracks;
    }
}
