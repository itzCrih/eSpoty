package it.itzcrih.espoty.gui.sky;

import it.itzcrih.espoty.config.ModConfig;
import it.itzcrih.espoty.gui.theme.ThemeManager;
import it.itzcrih.espoty.lyrics.LyricLine;
import it.itzcrih.espoty.lyrics.LyricsManager;
import it.itzcrih.espoty.spotify.SpotifyStateManager;
import it.itzcrih.espoty.spotify.models.PlaybackState;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Celestial sky lyrics renderer.
 */
public class SkyLyricsRenderer {

    private static final TextColor PURE_WHITE = TextColor.fromRgb(0xFFFFFF);
    private static final float TRANSITION_DURATION_MS = 400.0f;
    private static final ByteBufferBuilder BYTE_BUFFER_BUILDER = new ByteBufferBuilder(256 * 1024);
    private static final MultiBufferSource.BufferSource DEDICATED_BUFFER_SOURCE = MultiBufferSource.immediate(BYTE_BUFFER_BUILDER);

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

    // Cache structures
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
        if (skyMode == ModConfig.SkyLyricsMode.DYNAMIC_COMBAT) {
            renderDynamicCombat3D(context, mc, config, stateManager, state, camera, now);
            return;
        }
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
            // Gaze tracking
            float targetYaw = camera.yRot();
            if (Float.isNaN(smoothYaw)) {
                smoothYaw = targetYaw;
            } else {
                float diff = normalizeAngleDeg(targetYaw - smoothYaw);
                if (Math.abs(diff) < 0.04f) {
                    smoothYaw = targetYaw;
                } else {
                    float followFactor = 1.0f - (float) Math.exp(-dt * 14.0f);
                    smoothYaw += diff * followFactor;
                    smoothYaw = normalizeAngleDeg(smoothYaw);
                }
            }

            // Compute celestial coordinates
            float rad = (float) Math.toRadians(smoothYaw);
            posX = -(float) Math.sin(rad) * cachedDistance;
            posZ = (float) Math.cos(rad) * cachedDistance;
            angleYRad = (float) (Math.PI - rad);

            // Frustum culling
            float invLen = 1.0f / (float) Math.sqrt(posX * posX + posY * posY + posZ * posZ);
            float dirX = posX * invLen;
            float dirY = posY * invLen;
            float dirZ = posZ * invLen;

            float dot = fwd.x() * dirX + fwd.y() * dirY + fwd.z() * dirZ;
            if (dot < -0.15f) return;
        }

        PoseStack poseStack = context.matrices();
        if (poseStack == null) return;

        Font font = mc.font;
        if (font == null) return;

        // Rebuild cache if track or font changed
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

        // Synchronized scroll
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

        // Flush text buffer
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

                // Activation weight
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

        int shadowAlphaByte = Math.max(0, Math.min(255, (int) (finalAlpha * 220.0f)));
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
        // 1. Drop shadow
        if ((shadowColor & 0xFF000000) != 0) {
            poseStack.pushPose();
            poseStack.translate(0.0f, 0.0f, -0.04f);
            font.drawInBatch(comp, x + 1.2f, y + 1.2f, shadowColor, false,
                    poseStack.last().pose(), bufferSource,
                    Font.DisplayMode.NORMAL, 0, 0x00F000F0);
            poseStack.popPose();
        }

        // 2. Core text
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
        Style lyricStyle = baseStyle;

        String cleanTrack = sanitizeClean(trackName);
        cachedTitleComp = Component.literal(cleanTrack).withStyle(lyricStyle);
        cachedTitleHalfW = font.width(cachedTitleComp) / 2.0f;

        String cleanArtist = sanitizeClean(artistNames);
        if (!cleanArtist.isEmpty()) {
            cachedArtistComp = Component.literal(cleanArtist).withStyle(lyricStyle);
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
                    Component comp = Component.literal(raw).withStyle(lyricStyle);
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
                    Component comp = Component.literal(raw).withStyle(lyricStyle);
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

    // =======================================================
    // 3D DYNAMIC COMBAT / FOV COMBAT FLOW LYRICS SYSTEM
    // =======================================================

    private static class CombatWord {
        final String text;
        final Component component;
        final int width;
        final long startMs;
        final long endMs;

        CombatWord(String text, Component component, int width, long startMs, long endMs) {
            this.text = text;
            this.component = component;
            this.width = width;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    private static class CombatChunk {
        final int chunkId;
        final List<CombatWord> words;
        final int totalWidth;
        final long startMs;
        final long endMs;
        final boolean isRightSide;

        CombatChunk(int chunkId, List<CombatWord> words, int totalWidth, long startMs, long endMs, boolean isRightSide) {
            this.chunkId = chunkId;
            this.words = words;
            this.totalWidth = totalWidth;
            this.startMs = startMs;
            this.endMs = endMs;
            this.isRightSide = isRightSide;
        }
    }

    /**
     * An active 3D billboard anchored at a static world coordinate (X, Y, Z).
     * Once spawned at the player's position, this billboard NEVER follows the player;
     * it stays static in the 3D world as the player runs and duels, billboarding to face the camera.
     */
    private static class Active3DBillboard {
        final int chunkId;
        final List<CombatWord> words;
        final int totalWidth;
        final long startMs;
        final long endMs;
        final boolean isRightSide;
        final long lingerDuration;

        // Static 3D World Origin
        final double worldX;
        final double worldY;
        final double worldZ;
        final long spawnRealTime;

        boolean acceleratedFade = false;

        Active3DBillboard(
                int chunkId,
                List<CombatWord> words,
                int totalWidth,
                long startMs,
                long endMs,
                boolean isRightSide,
                double worldX,
                double worldY,
                double worldZ,
                long spawnRealTime
        ) {
            this.chunkId = chunkId;
            this.words = words;
            this.totalWidth = totalWidth;
            this.startMs = startMs;
            this.endMs = endMs;
            this.isRightSide = isRightSide;
            this.worldX = worldX;
            this.worldY = worldY;
            this.worldZ = worldZ;
            this.spawnRealTime = spawnRealTime;

            // Adaptive linger: fast rap clears in 750-1200ms, melodic phrases/ballads linger up to 2800ms
            long span = Math.max(150L, endMs - startMs);
            this.lingerDuration = Math.max(850L, Math.min(2800L, span * 2L + 300L));
        }
    }

    private static final List<CombatChunk> cachedCombatChunks = new ArrayList<>();
    private static final List<Active3DBillboard> active3DBillboards = new ArrayList<>();
    private static final Set<Integer> spawnedChunkIds = new HashSet<>();

    private static String lastCombatTrackKey = "";
    private static LyricsManager.SongLyrics lastCombatLyricsRef = null;
    private static ModConfig.LyricFont lastCombatFont = null;
    private static int lastSpaceWidth = 4;
    private static long lastProgressMs = -1;

    private static void renderDynamicCombat3D(
            WorldRenderContext context,
            Minecraft mc,
            ModConfig config,
            SpotifyStateManager stateManager,
            PlaybackState state,
            Camera camera,
            long now
    ) {
        Font font = mc.font;
        if (font == null) return;

        PoseStack poseStack = context.matrices();
        if (poseStack == null) return;

        // Lead-compensated progress (+100ms compensation for Windows media latency)
        long progressMs = stateManager.getInterpolatedProgressMs() + 100L;
        String trackKey = state.getTrackName() + " - " + state.getArtistNames();
        ModConfig.LyricFont currentFont = config.getLyricFont();
        LyricsManager lyricsMgr = LyricsManager.getInstance();
        LyricsManager.SongLyrics lyrics = lyricsMgr.getCurrentLyrics();

        // Detect track, lyrics, or font change
        boolean trackChanged = !trackKey.equals(lastCombatTrackKey);
        boolean lyricsChanged = (lyrics != lastCombatLyricsRef);
        boolean fontChanged = (currentFont != lastCombatFont);

        if (trackChanged || lyricsChanged || fontChanged) {
            lastCombatTrackKey = trackKey;
            lastCombatLyricsRef = lyrics;
            lastCombatFont = currentFont;
            rebuildCombatChunks(font, trackKey, lyrics, currentFont.getStyle(), state.getDurationMs());
            if (trackChanged) {
                active3DBillboards.clear();
                spawnedChunkIds.clear();
                lastProgressMs = -1;
            }
        }

        // Handle seeking (large jump in playback progress)
        if (lastProgressMs >= 0 && Math.abs(progressMs - lastProgressMs) > 1800L) {
            active3DBillboards.clear();
            spawnedChunkIds.clear();
        }
        lastProgressMs = progressMs;

        Vec3 camPos = camera.position();
        Vector3fc fwd = camera.forwardVector();
        Quaternionf camRot = camera.rotation();

        // 1. DYNAMIC SPAWNING:
        // When progress reaches a chunk's start window, spawn it into the 3D world
        // anchored at the player's CURRENT camera position and look direction!
        for (CombatChunk chunk : cachedCombatChunks) {
            if (progressMs >= chunk.startMs - 350 && progressMs <= chunk.endMs + 300) {
                if (!spawnedChunkIds.contains(chunk.chunkId)) {
                    spawnChunkIn3DWorld(camera, camPos, chunk, now);
                    spawnedChunkIds.add(chunk.chunkId);
                }
            }
        }

        // 2. RENDER ACTIVE 3D BILLBOARDS:
        // Multiple billboards can and do coexist simultaneously in the 3D world!
        ModConfig.CombatAnimation animMode = config.getCombatAnimation();
        Iterator<Active3DBillboard> it = active3DBillboards.iterator();
        while (it.hasNext()) {
            Active3DBillboard b = it.next();

            // Adaptive linger: accelerated when new lines arrive on the same side
            long totalLinger = b.acceleratedFade ? Math.min(b.lingerDuration, 750L) : b.lingerDuration;
            long lingerEnd = b.endMs + totalLinger;
            if (progressMs > lingerEnd) {
                it.remove();
                continue;
            }

            // Relative vector from current camera to static world billboard position
            float lifeSec = Math.max(0.0f, (progressMs - b.startMs) / 1000.0f);
            float baseFloatSpeed = b.acceleratedFade ? 0.44f : 0.22f;
            double floatY;
            if (animMode == ModConfig.CombatAnimation.WORD_FADE) {
                floatY = lifeSec * (baseFloatSpeed * 0.75f);
            } else if (animMode == ModConfig.CombatAnimation.SLIDE_DRIFT) {
                floatY = lifeSec * (baseFloatSpeed * 0.85f);
            } else if (animMode == ModConfig.CombatAnimation.NEON_PULSE) {
                floatY = lifeSec * (baseFloatSpeed * 0.95f);
            } else {
                floatY = lifeSec * baseFloatSpeed; // POP_FLOAT
            }

            double relX = b.worldX - camPos.x;
            double relY = (b.worldY + floatY) - camPos.y;
            double relZ = b.worldZ - camPos.z;

            // Distance & frustum check
            double distSq = relX * relX + relY * relY + relZ * relZ;
            if (distSq > 1600.0 || distSq < 0.05) continue; // culled beyond 40m

            // Check if in front of player
            float invDist = 1.0f / (float) Math.sqrt(distSq);
            float dot = (float) (relX * invDist * fwd.x() + relY * invDist * fwd.y() + relZ * invDist * fwd.z());
            if (dot < 0.15f) continue; // culled if outside field of view

            // Opacity & intro/outro scaling & sliding
            float alpha = 1.0f;
            float introPunch = 1.0f;
            float slideX = 0.0f;
            long timeSinceStart = progressMs - b.startMs;
            long timeSinceEnd = progressMs - b.endMs;

            if (timeSinceStart < 160) {
                float inT = Math.max(0.0f, Math.min(1.0f, (float) timeSinceStart / 160.0f));
                if (animMode == ModConfig.CombatAnimation.POP_FLOAT) {
                    alpha = inT * (2.0f - inT); // ease-out quad
                    introPunch = 0.82f + (0.18f * alpha);
                } else if (animMode == ModConfig.CombatAnimation.SLIDE_DRIFT) {
                    alpha = inT * (2.0f - inT);
                    float invT = 1.0f - inT;
                    slideX = (b.isRightSide ? 28.0f : -28.0f) * invT * invT;
                    introPunch = 0.94f + (0.06f * inT);
                } else if (animMode == ModConfig.CombatAnimation.WORD_FADE) {
                    alpha = inT; // smooth linear fade in
                    introPunch = 1.0f;
                } else if (animMode == ModConfig.CombatAnimation.NEON_PULSE) {
                    alpha = inT * inT;
                    introPunch = 0.88f + (0.12f * inT);
                }
            } else if (timeSinceEnd > 0) {
                float outT = Math.max(0.0f, Math.min(1.0f, 1.0f - ((float) timeSinceEnd / (float) totalLinger)));
                alpha = outT * outT; // smooth quadratic fadeout
                introPunch = 1.0f;
                if (animMode == ModConfig.CombatAnimation.SLIDE_DRIFT) {
                    float outProgress = (float) timeSinceEnd / (float) totalLinger;
                    slideX = (b.isRightSide ? 14.0f : -14.0f) * (outProgress * outProgress);
                }
            }

            if (alpha <= 0.01f) continue;

            poseStack.pushPose();
            poseStack.translate((float) relX, (float) relY, (float) relZ);

            // Billboard rotation: align with camera orientation so text always faces the viewer
            poseStack.mulPose(camRot);

            // 3D Scale factor: 0.024f base scale in world units
            float userScale = config.getSkyLyricsScale();
            float baseScale = 0.024f * userScale * introPunch;
            poseStack.scale(baseScale, -baseScale, baseScale);

            if (slideX != 0.0f) {
                poseStack.translate(slideX, 0.0f, 0.0f);
            }

            // Render words centered at the billboard's 3D anchor
            float startX = -b.totalWidth / 2.0f;
            render3DWords(font, b, startX, progressMs, alpha, animMode, now, poseStack, DEDICATED_BUFFER_SOURCE);

            poseStack.popPose();
        }

        // Flush text buffer
        DEDICATED_BUFFER_SOURCE.endBatch();
    }

    private static void spawnChunkIn3DWorld(Camera camera, Vec3 camPos, CombatChunk chunk, long now) {
        float pitch = camera.xRot();
        float yaw = camera.yRot();
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 upGlobal = new Vec3(0, 1, 0);
        Vec3 right = look.cross(upGlobal);
        if (right.lengthSqr() < 0.001) {
            right = new Vec3(1, 0, 0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(look).normalize();

        boolean isRightSide = chunk.isRightSide;

        // Anti-collision staggering & smart clearing:
        // When singing is extremely fast (e.g. Eminem - Godzilla), check existing billboards on the same side
        float vertOffset = 0.0f;
        int sameSideCount = 0;
        for (Active3DBillboard existing : active3DBillboards) {
            if (existing.isRightSide == isRightSide) {
                sameSideCount++;
                // Older billboard on this side accelerates upward float and clears out gracefully
                existing.acceleratedFade = true;
                // Alternate vertical height so consecutive phrases never compenetrate
                vertOffset = (sameSideCount % 2 == 1) ? -0.42f : 0.42f;
            }
        }

        int hash = (chunk.chunkId * 0x45d9f3b) ^ (chunk.chunkId >> 16);
        int varY = Math.abs(hash) % 3;

        // Position: 3.5m forward, 1.40m to the side, anti-collision vertical stagger
        float forwardDist = 3.5f + (Math.abs(hash >> 3) % 8) * 0.06f;
        float sideDist = 1.40f + (Math.abs(hash >> 6) % 10) * 0.04f;
        float baseVert = (varY == 0 ? 0.30f : (varY == 1 ? -0.05f : -0.30f));
        float vertDist = baseVert + vertOffset;

        Vec3 spawnPos = camPos.add(
                look.scale(forwardDist)
                        .add(right.scale(isRightSide ? sideDist : -sideDist))
                        .add(up.scale(vertDist))
        );

        active3DBillboards.add(new Active3DBillboard(
                chunk.chunkId,
                chunk.words,
                chunk.totalWidth,
                chunk.startMs,
                chunk.endMs,
                chunk.isRightSide,
                spawnPos.x,
                spawnPos.y,
                spawnPos.z,
                now
        ));

        // Enforce maximum concurrent active billboards cap (max 4 total: 2 left, 2 right)
        // Eliminates visual clutter in ultra-fast songs like Godzilla
        while (active3DBillboards.size() > 4) {
            active3DBillboards.remove(0);
        }
    }

    private static void render3DWords(
            Font font,
            Active3DBillboard b,
            float startX,
            long progressMs,
            float alpha,
            ModConfig.CombatAnimation animMode,
            long now,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        float currentX = startX;

        // Dynamic theme-based colors from ThemeManager!
        int primaryAccent = ThemeManager.getAccent();
        int secondaryAccent = ThemeManager.getAccent2();

        int activeGlowRgb = primaryAccent & 0x00FFFFFF;
        int secondaryGlowRgb = secondaryAccent & 0x00FFFFFF;

        float pulseFactor = 1.0f;
        if (animMode == ModConfig.CombatAnimation.NEON_PULSE) {
            pulseFactor = (float) (Math.sin((now % 10000L) * 0.007) * 0.28 + 0.95);
        }

        long beatSyncProgress = progressMs + 100L; // 100ms vocal onset lead time aligns highlighting right on the beat
        for (int i = 0; i < b.words.size(); i++) {
            CombatWord word = b.words.get(i);

            boolean isActive = (beatSyncProgress >= word.startMs && beatSyncProgress < word.endMs);
            boolean isPast = (beatSyncProgress >= word.endMs);

            int coreColor;
            int glowRgb;
            float glowStrength;
            float wordScale = 1.0f;
            float wordAlpha = alpha;

            if (isActive) {
                // Active word: pure white core with vibrant theme glow
                coreColor = 0xFFFFFFFF;
                glowRgb = activeGlowRgb;
                glowStrength = 1.05f * pulseFactor;

                // Word onset bump or fade
                float wordProgress = (float) (beatSyncProgress - word.startMs) / (float) Math.max(1L, word.endMs - word.startMs);
                if (animMode == ModConfig.CombatAnimation.POP_FLOAT && wordProgress < 0.22f) {
                    float p = 1.0f - (wordProgress / 0.22f);
                    wordScale = 1.0f + (0.09f * p * p);
                } else if (animMode == ModConfig.CombatAnimation.NEON_PULSE) {
                    wordScale = 1.0f + (0.04f * pulseFactor);
                } else if (animMode == ModConfig.CombatAnimation.WORD_FADE) {
                    // Smooth word-by-word fade in over 150ms
                    float fadeElapsed = (float) (beatSyncProgress - word.startMs);
                    if (fadeElapsed < 150.0f) {
                        float wFadeT = Math.max(0.0f, Math.min(1.0f, fadeElapsed / 150.0f));
                        wordAlpha *= wFadeT;
                    }
                }
            } else if (isPast) {
                // Past words: secondary theme accent with smooth ambient glow
                coreColor = 0xFF000000 | secondaryGlowRgb;
                glowRgb = secondaryGlowRgb;
                glowStrength = 0.65f;
            } else {
                // Future words
                if (animMode == ModConfig.CombatAnimation.WORD_FADE) {
                    // In WORD_FADE: future words remain invisible until their start timestamp
                    wordAlpha = 0.0f;
                    coreColor = 0x00000000;
                    glowRgb = secondaryGlowRgb;
                    glowStrength = 0.0f;
                } else {
                    // In other modes: dimmed translucent preview
                    coreColor = 0x48000000 | secondaryGlowRgb;
                    glowRgb = secondaryGlowRgb;
                    glowStrength = 0.22f;
                }
            }

            if (wordAlpha > 0.01f) {
                drawGlowText3D(font, word.component, currentX, 0.0f, coreColor, glowRgb, glowStrength, wordAlpha, wordScale, poseStack, bufferSource);
            }

            currentX += word.width + lastSpaceWidth;
        }
    }

    /**
     * Renders crisp 3D text glyphs wrapped in a real multi-ring Gaussian bloom glow,
     * using depth separation to ensure the glow sits physically behind the sharp core glyphs.
     */
    private static void drawGlowText3D(
            Font font,
            Component comp,
            float x,
            float y,
            int coreColor,
            int glowRgb,
            float glowStrength,
            float alpha,
            float scale,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        if (alpha <= 0.01f) return;

        poseStack.pushPose();
        poseStack.translate(x, y, 0.0f);
        if (Math.abs(scale - 1.0f) > 0.001f) {
            poseStack.scale(scale, scale, 1.0f);
        }

        int baseGlowAlpha = (int) (255.0f * alpha * glowStrength);
        if (baseGlowAlpha > 4) {
            // Ring 1: Outer diffuse halo (8 samples at radius ~3.2px, z = -0.012f)
            int outerA = Math.max(1, (int) (baseGlowAlpha * 0.12f));
            int outerColor = (outerA << 24) | (glowRgb & 0x00FFFFFF);
            drawRingOffsets3D(font, comp, 3.2f, outerColor, -0.012f, poseStack, bufferSource);

            // Ring 2: Mid body glow (8 samples at radius ~2.0px, z = -0.008f)
            int midA = Math.max(1, (int) (baseGlowAlpha * 0.24f));
            int midColor = (midA << 24) | (glowRgb & 0x00FFFFFF);
            drawRingOffsets3D(font, comp, 2.0f, midColor, -0.008f, poseStack, bufferSource);

            // Ring 3: Tight intense rim (8 samples at radius ~1.0px, z = -0.004f)
            int innerA = Math.max(1, (int) (baseGlowAlpha * 0.44f));
            int innerColor = (innerA << 24) | (glowRgb & 0x00FFFFFF);
            drawRingOffsets3D(font, comp, 1.0f, innerColor, -0.004f, poseStack, bufferSource);
        }

        // Core text drawn cleanly on top at z = +0.004f
        int coreA = (int) (((coreColor >>> 24) & 0xFF) * alpha);
        if (coreA > 4) {
            int finalCoreColor = (coreA << 24) | (coreColor & 0x00FFFFFF);
            poseStack.pushPose();
            poseStack.translate(0.0f, 0.0f, 0.004f);
            font.drawInBatch(comp, 0, 0, finalCoreColor, false,
                    poseStack.last().pose(), bufferSource,
                    Font.DisplayMode.SEE_THROUGH, 0, 0x00F000F0);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void drawRingOffsets3D(
            Font font,
            Component comp,
            float r,
            int color,
            float zOffset,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        poseStack.pushPose();
        poseStack.translate(0.0f, 0.0f, zOffset);
        float diag = r * 0.7071f;
        drawSample3D(font, comp, -r, 0, color, poseStack, bufferSource);
        drawSample3D(font, comp, r, 0, color, poseStack, bufferSource);
        drawSample3D(font, comp, 0, -r, color, poseStack, bufferSource);
        drawSample3D(font, comp, 0, r, color, poseStack, bufferSource);
        drawSample3D(font, comp, -diag, -diag, color, poseStack, bufferSource);
        drawSample3D(font, comp, diag, -diag, color, poseStack, bufferSource);
        drawSample3D(font, comp, -diag, diag, color, poseStack, bufferSource);
        drawSample3D(font, comp, diag, diag, color, poseStack, bufferSource);
        poseStack.popPose();
    }

    private static void drawSample3D(
            Font font,
            Component comp,
            float x,
            float y,
            int color,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        font.drawInBatch(comp, x, y, color, false,
                poseStack.last().pose(), bufferSource,
                Font.DisplayMode.SEE_THROUGH, 0, 0x00F000F0);
    }

    private static void rebuildCombatChunks(Font font, String trackKey, LyricsManager.SongLyrics lyrics, Style style, long trackDurationMs) {
        cachedCombatChunks.clear();
        lastCombatTrackKey = trackKey;
        lastSpaceWidth = font.width(" ");

        if (lyrics != null && lyrics.isSynced() && !lyrics.getLines().isEmpty()) {
            List<LyricLine> lines = lyrics.getLines();
            int globalChunkId = 0;
            for (int i = 0; i < lines.size(); i++) {
                LyricLine line = lines.get(i);
                String raw = sanitizeClean(line.getText());
                if (raw.isEmpty()) continue;

                long lineStart = line.getTimestampMs();
                long lineEnd;
                if (i + 1 < lines.size()) {
                    lineEnd = lines.get(i + 1).getTimestampMs();
                } else {
                    lineEnd = lineStart + 3500L;
                }
                long actualLineDuration = lineEnd - lineStart;

                // Split into uppercase words
                String[] words = raw.toUpperCase(Locale.ROOT).split("\\s+");
                if (words.length == 0) continue;

                // Natural singing cadence estimation:
                // Humans sing at ~240ms to 420ms per word depending on length.
                // Do NOT stretch words across long instrumental breaks (e.g. 7-second guitar gaps)!
                int totalChars = 0;
                for (String w : words) totalChars += w.length();
                long naturalSingingDuration = 220L + (totalChars * 52L) + (words.length * 120L);
                long lineSingingDuration = Math.min(actualLineDuration, Math.max(350L, naturalSingingDuration));

                // For very fast lines (Godzilla rap speeds) or short phrases (<= 4 words),
                // keep the whole line in 1 chunk to prevent overlapping micro-phrases!
                List<List<String>> chunkedWordGroups;
                if (words.length <= 4 || actualLineDuration < 1200L || lineSingingDuration < 1200L) {
                    List<String> singleChunk = new ArrayList<>();
                    for (String w : words) singleChunk.add(w);
                    chunkedWordGroups = new ArrayList<>();
                    chunkedWordGroups.add(singleChunk);
                } else {
                    chunkedWordGroups = partitionWords(words);
                }

                int totalWordsInLine = words.length;
                long curChunkStart = lineStart;

                for (int g = 0; g < chunkedWordGroups.size(); g++) {
                    List<String> group = chunkedWordGroups.get(g);
                    float ratio = (float) group.size() / (float) totalWordsInLine;
                    long chunkDuration = Math.max(180L, (long) (lineSingingDuration * ratio));
                    // Never let chunkEnd bleed past lineEnd
                    long chunkEnd = (g == chunkedWordGroups.size() - 1 && i + 1 < lines.size())
                            ? Math.min(lineEnd, curChunkStart + chunkDuration)
                            : (curChunkStart + chunkDuration);
                    if (chunkEnd <= curChunkStart) {
                        chunkEnd = curChunkStart + 180L;
                    }

                    // Syllable/char-weighted word durations for natural musical rhythm:
                    int groupChars = 0;
                    for (String w : group) groupChars += Math.max(2, w.length());

                    List<CombatWord> combatWords = new ArrayList<>();
                    int groupTotalW = 0;
                    long curWordStart = curChunkStart;
                    long remainingTime = chunkEnd - curChunkStart;

                    for (int w = 0; w < group.size(); w++) {
                        String wordStr = group.get(w);
                        Component wordComp = Component.literal(wordStr).withStyle(style);
                        int wWidth = font.width(wordComp);

                        int wordWeight = Math.max(2, wordStr.length());
                        long wDuration;
                        if (w == group.size() - 1) {
                            wDuration = Math.max(35L, chunkEnd - curWordStart);
                        } else {
                            wDuration = Math.max(35L, (remainingTime * wordWeight) / groupChars);
                        }
                        long wEnd = Math.min(chunkEnd, curWordStart + wDuration);
                        combatWords.add(new CombatWord(wordStr, wordComp, wWidth, curWordStart, wEnd));
                        groupTotalW += wWidth;
                        if (w < group.size() - 1) {
                            groupTotalW += lastSpaceWidth;
                        }
                        curWordStart = wEnd;
                    }

                    boolean isRightSide = (globalChunkId % 2 == 1);
                    cachedCombatChunks.add(new CombatChunk(globalChunkId, combatWords, groupTotalW, curChunkStart, chunkEnd, isRightSide));
                    globalChunkId++;
                    curChunkStart = chunkEnd;
                }
            }
        } else if (lyrics != null && !lyrics.getPlainLyrics().isEmpty()) {
            // Plain lyrics fallback: split lines, spread evenly over track duration
            String[] plainLines = lyrics.getPlainLyrics().split("\\r?\\n");
            List<String> validLines = new ArrayList<>();
            for (String l : plainLines) {
                String clean = sanitizeClean(l);
                if (!clean.isEmpty()) validLines.add(clean.toUpperCase(Locale.ROOT));
            }
            if (!validLines.isEmpty()) {
                long duration = trackDurationMs > 0 ? trackDurationMs : 180000L;
                long lineSpan = duration / validLines.size();
                int globalChunkId = 0;
                for (int i = 0; i < validLines.size(); i++) {
                    long lineStart = i * lineSpan;
                    long lineEnd = (i + 1) * lineSpan;
                    String[] words = validLines.get(i).split("\\s+");
                    List<List<String>> groups;
                    if (words.length <= 4 || lineSpan < 1200L) {
                        List<String> singleChunk = new ArrayList<>();
                        for (String w : words) singleChunk.add(w);
                        groups = new ArrayList<>();
                        groups.add(singleChunk);
                    } else {
                        groups = partitionWords(words);
                    }
                    long curChunkStart = lineStart;
                    for (int g = 0; g < groups.size(); g++) {
                        List<String> group = groups.get(g);
                        long cDuration = lineSpan / groups.size();
                        long cEnd = (g == groups.size() - 1) ? lineEnd : (curChunkStart + cDuration);
                        List<CombatWord> combatWords = new ArrayList<>();
                        int groupTotalW = 0;
                        long curWStart = curChunkStart;
                        long wDuration = Math.max(40L, (cEnd - curWStart) / group.size());
                        for (int w = 0; w < group.size(); w++) {
                            String wordStr = group.get(w);
                            Component wordComp = Component.literal(wordStr).withStyle(style);
                            int wWidth = font.width(wordComp);
                            long wEnd = (w == group.size() - 1) ? cEnd : (curWStart + wDuration);
                            combatWords.add(new CombatWord(wordStr, wordComp, wWidth, curWStart, wEnd));
                            groupTotalW += wWidth;
                            if (w < group.size() - 1) groupTotalW += lastSpaceWidth;
                            curWStart = wEnd;
                        }
                        boolean isRightSide = (globalChunkId % 2 == 1);
                        cachedCombatChunks.add(new CombatChunk(globalChunkId, combatWords, groupTotalW, curChunkStart, cEnd, isRightSide));
                        globalChunkId++;
                        curChunkStart = cEnd;
                    }
                }
            }
        }
    }

    private static List<List<String>> partitionWords(String[] words) {
        List<List<String>> result = new ArrayList<>();
        int len = words.length;
        if (len <= 4) {
            List<String> single = new ArrayList<>();
            for (String w : words) single.add(w);
            result.add(single);
            return result;
        }

        int idx = 0;
        while (idx < len) {
            int remaining = len - idx;
            int take;
            if (remaining == 5) {
                take = 3;
            } else if (remaining == 4) {
                take = 2;
            } else if (remaining >= 3) {
                take = 3;
            } else {
                take = remaining;
            }
            List<String> chunk = new ArrayList<>();
            for (int i = 0; i < take; i++) {
                chunk.add(words[idx + i]);
            }
            result.add(chunk);
            idx += take;
        }
        return result;
    }
}
