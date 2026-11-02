import os
import re
import shutil

COMPAT_FILES = [
    'it/itzcrih/espoty/client/ESpotyModClient.java',
    'it/itzcrih/espoty/gui/hud/SpotifyHudRenderer.java',
    'it/itzcrih/espoty/gui/screen/SpotifyScreen.java',
    'it/itzcrih/espoty/gui/screen/HudEditorScreen.java',
    'it/itzcrih/espoty/gui/sky/SkyLyricsRenderer.java',
    'it/itzcrih/espoty/gui/texture/AlbumArtManager.java',
    'it/itzcrih/espoty/gui/widget/MainMenuMiniPlayer.java',
    'it/itzcrih/espoty/config/ModConfig.java',
    'it/itzcrih/espoty/gui/render/RenderUtils.java',
    'it/itzcrih/espoty/audio/AudioDuckingManager.java',
]

def make_stubs():
    stub_dir = 'src-stub/com/terraformersmc/modmenu/api'
    os.makedirs(stub_dir, exist_ok=True)
    with open(os.path.join(stub_dir, 'ModMenuApi.java'), 'w', encoding='utf-8') as f:
        f.write('''package com.terraformersmc.modmenu.api;

public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return null;
    }
}
''')
    with open(os.path.join(stub_dir, 'ConfigScreenFactory.java'), 'w', encoding='utf-8') as f:
        f.write('''package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

@FunctionalInterface
public interface ConfigScreenFactory<T extends Screen> {
    T create(Screen parent);
}
''')

def ensure_modern():
    os.makedirs('src-compat/modern', exist_ok=True)
    for rel in COMPAT_FILES:
        src = os.path.join('src/main/java', rel)
        dst = os.path.join('src-compat/modern', rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        if os.path.exists(src):
            shutil.copy2(src, dst)

def build_mc121():
    os.makedirs('src-compat/mc121', exist_ok=True)
    for rel in COMPAT_FILES:
        src = os.path.join('src-compat/modern', rel)
        with open(src, 'r', encoding='utf-8') as f:
            c = f.read()

        c = c.replace('import net.minecraft.resources.Identifier;', 'import net.minecraft.resources.ResourceLocation;')
        c = c.replace('Identifier.fromNamespaceAndPath', 'ResourceLocation.fromNamespaceAndPath')
        c = re.sub(r'\bIdentifier\b', 'ResourceLocation', c)

        c = c.replace('.pushMatrix()', '.pushPose()')
        c = c.replace('.popMatrix()', '.popPose()')

        if 'AudioDuckingManager' in rel:
            c = c.replace('updateCategoryVolume', 'updateSourceVolume')

        if 'AlbumArtManager' in rel:
            c = c.replace('new DynamicTexture(() -> "espoty_album", image);', 'new DynamicTexture(image);')

        if 'ModConfig' in rel:
            c = c.replace('import net.minecraft.network.chat.FontDescription;\n', '')
            c = c.replace('new FontDescription.Resource(fontId)', 'fontId')

        if 'RenderUtils' in rel:
            c = c.replace('import net.minecraft.client.renderer.RenderPipelines;\n', '')
            c = re.sub(
                r'g\.blit\(RenderPipelines\.GUI_TEXTURED,\s*texture,\s*x,\s*y,\s*0\.0f,\s*0\.0f,\s*width,\s*height,\s*width,\s*height,\s*color\);',
                '''float colA = ((color >> 24) & 0xFF) / 255.0f;
        float colR = ((color >> 16) & 0xFF) / 255.0f;
        float colG = ((color >> 8) & 0xFF) / 255.0f;
        float colB = (color & 0xFF) / 255.0f;
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(colR, colG, colB, colA);
        g.blit(texture, x, y, 0.0f, 0.0f, width, height, width, height);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);''',
                c
            )

        if 'ESpotyModClient' in rel:
            c = c.replace('import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;', 'import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;\nimport com.mojang.blaze3d.platform.InputConstants;')
            c = re.sub(r'public static final KeyMapping\.Category CATEGORY_ESPOTY =[^;]+;', 'public static final String CATEGORY_ESPOTY = "key.category.espoty";', c)
            c = c.replace('GLFW.GLFW_KEY_K,\n                CATEGORY_ESPOTY', 'InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY_ESPOTY')
            c = c.replace('GLFW.GLFW_KEY_UNKNOWN,\n                CATEGORY_ESPOTY', 'InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY_ESPOTY')
            c = c.replace('WorldRenderEvents.END_MAIN', 'WorldRenderEvents.AFTER_TRANSLUCENT')
            c = c.replace('(scr, event) -> {\n                    if (event.button() == 0) {\n                        if (SpotifyHudRenderer.handleChatMouseClick(event.x(), event.y())) {\n                            return false;\n                        }\n                    }\n                    return true;\n                }', '(scr, mx, my, btn) -> {\n                    if (btn == 0) {\n                        if (SpotifyHudRenderer.handleChatMouseClick(mx, my)) {\n                            return false;\n                        }\n                    }\n                    return true;\n                }')

        if 'SpotifyHudRenderer' in rel:
            c = c.replace('guiGraphics.pose().translate((float) screenX, (float) screenY);', 'guiGraphics.pose().translate((float) screenX, (float) screenY, 0.0f);')
            c = c.replace('guiGraphics.pose().scale(scale, scale);', 'guiGraphics.pose().scale(scale, scale, 1.0f);')
            c = c.replace('.scale(0.72f, 0.72f);', '.scale(0.72f, 0.72f, 1.0f);')
            c = c.replace('guiGraphics.blit(texture, x, y, x + size, y + size, 0.0f, 1.0f, 0.0f, 1.0f);', 'guiGraphics.blit(texture, x, y, 0, 0, size, size, size, size);')

        if 'MainMenuMiniPlayer' in rel:
            c = c.replace('import net.minecraft.client.input.MouseButtonEvent;\n', '')
            c = c.replace('ScreenMouseEvents.allowMouseClick(screen).register(MainMenuMiniPlayer::handleMouseClick);', 'ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, btn) -> handleMouseClick(s, mx, my, btn));')
            c = re.sub(r'private static boolean handleMouseClick\(Screen screen, MouseButtonEvent event\) \{', 'private static boolean handleMouseClick(Screen screen, double mouseX, double mouseY, int button) {\n        int mx = (int) mouseX;\n        int my = (int) mouseY;\n        if (button != 0) return true;', c)
            c = c.replace('if (event.button() != 0) return true; // Only handle left clicks\n\n        int mx = (int) event.x();\n        int my = (int) event.y();', '')
            c = c.replace('guiGraphics.blit(texture, artX, artY, artX + artSize, artY + artSize, 0.0f, 1.0f, 0.0f, 1.0f);', 'guiGraphics.blit(texture, artX, artY, 0, 0, artSize, artSize, artSize, artSize);')

        if 'SpotifyScreen' in rel or 'HudEditorScreen' in rel:
            c = c.replace('import net.minecraft.client.input.KeyEvent;\n', '')
            c = c.replace('import net.minecraft.client.input.MouseButtonEvent;\n', '')
            c = c.replace('guiGraphics.pose().translate((float) cx, (float) cy);', 'guiGraphics.pose().translate((float) cx, (float) cy, 0.0f);')
            c = c.replace('guiGraphics.pose().scale(animScale, animScale);', 'guiGraphics.pose().scale(animScale, animScale, 1.0f);')
            c = c.replace('guiGraphics.pose().translate((float) -cx, (float) -cy);', 'guiGraphics.pose().translate((float) -cx, (float) -cy, 0.0f);')
            c = c.replace('guiGraphics.pose().translate(centerX, centerY);', 'guiGraphics.pose().translate(centerX, centerY, 0.0f);')
            c = c.replace('guiGraphics.pose().translate(-centerX, -centerY);', 'guiGraphics.pose().translate(-centerX, -centerY, 0.0f);')
            c = c.replace('guiGraphics.pose().translate(0, tabSlideY);', 'guiGraphics.pose().translate(0.0f, tabSlideY, 0.0f);')
            c = c.replace('guiGraphics.pose().translate(x + 40, y + 19);', 'guiGraphics.pose().translate((float)(x + 40), (float)(y + 19), 0.0f);')
            c = c.replace('guiGraphics.pose().scale(scale, scale);', 'guiGraphics.pose().scale(scale, scale, 1.0f);')
            c = c.replace('guiGraphics.pose().scale(1.35f, 1.35f);', 'guiGraphics.pose().scale(1.35f, 1.35f, 1.0f);')
            c = c.replace('net.minecraft.util.Util.getPlatform()', 'net.minecraft.Util.getPlatform()')
            c = c.replace('guiGraphics.blit(LOGO_ICON, logoX, logoY, logoX + logoSize, logoY + logoSize, 0f, 1f, 0f, 1f);', 'guiGraphics.blit(LOGO_ICON, logoX, logoY, 0, 0, logoSize, logoSize, logoSize, logoSize);')
            c = c.replace('guiGraphics.blit(texture, artX + 1, artY + 1, artX + artSize - 1, artY + artSize - 1, 0.0f, 1.0f, 0.0f, 1.0f);', 'guiGraphics.blit(texture, artX + 1, artY + 1, 0, 0, artSize - 2, artSize - 2, artSize - 2, artSize - 2);')
            c = c.replace('guiGraphics.blit(texture, artX, artY, artX + artSize, artY + artSize, 0.0f, 1.0f, 0.0f, 1.0f);', 'guiGraphics.blit(texture, artX, artY, 0, 0, artSize, artSize, artSize, artSize);')
            c = c.replace('guiGraphics.blit(texture, px, py, px + ps, py + ps, 0.0f, 1.0f, 0.0f, 1.0f);', 'guiGraphics.blit(texture, px, py, 0, 0, ps, ps, ps, ps);')

            c = re.sub(r'public boolean mouseClicked\(MouseButtonEvent event, boolean doubleClick\) \{', 'public boolean mouseClicked(double mouseX, double mouseY, int button) {', c)
            c = c.replace('int mx = (int) event.x();\n        int my = (int) event.y();', 'int mx = (int) mouseX;\n        int my = (int) mouseY;')
            c = c.replace('if (event.button() != 0) {', 'if (button != 0) {')
            c = c.replace('return super.mouseClicked(event, doubleClick);', 'return super.mouseClicked(mouseX, mouseY, button);')

            c = re.sub(r'public boolean mouseDragged\(MouseButtonEvent event, double dragX, double dragY\) \{', 'public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {', c)
            c = c.replace('int mx = (int) event.x();', 'int mx = (int) mouseX;')
            c = c.replace('int my = (int) event.y();', 'int my = (int) mouseY;')
            c = c.replace('(int) event.x()', '(int) mouseX')
            c = c.replace('(int) event.y()', '(int) mouseY')
            c = c.replace('return super.mouseDragged(event, dragX, dragY);', 'return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);')

            c = re.sub(r'public boolean mouseReleased\(MouseButtonEvent event\) \{', 'public boolean mouseReleased(double mouseX, double mouseY, int button) {', c)
            c = c.replace('return super.mouseReleased(event);', 'return super.mouseReleased(mouseX, mouseY, button);')

            c = re.sub(r'public boolean keyPressed\((net\.minecraft\.client\.input\.)?KeyEvent event\) \{', 'public boolean keyPressed(int keyCode, int scanCode, int modifiers) {', c)
            c = c.replace('int keyCode = event.key();', '')
            c = c.replace('return super.keyPressed(event);', 'return super.keyPressed(keyCode, scanCode, modifiers);')

        if 'SkyLyricsRenderer' in rel:
            c = c.replace('import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;', 'import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;')
            c = c.replace('import net.minecraft.network.chat.FontDescription;\n', '')
            c = c.replace('import org.joml.Vector3fc;\n', '')
            c = c.replace('Vector3fc', 'org.joml.Vector3f')
            c = c.replace('camera.forwardVector()', 'camera.getLookVector()')
            c = c.replace('camera.xRot()', 'camera.getXRot()')
            c = c.replace('camera.yRot()', 'camera.getYRot()')
            c = c.replace('camera.position()', 'camera.getPosition()')
            c = c.replace('context.matrices()', 'context.matrixStack()')
            c = c.replace('new FontDescription.Resource(MODERN_FONT)', 'MODERN_FONT')
            c = c.replace('new FontDescription.Resource(COOLFONT)', 'COOLFONT')
            c = c.replace('new FontDescription.Resource(fontId)', 'fontId')

        dst = os.path.join('src-compat/mc121', rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, 'w', encoding='utf-8') as f:
            f.write(c)

def build_mc120():
    os.makedirs('src-compat/mc120', exist_ok=True)
    for rel in COMPAT_FILES:
        src = os.path.join('src-compat/mc121', rel)
        with open(src, 'r', encoding='utf-8') as f:
            c = f.read()

        c = re.sub(r'ResourceLocation\.fromNamespaceAndPath\(([^,]+),\s*([^)]+)\)', r'new ResourceLocation(\1, \2)', c)

        if 'SpotifyHudRenderer' in rel:
            c = c.replace('render(GuiGraphics guiGraphics, DeltaTracker deltaTracker)', 'render(GuiGraphics guiGraphics, float tickDelta)')
            c = c.replace('net.minecraft.client.DeltaTracker', 'net.minecraft.client.gui.GuiGraphics')

        if 'SkyLyricsRenderer' in rel:
            c = c.replace('import com.mojang.blaze3d.vertex.ByteBufferBuilder;\n', '')
            c = c.replace('private static final ByteBufferBuilder BYTE_BUFFER_BUILDER = new ByteBufferBuilder(256 * 1024);\n', '')
            c = c.replace('private static final MultiBufferSource.BufferSource DEDICATED_BUFFER_SOURCE = MultiBufferSource.immediate(BYTE_BUFFER_BUILDER);',
                          'private static MultiBufferSource.BufferSource getBufferSource() { return Minecraft.getInstance().renderBuffers().bufferSource(); }')
            c = c.replace('DEDICATED_BUFFER_SOURCE.endBatch();', 'getBufferSource().endBatch();')
            c = c.replace('DEDICATED_BUFFER_SOURCE', 'getBufferSource()')

        if 'SpotifyScreen' in rel:
            c = c.replace('public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {',
                          'public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {\n        double horizontalAmount = 0.0;')
            c = c.replace('return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);',
                          'return super.mouseScrolled(mouseX, mouseY, verticalAmount);')

        if 'HudEditorScreen' in rel:
            c = c.replace('public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {',
                          'public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {')
            c = c.replace('return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);',
                          'return super.mouseScrolled(mouseX, mouseY, verticalAmount);')

        dst = os.path.join('src-compat/mc120', rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, 'w', encoding='utf-8') as f:
            f.write(c)

def build_mc1194():
    os.makedirs('src-compat/mc1194', exist_ok=True)
    for rel in COMPAT_FILES:
        src = os.path.join('src-compat/mc120', rel)
        with open(src, 'r', encoding='utf-8') as f:
            c = f.read()

        if 'ESpotyModClient' in rel:
            c = c.replace('import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;',
                          'import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;\nimport net.minecraft.client.gui.GuiGraphics;')
            c = c.replace('HudRenderCallback.EVENT.register(SpotifyHudRenderer::render);',
                          'HudRenderCallback.EVENT.register((matrices, tickDelta) -> SpotifyHudRenderer.render(new GuiGraphics(matrices), tickDelta));')

        if 'MainMenuMiniPlayer' in rel:
            c = c.replace('ScreenEvents.afterRender(screen).register(MainMenuMiniPlayer::render);',
                          'ScreenEvents.afterRender(screen).register((s, poseStack, mouseX, mouseY, tickDelta) -> render(s, new GuiGraphics(poseStack), mouseX, mouseY, tickDelta));')

        if 'SpotifyScreen' in rel:
            c = c.replace('@Override\n    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {',
                          '@Override public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) { render(new GuiGraphics(poseStack), mouseX, mouseY, partialTick); }\n\n    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {')

        if 'HudEditorScreen' in rel:
            c = c.replace('super.render(guiGraphics, mouseX, mouseY, partialTick);',
                          'super.render(guiGraphics.pose(), mouseX, mouseY, partialTick);')
            c = c.replace('@Override\n    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {',
                          '@Override public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) { render(new GuiGraphics(poseStack), mouseX, mouseY, partialTick); }\n\n    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {')

        dst = os.path.join('src-compat/mc1194', rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, 'w', encoding='utf-8') as f:
            f.write(c)

    gui_graphics_shim = '''package net.minecraft.client.gui;

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

    public int drawString(Font font, Component text, int x, int y, int color) {
        return drawString(font, text, x, y, color, false);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        return drawString(font, text, x, y, color, false);
    }

    public int drawString(Font font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, false);
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
'''
    shim_path = 'src-compat/mc1194/net/minecraft/client/gui/GuiGraphics.java'
    os.makedirs(os.path.dirname(shim_path), exist_ok=True)
    with open(shim_path, 'w', encoding='utf-8') as f:
        f.write(gui_graphics_shim)

if __name__ == '__main__':
    make_stubs()
    ensure_modern()
    build_mc121()
    build_mc120()
    build_mc1194()
    print('All compat families and stubs generated successfully!')
