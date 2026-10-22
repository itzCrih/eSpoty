package com.espoty.spotify;

import com.espoty.ESpotyMod;

import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;

public class SpotifyProcessDetector {
    private static long lastCheckTimestamp = 0;
    private static boolean cachedIsRunning = false;
    private static final long CACHE_DURATION_MS = 3000;

    /**
     * Checks if Spotify desktop application is currently running on the local PC.
     * Uses Java 21 ProcessHandle for ultra-fast, zero-native overhead detection.
     */
    public static boolean isSpotifyRunning() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTimestamp < CACHE_DURATION_MS) {
            return cachedIsRunning;
        }

        try {
            boolean found = ProcessHandle.allProcesses().anyMatch(handle -> {
                return handle.info().command().map(cmd -> {
                    String lower = cmd.toLowerCase(Locale.ROOT);
                    return lower.endsWith("spotify.exe") ||
                            lower.contains("\\spotify\\") ||
                            lower.contains("/spotify/") ||
                            lower.endsWith("/spotify");
                }).orElse(false);
            });

            cachedIsRunning = found;
            lastCheckTimestamp = now;
            return found;
        } catch (Exception e) {
            ESpotyMod.LOGGER.debug("ProcessHandle check failed, fallback to false", e);
            cachedIsRunning = false;
            lastCheckTimestamp = now;
            return false;
        }
    }

    /**
     * Force refreshes the running state without waiting for cache expiration.
     */
    public static boolean forceCheck() {
        lastCheckTimestamp = 0;
        return isSpotifyRunning();
    }

    /**
     * Attempts to launch Spotify on the host computer.
     */
    public static boolean launchSpotify() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                new ProcessBuilder("cmd.exe", "/c", "start", "spotify:").start();
                return true;
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", "-a", "Spotify").start();
                return true;
            } else if (os.contains("nix") || os.contains("nux")) {
                new ProcessBuilder("spotify").start();
                return true;
            } else {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create("spotify:"));
                    return true;
                }
            }
        } catch (Exception e) {
            ESpotyMod.LOGGER.error("Failed to launch Spotify desktop application", e);
        }
        return false;
    }
}
