package it.itzcrih.espoty.gui.screen;

import it.itzcrih.espoty.config.ModConfig;
import it.itzcrih.espoty.gui.hud.SpotifyHudRenderer;
import it.itzcrih.espoty.gui.render.AaRoundRect;
import it.itzcrih.espoty.gui.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * HUD Editor Screen.
 */
public class HudEditorScreen extends Screen {

    private final Screen parentScreen;
    private final ModConfig config;

    private int hudX;
    private int hudY;
    private float hudScale;
    private int cardWidth;
    private int cardHeight;

    private boolean isDragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    // Corner Resizing States
    private enum ResizeCorner {
        NONE,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    private ResizeCorner activeCorner = ResizeCorner.NONE;
    private int resizeAnchorX = 0;
    private int resizeAnchorY = 0;

    // Guideline flags for magnetic snapping visualization
    private boolean snapLeft = false;
    private boolean snapRight = false;
    private boolean snapTop = false;
    private boolean snapBottom = false;
    private boolean snapCenterH = false;
    private boolean snapCenterV = false;

    // Save button dimensions
    private static final int SAVE_BTN_W = 96;
    private static final int SAVE_BTN_H = 26;
    private static final int SAVE_BTN_Y = 12;

    public HudEditorScreen(Screen parentScreen) {
        super(Component.literal("HUD Editor"));
        this.parentScreen = parentScreen;
        this.config = ModConfig.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        this.hudScale = config.getHudScale();
        this.cardWidth = SpotifyHudRenderer.getCardWidth();
        this.cardHeight = SpotifyHudRenderer.getCardHeight(config);
        if (config.isHudShowLyrics() && !config.isHudCompact()) {
            this.cardHeight += 17;
        }

        int scaledW = getScaledWidth();
        int scaledH = getScaledHeight();

        if (config.getHudPosition() == ModConfig.HudPosition.CUSTOM && config.getHudCustomX() >= 0 && config.getHudCustomY() >= 0) {
            this.hudX = config.getHudCustomX();
            this.hudY = config.getHudCustomY();
        } else {
            switch (config.getHudPosition()) {
                case TOP_LEFT -> {
                    this.hudX = 10;
                    this.hudY = 10;
                }
                case BOTTOM_LEFT -> {
                    this.hudX = 10;
                    this.hudY = this.height - scaledH - 10;
                }
                case BOTTOM_RIGHT -> {
                    this.hudX = this.width - scaledW - 10;
                    this.hudY = this.height - scaledH - 10;
                }
                case TOP_RIGHT, OFF, CUSTOM -> {
                    this.hudX = this.width - scaledW - 10;
                    this.hudY = 10;
                }
            }
        }
        clampPosition();
    }

    private int getScaledWidth() {
        return (int) (cardWidth * hudScale);
    }

    private int getScaledHeight() {
        return (int) (cardHeight * hudScale);
    }

    private void clampPosition() {
        int scaledW = getScaledWidth();
        int scaledH = getScaledHeight();
        int snapMargin = 10;
        int threshold = 8;

        // Reset snap indicators
        snapLeft = false;
        snapRight = false;
        snapTop = false;
        snapBottom = false;
        snapCenterH = false;
        snapCenterV = false;

        // Edge magnetic snapping
        if (Math.abs(hudX - snapMargin) < threshold) {
            hudX = snapMargin;
            snapLeft = true;
        } else if (Math.abs(hudX - (this.width - scaledW - snapMargin)) < threshold) {
            hudX = this.width - scaledW - snapMargin;
            snapRight = true;
        }

        if (Math.abs(hudY - snapMargin) < threshold) {
            hudY = snapMargin;
            snapTop = true;
        } else if (Math.abs(hudY - (this.height - scaledH - snapMargin)) < threshold) {
            hudY = this.height - scaledH - snapMargin;
            snapBottom = true;
        }

        // Center magnetic snapping
        int centerScreenX = (this.width - scaledW) / 2;
        if (Math.abs(hudX - centerScreenX) < threshold) {
            hudX = centerScreenX;
            snapCenterH = true;
        }

        int centerScreenY = (this.height - scaledH) / 2;
        if (Math.abs(hudY - centerScreenY) < threshold) {
            hudY = centerScreenY;
            snapCenterV = true;
        }

        // Clamp inside visible bounds
        clampScreenBoundsOnly();
    }

    private void clampScreenBoundsOnly() {
        int scaledW = getScaledWidth();
        int scaledH = getScaledHeight();
        this.hudX = Math.max(2, Math.min(this.width - scaledW - 2, this.hudX));
        this.hudY = Math.max(2, Math.min(this.height - scaledH - 2, this.hudY));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. Soft atmospheric dark dimming overlay
        guiGraphics.fill(0, 0, this.width, this.height, 0x6605050A);

        // 2. Magnetic Guidelines
        int accent = ThemeManager.getAccent();
        int guideColor = ThemeManager.applyAlpha(accent, 0.45f);

        if (snapLeft) {
            guiGraphics.fill(10, 0, 11, this.height, guideColor);
        }
        if (snapRight) {
            guiGraphics.fill(this.width - 10, 0, this.width - 9, this.height, guideColor);
        }
        if (snapTop) {
            guiGraphics.fill(0, 10, this.width, 11, guideColor);
        }
        if (snapBottom) {
            guiGraphics.fill(0, this.height - 10, this.width, this.height - 9, guideColor);
        }
        if (snapCenterH) {
            guiGraphics.fill(this.width / 2, 0, this.width / 2 + 1, this.height, guideColor);
        }
        if (snapCenterV) {
            guiGraphics.fill(0, this.height / 2, this.width, this.height / 2 + 1, guideColor);
        }

        // 3. Render Live HUD Preview Card
        SpotifyHudRenderer.renderHudPreview(guiGraphics, hudX, hudY, hudScale, 1.0f);

        // 4. Editor HUD Bounding Box Outline & Handles
        int scaledW = getScaledWidth();
        int scaledH = getScaledHeight();
        boolean hudHover = isMouseOver(mouseX, mouseY, hudX, hudY, scaledW, scaledH);

        long now = System.currentTimeMillis();
        float pulse = 0.65f + 0.35f * (float) Math.sin((now % 2000) / 2000.0 * Math.PI * 2);
        int outlineColor = (isDragging || activeCorner != ResizeCorner.NONE)
                ? ThemeManager.applyAlpha(accent, 0.95f)
                : (hudHover ? ThemeManager.applyAlpha(accent, pulse) : ThemeManager.applyAlpha(accent, 0.40f));

        // Chamfered / rounded corner bounding highlight
        AaRoundRect.draw(guiGraphics, hudX - 2, hudY - 2, scaledW + 4, scaledH + 4, 8, 0x00000000, outlineColor);

        // Interactive Corner Handles (8x8 px with high-contrast ring & glow)
        int handleHitRadius = 10;
        int accent2 = ThemeManager.getAccent2();

        boolean tlHover = isMouseOver(mouseX, mouseY, hudX - handleHitRadius, hudY - handleHitRadius, handleHitRadius * 2, handleHitRadius * 2);
        boolean trHover = isMouseOver(mouseX, mouseY, hudX + scaledW - handleHitRadius, hudY - handleHitRadius, handleHitRadius * 2, handleHitRadius * 2);
        boolean blHover = isMouseOver(mouseX, mouseY, hudX - handleHitRadius, hudY + scaledH - handleHitRadius, handleHitRadius * 2, handleHitRadius * 2);
        boolean brHover = isMouseOver(mouseX, mouseY, hudX + scaledW - handleHitRadius, hudY + scaledH - handleHitRadius, handleHitRadius * 2, handleHitRadius * 2);

        drawCornerHandle(guiGraphics, hudX, hudY, tlHover || activeCorner == ResizeCorner.TOP_LEFT, accent, accent2);
        drawCornerHandle(guiGraphics, hudX + scaledW, hudY, trHover || activeCorner == ResizeCorner.TOP_RIGHT, accent, accent2);
        drawCornerHandle(guiGraphics, hudX, hudY + scaledH, blHover || activeCorner == ResizeCorner.BOTTOM_LEFT, accent, accent2);
        drawCornerHandle(guiGraphics, hudX + scaledW, hudY + scaledH, brHover || activeCorner == ResizeCorner.BOTTOM_RIGHT, accent, accent2);

        // Sleek floating badge above HUD indicating current scale and drag handle
        String hudBadge = "✥ Drag to Move  |  eSpoty HUD (" + Math.round(hudScale * 100.0f) + "%)";
        int badgeW = this.font.width(hudBadge) + 16;
        int badgeH = 16;
        int badgeX = hudX + (scaledW - badgeW) / 2;
        int badgeY = Math.max(4, hudY - 20);
        int badgeBg = isDragging ? ThemeManager.applyAlpha(accent, 0.45f) : 0xEE0D0D14;
        int badgeBorder = (isDragging || hudHover) ? accent : ThemeManager.applyAlpha(accent, 0.60f);
        AaRoundRect.draw(guiGraphics, badgeX, badgeY, badgeW, badgeH, 5, badgeBg, badgeBorder);
        guiGraphics.drawCenteredString(this.font, hudBadge, badgeX + (badgeW / 2), badgeY + 4, (isDragging || hudHover) ? 0xFFFFFFFF : accent2);

        // 5. Centered Top Control Bar with "Save" button
        renderTopControlBar(guiGraphics, mouseX, mouseY);

        // 6. Bottom Helper Pill: only "Drag to Move"
        renderBottomHelpBar(guiGraphics);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawCornerHandle(GuiGraphics g, int cx, int cy, boolean isHovered, int accent, int accent2) {
        int size = isHovered ? 8 : 6;
        int half = size / 2;
        int x = cx - half;
        int y = cy - half;

        if (isHovered) {
            g.fill(x - 2, y - 2, x + size + 2, y + size + 2, ThemeManager.applyAlpha(accent, 0.45f));
        }

        int color = isHovered ? accent : accent2;
        g.fill(x, y, x + size, y + size, color);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF0D0D14);
        if (isHovered) {
            g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        }
    }

    private void renderTopControlBar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int accent = ThemeManager.getAccent();
        int centerX = this.width / 2;

        int saveBtnW = SAVE_BTN_W;
        int saveBtnH = SAVE_BTN_H;
        int saveBtnX = centerX - (saveBtnW / 2);
        int saveBtnY = SAVE_BTN_Y;

        int auxBtnW = 68;
        int auxBtnH = 24;

        int cancelBtnX = saveBtnX - auxBtnW - 8;
        int cancelBtnY = saveBtnY + 1;

        int resetBtnX = saveBtnX + saveBtnW + 8;
        int resetBtnY = saveBtnY + 1;

        // Sleek floating glass dock background
        int dockW = auxBtnW + 8 + saveBtnW + 8 + auxBtnW + 16;
        int dockH = 34;
        int dockX = centerX - (dockW / 2);
        int dockY = 8;
        AaRoundRect.draw(guiGraphics, dockX, dockY, dockW, dockH, 8, 0xEB0D0D14, ThemeManager.applyAlpha(accent, 0.35f));

        // 1. Cancel Button (Left)
        boolean cancelHover = isMouseOver(mouseX, mouseY, cancelBtnX, cancelBtnY, auxBtnW, auxBtnH);
        int cancelBg = cancelHover ? 0x33FF4444 : 0x14FFFFFF;
        int cancelBorder = cancelHover ? 0x88FF4444 : 0x24FFFFFF;
        AaRoundRect.draw(guiGraphics, cancelBtnX, cancelBtnY, auxBtnW, auxBtnH, 6, cancelBg, cancelBorder);
        guiGraphics.drawCenteredString(this.font, "Cancel", cancelBtnX + (auxBtnW / 2), cancelBtnY + 8, cancelHover ? 0xFFFF7777 : 0xFFCCCCCC);

        // 2. SAVE Button (Center)
        boolean saveHover = isMouseOver(mouseX, mouseY, saveBtnX, saveBtnY, saveBtnW, saveBtnH);
        int saveBg = saveHover ? ThemeManager.getAccent() : ThemeManager.getAccentDim(0.85f);
        int saveBorder = ThemeManager.getAccent();
        AaRoundRect.draw(guiGraphics, saveBtnX, saveBtnY, saveBtnW, saveBtnH, 6, saveBg, saveBorder);

        // Top sheen on Save button
        guiGraphics.fill(saveBtnX + 3, saveBtnY + 1, saveBtnX + saveBtnW - 3, saveBtnY + 2, 0x66FFFFFF);

        // Centered text: exactly "Save"
        int textCol = saveHover ? 0xFF0D0D14 : 0xFF14141E;
        guiGraphics.drawCenteredString(this.font, "Save", saveBtnX + (saveBtnW / 2), saveBtnY + 9, textCol);

        // 3. Reset Button (Right)
        boolean resetHover = isMouseOver(mouseX, mouseY, resetBtnX, resetBtnY, auxBtnW, auxBtnH);
        int resetBg = resetHover ? 0x24FFFFFF : 0x14FFFFFF;
        int resetBorder = resetHover ? 0x55FFFFFF : 0x24FFFFFF;
        AaRoundRect.draw(guiGraphics, resetBtnX, resetBtnY, auxBtnW, auxBtnH, 6, resetBg, resetBorder);
        guiGraphics.drawCenteredString(this.font, "Reset", resetBtnX + (auxBtnW / 2), resetBtnY + 8, resetHover ? 0xFFFFFFFF : 0xFFCCCCCC);
    }

    private void renderBottomHelpBar(GuiGraphics guiGraphics) {
        String infoText = "Click & drag HUD card to move  •  Drag corner handles to resize  •  Arrow keys to nudge";
        int textW = this.font.width(infoText);
        int barW = textW + 24;
        int barH = 20;
        int barX = (this.width - barW) / 2;
        int barY = this.height - 28;

        AaRoundRect.draw(guiGraphics, barX, barY, barW, barH, 6, 0xD00D0D14, 0x28FFFFFF);
        guiGraphics.drawCenteredString(this.font, infoText, barX + (barW / 2), barY + 6, 0xFFDDDDDD);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();

        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }

        int centerX = this.width / 2;
        int saveBtnX = centerX - (SAVE_BTN_W / 2);
        int saveBtnY = SAVE_BTN_Y;

        int auxBtnW = 68;
        int auxBtnH = 24;
        int cancelBtnX = saveBtnX - auxBtnW - 8;
        int cancelBtnY = saveBtnY + 1;
        int resetBtnX = saveBtnX + SAVE_BTN_W + 8;
        int resetBtnY = saveBtnY + 1;

        // 1. Save Button Click
        if (isMouseOver(mx, my, saveBtnX, saveBtnY, SAVE_BTN_W, SAVE_BTN_H)) {
            config.setHudCustomPosition(hudX, hudY);
            config.setHudScale(hudScale);
            config.setHudPosition(ModConfig.HudPosition.CUSTOM);
            config.save();
            playClickSound();

            if (parentScreen instanceof SpotifyScreen spotifyScreen) {
                spotifyScreen.setStatus("HUD layout saved successfully!");
            }
            if (this.minecraft != null) {
                this.minecraft.setScreen(parentScreen);
            }
            return true;
        }

        // 2. Cancel Button Click
        if (isMouseOver(mx, my, cancelBtnX, cancelBtnY, auxBtnW, auxBtnH)) {
            playClickSound();
            if (this.minecraft != null) {
                this.minecraft.setScreen(parentScreen);
            }
            return true;
        }

        // 3. Reset Button Click
        if (isMouseOver(mx, my, resetBtnX, resetBtnY, auxBtnW, auxBtnH)) {
            playClickSound();
            this.hudScale = 1.0f;
            int scaledW = getScaledWidth();
            this.hudX = this.width - scaledW - 10;
            this.hudY = 10;
            clampPosition();
            return true;
        }

        // 4. Corner Handles Click (all 4 corners supported)
        int scaledW = getScaledWidth();
        int scaledH = getScaledHeight();
        int handleHitRadius = 10;

        // Top-Left corner: (hudX, hudY)
        if (isMouseOver(mx, my, hudX - handleHitRadius, hudY - handleHitRadius, handleHitRadius * 2, handleHitRadius * 2)) {
            this.activeCorner = ResizeCorner.TOP_LEFT;
            this.resizeAnchorX = hudX + scaledW;
            this.resizeAnchorY = hudY + scaledH;
            return true;
        }

        // Top-Right corner: (hudX + scaledW, hudY)
        if (isMouseOver(mx, my, hudX + scaledW - handleHitRadius, hudY - handleHitRadius, handleHitRadius * 2, handleHitRadius * 2)) {
            this.activeCorner = ResizeCorner.TOP_RIGHT;
            this.resizeAnchorX = hudX;
            this.resizeAnchorY = hudY + scaledH;
            return true;
        }

        // Bottom-Left corner: (hudX, hudY + scaledH)
        if (isMouseOver(mx, my, hudX - handleHitRadius, hudY + scaledH - handleHitRadius, handleHitRadius * 2, handleHitRadius * 2)) {
            this.activeCorner = ResizeCorner.BOTTOM_LEFT;
            this.resizeAnchorX = hudX + scaledW;
            this.resizeAnchorY = hudY;
            return true;
        }

        // Bottom-Right corner: (hudX + scaledW, hudY + scaledH)
        if (isMouseOver(mx, my, hudX + scaledW - handleHitRadius, hudY + scaledH - handleHitRadius, handleHitRadius * 2, handleHitRadius * 2)) {
            this.activeCorner = ResizeCorner.BOTTOM_RIGHT;
            this.resizeAnchorX = hudX;
            this.resizeAnchorY = hudY;
            return true;
        }

        // 5. HUD Card Drag Click (when clicking anywhere inside the card or its top handle badge)
        String hudBadge = "✥ Drag to Move  |  eSpoty HUD (" + Math.round(hudScale * 100.0f) + "%)";
        int badgeW = this.font.width(hudBadge) + 16;
        int badgeH = 16;
        int badgeX = hudX + (scaledW - badgeW) / 2;
        int badgeY = Math.max(4, hudY - 20);

        if (isMouseOver(mx, my, hudX - 4, hudY - 4, scaledW + 8, scaledH + 8)
                || isMouseOver(mx, my, badgeX, badgeY, badgeW, badgeH)) {
            this.isDragging = true;
            this.dragOffsetX = mx - hudX;
            this.dragOffsetY = my - hudY;
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (activeCorner != ResizeCorner.NONE) {
            int mx = (int) event.x();
            int my = (int) event.y();
            float aspect = (float) cardWidth / (float) cardHeight;
            float currentDist = 0f;

            switch (activeCorner) {
                case TOP_LEFT -> {
                    float distX = (float) (resizeAnchorX - mx);
                    float distY = (float) (resizeAnchorY - my) * aspect;
                    currentDist = (distX + distY) / 2.0f;
                }
                case TOP_RIGHT -> {
                    float distX = (float) (mx - resizeAnchorX);
                    float distY = (float) (resizeAnchorY - my) * aspect;
                    currentDist = (distX + distY) / 2.0f;
                }
                case BOTTOM_LEFT -> {
                    float distX = (float) (resizeAnchorX - mx);
                    float distY = (float) (my - resizeAnchorY) * aspect;
                    currentDist = (distX + distY) / 2.0f;
                }
                case BOTTOM_RIGHT -> {
                    float distX = (float) (mx - resizeAnchorX);
                    float distY = (float) (my - resizeAnchorY) * aspect;
                    currentDist = (distX + distY) / 2.0f;
                }
                case NONE -> {}
            }

            float targetScale = currentDist / (float) cardWidth;
            this.hudScale = Math.max(0.50f, Math.min(1.50f, Math.round(targetScale * 100.0f) / 100.0f));

            int newScaledW = getScaledWidth();
            int newScaledH = getScaledHeight();

            switch (activeCorner) {
                case TOP_LEFT -> {
                    this.hudX = resizeAnchorX - newScaledW;
                    this.hudY = resizeAnchorY - newScaledH;
                }
                case TOP_RIGHT -> {
                    this.hudX = resizeAnchorX;
                    this.hudY = resizeAnchorY - newScaledH;
                }
                case BOTTOM_LEFT -> {
                    this.hudX = resizeAnchorX - newScaledW;
                    this.hudY = resizeAnchorY;
                }
                case BOTTOM_RIGHT -> {
                    this.hudX = resizeAnchorX;
                    this.hudY = resizeAnchorY;
                }
                case NONE -> {}
            }

            clampScreenBoundsOnly();
            return true;
        }

        if (isDragging) {
            this.hudX = (int) event.x() - dragOffsetX;
            this.hudY = (int) event.y() - dragOffsetY;
            clampPosition();
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.isDragging = false;
        this.activeCorner = ResizeCorner.NONE;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();

        // Escape: Cancel and close
        if (keyCode == 256) {
            playClickSound();
            if (this.minecraft != null) {
                this.minecraft.setScreen(parentScreen);
            }
            return true;
        }

        // Enter: Save and close
        if (keyCode == 257 || keyCode == 335) {
            config.setHudCustomPosition(hudX, hudY);
            config.setHudScale(hudScale);
            config.setHudPosition(ModConfig.HudPosition.CUSTOM);
            config.save();
            playClickSound();
            if (parentScreen instanceof SpotifyScreen spotifyScreen) {
                spotifyScreen.setStatus("HUD layout saved successfully!");
            }
            if (this.minecraft != null) {
                this.minecraft.setScreen(parentScreen);
            }
            return true;
        }

        // Arrow keys nudge position
        if (keyCode == 265) { // UP
            this.hudY -= 1;
            clampPosition();
            return true;
        }
        if (keyCode == 264) { // DOWN
            this.hudY += 1;
            clampPosition();
            return true;
        }
        if (keyCode == 263) { // LEFT
            this.hudX -= 1;
            clampPosition();
            return true;
        }
        if (keyCode == 262) { // RIGHT
            this.hudX += 1;
            clampPosition();
            return true;
        }

        return super.keyPressed(event);
    }

    private boolean isMouseOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }
}
