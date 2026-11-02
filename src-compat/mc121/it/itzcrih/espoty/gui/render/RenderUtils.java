package it.itzcrih.espoty.gui.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class RenderUtils {

    private static final ResourceLocation ICON_CIRCLE_FILL = ResourceLocation.fromNamespaceAndPath("espoty", "textures/ui/circle_fill.png");
    private static final ResourceLocation ICON_CIRCLE_RING = ResourceLocation.fromNamespaceAndPath("espoty", "textures/ui/circle_ring.png");

    private RenderUtils() {}

    public static void drawSmoothCircle(GuiGraphics g, int x, int y, int size, int fillColor, int borderColor) {
        drawSmoothCircle(g, x, y, size, size, fillColor, borderColor);
    }

    public static void drawSmoothCircle(GuiGraphics g, int x, int y, int width, int height, int fillColor, int borderColor) {
        if ((fillColor & 0xFF000000) != 0) {
            drawTintedIcon(g, ICON_CIRCLE_FILL, x, y, width, height, fillColor);
        }
        if ((borderColor & 0xFF000000) != 0) {
            drawTintedIcon(g, ICON_CIRCLE_RING, x, y, width, height, borderColor);
        }
    }

    public static void drawTintedIcon(GuiGraphics g, ResourceLocation texture, int x, int y, int size, int color) {
        drawTintedIcon(g, texture, x, y, size, size, color);
    }

    public static void drawTintedIcon(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height, int color) {
        if ((color & 0xFF000000) == 0) return;
        float colA = ((color >> 24) & 0xFF) / 255.0f;
        float colR = ((color >> 16) & 0xFF) / 255.0f;
        float colG = ((color >> 8) & 0xFF) / 255.0f;
        float colB = (color & 0xFF) / 255.0f;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(colR, colG, colB, colA);
        g.blit(texture, x, y, 0.0f, 0.0f, width, height, width, height);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawGlowText(GuiGraphics g, Font font, String text, int x, int y, int textColor, int glowColor, float elemAlpha) {
        if (elemAlpha > 0.05f) {
            int glowRgb = glowColor & 0x00FFFFFF;
            int glowAlpha = (int) (elemAlpha * 75.0f);
            if (glowAlpha > 0) {
                int glowArgb = (glowAlpha << 24) | glowRgb;
                g.drawString(font, text, x - 1, y, glowArgb, false);
                g.drawString(font, text, x + 1, y, glowArgb, false);
                g.drawString(font, text, x, y - 1, glowArgb, false);
                g.drawString(font, text, x, y + 1, glowArgb, false);
            }
        }
        g.drawString(font, text, x, y, textColor, true);
    }

    public static void drawGlowCenteredText(GuiGraphics g, Font font, String text, int cx, int y, int textColor, int glowColor, float elemAlpha) {
        int x = cx - (font.width(text) / 2);
        drawGlowText(g, font, text, x, y, textColor, glowColor, elemAlpha);
    }

    public static void drawGlowComponent(GuiGraphics g, Font font, Component comp, int x, int y, int textColor, int glowColor, float elemAlpha) {
        if (elemAlpha > 0.05f) {
            int glowRgb = glowColor & 0x00FFFFFF;
            int glowAlpha = (int) (elemAlpha * 75.0f);
            if (glowAlpha > 0) {
                int glowArgb = (glowAlpha << 24) | glowRgb;
                g.drawString(font, comp, x - 1, y, glowArgb, false);
                g.drawString(font, comp, x + 1, y, glowArgb, false);
                g.drawString(font, comp, x, y - 1, glowArgb, false);
                g.drawString(font, comp, x, y + 1, glowArgb, false);
            }
        }
        g.drawString(font, comp, x, y, textColor, true);
    }
}
