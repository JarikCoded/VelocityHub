package de.velocityhub.redis;
import de.velocityhub.VelocityHubPlugin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import redis.clients.jedis.JedisPubSub;

/**
 * Handles incoming Redis Pub/Sub messages and dispatches them to the
 * appropriate VelocityHub managers.
 */
public final class RedisPubSubHandler extends JedisPubSub {

    private final Logger logger;

    public RedisPubSubHandler(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void onMessage(String channel, String message) {
        VelocityHubPlugin plugin = VelocityHub.getInstance();
        if (plugin == null) return;

        try {
            JsonObject payload = JsonParser.parseString(message).getAsJsonObject();

            switch (channel) {
                case RedisChannels.SERVER_STATUS    -> handleServerStatus(plugin, payload);
                case RedisChannels.SERVER_PLAYERS   -> handleServerPlayers(plugin, payload);
                case RedisChannels.SERVER_UPDATE    -> handleServerUpdate(plugin, payload);
                case RedisChannels.GROUP_UPDATE     -> handleGroupUpdate(plugin, payload);
                case RedisChannels.BROADCAST        -> handleBroadcast(plugin, payload);
                case RedisChannels.MAINTENANCE      -> handleMaintenance(plugin, payload);
                case RedisChannels.MOTD_UPDATE      -> handleMotdUpdate(plugin, payload);
                case RedisChannels.RELOAD           -> handleReload(plugin, payload);
                case RedisChannels.PLAYER_TRANSFER  -> handlePlayerTransfer(plugin, payload);
                case RedisChannels.PRIVATE_MESSAGE  -> handlePrivateMessage(plugin, payload);
                case RedisChannels.STAFF_CHAT       -> handleStaffChat(plugin, payload);
                case RedisChannels.ADMIN_CHAT       -> handleAdminChat(plugin, payload);
                case RedisChannels.ANNOUNCEMENT     -> handleAnnouncement(plugin, payload);
                case RedisChannels.PLAYER_EVENT     -> handlePlayerEvent(plugin, payload);
                default -> {
                    if (plugin.isDebugMode()) {
                        logger.debug("[Redis] Unhandled channel: {}", channel);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("[Redis] Error processing message on channel {}: {}", channel, e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private void handleServerStatus(VelocityHubPlugin plugin, JsonObject payload) {
        String serverName = payload.get("server").getAsString();
        String statusStr  = payload.get("status").getAsString();
        plugin.getServerManager().updateServerStatus(serverName, statusStr, false);
    }

    private void handleServerPlayers(VelocityHubPlugin plugin, JsonObject payload) {
        String serverName = payload.get("server").getAsString();
        int count         = payload.get("count").getAsInt();
        plugin.getServerManager().updatePlayerCount(serverName, count, false);
    }

    private void handleServerUpdate(VelocityHubPlugin plugin, JsonObject payload) {
        String action = payload.get("action").getAsString();
        plugin.getServerManager().handleRemoteServerUpdate(action, payload);
    }

    private void handleGroupUpdate(VelocityHubPlugin plugin, JsonObject payload) {
        String action = payload.get("action").getAsString();
        plugin.getGroupManager().handleRemoteGroupUpdate(action, payload);
    }

    private void handleBroadcast(VelocityHubPlugin plugin, JsonObject payload) {
        String message = payload.get("message").getAsString();
        plugin.getAnnouncementManager().deliverBroadcast(message);
    }

    private void handleMaintenance(VelocityHubPlugin plugin, JsonObject payload) {
        boolean enabled = payload.get("enabled").getAsBoolean();
        plugin.getMaintenanceManager().setMaintenanceLocal(enabled);
    }

    private void handleMotdUpdate(VelocityHubPlugin plugin, JsonObject payload) {
        String motd = payload.get("motd").getAsString();
        plugin.getConfigManager().updateMotdLocal(motd);
    }

    private void handleReload(VelocityHubPlugin plugin, JsonObject payload) {
        plugin.getConfigManager().reloadAll();
    }

    private void handlePlayerTransfer(VelocityHubPlugin plugin, JsonObject payload) {
        String playerUuid  = payload.get("player").getAsString();
        String targetProxy = payload.has("proxy") ? payload.get("proxy").getAsString() : null;
        String targetServer = payload.get("server").getAsString();
        plugin.getServerManager().transferPlayerIfOnThisProxy(playerUuid, targetServer, targetProxy);
    }

    private void handlePrivateMessage(VelocityHubPlugin plugin, JsonObject payload) {
        String senderUuid   = payload.get("senderUuid").getAsString();
        String senderName   = payload.get("senderName").getAsString();
        String targetUuid   = payload.get("targetUuid").getAsString();
        String message      = payload.get("message").getAsString();
        plugin.getPrivateChatManager().deliverMessage(senderUuid, senderName, targetUuid, message);
    }

    private void handleStaffChat(VelocityHubPlugin plugin, JsonObject payload) {
        String senderName = payload.get("senderName").getAsString();
        String message    = payload.get("message").getAsString();
        plugin.getStaffChatManager().deliverStaffMessage(senderName, message, false);
    }

    private void handleAdminChat(VelocityHubPlugin plugin, JsonObject payload) {
        String senderName = payload.get("senderName").getAsString();
        String message    = payload.get("message").getAsString();
        plugin.getStaffChatManager().deliverAdminMessage(senderName, message, false);
    }

    private void handleAnnouncement(VelocityHubPlugin plugin, JsonObject payload) {
        plugin.getAnnouncementManager().deliverAnnouncement(payload);
    }

    private void handlePlayerEvent(VelocityHubPlugin plugin, JsonObject payload) {
        String type       = payload.get("type").getAsString();
        String playerName = payload.get("playerName").getAsString();
        String server     = payload.has("server") ? payload.get("server").getAsString() : null;
        plugin.getAnnouncementManager().deliverPlayerEvent(type, playerName, server);
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        logger.info("[Redis] Subscribed to channel '{}' ({} total)", channel, subscribedChannels);
    }

    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        logger.info("[Redis] Unsubscribed from channel '{}'", channel);
    }
}
