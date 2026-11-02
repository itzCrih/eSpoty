package it.itzcrih.espoty.gui.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Universal high-definition UI rendering utilities:
 * - Anti-aliased high-resolution tinted icon blitting
 * - Glowing text & component rendering with ambient bloom auras
 */
public final class RenderUtils {

    private RenderUtils() {}

    /**
     * Renders a high-resolution texture icon with precise ARGB tinting and alpha blending.
     */
    public static void drawTintedIcon(GuiGraphics g, Identifier texture, int x, int y, int size, int color) {
        drawTintedIcon(g, texture, x, y, size, size, color);
    }

    public static void drawTintedIcon(GuiGraphics g, Identifier texture, int x, int y, int width, int height, int color) {
        if ((color & 0xFF000000) == 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height, width, height, color);
    }

    /**
     * Renders text wrapped in an ambient neon/OLED bloom glow in the theme accent color.
     */
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
