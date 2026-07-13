package de.velocityhub.listener;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;

/**
 * Handles incoming plugin messages from backend servers.
 *
 * <p>The {@code velocityhub:action} channel is used by companion Paper plugins
 * to send actions back to the proxy (e.g. GUI clicks, server selections).</p>
 *
 * <p>Message format: JSON with a {@code type} field.</p>
 */
public final class PluginMessageListener {

    /** Channel name for actions from backend to proxy. */
    public static final Key ACTION_CHANNEL = Key.key("velocityhub", "action");

    /** Channel name for lobby-item give (proxy → backend). */
    public static final Key LOBBY_ITEM_CHANNEL = Key.key("velocityhub", "lobby-item");

    /** Channel name for opening a GUI (proxy → backend). */
    public static final Key GUI_CHANNEL = Key.key("velocityhub", "gui");

    private final VelocityHubPlugin plugin;
    private final Logger logger;

    public PluginMessageListener(VelocityHubPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        // Only handle messages from backend servers
        if (!(event.getSource() instanceof ServerConnection serverConn)) return;
        if (!event.getIdentifier().equals(ACTION_CHANNEL)) return;

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        String raw;
        try {
            raw = new String(event.getData(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("[PluginMessage] Could not decode message: {}", e.getMessage());
            return;
        }

        try {
            JsonObject payload = JsonParser.parseString(raw).getAsJsonObject();
            String type = payload.get("type").getAsString();

            switch (type) {
                case "gui-click" -> handleGuiClick(serverConn, payload);
                case "server-status" -> handleServerStatus(payload);
                case "player-count" -> handlePlayerCount(payload);
                default -> {
                    if (plugin.isDebugMode()) {
                        logger.debug("[PluginMessage] Unknown action type: {}", type);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("[PluginMessage] Error processing action: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private void handleGuiClick(ServerConnection conn, JsonObject payload) {
        String playerUuid = payload.get("player").getAsString();
        String serverName = payload.get("server").getAsString();

        try {
            java.util.UUID uuid = java.util.UUID.fromString(playerUuid);
            plugin.getProxy().getPlayer(uuid).ifPresent(player ->
                    plugin.getServerManager().sendPlayerToServer(player, serverName));
        } catch (IllegalArgumentException e) {
            logger.warn("[PluginMessage] Invalid UUID in gui-click: {}", playerUuid);
        }
    }

    private void handleServerStatus(JsonObject payload) {
        String serverName = payload.get("server").getAsString();
        String status     = payload.get("status").getAsString();
        plugin.getServerManager().updateServerStatus(serverName, status, true);
    }

    private void handlePlayerCount(JsonObject payload) {
        String serverName = payload.get("server").getAsString();
        int    count      = payload.get("count").getAsInt();
        plugin.getServerManager().updatePlayerCount(serverName, count, true);
    }
}
