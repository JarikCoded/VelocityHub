package de.velocityhub.manager;
import de.velocityhub.VelocityHubPlugin;

import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.velocityhub.redis.RedisChannels;
import de.velocityhub.util.ComponentUtil;
import de.velocityhub.util.PermissionUtil;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/**
 * Handles staff chat and admin chat messages, routing them across all proxies
 * via Redis Pub/Sub.
 */
public final class StaffChatManager {

    private final VelocityHubPlugin plugin;
    private final ProxyServer proxy;
    private final Logger logger;

    public StaffChatManager(VelocityHubPlugin plugin, ProxyServer proxy, Logger logger) {
        this.plugin = plugin;
        this.proxy  = proxy;
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Sending
    // -------------------------------------------------------------------------

    /**
     * Sends a staff-chat message from the given player.
     *
     * @param sender  the player sending the message
     * @param message the message content
     */
    public void sendStaffMessage(Player sender, String message) {
        String proxyId = plugin.getConfigManager().getProxyId();
        String format  = plugin.getConfigManager().getMessage(
                "staff-chat.format",
                "&8[&cStaff&8] &7[%proxy%] &e%player%&7: &f%message%");
        String formatted = ComponentUtil.replace(format,
                "proxy", proxyId, "player", sender.getUsername(), "message", message);

        // Deliver locally
        deliverStaffMessage(sender.getUsername(), formatted, false);

        // Publish to other proxies
        JsonObject payload = new JsonObject();
        payload.addProperty("senderName", sender.getUsername());
        payload.addProperty("message", formatted);
        plugin.getRedisManager().publish(RedisChannels.STAFF_CHAT, payload);
    }

    /**
     * Sends an admin-chat message from the given player.
     *
     * @param sender  the player sending the message
     * @param message the message content
     */
    public void sendAdminMessage(Player sender, String message) {
        String proxyId = plugin.getConfigManager().getProxyId();
        String format  = plugin.getConfigManager().getMessage(
                "admin-chat.format",
                "&8[&4Admin&8] &7[%proxy%] &c%player%&7: &f%message%");
        String formatted = ComponentUtil.replace(format,
                "proxy", proxyId, "player", sender.getUsername(), "message", message);

        deliverAdminMessage(sender.getUsername(), formatted, false);

        JsonObject payload = new JsonObject();
        payload.addProperty("senderName", sender.getUsername());
        payload.addProperty("message", formatted);
        plugin.getRedisManager().publish(RedisChannels.ADMIN_CHAT, payload);
    }

    // -------------------------------------------------------------------------
    // Delivery (called locally and from Redis pub/sub)
    // -------------------------------------------------------------------------

    /**
     * Delivers a staff-chat message to all eligible players on this proxy.
     *
     * @param senderName  sender username (for logging)
     * @param message     the already-formatted message
     * @param logToConsole whether to also log to the console
     */
    public void deliverStaffMessage(String senderName, String message, boolean logToConsole) {
        Component component = ComponentUtil.parse(message);
        proxy.getAllPlayers().stream()
                .filter(p -> p.hasPermission(PermissionUtil.CMD_STAFFCHAT)
                        || p.hasPermission(PermissionUtil.ADMIN))
                .forEach(p -> p.sendMessage(component));
        if (logToConsole) proxy.getConsoleCommandSource().sendMessage(component);
    }

    /**
     * Delivers an admin-chat message to all eligible players on this proxy.
     *
     * @param senderName  sender username
     * @param message     formatted message
     * @param logToConsole whether to also log to the console
     */
    public void deliverAdminMessage(String senderName, String message, boolean logToConsole) {
        Component component = ComponentUtil.parse(message);
        proxy.getAllPlayers().stream()
                .filter(p -> p.hasPermission(PermissionUtil.CMD_ADMINCHAT)
                        || p.hasPermission(PermissionUtil.ADMIN))
                .forEach(p -> p.sendMessage(component));
        if (logToConsole) proxy.getConsoleCommandSource().sendMessage(component);
    }
}
