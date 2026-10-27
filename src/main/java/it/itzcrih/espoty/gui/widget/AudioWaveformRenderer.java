package it.itzcrih.espoty.gui.widget;

import it.itzcrih.espoty.config.ModConfig;
import it.itzcrih.espoty.gui.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;

public class AudioWaveformRenderer {

    /**
     * Backward-compatible overloads.
     */
    public static void renderWaveform(GuiGraphics guiGraphics, int x, int y, int width, int maxHeight,
                                      int numBars, boolean isPlaying, long timeMs, float alpha) {
        int colorBottomRgb = ModConfig.getInstance().isSyncHudTheme() ? ThemeManager.getAccent() : 0xFFFFD700;
        int colorTopRgb = ModConfig.getInstance().isSyncHudTheme() ? ThemeManager.getAccent2() : 0xFFFFF066;
        renderWaveform(guiGraphics, x, y, width, maxHeight, numBars, isPlaying, timeMs, alpha, colorBottomRgb, colorTopRgb);
    }

    public static void renderWaveform(GuiGraphics guiGraphics, int x, int y, int width, int maxHeight,
                                      int numBars, boolean isPlaying, long timeMs, float alpha, int colorBottomRgb, int colorTopRgb) {
        renderSyncedWaveform(guiGraphics, x, y, width, maxHeight, numBars, isPlaying, timeMs, alpha,
                colorBottomRgb, colorTopRgb, "track", "artist", true, 1.0f);
    }

    public static void renderSyncedWaveform(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int maxHeight,
            int numBars,
            boolean isPlaying,
            long songProgressMs,
            float alpha,
            int colorBottomRgb,
            int colorTopRgb,
            String trackTitle,
            String artistNames,
            boolean hasActiveLyric
    ) {
        renderSyncedWaveform(guiGraphics, x, y, width, maxHeight, numBars, isPlaying, songProgressMs, alpha,
                colorBottomRgb, colorTopRgb, trackTitle, artistNames, hasActiveLyric, 1.0f);
    }

    /**
     * Renders a song-synchronized audio equalizer waveform.
     */
    public static void renderSyncedWaveform(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int maxHeight,
            int numBars,
            boolean isPlaying,
            long songProgressMs,
            float alpha,
            int colorBottomRgb,
            int colorTopRgb,
            String trackTitle,
            String artistNames,
            boolean hasActiveLyric,
            float volumeRatio
    ) {
        if (numBars <= 0 || width <= 0 || maxHeight <= 0 || alpha <= 0.005f) return;

        int barWidth = Math.max(1, (width / numBars) - 1);
        int totalBarsWidth = numBars * (barWidth + 1) - 1;
        int startX = x + Math.max(0, (width - totalBarsWidth) / 2);

        int colorBottom = applyAlpha(colorBottomRgb, alpha);
        int colorTop = applyAlpha(colorTopRgb, alpha);

        if (!isPlaying || volumeRatio <= 0.005f) {
            // Resting idle state (gentle 2px bar)
            for (int i = 0; i < numBars; i++) {
                int bx = startX + (i * (barWidth + 1));
                guiGraphics.fill(bx, y - 2, bx + barWidth, y, colorBottom);
            }
            return;
        }

        // Volume scaling curve: responsive non-linear loudness
        double volScale = Math.max(0.0, Math.min(1.0, (double) volumeRatio));
        double volFactor = 0.20 + 0.80 * Math.pow(volScale, 0.7);

        // Derive track-specific musical signature and tempo
        int songHash = Math.abs(((trackTitle != null ? trackTitle : "") + "::" + (artistNames != null ? artistNames : "")).hashCode());
        int bpm = 110 + (songHash % 32); // 110 to 142 BPM
        double beatDurationMs = 60000.0 / bpm;

        double beatPhase = (songProgressMs % beatDurationMs) / beatDurationMs;
        // Powerful rhythmic kick on every beat (fast attack, exponential decay)
        double kickPulse = Math.pow(Math.max(0.0, 1.0 - beatPhase), 2.8);

        // Snare / off-beat groove
        double halfBeatPhase = ((songProgressMs * 2.0) % beatDurationMs) / beatDurationMs;
        double snarePulse = Math.pow(Math.max(0.0, 1.0 - halfBeatPhase), 2.2);

        // Rapid 16th-note shimmer
        double sixteenthPhase = ((songProgressMs * 4.0) % beatDurationMs) / beatDurationMs;
        double sixteenthPulse = Math.abs(Math.sin(sixteenthPhase * Math.PI));

        double songSec = songProgressMs / 1000.0;
        int tipColor = applyAlpha(0xFFFFFFFF, alpha * 0.92f);

        for (int i = 0; i < numBars; i++) {
            int bx = startX + (i * (barWidth + 1));

            // Frequency spectrum position: 0.0 = deep sub-bass (left), 1.0 = crystal treble (right)
            double f = (numBars > 1) ? (i / (double) (numBars - 1)) : 0.5;

            // Frequency bands response
            double wKick = Math.max(0.0, 1.0 - (f / 0.32));
            double subEnergy = (0.18 + 0.68 * kickPulse) * wKick;

            double wMid = Math.max(0.0, 1.0 - Math.abs(f - 0.48) / 0.28);
            double snareEnergy = (0.14 + 0.52 * snarePulse) * wMid;
            double vocalEnergy = hasActiveLyric ? (0.34 * wMid) : (0.08 * wMid);

            double wTreble = Math.max(0.0, (f - 0.55) / 0.45);
            double highEnergy = (0.14 + 0.50 * sixteenthPulse) * wTreble;

            // Organic harmonic wave unique to each individual bar (no repeating modulo)
            double barNoise = 0.12 * Math.sin(songSec * (3.0 + (i * 0.7)) + (i * 1.3) + (songHash * 0.02));
            double barNoise2 = 0.08 * Math.cos(songSec * (5.5 + (i * 0.4)) + (i * 2.1));

            double normalized = Math.max(0.08, Math.min(1.0, subEnergy + snareEnergy + vocalEnergy + highEnergy + 0.10 + barNoise + barNoise2));

            int h = Math.max(2, (int) (normalized * maxHeight * volFactor));
            int barTop = y - h;

            if (barWidth > 1) {
                guiGraphics.fillGradient(bx, barTop, bx + barWidth, y, colorTop, colorBottom);
                // Crisp luminous highlight tip
                if (h > 4) {
                    guiGraphics.fill(bx, barTop, bx + barWidth, barTop + 1, tipColor);
                }
            } else {
                guiGraphics.fill(bx, barTop, bx + 1, y, colorTop);
            }
        }
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = (argb >> 24) & 0xFF;
        int newA = Math.max(0, Math.min(255, (int) (a * Math.max(0f, Math.min(1f, alpha)))));
        return (newA << 24) | (argb & 0x00FFFFFF);
    }
}
