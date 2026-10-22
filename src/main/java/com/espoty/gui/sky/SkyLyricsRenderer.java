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

/**
 * Renders Spotify lyrics as a celestial, glowing aurora banner in the Minecraft sky.
 * Anchored to the heavens, with pitch-based starlight fade, smooth cosmic inertia,
 * and glowing Royal Gold & Neon Yellow typography.
 */
public class SkyLyricsRenderer {

    private static float smoothYaw = 0f;
    private static float smoothPitch = 0f;
    private static float skyAccessAlpha = 0f;
    private static String lastLyricText = "";
    private static long lastLyricChangeTime = 0;

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

        // 1. Sky Access Check: fades out if player is deep in a cave under solid blocks
        boolean canSeeSky = (mc.level != null && mc.player != null && mc.level.canSeeSky(mc.player.blockPosition()));
        skyAccessAlpha += ((canSeeSky ? 1.0f : 0.0f) - skyAccessAlpha) * 0.12f;
        if (skyAccessAlpha < 0.01f) return;

        // 2. Pitch / Elevation Fade:
        // When looking at horizon (pitch >= 0), lyrics are invisible so they never obstruct gameplay.
        // As player tilts head up into the sky (-pitch), lyrics smoothly illuminate the heavens.
        float pitch = camera.xRot(); // -90 (zenith) to +90 (ground)
        float yaw = camera.yRot();

        float lookUpAngle = -pitch;
        float skyFactor;
        if (lookUpAngle <= 4.0f) {
            skyFactor = 0.0f;
        } else if (lookUpAngle >= 32.0f) {
            skyFactor = 1.0f;
        } else {
            skyFactor = (lookUpAngle - 4.0f) / 28.0f;
        }
        skyFactor = skyFactor * skyFactor; // Smooth quadratic ease in

        float totalAlpha = skyFactor * skyAccessAlpha;
        if (totalAlpha < 0.01f) return;

        // 3. Smooth Cosmic Inertia: gentle easing so quick mouse twitches don't vibrate the sky banner
        smoothYaw += (yaw - smoothYaw) * 0.15f;
        smoothPitch += (pitch - smoothPitch) * 0.15f;

        PoseStack poseStack = context.matrices();
        if (poseStack == null) return;

        MultiBufferSource consumers = context.consumers();
        if (consumers == null) return;

        Font font = mc.font;
        if (font == null) return;

        long now = System.currentTimeMillis();
        float hoverBob = (float) Math.sin((now % 4500) / 4500.0 * Math.PI * 2) * 0.7f;

        // Position high in the celestial sphere (46 blocks away in the sky)
        float distance = 46.0f;
        float elevationAngle = Math.max(22.0f, -smoothPitch);
        float radYaw = (float) Math.toRadians(smoothYaw);
        float radElev = (float) Math.toRadians(elevationAngle);

        float dx = -(float) (Math.sin(radYaw) * Math.cos(radElev)) * distance;
        float dy = (float) Math.sin(radElev) * distance + hoverBob;
        float dz = (float) (Math.cos(radYaw) * Math.cos(radElev)) * distance;

        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        poseStack.mulPose(camera.rotation());

        // Majestic celestial scale: ~22 meters wide in 3D world space
        float scale = 0.11f;
        poseStack.scale(-scale, -scale, scale);

        long progressMs = stateManager.getInterpolatedProgressMs();
        LyricLine currentLyric = LyricsManager.getInstance().getActiveLyricLine(progressMs);
        LyricLine nextLyric = LyricsManager.getInstance().getNextLyricLine(progressMs);

        String lyricText = (currentLyric != null && !currentLyric.getText().isEmpty()) ? currentLyric.getText() : "";

        if (!lyricText.equals(lastLyricText)) {
            lastLyricText = lyricText;
            lastLyricChangeTime = now;
        }

        long elapsed = now - lastLyricChangeTime;
        float lineProgress = Math.min(1.0f, elapsed / 260.0f);
        float lineEase = 1.0f - (float) Math.pow(1.0f - lineProgress, 3);
        float lineYGlide = (1.0f - lineEase) * 4.0f;
        float activeAlpha = totalAlpha * lineEase;

        if (lyricText.isEmpty()) {
            // Instrumental or No Synced Lyrics: Floating Celestial Now Playing Card
            String title = "✦ NOW PLAYING ✦";
            String song = "♪ " + state.getTrackName() + " ♪";
            String artist = "by " + state.getArtistNames();

            int wTitle = font.width(title);
            int wSong = font.width(song);
            int wArtist = font.width(artist);

            font.drawInBatch(title, -wTitle / 2.0f, -12.0f, applyAlpha(0xFFFFF066, totalAlpha * 0.8f), true,
                    poseStack.last().pose(), consumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(song, -wSong / 2.0f, 0.0f, applyAlpha(0xFFFFD700, totalAlpha), true,
                    poseStack.last().pose(), consumers, Font.DisplayMode.NORMAL, applyAlpha(0x400A0A14, totalAlpha * 0.7f), 0xF000F0);
            font.drawInBatch(artist, -wArtist / 2.0f, 13.0f, applyAlpha(0xFFAAAAAA, totalAlpha * 0.7f), true,
                    poseStack.last().pose(), consumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        } else {
            // Full Synced Celestial Lyrics Presentation
            // 1. Constellation Song Info Badge
            String badge = "✦ " + state.getTrackName() + " • " + state.getArtistNames() + " ✦";
            int badgeW = font.width(badge);
            font.drawInBatch(badge, -badgeW / 2.0f, -13.0f, applyAlpha(0xFFFFF066, totalAlpha * 0.75f), true,
                    poseStack.last().pose(), consumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);

            // 2. Active Lyric Line (Centerpiece in Radiant Royal Gold with Ambient Bloom Aura)
            String mainText = "★  " + lyricText + "  ★";
            int mainW = font.width(mainText);
            float mainX = -mainW / 2.0f;
            float mainY = lineYGlide;

            // Soft 4-tap celestial starlight bloom aura
            int auraColor = applyAlpha(0x50FFD700, activeAlpha * 0.55f);
            font.drawInBatch(mainText, mainX - 1, mainY, auraColor, false, poseStack.last().pose(), consumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(mainText, mainX + 1, mainY, auraColor, false, poseStack.last().pose(), consumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(mainText, mainX, mainY - 1, auraColor, false, poseStack.last().pose(), consumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(mainText, mainX, mainY + 1, auraColor, false, poseStack.last().pose(), consumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);

            // Radiant main text with translucent cosmic backplate
            int mainColor = applyAlpha(0xFFFFD700, activeAlpha);
            int bgColor = applyAlpha(0x450A0A14, activeAlpha * 0.8f);
            font.drawInBatch(mainText, mainX, mainY, mainColor, true, poseStack.last().pose(), consumers, Font.DisplayMode.NORMAL, bgColor, 0xF000F0);

            // 3. Upcoming Lyric Line Preview (Ethereal Starlight Silver-Gold)
            if (nextLyric != null && !nextLyric.getText().isEmpty()) {
                String nextText = nextLyric.getText();
                int nextW = font.width(nextText);
                float nextX = -nextW / 2.0f;
                float nextY = mainY + 14.0f;

                int nextColor = applyAlpha(0x88FFFDE7, totalAlpha * 0.65f);
                int nextBg = applyAlpha(0x280A0A14, totalAlpha * 0.5f);
                font.drawInBatch(nextText, nextX, nextY, nextColor, true, poseStack.last().pose(), consumers, Font.DisplayMode.NORMAL, nextBg, 0xF000F0);
            }
        }

        poseStack.popPose();

        if (consumers instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)))));
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
