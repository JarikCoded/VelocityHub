package de.velocityhub.api;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.proxy.Player;
import de.velocityhub.model.NetworkServer;
import de.velocityhub.model.ServerGroup;

import java.util.Collection;
import java.util.Optional;

/**
 * Public static API for VelocityHub.
 *
 * <p>Other plugins may use this class to interact with VelocityHub without
 * needing a direct plugin-object reference.</p>
 *
 * <p>Example usage:
 * <pre>{@code
 * NetworkAPI.connect(player, "survival-1");
 * NetworkAPI.broadcast("&aHello network!");
 * int online = NetworkAPI.getOnlinePlayers();
 * }</pre>
 * </p>
 */
public final class NetworkAPI {

    private NetworkAPI() {}

    // -------------------------------------------------------------------------
    // Player routing
    // -------------------------------------------------------------------------

    /**
     * Connects a player to the specified server.
     *
     * @param player     the player to connect
     * @param serverName the target server name
     */
    public static void connect(Player player, String serverName) {
        requirePlugin().getServerManager().sendPlayerToServer(player, serverName);
    }

    /**
     * Connects a player to the best available server in the specified group.
     *
     * @param player    the player to connect
     * @param groupName the target group name
     */
    public static void connectGroup(Player player, String groupName) {
        VelocityHubPlugin plugin = requirePlugin();
        plugin.getGroupManager().getGroup(groupName).ifPresent(group -> {
            group.getServerNames().stream()
                    .map(s -> plugin.getServerManager().getServer(s))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(NetworkServer::isJoinable)
                    .findFirst()
                    .ifPresent(ns -> plugin.getServerManager().sendPlayerToServer(player, ns.getName()));
        });
    }

    /**
     * Sends a player to the best available lobby server.
     *
     * @param player the player to send to the lobby
     */
    public static void sendToLobby(Player player) {
        VelocityHubPlugin plugin = requirePlugin();
        plugin.getGroupManager().getBestLobbyServer()
                .ifPresent(ns -> plugin.getServerManager().sendPlayerToServer(player, ns.getName()));
    }

    // -------------------------------------------------------------------------
    // Broadcasting
    // -------------------------------------------------------------------------

    /**
     * Broadcasts a message to all players on the entire network.
     *
     * @param message the message (supports MiniMessage / legacy colour codes)
     */
    public static void broadcast(String message) {
        requirePlugin().getAnnouncementManager().broadcastNetwork(message, null);
    }

    // -------------------------------------------------------------------------
    // Server queries
    // -------------------------------------------------------------------------

    /**
     * Returns a {@link NetworkServer} by name.
     *
     * @param name the server name
     * @return an {@link Optional} with the server
     */
    public static Optional<NetworkServer> getServer(String name) {
        return requirePlugin().getServerManager().getServer(name);
    }

    /**
     * Returns all known network servers.
     *
     * @return unmodifiable collection of all servers
     */
    public static Collection<NetworkServer> getAllServers() {
        return requirePlugin().getServerManager().getAllServers();
    }

    // -------------------------------------------------------------------------
    // Group queries
    // -------------------------------------------------------------------------

    /**
     * Returns a {@link ServerGroup} by name.
     *
     * @param name the group name
     * @return an {@link Optional} with the group
     */
    public static Optional<ServerGroup> getGroup(String name) {
        return requirePlugin().getGroupManager().getGroup(name);
    }

    /**
     * Returns all known server groups.
     *
     * @return unmodifiable collection of all groups
     */
    public static Collection<ServerGroup> getAllGroups() {
        return requirePlugin().getGroupManager().getAllGroups();
    }

    // -------------------------------------------------------------------------
    // Player statistics
    // -------------------------------------------------------------------------

    /**
     * Returns the total number of players currently online across all proxies.
     *
     * @return online player count
     */
    public static int getOnlinePlayers() {
        return requirePlugin().getServerManager().getTotalOnlinePlayers();
    }

    // -------------------------------------------------------------------------
    // Maintenance
    // -------------------------------------------------------------------------

    /**
     * Returns whether maintenance mode is currently active.
     *
     * @return {@code true} if in maintenance
     */
    public static boolean isMaintenanceEnabled() {
        return requirePlugin().getMaintenanceManager().isMaintenanceEnabled();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static VelocityHubPlugin requirePlugin() {
        VelocityHubPlugin plugin = VelocityHub.getInstance();
        if (plugin == null) {
            throw new IllegalStateException("VelocityHub is not currently loaded.");
        }
        return plugin;
    }
}
