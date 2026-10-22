package com.espoty.gui.sky;

import com.espoty.config.ModConfig;
import com.espoty.lyrics.LyricLine;
import com.espoty.lyrics.LyricsManager;
import com.espoty.spotify.SpotifyStateManager;
import com.espoty.spotify.models.PlaybackState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders pure celestial lyrics directly in the 3D Minecraft sky along the horizon.
 * Features depth-tested occlusion (terrain, blocks, entities, and players occlude the text),
 * high-resolution modern typography, vertical scrolling ribbon with the synchronized line
 * centered, and a luminous starlight glow with zero UI boxes or backgrounds.
 */
public class SkyLyricsRenderer {

    private static final Identifier MODERN_FONT = Identifier.fromNamespaceAndPath("espoty", "modern");
    private static final Style MODERN_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(MODERN_FONT));

    private static float smoothYaw = 0f;
    private static boolean initialized = false;
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

        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) return;

        float yaw = camera.yRot();
        float pitch = camera.xRot();

        if (!initialized) {
            smoothYaw = yaw;
            initialized = true;
        }

        // Smooth celestial horizontal tracking
        float diffYaw = yaw - smoothYaw;
        while (diffYaw < -180.0f) diffYaw += 360.0f;
        while (diffYaw > 180.0f) diffYaw -= 360.0f;
        smoothYaw += diffYaw * 0.10f;

        // Reset scroll when track changes
        String currentTrack = state.getTrackName() + " - " + state.getArtistNames();
        if (!currentTrack.equals(lastTrackKey)) {
            lastTrackKey = currentTrack;
            smoothScrollY = 0.0;
        }

        PoseStack poseStack = context.matrices();
        if (poseStack == null) {
            poseStack = new PoseStack();
        }

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        if (bufferSource == null) return;

        Font font = mc.font;
        if (font == null) return;

        // Position 65 blocks away at the celestial horizon
        float distance = 65.0f;
        float baseElevation = 18.0f;
        if (pitch < -10.0f) {
            baseElevation += (-pitch - 10.0f) * 0.90f;
        }

        long now = System.currentTimeMillis();
        float hoverBob = (float) Math.sin((now % 5000) / 5000.0 * Math.PI * 2) * 1.2f;

        float radYaw = (float) Math.toRadians(smoothYaw);
        float fwdX = -(float) Math.sin(radYaw);
        float fwdZ = (float) Math.cos(radYaw);

        double targetDx = fwdX * distance;
        double targetDy = baseElevation + hoverBob;
        double targetDz = fwdZ * distance;

        poseStack.pushPose();
        poseStack.translate(targetDx, targetDy, targetDz);
        poseStack.mulPose(camera.rotation());

        // Billboard text scale (Large crisp celestial typography)
        float baseScale = 0.48f;
        poseStack.scale(baseScale, -baseScale, baseScale);

        LyricsManager lyricsMgr = LyricsManager.getInstance();
        LyricsManager.SongLyrics lyrics = lyricsMgr.getCurrentLyrics();
        long progressMs = stateManager.getInterpolatedProgressMs();

        if (lyrics != null && lyrics.isSynced() && !lyrics.getLines().isEmpty()) {
            List<LyricLine> lines = lyrics.getLines();
            int activeIdx = lyrics.getActiveIndex(progressMs);
            if (activeIdx < 0) activeIdx = 0;

            float lineSpacing = 17.0f;
            double targetScrollY = activeIdx * lineSpacing;

            if (Math.abs(targetScrollY - smoothScrollY) > 250.0) {
                smoothScrollY = targetScrollY;
            } else {
                smoothScrollY += (targetScrollY - smoothScrollY) * 0.12;
            }

            int count = lines.size();
            for (int i = 0; i < count; i++) {
                String rawText = lines.get(i).getText().trim().toUpperCase();
                if (rawText.isEmpty()) continue;

                float relY = (float) (i * lineSpacing - smoothScrollY);
                float distFromCenter = Math.abs(relY);

                // Cull lines outside visible vertical window
                if (distFromCenter > 85.0f) continue;

                float alpha = Math.max(0.0f, 1.0f - (distFromCenter / 80.0f));
                alpha = alpha * alpha; // Smooth quadratic falloff
                if (alpha < 0.04f) continue;

                boolean isActive = (i == activeIdx);
                int deltaIdx = Math.abs(i - activeIdx);

                float lineScale = isActive ? 1.25f : (deltaIdx == 1 ? 0.92f : (deltaIdx == 2 ? 0.82f : 0.72f));

                Component comp = Component.literal(rawText).withStyle(MODERN_STYLE);
                float halfW = font.width(comp) / 2.0f;

                int coreColor;
                int glowColor;

                if (isActive) {
                    coreColor = applyAlpha(0xFFFFFFFF, alpha);
                    glowColor = applyAlpha(0x60E8F4FF, alpha);
                } else if (deltaIdx == 1) {
                    coreColor = applyAlpha(0xD8FFFFFF, alpha * 0.75f);
                    glowColor = applyAlpha(0x35B0D0FF, alpha * 0.45f);
                } else if (deltaIdx == 2) {
                    coreColor = applyAlpha(0xA0E2E8F0, alpha * 0.50f);
                    glowColor = applyAlpha(0x2088B0E0, alpha * 0.30f);
                } else {
                    coreColor = applyAlpha(0x60CBD5E1, alpha * 0.30f);
                    glowColor = 0;
                }

                poseStack.pushPose();
                poseStack.translate(0.0f, relY, 0.0f);
                poseStack.scale(lineScale, lineScale, lineScale);

                drawModernGlowText(font, comp, -halfW, -4.0f, coreColor, glowColor, isActive, poseStack.last().pose(), bufferSource);

                poseStack.popPose();
            }
        } else {
            // Instrumental or unsynced: clean glowing track title at the celestial horizon
            String title = state.getTrackName().toUpperCase();
            Component comp = Component.literal(title).withStyle(MODERN_STYLE);
            float halfW = font.width(comp) / 2.0f;
            poseStack.pushPose();
            poseStack.scale(1.25f, 1.25f, 1.25f);
            drawModernGlowText(font, comp, -halfW, -4.0f, 0xFFFFFFFF, 0x60E8F4FF, true, poseStack.last().pose(), bufferSource);
            poseStack.popPose();
        }

        poseStack.popPose();

        // Flush text vertices to OpenGL
        bufferSource.endBatch();
    }

    private static void drawModernGlowText(
            Font font,
            Component comp,
            float x,
            float y,
            int coreColor,
            int glowColor,
            boolean isActive,
            Matrix4f matrix,
            MultiBufferSource bufferSource
    ) {
        if (isActive && glowColor != 0) {
            // Outer diffuse aura
            int outerGlow = applyAlpha(glowColor, 0.22f);
            font.drawInBatch(comp, x - 1.4f, y, outerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(comp, x + 1.4f, y, outerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(comp, x, y - 1.4f, outerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(comp, x, y + 1.4f, outerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);

            // Inner tight bloom (preserves crisp letter counters)
            int innerGlow = applyAlpha(glowColor, 0.45f);
            font.drawInBatch(comp, x - 0.70f, y - 0.70f, innerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(comp, x + 0.70f, y - 0.70f, innerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(comp, x - 0.70f, y + 0.70f, innerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(comp, x + 0.70f, y + 0.70f, innerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        } else if (!isActive) {
            // Soft subtle outline for readable non-active lines
            int subtleShadow = applyAlpha(0x40000000, 0.5f);
            font.drawInBatch(comp, x + 0.5f, y + 0.5f, subtleShadow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }

        // Crisp radiant core text
        font.drawInBatch(comp, x, y, coreColor, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)))));
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
