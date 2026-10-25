package com.espoty.gui.sky;

import com.espoty.config.ModConfig;
import com.espoty.lyrics.LyricLine;
import com.espoty.lyrics.LyricsManager;
import com.espoty.spotify.SpotifyStateManager;
import com.espoty.spotify.models.PlaybackState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Ultra-high-performance dual celestial lyrics renderer (North and South sky).
 * - Anchored permanently in world space towards both North (-Z) and South (+Z).
 * - View frustum / dot-product culling ensures only the visible monument renders (zero overhead).
 * - 100% allocation-free render loop with cached line components and text metrics.
 * - Strict depth-tested occlusion guarantees terrain and blocks stay in front.
 * - Dual-depth rendering with polygon offset completely eliminates OpenGL Z-fighting flicker.
 */
public class SkyLyricsRenderer {

    private static final ResourceLocation MODERN_FONT = new ResourceLocation("espoty", "modern");
    private static final Style MODERN_STYLE = Style.EMPTY.withFont(MODERN_FONT);

    private static final float DISTANCE = 65.0f;
    private static final float ELEVATION = 20.0f;
    private static final float TILT_ANGLE = (float) Math.toDegrees(Math.atan2(ELEVATION, DISTANCE));

    private static final Quaternionf TILT_QUAT = new Quaternionf().rotationX((float) Math.toRadians(TILT_ANGLE));
    private static final Quaternionf SOUTH_ROT_Y_QUAT = new Quaternionf().rotationY((float) Math.PI);

    // Pre-computed normalized direction components for zero-allocation culling
    private static final float INV_LEN = 1.0f / (float) Math.sqrt(ELEVATION * ELEVATION + DISTANCE * DISTANCE);
    private static final float DIR_Y = ELEVATION * INV_LEN;
    private static final float DIR_Z = DISTANCE * INV_LEN;

    // Smooth scroll state
    private static double smoothScrollY = 0.0;

    // Zero-allocation cache structures
    private enum LyricsType { SYNCED, PLAIN, INSTRUMENTAL }

    private static class CachedLine {
        final Component component;
        final float halfWidth;

        CachedLine(Component component, float halfWidth) {
            this.component = component;
            this.halfWidth = halfWidth;
        }
    }

    private static String lastTrackName = "";
    private static String lastArtistNames = "";
    private static LyricsManager.SongLyrics lastLyricsRef = null;

    private static LyricsType cachedType = LyricsType.INSTRUMENTAL;
    private static CachedLine[] cachedLines = null;
    private static int cachedLinesCount = 0;
    private static Component cachedTitleComp = null;
    private static float cachedTitleHalfW = 0.0f;
    private static Component cachedArtistComp = null;
    private static float cachedArtistHalfW = 0.0f;

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

        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) return;

        // Blazingly fast dot-product frustum culling:
        // Culls monuments behind the player view before doing any rendering or matrix ops.
        Vector3f fwd = camera.getLookVector();
        float dotNorth = fwd.y() * DIR_Y - fwd.z() * DIR_Z;
        float dotSouth = fwd.y() * DIR_Y + fwd.z() * DIR_Z;

        boolean renderNorth = dotNorth > -0.25f;
        boolean renderSouth = dotSouth > -0.25f;

        // If player is looking straight down into the floor/mining, skip everything
        if (!renderNorth && !renderSouth) return;

        PoseStack poseStack = context.matrixStack();
        if (poseStack == null) return;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        if (bufferSource == null) return;

        Font font = mc.font;
        if (font == null) return;

        // Check if track or lyrics changed to rebuild zero-allocation cache
        String trackName = state.getTrackName();
        String artistNames = state.getArtistNames();
        LyricsManager lyricsMgr = LyricsManager.getInstance();
        LyricsManager.SongLyrics lyrics = lyricsMgr.getCurrentLyrics();

        boolean trackChanged = !trackName.equals(lastTrackName) || !artistNames.equals(lastArtistNames);
        boolean lyricsChanged = (lyrics != lastLyricsRef);

        if (trackChanged || lyricsChanged) {
            lastTrackName = trackName;
            lastArtistNames = artistNames;
            lastLyricsRef = lyrics;
            rebuildCache(font, trackName, artistNames, lyrics);
            if (trackChanged) {
                smoothScrollY = -999999.0;
            }
        }

        // Calculate smooth scrolling
        float lineSpacing = 19.0f;
        int maxTotalLines = config.getSkyLyricsLength();
        int linesAbove = maxTotalLines / 2;
        int linesBelow = (maxTotalLines - 1) / 2;

        long progressMs = stateManager.getInterpolatedProgressMs();
        int activeIdx = 0;

        if (cachedType == LyricsType.SYNCED && lyrics != null) {
            activeIdx = lyrics.getActiveIndex(progressMs);
            if (activeIdx < 0) activeIdx = 0;
        } else if (cachedType == LyricsType.PLAIN) {
            long durationMs = state.getDurationMs();
            if (durationMs > 0 && cachedLinesCount > 0) {
                float progressRatio = (float) progressMs / (float) durationMs;
                activeIdx = Math.min(cachedLinesCount - 1, Math.max(0, (int) (progressRatio * cachedLinesCount)));
            }
        }

        double targetScrollY = activeIdx * lineSpacing;
        if (smoothScrollY < -900000.0 || Math.abs(targetScrollY - smoothScrollY) > 300.0) {
            smoothScrollY = targetScrollY;
        } else {
            smoothScrollY += (targetScrollY - smoothScrollY) * 0.10;
        }

        float userScale = config.getSkyLyricsScale();
        float baseScale = 0.20f * userScale;

        // 1. Render North monument (Facing player looking North)
        if (renderNorth) {
            poseStack.pushPose();
            poseStack.translate(0.0f, ELEVATION, -DISTANCE);
            poseStack.mulPose(TILT_QUAT);
            poseStack.scale(baseScale, -baseScale, baseScale);

            renderMonumentContent(font, lineSpacing, linesAbove, linesBelow, activeIdx, smoothScrollY, poseStack, bufferSource);

            poseStack.popPose();
        }

        // 2. Render South monument (Facing player looking South)
        if (renderSouth) {
            poseStack.pushPose();
            poseStack.translate(0.0f, ELEVATION, DISTANCE);
            poseStack.mulPose(SOUTH_ROT_Y_QUAT);
            poseStack.mulPose(TILT_QUAT);
            poseStack.scale(baseScale, -baseScale, baseScale);

            renderMonumentContent(font, lineSpacing, linesAbove, linesBelow, activeIdx, smoothScrollY, poseStack, bufferSource);

            poseStack.popPose();
        }

        // Flush all text batches once
        bufferSource.endBatch();
    }

    private static void renderMonumentContent(
            Font font,
            float lineSpacing,
            int linesAbove,
            int linesBelow,
            int activeIdx,
            double scrollY,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        if (cachedType == LyricsType.SYNCED || cachedType == LyricsType.PLAIN) {
            for (int i = 0; i < cachedLinesCount; i++) {
                int diff = i - activeIdx;
                if (diff < -linesAbove || diff > linesBelow) continue;

                CachedLine line = cachedLines[i];
                if (line == null) continue;

                float relY = (float) (i * lineSpacing - scrollY);
                float lineDist = Math.abs(relY) / lineSpacing;

                renderLyricsLine(font, line.component, line.halfWidth, relY, lineDist, diff, linesAbove, linesBelow, poseStack, bufferSource);
            }
        } else {
            // Instrumental: Title & Artist
            if (cachedTitleComp != null) {
                poseStack.pushPose();
                poseStack.translate(0.0f, -6.0f, 0.0f);
                poseStack.scale(1.15f, 1.15f, 1.15f);
                drawCleanModernText(font, cachedTitleComp, -cachedTitleHalfW, -4.0f, 0xFFFFFFFF, 0xB0000000, poseStack, bufferSource);
                poseStack.popPose();
            }

            if (cachedArtistComp != null) {
                poseStack.pushPose();
                poseStack.translate(0.0f, 14.0f, 0.0f);
                poseStack.scale(0.85f, 0.85f, 0.85f);
                drawCleanModernText(font, cachedArtistComp, -cachedArtistHalfW, -4.0f, 0xC0CBD5E1, 0x80000000, poseStack, bufferSource);
                poseStack.popPose();
            }
        }
    }

    private static void renderLyricsLine(
            Font font,
            Component comp,
            float halfW,
            float relY,
            float lineDist,
            int diff,
            int linesAbove,
            int linesBelow,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        int maxDelta = (diff < 0) ? linesAbove : linesBelow;
        float edgeFade = 1.0f;
        if (Math.abs(diff) > maxDelta - 2) {
            edgeFade = Math.max(0.0f, (maxDelta - Math.abs(diff) + 1) / 3.0f);
        }

        float lineScale;
        int coreColor;
        int shadowColor;

        if (lineDist < 0.6f) {
            // Active synchronized line: radiant pure white spotlight
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
            // Distant lines: smooth graceful fade into subtle slate
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

    private static void rebuildCache(Font font, String trackName, String artistNames, LyricsManager.SongLyrics lyrics) {
        String title = trackName.toUpperCase();
        String artist = artistNames.toUpperCase();

        cachedTitleComp = Component.literal(title).withStyle(MODERN_STYLE);
        cachedTitleHalfW = font.width(cachedTitleComp) / 2.0f;

        if (!artist.isEmpty()) {
            cachedArtistComp = Component.literal(artist).withStyle(MODERN_STYLE);
            cachedArtistHalfW = font.width(cachedArtistComp) / 2.0f;
        } else {
            cachedArtistComp = null;
            cachedArtistHalfW = 0.0f;
        }

        if (lyrics != null && lyrics.isSynced() && !lyrics.getLines().isEmpty()) {
            cachedType = LyricsType.SYNCED;
            List<LyricLine> lines = lyrics.getLines();
            cachedLinesCount = lines.size();
            cachedLines = new CachedLine[cachedLinesCount];
            for (int i = 0; i < cachedLinesCount; i++) {
                String raw = lines.get(i).getText().trim().toUpperCase();
                if (!raw.isEmpty()) {
                    Component comp = Component.literal(raw).withStyle(MODERN_STYLE);
                    cachedLines[i] = new CachedLine(comp, font.width(comp) / 2.0f);
                } else {
                    cachedLines[i] = null;
                }
            }
        } else if (lyrics != null && !lyrics.getPlainLyrics().isEmpty()) {
            cachedType = LyricsType.PLAIN;
            String[] rawPlain = lyrics.getPlainLyrics().split("\\r?\\n");
            cachedLinesCount = rawPlain.length;
            cachedLines = new CachedLine[cachedLinesCount];
            for (int i = 0; i < cachedLinesCount; i++) {
                String raw = rawPlain[i].trim().toUpperCase();
                if (!raw.isEmpty()) {
                    Component comp = Component.literal(raw).withStyle(MODERN_STYLE);
                    cachedLines[i] = new CachedLine(comp, font.width(comp) / 2.0f);
                } else {
                    cachedLines[i] = null;
                }
            }
        } else {
            cachedType = LyricsType.INSTRUMENTAL;
            cachedLines = null;
            cachedLinesCount = 0;
        }
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)))));
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
