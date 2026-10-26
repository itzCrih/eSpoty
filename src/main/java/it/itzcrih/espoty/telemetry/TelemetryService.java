package it.itzcrih.espoty.telemetry;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.itzcrih.espoty.ESpotyMod;
import it.itzcrih.espoty.config.ModConfig;
import it.itzcrih.espoty.update.UpdateChecker;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance, non-blocking telemetry and diagnostics reporting service.
 * Batches events locally and dispatches payloads over HTTPS every 10 minutes.
 * Guarantees zero game frame drops and zero crash propagation.
 */
public class TelemetryService {

    private static final String ENDPOINT = "https://api.itzcrih.it/api/v1/telemetry/collect";
    private static final String APP_ID = "espoty";
    private static final int MAX_QUEUE_SIZE = 50;
    private static final Gson GSON = new Gson();

    private static TelemetryService instance;

    private final String sessionId = UUID.randomUUID().toString();
    private final ConcurrentLinkedQueue<MetricEvent> eventQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;

    public static synchronized TelemetryService getInstance() {
        if (instance == null) {
            instance = new TelemetryService();
        }
        return instance;
    }

    private TelemetryService() {
        // Daemon single-thread executor with low thread priority
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "espoty-metrics-worker");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // Initial launch heartbeat delayed by 15s to allow game to finish boot
        scheduler.schedule(this::reportStartup, 15, TimeUnit.SECONDS);

        // Recurring periodic batch flush every 10 minutes
        scheduler.scheduleAtFixedRate(this::flushBatch, 10, 10, TimeUnit.MINUTES);
    }

    public void recordEvent(String eventName, JsonObject metadata) {
        if (!ModConfig.getInstance().isMetricsEnabled()) return;

        if (queueSize.get() >= MAX_QUEUE_SIZE) {
            // Evict oldest event if queue exceeds limit to prevent memory bloat
            if (eventQueue.poll() != null) {
                queueSize.decrementAndGet();
            }
        }

        MetricEvent event = new MetricEvent(eventName, System.currentTimeMillis(), metadata);
        eventQueue.add(event);
        queueSize.incrementAndGet();
    }

    public void recordError(String errorType, String message) {
        if (!ModConfig.getInstance().isMetricsEnabled()) return;

        JsonObject meta = new JsonObject();
        meta.addProperty("error_type", sanitize(errorType, 64));
        meta.addProperty("error_msg", sanitize(message, 128));
        recordEvent("error", meta);
    }

    private void reportStartup() {
        if (!ModConfig.getInstance().isMetricsEnabled()) return;

        JsonObject meta = new JsonObject();
        meta.addProperty("sky_lyrics", ModConfig.getInstance().isSkyLyricsEnabled());
        meta.addProperty("hud_pos", ModConfig.getInstance().getHudPosition().name());
        meta.addProperty("theme", ModConfig.getInstance().getThemeLook().name());
        meta.addProperty("theme_accent", ModConfig.getInstance().getThemeAccent().name());
        recordEvent("launch", meta);

        // Immediate flush of startup event
        flushBatch();
    }

    private synchronized void flushBatch() {
        try {
            if (!ModConfig.getInstance().isMetricsEnabled()) {
                eventQueue.clear();
                queueSize.set(0);
                return;
            }

            JsonArray eventsArray = new JsonArray();
            MetricEvent ev;
            int count = 0;
            while ((ev = eventQueue.poll()) != null && count < MAX_QUEUE_SIZE) {
                JsonObject evObj = new JsonObject();
                evObj.addProperty("event", ev.name);
                evObj.addProperty("timestamp", ev.timestamp);
                if (ev.data != null) {
                    evObj.add("data", ev.data);
                }
                eventsArray.add(evObj);
                count++;
            }
            queueSize.addAndGet(-count);

            // Even if no specific events queued, send a periodic heartbeat
            if (eventsArray.isEmpty()) {
                JsonObject heartbeat = new JsonObject();
                heartbeat.addProperty("event", "heartbeat");
                heartbeat.addProperty("timestamp", System.currentTimeMillis());
                eventsArray.add(heartbeat);
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("app_id", APP_ID);
            payload.addProperty("app_version", UpdateChecker.getCurrentVersion());
            payload.addProperty("install_id", ModConfig.getInstance().getAnonymousInstallId());
            payload.addProperty("session_id", sessionId);

            String mcVer = FabricLoader.getInstance().getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
            payload.addProperty("mc_version", mcVer);

            String loaderVer = FabricLoader.getInstance().getModContainer("fabricloader")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
            payload.addProperty("loader_version", loaderVer);

            payload.addProperty("os", sanitize(System.getProperty("os.name"), 32));
            payload.addProperty("java_version", sanitize(System.getProperty("java.version"), 32));
            payload.add("events", eventsArray);

            String jsonPayload = GSON.toJson(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "eSpoty-Metrics/" + UpdateChecker.getCurrentVersion())
                    .timeout(Duration.ofSeconds(4))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> null); // Non-blocking and silent fail
        } catch (Throwable ignored) {
            // Guarantee total failure isolation
        }
    }

    private static String sanitize(String input, int maxLen) {
        if (input == null) return "unknown";
        String clean = input.replaceAll("[\\r\\n\\t]", " ").trim();
        if (clean.length() > maxLen) {
            return clean.substring(0, maxLen);
        }
        return clean;
    }

    private record MetricEvent(String name, long timestamp, JsonObject data) {}
}
