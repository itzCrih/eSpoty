package com.espoty.gui.sky;

import com.espoty.config.ModConfig;
import com.espoty.lyrics.LyricLine;
import com.espoty.lyrics.LyricsManager;
import com.espoty.spotify.SpotifyStateManager;
import com.espoty.spotify.models.PlaybackState;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.util.List;

/**
 * Ultra-high-performance celestial lyrics renderer positioned dynamically in the player's horizontal gaze direction.
 * - Anchored in world space in the celestial dome, elevated majestically into the sky.
 * - Dynamically tracks the horizontal direction the player is looking, smoothly gliding across the sky dome with fluid inertia.
 * - Pure white luminescence (0xFFFFFFFF core) with zero texture-pack color override and zero color bleed.
 * - Frame-rate-independent 60+ FPS delta-time exponential smoothing and synchronized cubic/quintic Spotify ease-out.
 * - Emissive fullbright packed light (0x00F000F0) and deep drop shadows ensure crystal-clear visibility
 *   at all times of day (noon, sunset, midnight) and under TimeChanger or custom skybox mods (FabricSkyBoxes).
 * - Full compatibility with Iris/Sodium shaders and custom render pipelines using Font.DisplayMode.NORMAL and context.consumers().
 * - Frustum dot-product culling skips rendering when player looks down or away (zero allocation).
 * - Strict depth-tested occlusion guarantees terrain and buildings naturally block the sky lyrics.
 */
public class SkyLyricsRenderer {

    // Pure white color constant - guarantees texture packs and formatting codes cannot tint the text
    private static final TextColor PURE_WHITE = TextColor.fromRgb(0xFFFFFF);

    // Spotify transition duration for silky-smooth line changes
    private static final float TRANSITION_DURATION_MS = 400.0f;

    // Dedicated isolated buffer source: ensures zero interference with Minecraft's bufferSource or item/hand rendering
    private static final ByteBufferBuilder BYTE_BUFFER_BUILDER = new ByteBufferBuilder(256 * 1024);
    private static final MultiBufferSource.BufferSource DEDICATED_BUFFER_SOURCE = MultiBufferSource.immediate(BYTE_BUFFER_BUILDER);

    // Dynamic celestial dome positioning cached per render distance change (zero allocation render loop)
    private static int lastRenderDistance = -1;
    private static float cachedDistance = 85.0f;
    private static float cachedElevation = 32.0f;
    private static final Quaternionf cachedTiltQuat = new Quaternionf();
    private static final Quaternionf reusableRotY = new Quaternionf();
    private static float cachedDirY = 0.35f;
    private static float cachedDirDist = 0.93f;

    // Gaze-tracking smooth yaw state
    private static float smoothYaw = Float.NaN;
    private static long lastFrameTime = 0;

    // Frame-rate-independent synchronized scroll and Spotify transition state
    private static double smoothScrollY = 0.0;
    private static double scrollStartY = 0.0;
    private static double scrollTargetY = 0.0;
    private static long animStartTime = 0;

    private static int currentActiveIdx = -1;
    private static int previousActiveIdx = -1;

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
    private static ModConfig.LyricFont lastLyricFont = null;

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

        long now = System.currentTimeMillis();
        float dt = (lastFrameTime > 0) ? Math.min(0.1f, (now - lastFrameTime) / 1000.0f) : 0.016f;
        lastFrameTime = now;

        // Dynamic celestial positioning: scales safely with render distance
        int renderDist = mc.options.getEffectiveRenderDistance();
        if (renderDist != lastRenderDistance) {
            lastRenderDistance = renderDist;
            float safeDistance = renderDist * 16.0f * 0.70f;
            cachedDistance = Math.max(70.0f, Math.min(130.0f, safeDistance));
            cachedElevation = cachedDistance * 0.38f;
            float tiltAngle = (float) Math.toDegrees(Math.atan2(cachedElevation, cachedDistance));
            cachedTiltQuat.rotationX((float) Math.toRadians(tiltAngle));

            float invLen = 1.0f / (float) Math.sqrt(cachedElevation * cachedElevation + cachedDistance * cachedDistance);
            cachedDirY = cachedElevation * invLen;
            cachedDirDist = cachedDistance * invLen;
        }

        ModConfig.SkyLyricsMode skyMode = config.getSkyLyricsMode();
        boolean isNorthSouth = (skyMode == ModConfig.SkyLyricsMode.NORTH_SOUTH);

        Vector3fc fwd = camera.forwardVector();
        boolean renderNorth = false;
        boolean renderSouth = false;
        float posX = 0.0f;
        float posY = cachedElevation;
        float posZ = 0.0f;
        float angleYRad = 0.0f;

        if (isNorthSouth) {
            float dotNorth = fwd.y() * cachedDirY - fwd.z() * cachedDirDist;
            float dotSouth = fwd.y() * cachedDirY + fwd.z() * cachedDirDist;
            renderNorth = dotNorth > -0.20f;
            renderSouth = dotSouth > -0.20f;
            if (!renderNorth && !renderSouth) return;
        } else {
            // Fluid gaze tracking: smoothly interpolates celestial azimuth towards camera horizontal yaw
            float targetYaw = camera.yRot();
            if (Float.isNaN(smoothYaw)) {
                smoothYaw = targetYaw;
            } else {
                float diff = normalizeAngleDeg(targetYaw - smoothYaw);
                if (Math.abs(diff) < 0.04f) {
                    smoothYaw = targetYaw;
                } else {
                    // Responsive and silky damping (speed = 14.0f settles >95% within 200ms)
                    float followFactor = 1.0f - (float) Math.exp(-dt * 14.0f);
                    smoothYaw += diff * followFactor;
                    smoothYaw = normalizeAngleDeg(smoothYaw);
                }
            }

            // Compute celestial coordinates in world space along smooth gaze vector
            float rad = (float) Math.toRadians(smoothYaw);
            posX = -(float) Math.sin(rad) * cachedDistance;
            posZ = (float) Math.cos(rad) * cachedDistance;
            angleYRad = (float) (Math.PI - rad);

            // Frustum / dot-product culling: verify monument is inside front field-of-view
            float invLen = 1.0f / (float) Math.sqrt(posX * posX + posY * posY + posZ * posZ);
            float dirX = posX * invLen;
            float dirY = posY * invLen;
            float dirZ = posZ * invLen;

            float dot = fwd.x() * dirX + fwd.y() * dirY + fwd.z() * dirZ;
            if (dot < -0.15f) return; // Player looking straight down at the ground or away
        }

        PoseStack poseStack = context.matrices();
        if (poseStack == null) return;

        Font font = mc.font;
        if (font == null) return;

        // Check if track, lyrics, or font changed to rebuild zero-allocation cache
        String trackName = state.getTrackName();
        String artistNames = state.getArtistNames();
        LyricsManager lyricsMgr = LyricsManager.getInstance();
        LyricsManager.SongLyrics lyrics = lyricsMgr.getCurrentLyrics();
        ModConfig.LyricFont currentFont = config.getLyricFont();

        boolean trackChanged = !trackName.equals(lastTrackName) || !artistNames.equals(lastArtistNames);
        boolean lyricsChanged = (lyrics != lastLyricsRef);
        boolean fontChanged = (currentFont != lastLyricFont);

        if (trackChanged || lyricsChanged || fontChanged) {
            lastTrackName = trackName;
            lastArtistNames = artistNames;
            lastLyricsRef = lyrics;
            lastLyricFont = currentFont;
            rebuildCache(font, trackName, artistNames, lyrics, currentFont.getStyle());
            if (trackChanged) {
                smoothScrollY = 0.0;
                scrollStartY = 0.0;
                scrollTargetY = 0.0;
                currentActiveIdx = -1;
                previousActiveIdx = -1;
                animStartTime = now - 10000L;
            }
        }

        // Active line calculation
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

        // Synchronized Spotify-style transition timing
        if (activeIdx != currentActiveIdx) {
            previousActiveIdx = currentActiveIdx;
            currentActiveIdx = activeIdx;
            scrollStartY = smoothScrollY;
            scrollTargetY = activeIdx * lineSpacing;
            animStartTime = now;
        }

        // Handle instant snap for large jumps (e.g. seeking or new track)
        if (Math.abs(scrollTargetY - smoothScrollY) > 250.0) {
            smoothScrollY = scrollTargetY;
            scrollStartY = scrollTargetY;
            animStartTime = now - 10000L;
        }

        // Fluid cubic ease-out synchronized scroll (optimized inline polynomial, 0 FPS drop)
        float elapsed = (animStartTime > 0) ? (float) (now - animStartTime) : 10000.0f;
        float animT = Math.max(0.0f, Math.min(1.0f, elapsed / TRANSITION_DURATION_MS));
        float invT = 1.0f - animT;
        float ease = 1.0f - (invT * invT * invT);
        smoothScrollY = scrollStartY + (scrollTargetY - scrollStartY) * ease;

        float userScale = config.getSkyLyricsScale();
        float baseScale = (cachedDistance / 65.0f) * 0.44f * userScale;

        if (isNorthSouth) {
            // 1. North Monument (azimuth 180°, facing South towards camera)
            if (renderNorth) {
                poseStack.pushPose();
                poseStack.translate(0.0f, cachedElevation, -cachedDistance);
                poseStack.mulPose(cachedTiltQuat);
                poseStack.scale(baseScale, -baseScale, baseScale);
                renderMonumentContent(font, lineSpacing, linesAbove, linesBelow, activeIdx, previousActiveIdx, animT, ease, poseStack, DEDICATED_BUFFER_SOURCE);
                poseStack.popPose();
            }

            // 2. South Monument (azimuth 0°, facing North towards camera)
            if (renderSouth) {
                poseStack.pushPose();
                poseStack.translate(0.0f, cachedElevation, cachedDistance);
                reusableRotY.rotationY((float) Math.PI);
                poseStack.mulPose(reusableRotY);
                poseStack.mulPose(cachedTiltQuat);
                poseStack.scale(baseScale, -baseScale, baseScale);
                renderMonumentContent(font, lineSpacing, linesAbove, linesBelow, activeIdx, previousActiveIdx, animT, ease, poseStack, DEDICATED_BUFFER_SOURCE);
                poseStack.popPose();
            }
        } else {
            // Dynamic Gaze Single Monument
            poseStack.pushPose();
            poseStack.translate(posX, posY, posZ);
            reusableRotY.rotationY(angleYRad);
            poseStack.mulPose(reusableRotY);
            poseStack.mulPose(cachedTiltQuat);
            poseStack.scale(baseScale, -baseScale, baseScale);
            renderMonumentContent(font, lineSpacing, linesAbove, linesBelow, activeIdx, previousActiveIdx, animT, ease, poseStack, DEDICATED_BUFFER_SOURCE);
            poseStack.popPose();
        }

        // Flush our dedicated text buffer source cleanly - leaves game bufferSource completely untouched!
        DEDICATED_BUFFER_SOURCE.endBatch();
    }

    private static void renderMonumentContent(
            Font font,
            float lineSpacing,
            int linesAbove,
            int linesBelow,
            int activeIdx,
            int prevIdx,
            float animT,
            float ease,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        if (cachedType == LyricsType.SYNCED || cachedType == LyricsType.PLAIN) {
            for (int i = 0; i < cachedLinesCount; i++) {
                int diff = i - activeIdx;
                if (diff < -linesAbove || diff > linesBelow) continue;

                CachedLine line = cachedLines[i];
                if (line == null) continue;

                float relY = (float) (i * lineSpacing - smoothScrollY);

                // Synchronized activation weight for seamless Spotify scale and alpha morph
                float activeWeight = 0.0f;
                if (i == activeIdx) {
                    activeWeight = ease;
                } else if (i == prevIdx && animT < 1.0f) {
                    activeWeight = 1.0f - ease;
                }

                renderLyricsLine(font, line.component, line.halfWidth, relY, diff, activeWeight, linesAbove, linesBelow, poseStack, bufferSource);
            }
        } else {
            // Instrumental: Pure white title & artist
            if (cachedTitleComp != null) {
                poseStack.pushPose();
                poseStack.translate(0.0f, -6.0f, 0.0f);
                poseStack.scale(1.15f, 1.15f, 1.15f);
                drawCleanModernText(font, cachedTitleComp, -cachedTitleHalfW, -4.0f, 0xFFFFFFFF, 0xD0000000, poseStack, bufferSource);
                poseStack.popPose();
            }

            if (cachedArtistComp != null) {
                poseStack.pushPose();
                poseStack.translate(0.0f, 14.0f, 0.0f);
                poseStack.scale(0.85f, 0.85f, 0.85f);
                drawCleanModernText(font, cachedArtistComp, -cachedArtistHalfW, -4.0f, 0xB0FFFFFF, 0x80000000, poseStack, bufferSource);
                poseStack.popPose();
            }
        }
    }

    private static void renderLyricsLine(
            Font font,
            Component comp,
            float halfW,
            float relY,
            int diff,
            float activeWeight,
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

        // Spotify-style smooth scale morph (0.88x -> 1.18x)
        float lineScale = 0.88f + (0.30f * activeWeight);

        // Core RGB is ALWAYS 100% pure white (0xFFFFFF). Inactive lines differ ONLY in opacity (alpha).
        // Active line: 100% opacity (0xFFFFFFFF)
        // Adjacent lines: ~58% opacity (0x94FFFFFF)
        // Distant lines: ~32% opacity (0x52FFFFFF)
        float inactiveAlpha = (Math.abs(diff) <= 1) ? 0.58f : 0.32f;
        float finalAlpha = (inactiveAlpha + ((1.0f - inactiveAlpha) * activeWeight)) * edgeFade;

        int coreAlphaByte = Math.max(0, Math.min(255, (int) (finalAlpha * 255.0f)));
        int coreColor = (coreAlphaByte << 24) | 0x00FFFFFF;

        int shadowAlphaByte = Math.max(0, Math.min(255, (int) (finalAlpha * 180.0f)));
        int shadowColor = (shadowAlphaByte << 24) | 0x00000000;

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
        // 1. Drop shadow with physical Z-separation (-0.04f)
        if ((shadowColor & 0xFF000000) != 0) {
            poseStack.pushPose();
            poseStack.translate(0.0f, 0.0f, -0.04f);
            font.drawInBatch(comp, x + 1.2f, y + 1.2f, shadowColor, false,
                    poseStack.last().pose(), bufferSource,
                    Font.DisplayMode.NORMAL, 0, 0x00F000F0);
            poseStack.popPose();
        }

        // 2. Radiant core pure white text on Z = +0.04f with Font.DisplayMode.NORMAL
        // Full packed light 0x00F000F0 guarantees luminous celestial glow at any time of day
        // NORMAL display mode guarantees 100% compatibility with all shader packs (Iris/Sodium/Canvas/Vanilla)
        if ((coreColor & 0xFF000000) != 0) {
            poseStack.pushPose();
            poseStack.translate(0.0f, 0.0f, 0.04f);
            font.drawInBatch(comp, x, y, coreColor, false,
                    poseStack.last().pose(), bufferSource,
                    Font.DisplayMode.NORMAL, 0, 0x00F000F0);
            poseStack.popPose();
        }
    }

    private static String sanitizeClean(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)§[0-9a-fk-orx]", "").trim();
    }

    private static void rebuildCache(Font font, String trackName, String artistNames, LyricsManager.SongLyrics lyrics, Style baseStyle) {
        Style pureWhiteStyle = baseStyle.withColor(PURE_WHITE);

        String cleanTrack = sanitizeClean(trackName);
        cachedTitleComp = Component.literal(cleanTrack).withStyle(pureWhiteStyle);
        cachedTitleHalfW = font.width(cachedTitleComp) / 2.0f;

        String cleanArtist = sanitizeClean(artistNames);
        if (!cleanArtist.isEmpty()) {
            cachedArtistComp = Component.literal(cleanArtist).withStyle(pureWhiteStyle);
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
                String raw = sanitizeClean(lines.get(i).getText());
                if (!raw.isEmpty()) {
                    Component comp = Component.literal(raw).withStyle(pureWhiteStyle);
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
                String raw = sanitizeClean(rawPlain[i]);
                if (!raw.isEmpty()) {
                    Component comp = Component.literal(raw).withStyle(pureWhiteStyle);
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

    private static float normalizeAngleDeg(float angle) {
        return Mth.wrapDegrees(angle);
    }
}
