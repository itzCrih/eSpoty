package com.espoty.gui.sky;

import com.espoty.config.ModConfig;
import com.espoty.lyrics.LyricLine;
import com.espoty.lyrics.LyricsManager;
import com.espoty.spotify.SpotifyStateManager;
import com.espoty.spotify.models.PlaybackState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Renders celestial lyrics in a static position in the North sky.
 * Anchored permanently in world space towards North (does not follow camera rotation).
 * Strict depth-tested occlusion guarantees terrain, blocks, and entities stay in front.
 * Dual-depth rendering with polygon offset eliminates all Z-fighting flicker.
 */
public class SkyLyricsRenderer {

    private static final Identifier MODERN_FONT = Identifier.fromNamespaceAndPath("espoty", "modern");
    private static final Style MODERN_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(MODERN_FONT));

    private static double smoothScrollY = 0.0;
    private static String lastTrackKey = "";

    public static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        ModConfig config = ModConfig.getInstance();
        if (!config.isEnabled() || !config.isSkyLyricsEnabled()) return;

        SpotifyStateManager stateManager = SpotifyStateManager.getInstance();
        PlaybackState state = stateManager.getCurrentState();
        if (state == null || state.getTrackName().isEmpty() || !state.isPlaying()) {
            return;
        }

        PoseStack poseStack = context.matrices();
        if (poseStack == null) return;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        if (bufferSource == null) return;

        Font font = mc.font;
        if (font == null) return;

        // Reset scroll when track changes
        String currentTrack = state.getTrackName() + " - " + state.getArtistNames();
        if (!currentTrack.equals(lastTrackKey)) {
            lastTrackKey = currentTrack;
            smoothScrollY = -999999.0;
        }

        // Static position in the North sky (-Z axis)
        float distance = 65.0f;
        float elevation = 20.0f;
        float tiltAngle = (float) Math.toDegrees(Math.atan2(elevation, distance));

        poseStack.pushPose();
        // Translate to static North sky position relative to camera eye
        poseStack.translate(0.0f, elevation, -distance);

        // Tilt downward towards player eyes around the X axis
        poseStack.mulPose(new Quaternionf().rotationX((float) Math.toRadians(tiltAngle)));

        // Scale text (Font Y is inverted in Minecraft 3D rendering)
        float userScale = config.getSkyLyricsScale();
        float baseScale = 0.20f * userScale;
        poseStack.scale(baseScale, -baseScale, baseScale);

        LyricsManager lyricsMgr = LyricsManager.getInstance();
        LyricsManager.SongLyrics lyrics = lyricsMgr.getCurrentLyrics();
        long progressMs = stateManager.getInterpolatedProgressMs();

        float lineSpacing = 19.0f;
        int maxTotalLines = config.getSkyLyricsLength();
        int linesAbove = maxTotalLines / 2;
        int linesBelow = (maxTotalLines - 1) / 2;

        if (lyrics != null && lyrics.isSynced() && !lyrics.getLines().isEmpty()) {
            List<LyricLine> lines = lyrics.getLines();
            int activeIdx = lyrics.getActiveIndex(progressMs);
            if (activeIdx < 0) activeIdx = 0;

            double targetScrollY = activeIdx * lineSpacing;
            if (smoothScrollY < -900000.0 || Math.abs(targetScrollY - smoothScrollY) > 300.0) {
                smoothScrollY = targetScrollY;
            } else {
                smoothScrollY += (targetScrollY - smoothScrollY) * 0.10;
            }

            int count = lines.size();
            for (int i = 0; i < count; i++) {
                int diff = i - activeIdx;
                if (diff < -linesAbove || diff > linesBelow) continue;

                String rawText = lines.get(i).getText().trim().toUpperCase();
                if (rawText.isEmpty()) continue;

                float relY = (float) (i * lineSpacing - smoothScrollY);
                float lineDist = Math.abs(relY) / lineSpacing;

                renderLyricsLine(font, rawText, relY, lineDist, diff, linesAbove, linesBelow, poseStack, bufferSource);
            }
        } else if (lyrics != null && !lyrics.getPlainLyrics().isEmpty()) {
            String[] plainLines = lyrics.getPlainLyrics().split("\\r?\\n");
            long durationMs = state.getDurationMs();
            int activeIdx = 0;
            if (durationMs > 0 && plainLines.length > 0) {
                float progressRatio = (float) progressMs / (float) durationMs;
                activeIdx = Math.min(plainLines.length - 1, Math.max(0, (int) (progressRatio * plainLines.length)));
            }

            double targetScrollY = activeIdx * lineSpacing;
            if (smoothScrollY < -900000.0 || Math.abs(targetScrollY - smoothScrollY) > 300.0) {
                smoothScrollY = targetScrollY;
            } else {
                smoothScrollY += (targetScrollY - smoothScrollY) * 0.10;
            }

            for (int i = 0; i < plainLines.length; i++) {
                int diff = i - activeIdx;
                if (diff < -linesAbove || diff > linesBelow) continue;

                String rawText = plainLines[i].trim().toUpperCase();
                if (rawText.isEmpty()) continue;

                float relY = (float) (i * lineSpacing - smoothScrollY);
                float lineDist = Math.abs(relY) / lineSpacing;

                renderLyricsLine(font, rawText, relY, lineDist, diff, linesAbove, linesBelow, poseStack, bufferSource);
            }
        } else {
            // Instrumental or unsynced: clean track title and artist anchored in the North sky
            String title = state.getTrackName().toUpperCase();
            String artist = state.getArtistNames().toUpperCase();

            Component titleComp = Component.literal(title).withStyle(MODERN_STYLE);
            float titleHalfW = font.width(titleComp) / 2.0f;

            poseStack.pushPose();
            poseStack.translate(0.0f, -6.0f, 0.0f);
            poseStack.scale(1.15f, 1.15f, 1.15f);
            drawCleanModernText(font, titleComp, -titleHalfW, -4.0f, 0xFFFFFFFF, 0xB0000000, poseStack, bufferSource);
            poseStack.popPose();

            if (!artist.isEmpty()) {
                Component artistComp = Component.literal(artist).withStyle(MODERN_STYLE);
                float artistHalfW = font.width(artistComp) / 2.0f;

                poseStack.pushPose();
                poseStack.translate(0.0f, 14.0f, 0.0f);
                poseStack.scale(0.85f, 0.85f, 0.85f);
                drawCleanModernText(font, artistComp, -artistHalfW, -4.0f, 0xC0CBD5E1, 0x80000000, poseStack, bufferSource);
                poseStack.popPose();
            }
        }

        poseStack.popPose();

        // Flush text vertices to OpenGL
        bufferSource.endBatch();
    }

    private static void renderLyricsLine(
            Font font,
            String rawText,
            float relY,
            float lineDist,
            int diff,
            int linesAbove,
            int linesBelow,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        Component comp = Component.literal(rawText).withStyle(MODERN_STYLE);
        float halfW = font.width(comp) / 2.0f;

        int maxDelta = (diff < 0) ? linesAbove : linesBelow;
        float edgeFade = 1.0f;
        if (Math.abs(diff) > maxDelta - 2) {
            edgeFade = Math.max(0.0f, (maxDelta - Math.abs(diff) + 1) / 3.0f);
        }

        float lineScale;
        int coreColor;
        int shadowColor;

        if (lineDist < 0.6f) {
            // Active synchronized line: radiant white spotlight
            float t = 1.0f - (lineDist / 0.6f);
            lineScale = 1.0f + 0.16f * t;
            float coreAlpha = 0.85f + 0.15f * t;
            float shadowAlpha = 0.55f + 0.15f * t;
            coreColor = applyAlpha(0xFFFFFFFF, coreAlpha * edgeFade);
            shadowColor = applyAlpha(0xFF000000, shadowAlpha * edgeFade);
        } else if (lineDist < 1.6f) {
            // Immediately adjacent lines: soft clean modern white
            float t = 1.0f - ((lineDist - 0.6f) / 1.0f);
            lineScale = 0.92f + 0.08f * t;
            float coreAlpha = 0.60f + 0.25f * t;
            float shadowAlpha = 0.40f + 0.15f * t;
            coreColor = applyAlpha(0xFFE2E8F0, coreAlpha * edgeFade);
            shadowColor = applyAlpha(0xFF000000, shadowAlpha * edgeFade);
        } else if (lineDist < 2.8f) {
            // Secondary adjacent lines: soft silver
            float t = 1.0f - ((lineDist - 1.6f) / 1.2f);
            lineScale = 0.84f + 0.08f * t;
            float coreAlpha = 0.42f + 0.18f * t;
            float shadowAlpha = 0.30f + 0.10f * t;
            coreColor = applyAlpha(0xFFCBD5E1, coreAlpha * edgeFade);
            shadowColor = applyAlpha(0xFF000000, shadowAlpha * edgeFade);
        } else {
            // Distant lines: smooth graceful fade into slate
            float progress = (lineDist - 2.8f) / Math.max(1.0f, maxDelta - 2.8f);
            lineScale = 0.80f;
            float coreAlpha = Math.max(0.12f, 0.42f - (progress * 0.28f));
            float shadowAlpha = coreAlpha * 0.65f;
            coreColor = applyAlpha(0xFF94A3B8, coreAlpha * edgeFade);
            shadowColor = applyAlpha(0xFF000000, shadowAlpha * edgeFade);
        }

        poseStack.pushPose();
        poseStack.translate(0.0f, relY, 0.0f);
        poseStack.scale(lineScale, lineScale, lineScale);

        drawCleanModernText(font, comp, -halfW, -4.0f, coreColor, shadowColor, poseStack, bufferSource);

        poseStack.popPose();
    }

    private static void drawCleanModernText(
            Font font,
            Component comp,
            float x,
            float y,
            int coreColor,
            int shadowColor,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        // 1. Drop shadow pushed back along Z to completely eliminate Z-fighting flicker
        if ((shadowColor & 0xFF000000) != 0) {
            poseStack.pushPose();
            poseStack.translate(0.0f, 0.0f, -0.04f);
            font.drawInBatch(comp, x + 1.2f, y + 1.2f, shadowColor, false,
                    poseStack.last().pose(), bufferSource,
                    Font.DisplayMode.NORMAL, 0, 0xF000F0);
            poseStack.popPose();
        }

        // 2. Crisp radiant core text on Z = +0.02f with POLYGON_OFFSET
        if ((coreColor & 0xFF000000) != 0) {
            poseStack.pushPose();
            poseStack.translate(0.0f, 0.0f, 0.02f);
            font.drawInBatch(comp, x, y, coreColor, false,
                    poseStack.last().pose(), bufferSource,
                    Font.DisplayMode.POLYGON_OFFSET, 0, 0xF000F0);
            poseStack.popPose();
        }
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)))));
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
