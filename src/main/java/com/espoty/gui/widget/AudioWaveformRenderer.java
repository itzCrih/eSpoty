package com.espoty.gui.widget;

import net.minecraft.client.gui.GuiGraphics;

public class AudioWaveformRenderer {

    /**
     * Renders a modern dynamic audio visualizer waveform in Gold & Yellow.
     *
     * @param guiGraphics Graphics context
     * @param x Left X coordinate
     * @param y Bottom Y coordinate (base of waveform)
     * @param width Total width of the visualizer area
     * @param maxHeight Maximum bar height
     * @param numBars Number of equalizer frequency bars
     * @param isPlaying True if Spotify playback is active
     * @param timeMs System time in milliseconds for smooth phase animation
     * @param alpha Global alpha multiplier (0.0 to 1.0) for fade animations
     */
    public static void renderWaveform(GuiGraphics guiGraphics, int x, int y, int width, int maxHeight,
                                      int numBars, boolean isPlaying, long timeMs, float alpha) {
        if (numBars <= 0 || width <= 0 || maxHeight <= 0 || alpha <= 0.01f) return;

        int barWidth = Math.max(1, (width / numBars) - 1);
        int totalBarsWidth = numBars * (barWidth + 1) - 1;
        int startX = x + Math.max(0, (width - totalBarsWidth) / 2);

        double baseTime = timeMs / 140.0;

        for (int i = 0; i < numBars; i++) {
            int bx = startX + (i * (barWidth + 1));
            int h;

            if (isPlaying) {
                // Multi-harmonic synthesized audio frequency waves
                double freq1 = Math.sin(baseTime + (i * 0.42));
                double freq2 = Math.sin((baseTime * 1.7) + (i * 0.85));
                double freq3 = Math.cos((baseTime * 0.6) - (i * 0.3));
                double combined = (freq1 * 0.45) + (freq2 * 0.35) + (freq3 * 0.20);
                
                // Frequency curve (accentuate mids and bass)
                double curve = Math.sin((i / (double) numBars) * Math.PI) * 0.3 + 0.7;
                double normalized = Math.max(0.08, Math.min(1.0, (combined * 0.5 + 0.5) * curve));

                h = Math.max(2, (int) (normalized * maxHeight));
            } else {
                // Resting idle state (gentle 2px bar)
                h = 2;
            }

            int barTop = y - h;
            int barBottom = y;

            int colorBottom = applyAlpha(0xFFFFD700, alpha); // Royal Gold base
            int colorTop = applyAlpha(0xFFFFF066, alpha);    // Neon Yellow crest

            if (barWidth > 1) {
                guiGraphics.fillGradient(bx, barTop, bx + barWidth, barBottom, colorTop, colorBottom);
                // Crisp highlight tip
                if (h > 4) {
                    int tipColor = applyAlpha(0xFFFFFFFF, alpha * 0.85f);
                    guiGraphics.fill(bx, barTop, bx + barWidth, barTop + 1, tipColor);
                }
            } else {
                guiGraphics.fill(bx, barTop, bx + 1, barBottom, colorTop);
            }
        }
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = (argb >> 24) & 0xFF;
        int newA = Math.max(0, Math.min(255, (int) (a * alpha)));
        return (newA << 24) | (argb & 0x00FFFFFF);
    }
}
