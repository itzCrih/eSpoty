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
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders pure celestial lyrics directly in the 3D Minecraft sky along the horizon.
 * Features depth-tested occlusion (terrain, blocks, entities, and players occlude the text),
 * horizontal panoramic scrolling with the synchronized line centered, and a multi-pass
 * glowing neon starlight bloom effect with NO background or UI boxes.
 */
public class SkyLyricsRenderer {

    private static float smoothYaw = 0f;
    private static boolean initialized = false;
    private static double smoothScroll = 0.0;
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
            smoothScroll = 0.0;
        }

        PoseStack poseStack = context.matrices();
        if (poseStack == null) {
            poseStack = new PoseStack();
        }

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        if (bufferSource == null) return;

        Font font = mc.font;
        if (font == null) return;

        // Position 95 blocks away at the horizon
        float distance = 95.0f;
        float baseElevation = 15.0f;
        if (pitch < -10.0f) {
            baseElevation += (-pitch - 10.0f) * 0.85f;
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

        // Billboard text scale (3D world space: positive X, negative Y to invert font coords, positive Z)
        float baseScale = 0.35f;
        poseStack.scale(baseScale, -baseScale, baseScale);

        LyricsManager lyricsMgr = LyricsManager.getInstance();
        LyricsManager.SongLyrics lyrics = lyricsMgr.getCurrentLyrics();
        long progressMs = stateManager.getInterpolatedProgressMs();

        if (lyrics != null && lyrics.isSynced() && !lyrics.getLines().isEmpty()) {
            List<LyricLine> lines = lyrics.getLines();
            int activeIdx = lyrics.getActiveIndex(progressMs);
            if (activeIdx < 0) activeIdx = 0;

            // Calculate cumulative widths and positions for all lines
            int count = lines.size();
            float[] widths = new float[count];
            String[] texts = new String[count];
            double[] lineCenters = new double[count];

            float gap = 32.0f;
            double runningX = 0.0;

            for (int i = 0; i < count; i++) {
                String text = lines.get(i).getText().trim().toUpperCase();
                texts[i] = text;
                widths[i] = font.width(text);
                if (i > 0) {
                    runningX += (widths[i - 1] / 2.0f) + gap + (widths[i] / 2.0f);
                }
                lineCenters[i] = runningX;
            }

            double targetScroll = lineCenters[activeIdx];
            smoothScroll += (targetScroll - smoothScroll) * 0.11;

            // Render visible lines along the horizon panorama
            for (int i = 0; i < count; i++) {
                if (texts[i].isEmpty()) continue;

                float xPos = (float) (lineCenters[i] - smoothScroll);
                float distFromCenter = Math.abs(xPos);

                // Cull lines far off to the sides
                if (distFromCenter > 380.0f) continue;

                float alpha = Math.max(0.0f, 1.0f - (distFromCenter / 360.0f));
                alpha = alpha * alpha; // smooth quadratic falloff
                if (alpha < 0.04f) continue;

                boolean isActive = (i == activeIdx);
                float lineScale = isActive ? 1.25f : 0.88f;

                poseStack.pushPose();
                poseStack.translate(xPos, isActive ? -3.0f : 0.0f, 0.0f);
                poseStack.scale(lineScale, lineScale, lineScale);

                int coreColor = isActive ? applyAlpha(0xFFFFFFFF, alpha) : applyAlpha(0x99FFF9C4, alpha * 0.7f);
                int glowColor = isActive ? applyAlpha(0xFFFFFFFF, alpha) : applyAlpha(0x66FFD700, alpha * 0.5f);

                float halfW = widths[i] / 2.0f;
                drawGlowText(font, texts[i], -halfW, -4.0f, coreColor, glowColor, isActive, poseStack.last().pose(), bufferSource);

                poseStack.popPose();
            }
        } else {
            // Instrumental or unsynced: clean glowing track title at the horizon
            String title = state.getTrackName().toUpperCase();
            float halfW = font.width(title) / 2.0f;
            poseStack.pushPose();
            poseStack.scale(1.22f, 1.22f, 1.22f);
            drawGlowText(font, title, -halfW, -4.0f, 0xFFFFFFFF, 0xFFFFF066, true, poseStack.last().pose(), bufferSource);
            poseStack.popPose();
        }

        poseStack.popPose();

        // Flush text vertices to OpenGL
        bufferSource.endBatch();
    }

    private static void drawGlowText(
            Font font,
            String text,
            float x,
            float y,
            int coreColor,
            int glowColor,
            boolean isActive,
            Matrix4f matrix,
            MultiBufferSource bufferSource
    ) {
        if (isActive) {
            // Multi-pass radiant starlight bloom aura (exact look of Screenshot 2)
            // Outer diffuse aura (radius 2.2px)
            int outerGlow = applyAlpha(glowColor, 0.28f);
            font.drawInBatch(text, x - 2.2f, y, outerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(text, x + 2.2f, y, outerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(text, x, y - 2.2f, outerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(text, x, y + 2.2f, outerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);

            // Inner bright bloom (radius 1.1px)
            int innerGlow = applyAlpha(glowColor, 0.55f);
            font.drawInBatch(text, x - 1.1f, y - 1.1f, innerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(text, x + 1.1f, y - 1.1f, innerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(text, x - 1.1f, y + 1.1f, innerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(text, x + 1.1f, y + 1.1f, innerGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        } else {
            // Soft subtle glow for non-active lines
            int softGlow = applyAlpha(glowColor, 0.25f);
            font.drawInBatch(text, x - 1.0f, y, softGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(text, x + 1.0f, y, softGlow, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }

        // Sharp radiant core text
        font.drawInBatch(text, x, y, coreColor, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)))));
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
