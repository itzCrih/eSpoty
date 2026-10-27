package it.itzcrih.espoty.gui.theme;

import it.itzcrih.espoty.config.ModConfig;

/**
 * Modern Theme Engine for eSpoty.
 * Manages theme presets, accent color palettes, and glass styling.
 */
public class ThemeManager {

    public enum ThemeLook {
        BOREALIS("Borealis", "Neon Mint & Deep Space", 0x0B0F19, 0x111625, 0x161F33, 0x202B44, 0xFF00FFA3, 0xFF05F29A, 0x2800FFA3, 0x1800FFA3, 0xFFFFFFFF, 0xFF94A3B8, 0xFF64748B),
        MIDNIGHT("Midnight", "Obsidian & Royal Violet Glass", 0x0D0B18, 0x131024, 0x1A1530, 0x241D42, 0xFFA855F7, 0xFFC084FC, 0x28A855F7, 0x18A855F7, 0xFFFFFFFF, 0xFFA78BFA, 0xFF7C3AED),
        OLED("OLED Black", "Pure Pitch Black & Lime Emerald", 0x000000, 0x080808, 0x121212, 0x1C1C1C, 0xFF22C55E, 0xFF16A34A, 0x20FFFFFF, 0x12FFFFFF, 0xFFFFFFFF, 0xFFA1A1AA, 0xFF52525B),
        DAYLIGHT("Daylight", "Frosted Slate & Azure Sky", 0x1E293B, 0x0F172A, 0x26354D, 0x334460, 0xFF38BDF8, 0xFF0EA5E9, 0x3038BDF8, 0x2038BDF8, 0xFFF8FAFC, 0xFFCBD5E1, 0xFF94A3B8),
        SUNSET("Sunset", "Velvet Dusk & Coral Rose", 0x160C14, 0x1F111C, 0x2A1727, 0x3B1F37, 0xFFF97316, 0xFFFB7185, 0x28FB7185, 0x18F97316, 0xFFFFF1F2, 0xFFFDA4AF, 0xFF9F1239),
        CYBER_GOLD("Cyber Gold", "Dark Titanium & Radiant Gold", 0x0D0D14, 0x14141E, 0x1C1C29, 0x28283A, 0xFFFFD700, 0xFFFFF59D, 0x28FFD700, 0x18FFD700, 0xFFFEFCE8, 0xFFFEF08A, 0xFFCA8A04);

        private final String title;
        private final String subtitle;
        private final int windowRgb;
        private final int panelRgb;
        private final int cardRgb;
        private final int cardHoverRgb;
        private final int accentColor;
        private final int accentColor2;
        private final int glassEdge;
        private final int sheen;
        private final int textPrimary;
        private final int textSecondary;
        private final int textMuted;

        ThemeLook(String title, String subtitle, int windowRgb, int panelRgb, int cardRgb, int cardHoverRgb,
                  int accentColor, int accentColor2, int glassEdge, int sheen,
                  int textPrimary, int textSecondary, int textMuted) {
            this.title = title;
            this.subtitle = subtitle;
            this.windowRgb = windowRgb;
            this.panelRgb = panelRgb;
            this.cardRgb = cardRgb;
            this.cardHoverRgb = cardHoverRgb;
            this.accentColor = accentColor;
            this.accentColor2 = accentColor2;
            this.glassEdge = glassEdge;
            this.sheen = sheen;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.textMuted = textMuted;
        }

        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }
        public int getWindowRgb() { return windowRgb; }
        public int getPanelRgb() { return panelRgb; }
        public int getCardRgb() { return cardRgb; }
        public int getCardHoverRgb() { return cardHoverRgb; }
        public int getAccentColor() { return accentColor; }
        public int getAccentColor2() { return accentColor2; }
        public int getGlassEdge() { return glassEdge; }
        public int getSheen() { return sheen; }
        public int getTextPrimary() { return textPrimary; }
        public int getTextSecondary() { return textSecondary; }
        public int getTextMuted() { return textMuted; }
    }

    public enum ThemeAccent {
        MATCH_THEME("Default", 0, 0),
        MINT_CYAN("Mint Cyan", 0xFF00FFA3, 0xFF05F29A),
        SPOTIFY_GREEN("Spotify Green", 0xFF1DB954, 0xFF22C55E),
        ROYAL_PURPLE("Royal Violet", 0xFFA855F7, 0xFFC084FC),
        ELECTRIC_BLUE("Electric Blue", 0xFF38BDF8, 0xFF0284C7),
        SUNSET_ORANGE("Sunset Orange", 0xFFF97316, 0xFFFB923C),
        CORAL_ROSE("Coral Rose", 0xFFFB7185, 0xFFF43F5E),
        CYBER_GOLD("Cyber Gold", 0xFFFFD700, 0xFFFFF59D),
        ICE_WHITE("Ice White", 0xFFF8FAFC, 0xFFE2E8F0);

        private final String title;
        private final int color;
        private final int color2;

        ThemeAccent(String title, int color, int color2) {
            this.title = title;
            this.color = color;
            this.color2 = color2;
        }

        public String getTitle() { return title; }
        public int getColor() { return color; }
        public int getColor2() { return color2; }
    }

    // Cached theme colors
    private static ThemeLook cachedLook;
    private static ThemeAccent cachedAccentSetting;
    private static float cachedOpacity = -1f;

    private static int cachedAccent;
    private static int cachedAccent2;
    private static int cachedWindowBg;
    private static int cachedPanelBg;
    private static int cachedCardBg;
    private static int cachedCardHoverBg;
    private static int cachedGlassEdge;
    private static int cachedSheen;
    private static int cachedTextPrimary;
    private static int cachedTextSecondary;
    private static int cachedTextMuted;

    private static void updateCacheIfNeeded() {
        ModConfig config = ModConfig.getInstance();
        ThemeLook look = config.getThemeLook();
        if (look == null) look = ThemeLook.BOREALIS;
        ThemeAccent accent = config.getThemeAccent();
        if (accent == null) accent = ThemeAccent.MATCH_THEME;
        float opacity = config.getGlassOpacity();

        if (look != cachedLook || accent != cachedAccentSetting || Math.abs(opacity - cachedOpacity) > 0.001f) {
            cachedLook = look;
            cachedAccentSetting = accent;
            cachedOpacity = opacity;

            if (accent != ThemeAccent.MATCH_THEME) {
                cachedAccent = accent.getColor();
                cachedAccent2 = accent.getColor2();
                cachedGlassEdge = applyAlpha(accent.getColor(), 0.22f);
                cachedSheen = applyAlpha(accent.getColor(), 0.16f);
            } else {
                cachedAccent = look.getAccentColor();
                cachedAccent2 = look.getAccentColor2();
                cachedGlassEdge = look.getGlassEdge();
                cachedSheen = look.getSheen();
            }

            cachedWindowBg = applyAlpha(0xFF000000 | look.getWindowRgb(), opacity);
            float panelOpacity = Math.min(1.0f, opacity + 0.05f);
            cachedPanelBg = applyAlpha(0xFF000000 | look.getPanelRgb(), panelOpacity);
            cachedCardBg = applyAlpha(0xFF000000 | look.getCardRgb(), 0.70f);
            cachedCardHoverBg = applyAlpha(0xFF000000 | look.getCardHoverRgb(), 0.85f);
            cachedTextPrimary = look.getTextPrimary();
            cachedTextSecondary = look.getTextSecondary();
            cachedTextMuted = look.getTextMuted();
        }
    }

    public static void invalidateCache() {
        cachedLook = null;
        cachedAccentSetting = null;
        cachedOpacity = -1f;
        updateCacheIfNeeded();
    }

    public static ThemeLook getLook() {
        updateCacheIfNeeded();
        return cachedLook;
    }

    public static ThemeAccent getAccentSetting() {
        updateCacheIfNeeded();
        return cachedAccentSetting;
    }

    public static int getAccent() {
        updateCacheIfNeeded();
        return cachedAccent;
    }

    public static int getAccent2() {
        updateCacheIfNeeded();
        return cachedAccent2;
    }

    public static int getAccentGlow(float alphaFactor) {
        return applyAlpha(getAccent(), alphaFactor * 0.40f);
    }

    public static int getAccentDim(float alphaFactor) {
        return applyAlpha(getAccent(), alphaFactor * 0.20f);
    }

    public static int getWindowBg() {
        updateCacheIfNeeded();
        return cachedWindowBg;
    }

    public static int getPanelBg() {
        updateCacheIfNeeded();
        return cachedPanelBg;
    }

    public static int getCardBg() {
        updateCacheIfNeeded();
        return cachedCardBg;
    }

    public static int getCardHoverBg() {
        updateCacheIfNeeded();
        return cachedCardHoverBg;
    }

    public static int getGlassEdge() {
        updateCacheIfNeeded();
        return cachedGlassEdge;
    }

    public static int getSheen() {
        updateCacheIfNeeded();
        return cachedSheen;
    }

    public static int getTextPrimary() {
        updateCacheIfNeeded();
        return cachedTextPrimary;
    }

    public static int getTextSecondary() {
        updateCacheIfNeeded();
        return cachedTextSecondary;
    }

    public static int getTextMuted() {
        updateCacheIfNeeded();
        return cachedTextMuted;
    }

    public static int applyAlpha(int argb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)))));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static int mix(int c1, int c2, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
