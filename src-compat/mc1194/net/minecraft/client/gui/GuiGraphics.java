package net.minecraft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public class GuiGraphics {
    private final PoseStack pose;

    public GuiGraphics(PoseStack pose) {
        this.pose = pose;
    }

    public PoseStack pose() {
        return pose;
    }

    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        GuiComponent.fill(pose, minX, minY, maxX, maxY, color);
    }

    public void fillGradient(int startX, int startY, int endX, int endY, int colorStart, int colorEnd) {
        GuiComponent.fillGradient(pose, startX, startY, endX, endY, colorStart, colorEnd);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean dropShadow) {
        if (dropShadow) {
            return font.drawShadow(pose, text, (float) x, (float) y, color);
        } else {
            return font.draw(pose, text, (float) x, (float) y, color);
        }
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow) {
        if (dropShadow) {
            return font.drawShadow(pose, text, (float) x, (float) y, color);
        } else {
            return font.draw(pose, text, (float) x, (float) y, color);
        }
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
        if (dropShadow) {
            return font.drawShadow(pose, text, (float) x, (float) y, color);
        } else {
            return font.draw(pose, text, (float) x, (float) y, color);
        }
    }

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        GuiComponent.drawCenteredString(pose, font, text, x, y, color);
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        GuiComponent.drawCenteredString(pose, font, text, x, y, color);
    }

    public void blit(ResourceLocation atlasLocation, int x, int y, float uOffset, float vOffset, int uWidth, int vHeight, int textureWidth, int textureHeight) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, atlasLocation);
        GuiComponent.blit(pose, x, y, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
    }

    public void blit(ResourceLocation atlasLocation, int x, int y, int uOffset, int vOffset, int uWidth, int vHeight) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, atlasLocation);
        GuiComponent.blit(pose, x, y, uOffset, vOffset, uWidth, vHeight, 256, 256);
    }

    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        int windowHeight = mc.getWindow().getHeight();
        int x = (int) (minX * scale);
        int y = (int) (windowHeight - maxY * scale);
        int w = (int) ((maxX - minX) * scale);
        int h = (int) ((maxY - minY) * scale);
        RenderSystem.enableScissor(Math.max(0, x), Math.max(0, y), Math.max(0, w), Math.max(0, h));
    }

    public void disableScissor() {
        RenderSystem.disableScissor();
    }
}
