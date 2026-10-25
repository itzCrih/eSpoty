package com.espoty.audio;

import com.espoty.config.ModConfig;
import com.espoty.spotify.SpotifyStateManager;
import com.espoty.spotify.models.PlaybackState;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

public class AudioDuckingManager {
    private static AudioDuckingManager INSTANCE;

    private boolean isDucked = false;
    private double baseMusicVolume = 1.0;
    private double baseMasterVolume = 1.0;

    public static synchronized AudioDuckingManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AudioDuckingManager();
        }
        return INSTANCE;
    }

    private AudioDuckingManager() {}

    public void tick(Minecraft client) {
        if (client == null || client.options == null || client.getSoundManager() == null) {
            return;
        }

        ModConfig config = ModConfig.getInstance();
        if (!config.isAudioDuckingEnabled()) {
            if (isDucked) {
                restore(client);
            }
            return;
        }

        SpotifyStateManager stateManager = SpotifyStateManager.getInstance();
        PlaybackState state = stateManager.getCurrentState();
        boolean shouldDuck = stateManager.isSpotifyRunning() && state != null && state.isPlaying();

        if (shouldDuck) {
            // Keep Minecraft music stopped and muted completely while Spotify plays
            try {
                client.getSoundManager().stop(null, SoundSource.MUSIC);
            } catch (Exception ignored) {}

            if (!isDucked) {
                try {
                    this.baseMusicVolume = client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get();
                    this.baseMasterVolume = client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get();
                } catch (Exception e) {
                    this.baseMusicVolume = 1.0;
                    this.baseMasterVolume = 1.0;
                }
                this.isDucked = true;
            }

            // Mute Minecraft music and duck master sound
            setVolume(client, SoundSource.MUSIC, 0.0f);
            float duckedMaster = (float) Math.max(0.05, baseMasterVolume * config.getDuckingMultiplier());
            setVolume(client, SoundSource.MASTER, duckedMaster);
        } else if (isDucked) {
            restore(client);
        }
    }

    public void restore(Minecraft client) {
        if (client == null || client.getSoundManager() == null) {
            isDucked = false;
            return;
        }

        setVolume(client, SoundSource.MUSIC, (float) baseMusicVolume);
        setVolume(client, SoundSource.MASTER, (float) baseMasterVolume);
        this.isDucked = false;
    }

    private void setVolume(Minecraft client, SoundSource source, float vol) {
        if (client == null || client.getSoundManager() == null) return;
        try {
            java.lang.reflect.Method m = client.getSoundManager().getClass().getMethod("updateCategoryVolume", SoundSource.class, float.class);
            m.invoke(client.getSoundManager(), source, vol);
            return;
        } catch (Throwable ignored) {}
        try {
            java.lang.reflect.Method m = client.getSoundManager().getClass().getMethod("updateSourceVolume", SoundSource.class, float.class);
            m.invoke(client.getSoundManager(), source, vol);
        } catch (Throwable ignored) {}
    }

    public boolean isDucked() {
        return isDucked;
    }
}
