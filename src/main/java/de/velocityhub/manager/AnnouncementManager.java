package de.velocityhub.manager;
import de.velocityhub.VelocityHubPlugin;

import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.velocityhub.api.event.BroadcastEvent;
import de.velocityhub.redis.RedisChannels;
import de.velocityhub.util.ComponentUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Sends network-wide broadcasts, titles, actionbar messages, and boss bars,
 * routing them across all proxies via Redis Pub/Sub.
 *
 * <p>Also handles player join/leave notifications.</p>
 */
public final class AnnouncementManager {

    private final VelocityHubPlugin plugin;
    private final ProxyServer proxy;
    private final Logger logger;

    /** Active boss bars keyed by an arbitrary ID. */
    private final Map<String, BossBar> activeBossBars = new ConcurrentHashMap<>();

    public AnnouncementManager(VelocityHubPlugin plugin, ProxyServer proxy, Logger logger) {
        this.plugin = plugin;
        this.proxy  = proxy;
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Broadcast
    // -------------------------------------------------------------------------

    /**
     * Sends a broadcast to all players on the entire network.
     *
     * @param rawMessage the message to broadcast (supports MiniMessage / legacy codes)
     * @param sender     the player issuing the broadcast, or {@code null} for console
     */
    public void broadcastNetwork(String rawMessage, Player sender) {
        // Fire API event
        BroadcastEvent event = new BroadcastEvent(rawMessage, sender);
        proxy.getEventManager().fire(event).thenAccept(e -> {
            if (e.isCancelled()) return;

            String prefix = plugin.getConfigManager().getMessage(
                    "broadcast.prefix", "&6[Broadcast] &r");
            String formatted = prefix + e.getMessage();

            // Deliver locally
            deliverBroadcast(formatted);

            // Publish to all proxies
            JsonObject payload = new JsonObject();
            payload.addProperty("message", formatted);
            plugin.getRedisManager().publish(RedisChannels.BROADCAST, payload);

            // Discord webhook
            plugin.getDiscordWebhookManager()
                    .sendBroadcast(ComponentUtil.stripFormatting(formatted));
        });
    }

    /**
     * Delivers a broadcast to all local players.
     * Called both locally and from the Redis pub/sub handler.
     *
     * @param message the already-formatted message
     */
    public void deliverBroadcast(String message) {
        Component component = ComponentUtil.parse(message);
        proxy.getAllPlayers().forEach(p -> p.sendMessage(component));
        proxy.getConsoleCommandSource().sendMessage(component);
    }

    // -------------------------------------------------------------------------
    // Title / ActionBar / BossBar
    // -------------------------------------------------------------------------

    /**
     * Sends a network-wide announcement with optional title, actionbar, and bossbar.
     *
     * @param payload the JSON payload describing the announcement
     */
    public void deliverAnnouncement(JsonObject payload) {
        String type = payload.get("type").getAsString();

        switch (type.toLowerCase()) {
            case "title" -> {
                String title    = payload.has("title")    ? payload.get("title").getAsString()    : "";
                String subtitle = payload.has("subtitle") ? payload.get("subtitle").getAsString() : "";
                int fadeIn  = payload.has("fadeIn")  ? payload.get("fadeIn").getAsInt()  : 10;
                int stay    = payload.has("stay")    ? payload.get("stay").getAsInt()    : 70;
                int fadeOut = payload.has("fadeOut") ? payload.get("fadeOut").getAsInt() : 20;

                Title.Times times = Title.Times.times(
                        Duration.ofMillis(fadeIn * 50L),
                        Duration.ofMillis(stay   * 50L),
                        Duration.ofMillis(fadeOut * 50L));
                Title t = Title.title(
                        ComponentUtil.parse(title),
                        ComponentUtil.parse(subtitle),
                        times);
                proxy.getAllPlayers().forEach(p -> p.showTitle(t));
            }
            case "actionbar" -> {
                String text = payload.has("text") ? payload.get("text").getAsString() : "";
                Component component = ComponentUtil.parse(text);
                proxy.getAllPlayers().forEach(p -> p.sendActionBar(component));
            }
            case "bossbar" -> {
                String id        = payload.has("id")       ? payload.get("id").getAsString()       : "default";
                String text      = payload.has("text")     ? payload.get("text").getAsString()     : "";
                float  progress  = payload.has("progress") ? payload.get("progress").getAsFloat()  : 1.0f;
                String colorStr  = payload.has("color")    ? payload.get("color").getAsString()    : "RED";
                String overlayStr= payload.has("overlay")  ? payload.get("overlay").getAsString()  : "PROGRESS";
                int    duration  = payload.has("duration") ? payload.get("duration").getAsInt()    : -1;

                BossBar.Color color;
                try { color = BossBar.Color.valueOf(colorStr.toUpperCase()); }
                catch (Exception e) { color = BossBar.Color.RED; }

                BossBar.Overlay overlay;
                try { overlay = BossBar.Overlay.valueOf(overlayStr.toUpperCase()); }
                catch (Exception e) { overlay = BossBar.Overlay.PROGRESS; }

                BossBar bar = BossBar.bossBar(
                        ComponentUtil.parse(text),
                        Math.max(0f, Math.min(1f, progress)),
                        color, overlay);

                activeBossBars.put(id, bar);
                proxy.getAllPlayers().forEach(p -> p.showBossBar(bar));

                if (duration > 0) {
                    ScheduledExecutorService ses =
                            Executors.newSingleThreadScheduledExecutor();
                    ses.schedule(() -> {
                        proxy.getAllPlayers().forEach(p -> p.hideBossBar(bar));
                        activeBossBars.remove(id);
                        ses.shutdownNow();
                    }, duration, TimeUnit.SECONDS);
                }
            }
        }
    }

    /**
     * Sends a title, actionbar, or bossbar announcement to the whole network.
     *
     * @param type     the announcement type
     * @param payload  the JSON payload (must include {@code type} field)
     */
    public void announceNetwork(String type, JsonObject payload) {
        payload.addProperty("type", type);
        deliverAnnouncement(payload);
        plugin.getRedisManager().publish(RedisChannels.ANNOUNCEMENT, payload);
    }

    // -------------------------------------------------------------------------
    // Player join/leave notifications
    // -------------------------------------------------------------------------

    /**
     * Delivers a player join or leave notification to staff players on this proxy.
     *
     * @param type       {@code "join"} or {@code "quit"}
     * @param playerName the player's username
     * @param server     the server the player is on (may be {@code null})
     */
    public void deliverPlayerEvent(String type, String playerName, String server) {
        String msgKey = "join".equals(type) ? "join-leave.join" : "join-leave.quit";
        String def    = "join".equals(type)
                ? "&e%player% &ajoined the network."
                : "&e%player% &cleft the network.";
        String raw = ComponentUtil.replace(
                plugin.getConfigManager().getMessage(msgKey, def),
                "player", playerName,
                "server", server != null ? server : "");
        Component component = ComponentUtil.parse(raw);

        proxy.getAllPlayers().stream()
                .filter(p -> p.hasPermission(de.velocityhub.util.PermissionUtil.NOTIFY_JOIN_LEAVE)
                        || p.hasPermission(de.velocityhub.util.PermissionUtil.ADMIN))
                .forEach(p -> p.sendMessage(component));
    }
}
