package com.espoty.gui.sky;

import com.espoty.config.ModConfig;
import com.espoty.lyrics.LyricLine;
import com.espoty.lyrics.LyricsManager;
import com.espoty.spotify.SpotifyStateManager;
import com.espoty.spotify.models.PlaybackState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.HitResult;

/**
 * Projects Spotify lyrics into the Minecraft sky as an ethereal celestial aurora banner.
 * Uses 3D-to-2D celestial sky projection with smooth cosmic inertia, obstacle raycasting,
 * and glowing Royal Gold & Neon Yellow typography.
 */
public class SkyLyricsRenderer {

    private static float smoothYaw = 0f;
    private static float smoothPitch = 0f;
    private static float currentSkyAlpha = 0f;
    private static String lastLyricText = "";
    private static long lastLyricChangeTime = 0;
    private static boolean initialized = false;

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
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

        float pitch = camera.xRot(); // -90 (zenith) to +90 (nadir), 0 (horizon)
        float yaw = camera.yRot();

        if (!initialized) {
            smoothYaw = yaw;
            smoothPitch = pitch;
            initialized = true;
        }

        // 1. Ceiling / Obstruction check via raycast
        boolean isObstructed = false;
        if (mc.player != null) {
            HitResult hit = mc.player.pick(40.0D, 1.0F, false);
            if (hit != null && hit.getType() == HitResult.Type.BLOCK && pitch < -5.0f) {
                isObstructed = true;
            }
        }

        // 2. Pitch / Elevation factor:
        // When pitch >= 0 (looking at horizon or ground), opacity is 0.
        // As player tilts head up into the sky (-pitch), opacity quickly reaches 1.0.
        float lookUpAngle = -pitch;
        float pitchAlpha;
        if (lookUpAngle <= 4.0f || isObstructed) {
            pitchAlpha = 0.0f;
        } else if (lookUpAngle >= 18.0f) {
            pitchAlpha = 1.0f;
        } else {
            pitchAlpha = (lookUpAngle - 4.0f) / 14.0f;
            pitchAlpha = pitchAlpha * pitchAlpha; // smooth quadratic ease
        }

        currentSkyAlpha += (pitchAlpha - currentSkyAlpha) * 0.18f;
        if (currentSkyAlpha < 0.01f) return;

        // 3. Smooth Cosmic Inertia (lag behind camera rotation for celestial weight)
        float diffYaw = yaw - smoothYaw;
        while (diffYaw < -180.0f) diffYaw += 360.0f;
        while (diffYaw > 180.0f) diffYaw -= 360.0f;
        smoothYaw += diffYaw * 0.14f;

        smoothPitch += (pitch - smoothPitch) * 0.14f;

        // 4. Perspective Screen Projection
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();

        double fovY = mc.options.fov().get();
        double aspect = (double) screenWidth / (double) screenHeight;
        double fovX = 2.0 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(fovY / 2.0)) * aspect));

        // When looking high into the sky (pitch < -24), celestial anchor stays centered in view.
        // When looking down towards horizon (pitch > -24), celestial anchor stays at -24 elevation in the sky,
        // causing the lyrics to slide smoothly towards the top of the screen and disappear.
        float anchorPitch = Math.min(-24.0f, smoothPitch);
        float deltaPitch = smoothPitch - anchorPitch;
        float deltaYaw = yaw - smoothYaw;
        while (deltaYaw < -180.0f) deltaYaw += 360.0f;
        while (deltaYaw > 180.0f) deltaYaw -= 360.0f;

        double tanHalfFovX = Math.tan(Math.toRadians(fovX / 2.0));
        double tanHalfFovY = Math.tan(Math.toRadians(fovY / 2.0));
        double tanDeltaX = Math.tan(Math.toRadians(deltaYaw));
        double tanDeltaY = Math.tan(Math.toRadians(deltaPitch));

        float projX = (float) (screenWidth / 2.0 - (tanDeltaX / tanHalfFovX) * (screenWidth / 2.0));
        float projY = (float) (screenHeight / 2.0 + (tanDeltaY / tanHalfFovY) * (screenHeight / 2.0));

        // Gentle cosmic breathing bob
        long now = System.currentTimeMillis();
        float hoverBob = (float) Math.sin((now % 4500) / 4500.0 * Math.PI * 2) * 3.5f;
        projY += hoverBob;

        // 5. Lyrics & Content Retrieval
        Font font = mc.font;
        long progressMs = stateManager.getInterpolatedProgressMs();
        LyricLine currentLyric = LyricsManager.getInstance().getActiveLyricLine(progressMs);
        LyricLine nextLyric = LyricsManager.getInstance().getNextLyricLine(progressMs);

        String lyricText = (currentLyric != null && !currentLyric.getText().isEmpty()) ? currentLyric.getText() : "";

        if (!lyricText.equals(lastLyricText)) {
            lastLyricText = lyricText;
            lastLyricChangeTime = now;
        }

        long elapsed = now - lastLyricChangeTime;
        float lineProgress = Math.min(1.0f, elapsed / 220.0f);
        float lineEase = 1.0f - (float) Math.pow(1.0f - lineProgress, 3);
        float lineYGlide = (1.0f - lineEase) * 4.0f;
        float activeAlpha = currentSkyAlpha * lineEase;

        boolean hasLyrics = !lyricText.isEmpty();
        boolean hasNext = (nextLyric != null && !nextLyric.getText().isEmpty());

        // Calculate card dimensions
        String mainText = hasLyrics ? "★  " + lyricText + "  ★" : "♪ " + state.getTrackName() + " ♪";
        int mainW = font.width(mainText);
        String headerText = hasLyrics ? "✦ " + state.getTrackName() + " • " + state.getArtistNames() + " ✦" : "✦ NOW PLAYING IN THE SKY ✦";
        int headerW = font.width(headerText);
        String nextText = (hasLyrics && hasNext) ? nextLyric.getText() : "";
        int nextW = font.width(nextText);

        int cardW = Math.max(220, Math.max(mainW, Math.max(headerW, nextW)) + 36);
        int cardH = (hasLyrics && hasNext) ? 54 : 40;

        int cardX = (int) (projX - cardW / 2.0f);
        int cardY = (int) (projY - cardH / 2.0f);

        // Offscreen check
        if (cardX + cardW < -50 || cardX > screenWidth + 50 || cardY + cardH < -50 || cardY > screenHeight + 50) {
            return;
        }

        guiGraphics.pose().pushMatrix();

        // 6. Draw Frosted Obsidian Celestial Sky Capsule
        renderCelestialGlassCard(guiGraphics, cardX, cardY, cardW, cardH, currentSkyAlpha);

        // 7. Header (Constellation song badge)
        int hY = cardY + 6;
        guiGraphics.drawCenteredString(font, headerText, (int) projX, hY, applyAlpha(0xFFFFF066, currentSkyAlpha * 0.85f));

        // 8. Main Lyric (Radiant Royal Gold with Ambient Bloom Aura)
        int mY = cardY + 19 + (int) lineYGlide;

        // 4-tap subtle golden bloom glow around active line
        int glowColor = applyAlpha(0x44FFD700, activeAlpha * 0.6f);
        guiGraphics.drawCenteredString(font, mainText, (int) projX - 1, mY, glowColor);
        guiGraphics.drawCenteredString(font, mainText, (int) projX + 1, mY, glowColor);
        guiGraphics.drawCenteredString(font, mainText, (int) projX, mY - 1, glowColor);
        guiGraphics.drawCenteredString(font, mainText, (int) projX, mY + 1, glowColor);

        // Main radiant text
        guiGraphics.drawCenteredString(font, mainText, (int) projX, mY, applyAlpha(0xFFFFD700, activeAlpha));

        // 9. Upcoming lyric preview (if present)
        if (hasLyrics && hasNext) {
            // Subtle glowing starlight separator line
            int divY = cardY + 33;
            guiGraphics.fill(cardX + 16, divY, cardX + cardW - 16, divY + 1, applyAlpha(0x22FFD700, currentSkyAlpha * 0.7f));

            int nY = divY + 4;
            guiGraphics.drawCenteredString(font, nextText, (int) projX, nY, applyAlpha(0x99FFFDE7, currentSkyAlpha * 0.75f));
        }

        guiGraphics.pose().popMatrix();
    }

    private static void renderCelestialGlassCard(GuiGraphics guiGraphics, int x, int y, int w, int h, float alpha) {
        // Multi-tier soft cosmic shadow
        guiGraphics.fill(x + 1, y + 2, x + w - 1, y + h + 3, applyAlpha(0x35000000, alpha));

        // Frosted deep obsidian glass body
        guiGraphics.fill(x + 3, y, x + w - 3, y + h, applyAlpha(0x880B0B14, alpha));
        guiGraphics.fill(x, y + 3, x + 3, y + h - 3, applyAlpha(0x880B0B14, alpha));
        guiGraphics.fill(x + w - 3, y + 3, x + w, y + h - 3, applyAlpha(0x880B0B14, alpha));

        // Top radiant gold specular gradient line
        int cx = x + (w / 2);
        guiGraphics.fillGradient(x + 8, y, cx, y + 2, applyAlpha(0x00FFD700, alpha), applyAlpha(0xFFFFD700, alpha));
        guiGraphics.fillGradient(cx, y, x + w - 8, y + 2, applyAlpha(0xFFFFD700, alpha), applyAlpha(0x00FFD700, alpha));

        // Subtle glowing golden border
        int border = applyAlpha(0x30FFD700, alpha);
        guiGraphics.fill(x + 3, y + h - 1, x + w - 3, y + h, border);
        guiGraphics.fill(x, y + 3, x + 1, y + h - 3, border);
        guiGraphics.fill(x + w - 1, y + 3, x + w, y + h - 3, border);
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)))));
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
