package it.itzcrih.espoty.client;

import it.itzcrih.espoty.ESpotyMod;
import it.itzcrih.espoty.config.ModConfig;
import it.itzcrih.espoty.gui.hud.SpotifyHudRenderer;
import it.itzcrih.espoty.gui.screen.SpotifyScreen;
import it.itzcrih.espoty.spotify.SpotifyAuth;
import it.itzcrih.espoty.spotify.SpotifyProcessDetector;
import it.itzcrih.espoty.spotify.SpotifyStateManager;
import it.itzcrih.espoty.spotify.models.PlaybackState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public class ESpotyModClient implements ClientModInitializer {
    public static final String CATEGORY_ESPOTY = "key.category.espoty";

    private static KeyMapping keyOpenGui;
    private static KeyMapping keyPlayPause;
    private static KeyMapping keyNext;
    private static KeyMapping keyPrev;
    private static KeyMapping keyToggleHud;

    public static KeyMapping getKeyOpenGui() {
        return keyOpenGui;
    }

    @Override
    public void onInitializeClient() {

        ModConfig.getInstance();
        SpotifyStateManager stateManager = SpotifyStateManager.getInstance();

        keyOpenGui = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.espoty.open",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY_ESPOTY
        ));

        keyPlayPause = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.espoty.play_pause",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY_ESPOTY
        ));

        keyNext = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.espoty.next",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY_ESPOTY
        ));

        keyPrev = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.espoty.prev",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY_ESPOTY
        ));

        keyToggleHud = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.espoty.toggle_hud",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY_ESPOTY
        ));

        HudRenderCallback.EVENT.register((matrices, tickDelta) -> SpotifyHudRenderer.render(new GuiGraphics(matrices), tickDelta));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(it.itzcrih.espoty.gui.sky.SkyLyricsRenderer::render);

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof ChatScreen) {
                ScreenMouseEvents.allowMouseClick(screen).register((scr, mx, my, btn) -> {
                    if (btn == 0) {
                        if (SpotifyHudRenderer.handleChatMouseClick(mx, my)) {
                            return false;
                        }
                    }
                    return true;
                });
            }
        });

        it.itzcrih.espoty.gui.widget.MainMenuMiniPlayer.register();

        it.itzcrih.espoty.update.UpdateChecker.getInstance().checkForUpdatesAsync();

        it.itzcrih.espoty.telemetry.TelemetryService.getInstance();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            it.itzcrih.espoty.audio.AudioDuckingManager.getInstance().tick(client);

            it.itzcrih.espoty.update.UpdateChecker.getInstance().checkAndNotifyInChat(client);

            while (keyOpenGui.consumeClick()) {
                client.setScreen(new SpotifyScreen());
            }

            if (client.player == null) return;

            while (keyPlayPause.consumeClick()) {
                stateManager.togglePlayPause();
            }

            while (keyNext.consumeClick()) {
                stateManager.next();
            }

            while (keyPrev.consumeClick()) {
                stateManager.previous();
            }

            while (keyToggleHud.consumeClick()) {
                ModConfig cfg = ModConfig.getInstance();
                cfg.setEnabled(!cfg.isEnabled());
                sendChatFeedback(client, "eSpoty HUD " + (cfg.isEnabled() ? "Enabled" : "Disabled"));
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("espoty")
                    .executes(context -> {
                        Minecraft.getInstance().execute(() -> {
                            Minecraft.getInstance().setScreen(new SpotifyScreen());
                        });
                        return 1;
                    })
                    .then(ClientCommandManager.literal("refresh").executes(context -> {
                        stateManager.fullReload();
                        sendChatFeedback(context.getSource().getClient(), "§aForced full refresh! Spotify bridge and session reloaded.");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("play").executes(context -> {
                        stateManager.play();
                        sendChatFeedback(context.getSource().getClient(), "Spotify playback resumed.");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("pause").executes(context -> {
                        stateManager.pause();
                        sendChatFeedback(context.getSource().getClient(), "Spotify playback paused.");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("next").executes(context -> {
                        stateManager.next();
                        sendChatFeedback(context.getSource().getClient(), "Skipped to next track.");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("prev").executes(context -> {
                        stateManager.previous();
                        sendChatFeedback(context.getSource().getClient(), "Returned to previous track.");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("now").executes(context -> {
                        PlaybackState s = stateManager.getCurrentState();
                        if (s != null) {
                            sendChatFeedback(context.getSource().getClient(), "Now playing: §e" + s.getTrackName() + " §7by §6" + s.getArtistNames());
                        } else {
                            sendChatFeedback(context.getSource().getClient(), "No active playback detected.");
                        }
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("status").executes(context -> {
                        boolean pc = SpotifyProcessDetector.isSpotifyRunning();
                        PlaybackState s = stateManager.getCurrentState();
                        String track = (s != null) ? s.getTrackName() : "None";
                        sendChatFeedback(context.getSource().getClient(), "eSpoty Status: [PC App: " + (pc ? "§aRunning" : "§cClosed") + "§f] [Active Track: §e" + track + "§f]");
                        return 1;
                    }))
            );
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            it.itzcrih.espoty.audio.AudioDuckingManager.getInstance().restore(client);
            stateManager.shutdown();
        });
    }

    private static void sendChatFeedback(Minecraft client, String message) {
        if (client.player != null && ModConfig.getInstance().isShowChatFeedback()) {
            client.player.displayClientMessage(Component.literal("§6[eSpoty] §f" + message), false);
        }
    }
}
