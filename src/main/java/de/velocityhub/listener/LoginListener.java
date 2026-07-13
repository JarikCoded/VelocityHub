package de.velocityhub.listener;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.google.gson.JsonObject;
import de.velocityhub.redis.RedisChannels;
import de.velocityhub.redis.RedisKeys;
import de.velocityhub.util.ComponentUtil;
import org.slf4j.Logger;

/**
 * Handles player login and disconnect events.
 *
 * <p>On login: checks maintenance mode, loads player data, updates Redis,
 * fires join notification.</p>
 * <p>On disconnect: saves player data, updates Redis, fires leave notification.</p>
 */
public final class LoginListener {

    private final VelocityHubPlugin plugin;
    private final Logger logger;

    public LoginListener(VelocityHubPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();

        // Check maintenance mode
        if (plugin.getMaintenanceManager().isMaintenanceEnabled()
                && !plugin.getMaintenanceManager().canBypass(player)) {
            String rawMsg = plugin.getConfigManager()
                    .getMessage("maintenance.kick-message",
                            "&cThe network is currently under maintenance.\n&7Please try again later.");
            event.setResult(ResultedEvent.ComponentResult.denied(ComponentUtil.parse(rawMsg)));
            return;
        }

        // Load player data asynchronously
        plugin.getPlayerDataManager()
                .loadPlayer(player.getUniqueId(), player.getUsername())
                .thenAccept(data -> {
                    // Register in Redis online players set
                    plugin.getRedisManager().sadd(
                            RedisKeys.ONLINE_PLAYERS, player.getUniqueId().toString());
                    plugin.getRedisManager().set(
                            String.format(RedisKeys.PLAYER_PROXY, player.getUniqueId()),
                            plugin.getConfigManager().getProxyId());

                    // Statistics
                    plugin.getStatisticsManager().recordJoin("");

                    // Publish join event to all proxies
                    JsonObject payload = new JsonObject();
                    payload.addProperty("type", "join");
                    payload.addProperty("playerName", player.getUsername());
                    plugin.getRedisManager().publish(RedisChannels.PLAYER_EVENT, payload);

                    // Discord notification
                    plugin.getDiscordWebhookManager()
                            .sendPlayerEvent(player.getUsername(), "join");

                    // Auto-connect to last server if configured
                    boolean reconnectToLast = plugin.getConfigManager().getMain()
                            .node("player", "reconnect-last-server").getBoolean(false);
                    if (reconnectToLast && data.getLastServer() != null) {
                        plugin.getServerManager()
                                .getServer(data.getLastServer())
                                .ifPresent(ns -> {
                                    if (ns.isJoinable()) {
                                        plugin.getServerManager()
                                                .sendPlayerToServer(player, ns.getName());
                                    }
                                });
                    }
                });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        // Save player data
        plugin.getPlayerDataManager().unloadPlayer(player.getUniqueId());

        // Dequeue if in a queue
        plugin.getQueueManager().dequeue(player);

        // Remove from Redis online players set
        plugin.getRedisManager().srem(
                RedisKeys.ONLINE_PLAYERS, player.getUniqueId().toString());
        plugin.getRedisManager().delete(
                String.format(RedisKeys.PLAYER_PROXY, player.getUniqueId()),
                String.format(RedisKeys.PLAYER_SERVER, player.getUniqueId()));

        // Publish leave event to all proxies
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "quit");
        payload.addProperty("playerName", player.getUsername());
        plugin.getRedisManager().publish(RedisChannels.PLAYER_EVENT, payload);

        // Discord notification
        plugin.getDiscordWebhookManager()
                .sendPlayerEvent(player.getUsername(), "quit");
    }
}
