package de.velocityhub.manager;
import de.velocityhub.VelocityHubPlugin;

import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.velocityhub.redis.RedisChannels;
import de.velocityhub.redis.RedisKeys;
import de.velocityhub.util.ComponentUtil;
import de.velocityhub.util.PermissionUtil;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages network-wide maintenance mode.
 *
 * <p>When enabled, only players with {@link PermissionUtil#BYPASS_MAINTENANCE}
 * (or {@link PermissionUtil#ADMIN}) may join any server. All other players are
 * kicked with a configurable message.</p>
 *
 * <p>The state is stored in Redis and synchronised across all proxies.</p>
 */
public final class MaintenanceManager {

    private final VelocityHubPlugin plugin;
    private final ProxyServer proxy;
    private final Logger logger;

    private final AtomicBoolean maintenanceEnabled = new AtomicBoolean(false);

    public MaintenanceManager(VelocityHubPlugin plugin, ProxyServer proxy, Logger logger) {
        this.plugin = plugin;
        this.proxy  = proxy;
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Loads the initial maintenance state from Redis.
     */
    public void loadFromRedis() {
        plugin.getRedisManager().get(RedisKeys.MAINTENANCE)
                .thenAccept(opt -> opt.ifPresent(v -> maintenanceEnabled.set("true".equals(v))));
    }

    // -------------------------------------------------------------------------
    // State management
    // -------------------------------------------------------------------------

    /**
     * Enables or disables maintenance mode, persisting the change to Redis and
     * broadcasting to all proxies.
     *
     * @param enabled whether maintenance should be active
     * @param actor   the player who triggered the change, or {@code null} for console
     */
    public void setMaintenance(boolean enabled, Player actor) {
        maintenanceEnabled.set(enabled);
        plugin.getRedisManager().set(RedisKeys.MAINTENANCE, String.valueOf(enabled));

        // Publish to all proxies
        JsonObject payload = new JsonObject();
        payload.addProperty("enabled", enabled);
        plugin.getRedisManager().publish(RedisChannels.MAINTENANCE, payload);

        String actorName = actor != null ? actor.getUsername() : "Console";
        String msgKey    = enabled ? "maintenance.enabled" : "maintenance.disabled";
        String msg       = plugin.getConfigManager()
                .getMessage(msgKey, enabled ? "&cMaintenance enabled." : "&aMaintenance disabled.");

        if (enabled) {
            // Kick all non-admin players
            kickNonAdminPlayers();
        }

        String broadcastMsg = ComponentUtil.replace(
                plugin.getConfigManager().getMessage(
                        enabled ? "maintenance.broadcast-on" : "maintenance.broadcast-off",
                        enabled ? "&cMaintenance mode has been &lenabled&r&c by %player%."
                                : "&aMaintenance mode has been &ldisabled&r&a by %player%."),
                "player", actorName);

        Component broadcast = ComponentUtil.parse(broadcastMsg);
        proxy.getAllPlayers().stream()
                .filter(p -> p.hasPermission(PermissionUtil.ADMIN)
                        || p.hasPermission(PermissionUtil.BYPASS_MAINTENANCE))
                .forEach(p -> p.sendMessage(broadcast));

        logger.info("[Maintenance] {} maintenance mode (actor: {}).",
                enabled ? "Enabled" : "Disabled", actorName);
    }

    /**
     * Updates the local maintenance flag without publishing to Redis.
     * Called by the Redis pub/sub handler.
     *
     * @param enabled new state
     */
    public void setMaintenanceLocal(boolean enabled) {
        maintenanceEnabled.set(enabled);
        if (enabled) kickNonAdminPlayers();
    }

    /** @return {@code true} if maintenance mode is currently active */
    public boolean isMaintenanceEnabled() {
        return maintenanceEnabled.get();
    }

    /**
     * Returns {@code true} if the given player is allowed to bypass maintenance.
     *
     * @param player the player to check
     * @return {@code true} if allowed in during maintenance
     */
    public boolean canBypass(Player player) {
        return player.hasPermission(PermissionUtil.ADMIN)
                || player.hasPermission(PermissionUtil.BYPASS_MAINTENANCE);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void kickNonAdminPlayers() {
        String rawMsg = plugin.getConfigManager()
                .getMessage("maintenance.kick-message",
                        "&cThe network is currently under maintenance.\n&7Please try again later.");
        Component kickMsg = ComponentUtil.parse(rawMsg);
        proxy.getAllPlayers().stream()
                .filter(p -> !canBypass(p))
                .forEach(p -> p.disconnect(kickMsg));
    }
}
