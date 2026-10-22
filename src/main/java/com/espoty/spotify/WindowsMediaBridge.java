package com.espoty.spotify;

import com.espoty.ESpotyMod;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public class WindowsMediaBridge {
    private static WindowsMediaBridge INSTANCE;
    private static final Gson GSON = new Gson();

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private BufferedWriter writer;
    private Process process;
    private Thread readerThread;
    private volatile boolean isRunning = false;
    private final boolean isWindows;

    public static synchronized WindowsMediaBridge getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new WindowsMediaBridge();
        }
        return INSTANCE;
    }

    private WindowsMediaBridge() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        this.isWindows = os.contains("win");
    }

    public synchronized void start() {
        if (!isWindows || isRunning) return;

        try {
            Path configDir = FabricLoader.getInstance().getConfigDir().resolve("espoty");
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            Path scriptPath = configDir.resolve("bridge.ps1");
            try (InputStream in = getClass().getResourceAsStream("/assets/espoty/bridge.ps1")) {
                if (in != null) {
                    Files.copy(in, scriptPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            if (!Files.exists(scriptPath)) {
                ESpotyMod.LOGGER.error("[eSpoty] Cannot start WindowsMediaBridge: bridge.ps1 not found!");
                return;
            }

            // Create localhost socket server for rock-solid bi-directional communication
            serverSocket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
            int port = serverSocket.getLocalPort();
            serverSocket.setSoTimeout(12000);

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy", "Bypass",
                    "-File", scriptPath.toAbsolutePath().toString(),
                    "-Port", String.valueOf(port)
            );
            pb.redirectErrorStream(true);
            this.process = pb.start();

            // Accept connection in background
            new Thread(() -> {
                try {
                    clientSocket = serverSocket.accept();
                    writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));
                    isRunning = true;

                    readerThread = new Thread(this::readLoop, "eSpoty-BridgeReader");
                    readerThread.setDaemon(true);
                    readerThread.start();

                    ESpotyMod.LOGGER.info("[eSpoty] Connected to Windows Media SMTC Bridge on port {}", port);
                } catch (Exception e) {
                    ESpotyMod.LOGGER.error("[eSpoty] Failed to establish connection with bridge process", e);
                    shutdown();
                }
            }, "eSpoty-BridgeAcceptor").start();

        } catch (Exception e) {
            ESpotyMod.LOGGER.error("[eSpoty] Failed to launch WindowsMediaBridge", e);
            shutdown();
        }
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (isRunning && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("{") && line.endsWith("}")) {
                    try {
                        JsonObject json = GSON.fromJson(line, JsonObject.class);
                        if (json != null) {
                            handleStateJson(json);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            if (isRunning) {
                ESpotyMod.LOGGER.debug("[eSpoty] Bridge read loop terminated: {}", e.getMessage());
            }
        } finally {
            isRunning = false;
        }
    }

    private void handleStateJson(JsonObject json) {
        boolean running = json.has("running") && json.get("running").getAsBoolean();
        int vol = json.has("vol") ? json.get("vol").getAsInt() : 100;

        if (!running) {
            SpotifyStateManager.getInstance().updateLocalState(false, "", "", "", 0, 0, false, false, "none", vol, true, true, true, true, true, true);
            return;
        }

        String title = json.has("title") ? json.get("title").getAsString() : "";
        String artist = json.has("artist") ? json.get("artist").getAsString() : "";
        String album = json.has("album") ? json.get("album").getAsString() : "";
        long pos = json.has("pos") ? json.get("pos").getAsLong() : 0;
        long dur = json.has("dur") ? json.get("dur").getAsLong() : 0;
        boolean playing = json.has("playing") && json.get("playing").getAsBoolean();
        boolean shuffle = json.has("shuffle") && json.get("shuffle").getAsBoolean();
        String repeat = json.has("repeat") ? json.get("repeat").getAsString() : "none";

        boolean canPlayPause = !json.has("canPlayPause") || json.get("canPlayPause").getAsBoolean();
        boolean canNext = !json.has("canNext") || json.get("canNext").getAsBoolean();
        boolean canPrev = !json.has("canPrev") || json.get("canPrev").getAsBoolean();
        boolean canShuffle = !json.has("canShuffle") || json.get("canShuffle").getAsBoolean();
        boolean canRepeat = !json.has("canRepeat") || json.get("canRepeat").getAsBoolean();
        boolean canSeek = !json.has("canSeek") || json.get("canSeek").getAsBoolean();

        SpotifyStateManager.getInstance().updateLocalState(
                true, title, artist, album, pos, dur, playing, shuffle, repeat, vol,
                canPlayPause, canNext, canPrev, canShuffle, canRepeat, canSeek
        );
    }

    public synchronized void sendCommand(String cmd) {
        if (!isRunning || writer == null) return;
        try {
            writer.write(cmd);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            ESpotyMod.LOGGER.debug("[eSpoty] Failed to send command: {}", cmd, e);
        }
    }

    public synchronized void restart() {
        shutdown();
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {}
        start();
    }

    public synchronized void shutdown() {
        isRunning = false;
        if (writer != null) {
            try {
                writer.write("quit");
                writer.newLine();
                writer.flush();
            } catch (Exception ignored) {}
            writer = null;
        }
        if (clientSocket != null) {
            try { clientSocket.close(); } catch (Exception ignored) {}
            clientSocket = null;
        }
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (Exception ignored) {}
            serverSocket = null;
        }
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
    }
}
