package com.espoty.spotify;

import com.espoty.ESpotyMod;
import com.espoty.gui.texture.AlbumArtManager;
import com.espoty.lyrics.LyricsManager;
import com.espoty.spotify.models.PlaybackState;
import com.espoty.spotify.models.QueueItem;
import com.espoty.spotify.models.QueueResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class SpotifyStateManager {
    private static SpotifyStateManager INSTANCE;

    private final SpotifyClient client;
    private final SpotifyAuth auth;
    private final ScheduledExecutorService scheduler;
    private final WindowsMediaBridge windowsBridge;

    private PlaybackState currentState = null;
    private long stateFetchTimestamp = 0;
    private QueueResponse currentQueue = null;

    private boolean isSpotifyRunning = false;
    private String currentArtworkUrl = "";
    private String lastTrackKey = "";
    private long lastTrackChangeTime = 0;

    private final List<Consumer<PlaybackState>> trackChangeListeners = new ArrayList<>();

    public static synchronized SpotifyStateManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SpotifyStateManager();
        }
        return INSTANCE;
    }

    private SpotifyStateManager() {
        this.client = SpotifyClient.getInstance();
        this.auth = SpotifyAuth.getInstance();
        this.windowsBridge = WindowsMediaBridge.getInstance();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "eSpoty-State-Worker");
            t.setDaemon(true);
            return t;
        });

        // Launch bi-directional Windows SMTC Bridge
        windowsBridge.start();

        // Optional Web API polling if user authorized
        startWebPolling();
    }

    private void startWebPolling() {
        scheduler.scheduleWithFixedDelay(this::pollWebApi, 2000, 2000, TimeUnit.MILLISECONDS);
    }

    private void pollWebApi() {
        if (!auth.isAuthenticated()) return;

        try {
            client.fetchPlaybackState().thenAccept(state -> {
                if (state != null) {
                    this.stateFetchTimestamp = System.currentTimeMillis();
                    this.isSpotifyRunning = true;
                    boolean changed = !state.getTrackId().equals(this.lastTrackKey);
                    this.currentState = state;

                    if (changed && !state.getTrackId().isEmpty()) {
                        this.lastTrackKey = state.getTrackId();
                        this.lastTrackChangeTime = System.currentTimeMillis();
                        this.currentArtworkUrl = state.getAlbumArtUrl();

                        LyricsManager.getInstance().updateForTrack(state);
                        if (!state.getAlbumArtUrl().isEmpty()) {
                            AlbumArtManager.getInstance().getAlbumTexture(state.getAlbumArtUrl());
                        }

                        notifyTrackChange(state);
                    }
                }
            }).exceptionally(ex -> null);
        } catch (Exception ignored) {}
    }

    /**
     * Called in real-time by WindowsMediaBridge via TCP socket.
     */
    public synchronized void updateLocalState(boolean running, String title, String artist, String album,
                                             long pos, long dur, boolean playing, boolean shuffle, String repeat,
                                             int volumePercent, boolean canPlayPause, boolean canNext, boolean canPrev,
                                             boolean canShuffle, boolean canRepeat, boolean canSeek) {
        this.isSpotifyRunning = running;

        if (!running || title == null || title.isEmpty()) {
            if (!auth.isAuthenticated()) {
                this.currentState = null;
            }
            return;
        }

        String trackKey = title + " - " + artist;
        boolean changed = !trackKey.equals(this.lastTrackKey);

        if (changed) {
            this.lastTrackKey = trackKey;
            this.lastTrackChangeTime = System.currentTimeMillis();
            this.currentArtworkUrl = "";

            // Automatically query 600x600 HD artwork via iTunes Public API
            ArtworkFinder.getInstance().findArtworkUrl(title, artist).thenAccept(artUrl -> {
                synchronized (SpotifyStateManager.this) {
                    this.currentArtworkUrl = artUrl;
                    if (!artUrl.isEmpty()) {
                        AlbumArtManager.getInstance().getAlbumTexture(artUrl);
                    }
                    if (this.currentState != null && this.lastTrackKey.equals(trackKey)) {
                        this.currentState = new PlaybackState(
                                this.currentState.getTrackId(),
                                this.currentState.getTrackName(),
                                this.currentState.getArtistNames(),
                                this.currentState.getAlbumName(),
                                artUrl,
                                this.currentState.getDurationMs(),
                                this.currentState.getProgressMs(),
                                this.currentState.isPlaying(),
                                this.currentState.isShuffleState(),
                                this.currentState.getRepeatState(),
                                this.currentState.getVolumePercent(),
                                this.currentState.getDeviceName(),
                                this.currentState.getDeviceType(),
                                this.currentState.canPlayPause(),
                                this.currentState.canNext(),
                                this.currentState.canPrev(),
                                this.currentState.canShuffle(),
                                this.currentState.canRepeat(),
                                this.currentState.canSeek()
                        );
                    }
                }
            });
        }

        long currentInterpolated = getInterpolatedProgressMs();
        boolean playingChanged = (this.currentState == null || this.currentState.isPlaying() != playing);
        boolean outOfSync = Math.abs(currentInterpolated - pos) > 800;

        long effectiveProgress;
        if (changed || playingChanged || outOfSync || !playing) {
            this.stateFetchTimestamp = System.currentTimeMillis();
            effectiveProgress = pos;
        } else {
            // Retain client-side sub-frame interpolation for buttery smooth 60 FPS HUD and lyrics
            effectiveProgress = currentInterpolated;
        }

        this.currentState = new PlaybackState(
                "local_" + trackKey.hashCode(),
                title,
                artist,
                album,
                this.currentArtworkUrl,
                dur,
                effectiveProgress,
                playing,
                shuffle,
                repeat,
                volumePercent,
                "Spotify Desktop (PC)",
                "Computer",
                canPlayPause,
                canNext,
                canPrev,
                canShuffle,
                canRepeat,
                canSeek
        );

        if (changed) {
            LyricsManager.getInstance().updateForTrack(this.currentState);
            notifyTrackChange(this.currentState);
        }
    }

    public synchronized void addTrackChangeListener(Consumer<PlaybackState> listener) {
        trackChangeListeners.add(listener);
    }

    private synchronized void notifyTrackChange(PlaybackState state) {
        for (Consumer<PlaybackState> listener : trackChangeListeners) {
            try {
                listener.accept(state);
            } catch (Exception e) {
                ESpotyMod.LOGGER.error("Track change listener error", e);
            }
        }
    }

    public PlaybackState getCurrentState() {
        return currentState;
    }

    public boolean isSpotifyRunning() {
        return isSpotifyRunning;
    }

    public long getLastTrackChangeTime() {
        return lastTrackChangeTime;
    }

    /**
     * Calculates client-interpolated playback progress for smooth 60 FPS HUD and lyrics.
     */
    public long getInterpolatedProgressMs() {
        PlaybackState s = currentState;
        if (s == null) return 0;
        if (!s.isPlaying()) return s.getProgressMs();

        long elapsed = System.currentTimeMillis() - stateFetchTimestamp;
        long progress = s.getProgressMs() + elapsed;
        return Math.min(s.getDurationMs(), Math.max(0, progress));
    }

    public void forceRefreshState() {
        windowsBridge.sendCommand("refresh");
        AlbumArtManager.getInstance().clearCache();
        if (auth.isAuthenticated()) {
            CompletableFuture.runAsync(this::pollWebApi);
        }
    }

    public void fullReload() {
        this.currentState = null;
        this.lastTrackKey = "";
        this.currentArtworkUrl = "";
        AlbumArtManager.getInstance().clearCache();
        windowsBridge.restart();
    }

    public void refreshQueue(Consumer<QueueResponse> callback) {
        if (auth.isAuthenticated()) {
            client.fetchQueue().thenAccept(queue -> {
                this.currentQueue = queue;
                if (callback != null) callback.accept(queue);
            });
        } else {
            PlaybackState s = currentState;
            if (s != null) {
                QueueItem cur = new QueueItem(s.getTrackId(), s.getTrackName(), s.getArtistNames(), s.getAlbumName(), s.getAlbumArtUrl(), s.getDurationMs());
                this.currentQueue = new QueueResponse(cur, Collections.emptyList());
            } else {
                this.currentQueue = new QueueResponse(null, Collections.emptyList());
            }
            if (callback != null) callback.accept(this.currentQueue);
        }
    }

    public QueueResponse getCurrentQueue() {
        return currentQueue;
    }

    // Direct Playback Controls
    public void togglePlayPause() {
        windowsBridge.sendCommand("toggle");
        if (auth.isAuthenticated()) {
            client.pause();
        }
    }

    public void play() {
        windowsBridge.sendCommand("play");
        if (auth.isAuthenticated()) {
            client.play();
        }
    }

    public void pause() {
        windowsBridge.sendCommand("pause");
        if (auth.isAuthenticated()) {
            client.pause();
        }
    }

    public void next() {
        windowsBridge.sendCommand("next");
        if (auth.isAuthenticated()) {
            client.next();
        }
    }

    public void previous() {
        windowsBridge.sendCommand("prev");
        if (auth.isAuthenticated()) {
            client.previous();
        }
    }

    public void seek(long positionMs) {
        windowsBridge.sendCommand("seek:" + positionMs);
        if (auth.isAuthenticated()) {
            client.seek(positionMs);
        }
    }

    public void toggleShuffle() {
        windowsBridge.sendCommand("shuffletoggle");
    }

    public void cycleRepeat() {
        windowsBridge.sendCommand("repeatcycle");
    }

    public void setVolume(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        com.espoty.config.ModConfig.getInstance().setVolume(clamped);
        windowsBridge.sendCommand("vol:" + clamped);
        if (auth.isAuthenticated()) {
            client.setVolume(clamped);
        }
    }

    public boolean isCurrentTrackLiked() {
        PlaybackState s = currentState;
        if (s == null) return false;
        String key = s.getTrackName() + " - " + s.getArtistNames();
        return com.espoty.config.ModConfig.getInstance().isTrackLiked(key);
    }

    public boolean toggleCurrentTrackLiked() {
        PlaybackState s = currentState;
        if (s == null) return false;
        String key = s.getTrackName() + " - " + s.getArtistNames();
        return com.espoty.config.ModConfig.getInstance().toggleTrackLiked(key);
    }

    public void shutdown() {
        scheduler.shutdownNow();
        windowsBridge.shutdown();
    }
}
