package de.velocityhub.manager;
import de.velocityhub.VelocityHubPlugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends webhook notifications to a Discord channel.
 *
 * <p>Supported event types:
 * <ul>
 *   <li>Broadcasts</li>
 *   <li>Player joins / leaves</li>
 *   <li>Server status changes</li>
 * </ul>
 * </p>
 *
 * <p>Configure the webhook URL in {@code config.yml} under
 * {@code discord.webhook-url}.</p>
 */
public final class DiscordWebhookManager {

    private static final MediaType JSON_TYPE =
            MediaType.get("application/json; charset=utf-8");

    private final VelocityHubPlugin plugin;
    private final Logger logger;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "velocityhub-discord");
        t.setDaemon(true);
        return t;
    });

    public DiscordWebhookManager(VelocityHubPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Sends a broadcast notification to Discord.
     *
     * @param plainMessage the plain-text broadcast message (no colour codes)
     */
    public void sendBroadcast(String plainMessage) {
        String url = getWebhookUrl();
        if (url == null || url.isBlank()) return;

        boolean enabled = plugin.getConfigManager().getMain()
                .node("discord", "events", "broadcast").getBoolean(true);
        if (!enabled) return;

        String username = plugin.getConfigManager().getMain()
                .node("discord", "username").getString("VelocityHub");
        String avatarUrl = plugin.getConfigManager().getMain()
                .node("discord", "avatar-url").getString("");

        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        if (!avatarUrl.isBlank()) body.addProperty("avatar_url", avatarUrl);
        body.addProperty("content", "📢 **Broadcast:** " + plainMessage);

        postAsync(url, body);
    }

    /**
     * Sends a player join or leave notification to Discord.
     *
     * @param playerName the player's username
     * @param type       {@code "join"} or {@code "quit"}
     */
    public void sendPlayerEvent(String playerName, String type) {
        String url = getWebhookUrl();
        if (url == null || url.isBlank()) return;

        boolean enabled = plugin.getConfigManager().getMain()
                .node("discord", "events", type).getBoolean(false);
        if (!enabled) return;

        String username = plugin.getConfigManager().getMain()
                .node("discord", "username").getString("VelocityHub");

        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        if ("join".equals(type)) {
            body.addProperty("content", "✅ **" + playerName + "** joined the network.");
        } else {
            body.addProperty("content", "❌ **" + playerName + "** left the network.");
        }

        postAsync(url, body);
    }

    /**
     * Sends a server status change notification to Discord.
     *
     * @param serverName the server name
     * @param status     the new status string
     */
    public void sendServerStatus(String serverName, String status) {
        String url = getWebhookUrl();
        if (url == null || url.isBlank()) return;

        boolean enabled = plugin.getConfigManager().getMain()
                .node("discord", "events", "server-status").getBoolean(false);
        if (!enabled) return;

        String username = plugin.getConfigManager().getMain()
                .node("discord", "username").getString("VelocityHub");

        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("content", "🖥️ Server **" + serverName + "** is now **" + status + "**.");

        postAsync(url, body);
    }

    /** Shuts down the HTTP executor. */
    public void shutdown() {
        executor.shutdownNow();
        httpClient.dispatcher().executorService().shutdown();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void postAsync(String url, JsonObject body) {
        CompletableFuture.runAsync(() -> {
            RequestBody rb = RequestBody.create(gson.toJson(body), JSON_TYPE);
            Request request = new Request.Builder().url(url).post(rb).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warn("[Discord] Webhook failed: HTTP {}", response.code());
                }
            } catch (IOException e) {
                logger.warn("[Discord] Webhook error: {}", e.getMessage());
            }
        }, executor);
    }

    private String getWebhookUrl() {
        return plugin.getConfigManager().getMain()
                .node("discord", "webhook-url").getString("");
    }
}
