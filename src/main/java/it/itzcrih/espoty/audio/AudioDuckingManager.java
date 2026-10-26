package it.itzcrih.espoty.audio;

import it.itzcrih.espoty.config.ModConfig;
import it.itzcrih.espoty.spotify.SpotifyStateManager;
import it.itzcrih.espoty.spotify.models.PlaybackState;
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
            client.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, 0.0f);
            float duckedMaster = (float) Math.max(0.05, baseMasterVolume * config.getDuckingMultiplier());
            client.getSoundManager().updateCategoryVolume(SoundSource.MASTER, duckedMaster);
        } else if (isDucked) {
            restore(client);
        }
    }

    public void restore(Minecraft client) {
        if (client == null || client.getSoundManager() == null) {
            isDucked = false;
            return;
        }

        try {
            client.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, (float) baseMusicVolume);
            client.getSoundManager().updateCategoryVolume(SoundSource.MASTER, (float) baseMasterVolume);
        } catch (Exception ignored) {}
        this.isDucked = false;
    }

    public boolean isDucked() {
        return isDucked;
    }
}
