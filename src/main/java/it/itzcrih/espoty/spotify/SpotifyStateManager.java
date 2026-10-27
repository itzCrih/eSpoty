package it.itzcrih.espoty.spotify;

import it.itzcrih.espoty.ESpotyMod;
import it.itzcrih.espoty.config.ModConfig;
import it.itzcrih.espoty.gui.texture.AlbumArtManager;
import it.itzcrih.espoty.lyrics.LyricsManager;
import it.itzcrih.espoty.spotify.models.PlaybackState;
import it.itzcrih.espoty.spotify.models.QueueItem;
import it.itzcrih.espoty.spotify.models.QueueResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
    private boolean isLikedOnSpotify = false;

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
                                             int volumePercent, String artUrl, boolean liked,
                                             boolean canPlayPause, boolean canNext, boolean canPrev,
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
        this.isLikedOnSpotify = liked;

        if (changed) {
            this.lastTrackKey = trackKey;
            this.lastTrackChangeTime = System.currentTimeMillis();
            if (artUrl != null && !artUrl.isEmpty()) {
                this.currentArtworkUrl = artUrl;
                AlbumArtManager.getInstance().getAlbumTexture(artUrl);
            } else {
                this.currentArtworkUrl = "";
                // Fallback to iTunes query if not provided by CLI
                ArtworkFinder.getInstance().findArtworkUrl(title, artist).thenAccept(foundArt -> {
                    synchronized (SpotifyStateManager.this) {
                        if (foundArt != null && !foundArt.isEmpty()) {
                            this.currentArtworkUrl = foundArt;
                            AlbumArtManager.getInstance().getAlbumTexture(foundArt);
                        }
                    }
                });
            }
        } else if ((this.currentArtworkUrl.isEmpty() || !this.currentArtworkUrl.equals(artUrl)) && artUrl != null && !artUrl.isEmpty()) {
            this.currentArtworkUrl = artUrl;
            AlbumArtManager.getInstance().getAlbumTexture(artUrl);
        }

        // Anchor progress to timestamp
        this.stateFetchTimestamp = System.currentTimeMillis();

        int effectiveVol = (volumePercent >= 0)
                ? volumePercent
                : (this.currentState != null && this.currentState.getVolumePercent() >= 0
                        ? this.currentState.getVolumePercent()
                        : ModConfig.getInstance().getVolume());

        this.currentState = new PlaybackState(
                "local_" + trackKey.hashCode(),
                title,
                artist,
                album,
                this.currentArtworkUrl,
                dur,
                pos,
                playing,
                shuffle,
                repeat,
                effectiveVol,
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
     * Calculates client-interpolated playback progress.
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

    public synchronized void updateQueueFromJson(JsonObject data) {
        if (data == null) return;
        try {
            QueueItem curItem = null;
            if (data.has("currently_playing") && data.get("currently_playing").isJsonObject()) {
                JsonObject cp = data.getAsJsonObject("currently_playing");
                String uri = cp.has("uri") ? cp.get("uri").getAsString() : "";
                String name = cp.has("name") ? cp.get("name").getAsString() : "";
                String artist = cp.has("artist") ? cp.get("artist").getAsString() : "";
                curItem = new QueueItem(uri, name, artist, "", "", 0);
            }

            List<QueueItem> nextList = new ArrayList<>();
            if (data.has("next_tracks") && data.get("next_tracks").isJsonArray()) {
                JsonArray arr = data.getAsJsonArray("next_tracks");
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        JsonObject obj = el.getAsJsonObject();
                        String uri = obj.has("uri") ? obj.get("uri").getAsString() : "";
                        String name = obj.has("name") ? obj.get("name").getAsString() : "";
                        String artist = obj.has("artist") ? obj.get("artist").getAsString() : "";
                        nextList.add(new QueueItem(uri, name, artist, "", "", 0));
                    }
                }
            }
            this.currentQueue = new QueueResponse(curItem, nextList);
        } catch (Exception e) {
            ESpotyMod.LOGGER.debug("[eSpoty] Failed to parse CLI queue JSON", e);
        }
    }

    public void requestQueueRefresh() {
        windowsBridge.sendCommand("queue:list");
        if (auth.isAuthenticated()) {
            client.fetchQueue().thenAccept(q -> {
                if (q != null) this.currentQueue = q;
            });
        }
    }

    public synchronized void removeQueueItem(int index) {
        if (currentQueue != null) {
            List<QueueItem> items = new ArrayList<>(currentQueue.getQueue());
            if (index >= 0 && index < items.size()) {
                items.remove(index);
                this.currentQueue = new QueueResponse(currentQueue.getCurrentlyPlaying(), items);
            }
        }
        windowsBridge.sendCommand("queue:remove:" + index);
    }

    public synchronized void moveQueueItem(int fromIndex, int toIndex) {
        if (currentQueue != null) {
            List<QueueItem> items = new ArrayList<>(currentQueue.getQueue());
            if (fromIndex >= 0 && fromIndex < items.size() && toIndex >= 0 && toIndex < items.size() && fromIndex != toIndex) {
                QueueItem item = items.remove(fromIndex);
                items.add(toIndex, item);
                this.currentQueue = new QueueResponse(currentQueue.getCurrentlyPlaying(), items);
            }
        }
        windowsBridge.sendCommand("queue:move:" + fromIndex + ":" + toIndex);
    }

    public void playQueueItem(String uri) {
        if (uri != null && !uri.isEmpty()) {
            windowsBridge.sendCommand("queue:play:" + uri);
        }
    }

    public void refreshQueue(Consumer<QueueResponse> callback) {
        if (auth.isAuthenticated()) {
            client.fetchQueue().thenAccept(queue -> {
                this.currentQueue = queue;
                if (callback != null) callback.accept(queue);
            });
        } else {
            windowsBridge.sendCommand("queue:list");
            if (callback != null && currentQueue != null) callback.accept(currentQueue);
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
        it.itzcrih.espoty.config.ModConfig.getInstance().setVolume(clamped);
        windowsBridge.sendCommand("vol:" + clamped);
        if (auth.isAuthenticated()) {
            client.setVolume(clamped);
        }
    }

    public boolean isCurrentTrackLiked() {
        return this.isLikedOnSpotify;
    }

    public boolean toggleCurrentTrackLiked() {
        windowsBridge.sendCommand("togglelike");
        this.isLikedOnSpotify = !this.isLikedOnSpotify;
        if (currentState != null) {
            String key = currentState.getTrackName() + " - " + currentState.getArtistNames();
            it.itzcrih.espoty.config.ModConfig.getInstance().toggleTrackLiked(key);
        }
        return this.isLikedOnSpotify;
    }

    public void shutdown() {
        scheduler.shutdownNow();
        windowsBridge.shutdown();
    }
}
