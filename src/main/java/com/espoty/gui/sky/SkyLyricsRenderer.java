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

import java.util.List;

/**
 * Renders clean, modern celestial lyrics in the 3D Minecraft sky along the horizon.
 * Strict depth-tested occlusion ensures terrain, blocks, entities, and players always
 * render in front. Clean minimalist typography — no multi-copy fake glow.
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
        float baseElevation = 14.0f;
        if (pitch < -10.0f) {
            baseElevation += (-pitch - 10.0f) * 0.70f;
        }

        long now = System.currentTimeMillis();
        float hoverBob = (float) Math.sin((now % 5000) / 5000.0 * Math.PI * 2) * 1.0f;

        float radYaw = (float) Math.toRadians(smoothYaw);
        float fwdX = -(float) Math.sin(radYaw);
        float fwdZ = (float) Math.cos(radYaw);

        double targetDx = fwdX * distance;
        double targetDy = baseElevation + hoverBob;
        double targetDz = fwdZ * distance;

        poseStack.pushPose();
        poseStack.translate(targetDx, targetDy, targetDz);
        poseStack.mulPose(camera.rotation());

        // Customizable billboard text scale (User setting: 50% to 200%)
        float userScale = config.getSkyLyricsScale();
        float baseScale = 0.20f * userScale;
        poseStack.scale(baseScale, -baseScale, baseScale);

        LyricsManager lyricsMgr = LyricsManager.getInstance();
        LyricsManager.SongLyrics lyrics = lyricsMgr.getCurrentLyrics();
        long progressMs = stateManager.getInterpolatedProgressMs();

        if (lyrics != null && lyrics.isSynced() && !lyrics.getLines().isEmpty()) {
            List<LyricLine> lines = lyrics.getLines();
            int activeIdx = lyrics.getActiveIndex(progressMs);
            if (activeIdx < 0) activeIdx = 0;

            float lineSpacing = 15.0f;
            double targetScrollY = activeIdx * lineSpacing;

            if (Math.abs(targetScrollY - smoothScrollY) > 200.0) {
                smoothScrollY = targetScrollY;
            } else {
                smoothScrollY += (targetScrollY - smoothScrollY) * 0.12;
            }

            // User setting for visible lyrics length / number of lines
            int maxTotalLines = config.getSkyLyricsLength();
            int maxAboveBelow = Math.max(1, (maxTotalLines - 1) / 2);
            float maxDist = (maxAboveBelow + 0.6f) * lineSpacing;

            int count = lines.size();
            for (int i = 0; i < count; i++) {
                String rawText = lines.get(i).getText().trim().toUpperCase();
                if (rawText.isEmpty()) continue;

                float relY = (float) (i * lineSpacing - smoothScrollY);
                float distFromCenter = Math.abs(relY);

                int deltaIdx = Math.abs(i - activeIdx);
                if (deltaIdx > maxAboveBelow) continue;

                float alpha = Math.max(0.0f, 1.0f - (distFromCenter / maxDist));
                alpha = alpha * alpha; // Smooth quadratic falloff
                if (alpha < 0.05f) continue;

                boolean isActive = (i == activeIdx);

                // Active line is larger, adjacent lines slightly smaller, distant lines smaller still
                float lineScale = isActive ? 1.15f : (deltaIdx == 1 ? 0.88f : 0.78f);

                Component comp = Component.literal(rawText).withStyle(MODERN_STYLE);
                float halfW = font.width(comp) / 2.0f;

                // Clean color hierarchy — no glow, just brightness difference
                int coreColor;
                if (isActive) {
                    // Bright white — the text pops simply by being the brightest
                    coreColor = applyAlpha(0xFFFFFFFF, alpha);
                } else if (deltaIdx == 1) {
                    // Soft light blue-white, noticeably dimmer
                    coreColor = applyAlpha(0xFFC8D8E8, alpha * 0.55f);
                } else {
                    // Faded, ghostly — just enough to read
                    coreColor = applyAlpha(0xFFA0B0C0, alpha * 0.30f);
                }

                poseStack.pushPose();
                poseStack.translate(0.0f, relY, 0.0f);
                poseStack.scale(lineScale, lineScale, lineScale);

                // Single clean text draw with MC's native drop shadow for depth
                // The shadow=true param adds a subtle 1px shadow automatically
                font.drawInBatch(comp, -halfW, -4.0f, coreColor, true,
                        poseStack.last().pose(), bufferSource,
                        Font.DisplayMode.POLYGON_OFFSET, 0, 0xF000F0);

                poseStack.popPose();
            }
        } else {
            // Instrumental or unsynced: clean track title at the celestial horizon
            String title = state.getTrackName().toUpperCase();
            Component comp = Component.literal(title).withStyle(MODERN_STYLE);
            float halfW = font.width(comp) / 2.0f;
            poseStack.pushPose();
            poseStack.scale(1.15f, 1.15f, 1.15f);
            font.drawInBatch(comp, -halfW, -4.0f, 0xFFFFFFFF, true,
                    poseStack.last().pose(), bufferSource,
                    Font.DisplayMode.POLYGON_OFFSET, 0, 0xF000F0);
            poseStack.popPose();
        }

        poseStack.popPose();

        // Flush text vertices to OpenGL
        bufferSource.endBatch();
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)))));
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
