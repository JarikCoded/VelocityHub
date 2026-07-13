package de.velocityhub.manager;
import de.velocityhub.VelocityHubPlugin;

import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.velocityhub.api.event.ServerStatusChangeEvent;
import de.velocityhub.model.NetworkServer;
import de.velocityhub.model.ServerStatus;
import de.velocityhub.redis.RedisChannels;
import de.velocityhub.redis.RedisKeys;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the known set of {@link NetworkServer}s, propagates status/count
 * changes across proxies via Redis, and handles player transfers.
 */
public final class ServerManager {

    private final VelocityHubPlugin plugin;
    private final ProxyServer proxy;
    private final Logger logger;

    /** Live map of server name (lower-cased) → NetworkServer. */
    private final Map<String, NetworkServer> servers = new ConcurrentHashMap<>();

    public ServerManager(VelocityHubPlugin plugin, ProxyServer proxy, Logger logger) {
        this.plugin = plugin;
        this.proxy  = proxy;
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * (Re-)loads servers from {@code servers.yml} and syncs with Redis.
     */
    public void loadFromConfig() {
        servers.clear();
        CommentedConfigurationNode serversNode = plugin.getConfigManager().getServers()
                .node("servers");
        if (serversNode == null || serversNode.virtual()) {
            logger.warn("[ServerManager] No servers defined in servers.yml.");
            return;
        }
        serversNode.childrenMap().forEach((nameKey, node) -> {
            String name        = nameKey.toString();
            String displayName = node.node("display-name").getString(name);
            String motd        = node.node("motd").getString("");
            String icon        = node.node("icon").getString("STONE");
            int    maxPlayers  = node.node("max-players").getInt(100);

            NetworkServer ns = new NetworkServer(name, displayName, motd, icon, maxPlayers);

            // Restore cached status from Redis
            plugin.getRedisManager().get(String.format(RedisKeys.SERVER_STATUS, name))
                    .thenAccept(opt -> opt.ifPresent(s -> ns.setStatus(ServerStatus.fromString(s))));
            plugin.getRedisManager().get(String.format(RedisKeys.SERVER_PLAYERS, name))
                    .thenAccept(opt -> opt.ifPresent(s -> ns.setOnlinePlayers(parseIntSafe(s))));

            servers.put(name.toLowerCase(), ns);
            logger.debug("[ServerManager] Registered server: {}", name);
        });
        logger.info("[ServerManager] Loaded {} server(s).", servers.size());
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /**
     * Returns a server by its internal name (case-insensitive).
     *
     * @param name the server name
     * @return an {@link Optional} containing the server, or empty
     */
    public Optional<NetworkServer> getServer(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(servers.get(name.toLowerCase()));
    }

    /**
     * Returns all known servers as an unmodifiable collection.
     *
     * @return collection of servers
     */
    public Collection<NetworkServer> getAllServers() {
        return Collections.unmodifiableCollection(servers.values());
    }

    /** @return the total number of players across all known servers */
    public int getTotalOnlinePlayers() {
        return servers.values().stream().mapToInt(NetworkServer::getOnlinePlayers).sum();
    }

    // -------------------------------------------------------------------------
    // Mutations (with Redis propagation)
    // -------------------------------------------------------------------------

    /**
     * Updates the status of a server both locally and, if {@code propagate} is
     * {@code true}, across all connected proxies via Redis.
     *
     * @param serverName the server name (case-insensitive)
     * @param statusStr  the new status string
     * @param propagate  whether to publish the change to Redis
     */
    public void updateServerStatus(String serverName, String statusStr, boolean propagate) {
        NetworkServer ns = servers.get(serverName.toLowerCase());
        if (ns == null) {
            logger.debug("[ServerManager] Unknown server in status update: {}", serverName);
            return;
        }
        ServerStatus oldStatus = ns.getStatus();
        ServerStatus newStatus = ServerStatus.fromString(statusStr);
        ns.setStatus(newStatus);

        // Persist in Redis
        plugin.getRedisManager().set(
                String.format(RedisKeys.SERVER_STATUS, serverName), statusStr);

        // Fire API event
        proxy.getEventManager().fireAndForget(
                new ServerStatusChangeEvent(ns, oldStatus, newStatus));

        if (propagate) {
            JsonObject payload = new JsonObject();
            payload.addProperty("server", serverName);
            payload.addProperty("status", statusStr);
            plugin.getRedisManager().publish(RedisChannels.SERVER_STATUS, payload);
        }
    }

    /**
     * Updates the online player count for a server.
     *
     * @param serverName the server name
     * @param count      the new player count
     * @param propagate  whether to publish to Redis
     */
    public void updatePlayerCount(String serverName, int count, boolean propagate) {
        NetworkServer ns = servers.get(serverName.toLowerCase());
        if (ns == null) return;
        ns.setOnlinePlayers(count);
        plugin.getRedisManager().set(
                String.format(RedisKeys.SERVER_PLAYERS, serverName), String.valueOf(count));

        if (propagate) {
            JsonObject payload = new JsonObject();
            payload.addProperty("server", serverName);
            payload.addProperty("count", count);
            plugin.getRedisManager().publish(RedisChannels.SERVER_PLAYERS, payload);
        }
    }

    // -------------------------------------------------------------------------
    // Remote update handler (called from RedisPubSubHandler)
    // -------------------------------------------------------------------------

    /**
     * Handles a remote server-update event (add/remove/update).
     *
     * @param action  the action string ({@code "add"}, {@code "remove"}, {@code "update"})
     * @param payload the JSON payload
     */
    public void handleRemoteServerUpdate(String action, JsonObject payload) {
        switch (action.toLowerCase()) {
            case "add", "update" -> {
                String name        = payload.get("name").getAsString();
                String displayName = payload.get("displayName").getAsString();
                String motd        = payload.get("motd").getAsString();
                String icon        = payload.get("icon").getAsString();
                int    maxPlayers  = payload.get("maxPlayers").getAsInt();

                NetworkServer existing = servers.get(name.toLowerCase());
                if (existing != null) {
                    existing.setDisplayName(displayName);
                    existing.setMotd(motd);
                    existing.setIcon(icon);
                    existing.setMaxPlayers(maxPlayers);
                } else {
                    servers.put(name.toLowerCase(),
                            new NetworkServer(name, displayName, motd, icon, maxPlayers));
                }
            }
            case "remove" -> {
                String name = payload.get("name").getAsString();
                servers.remove(name.toLowerCase());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Player transfer
    // -------------------------------------------------------------------------

    /**
     * If a player with the given UUID is on THIS proxy, connects them to
     * {@code targetServer}.
     *
     * <p>The {@code targetProxy} parameter may be {@code null} to indicate
     * "any proxy", in which case the transfer is always attempted.</p>
     *
     * @param playerUuidStr UUID string of the player to transfer
     * @param targetServer  the server to connect the player to
     * @param targetProxy   the proxy ID to restrict the transfer to, or {@code null}
     */
    public void transferPlayerIfOnThisProxy(String playerUuidStr,
                                            String targetServer,
                                            String targetProxy) {
        String myProxyId = plugin.getConfigManager().getProxyId();
        if (targetProxy != null && !targetProxy.equals(myProxyId)) return;

        try {
            UUID uuid = UUID.fromString(playerUuidStr);
            proxy.getPlayer(uuid).ifPresent(player -> {
                Optional<RegisteredServer> rs = proxy.getServer(targetServer);
                rs.ifPresent(server ->
                        player.createConnectionRequest(server).fireAndForget());
            });
        } catch (IllegalArgumentException e) {
            logger.warn("[ServerManager] Invalid UUID in transfer request: {}", playerUuidStr);
        }
    }

    /**
     * Sends a player to a server and publishes the transfer to Redis so other
     * proxies can act if the player is not on this proxy.
     *
     * @param player       the player to send
     * @param serverName   the target server name
     */
    public void sendPlayerToServer(Player player, String serverName) {
        Optional<RegisteredServer> rs = proxy.getServer(serverName);
        if (rs.isPresent()) {
            player.createConnectionRequest(rs.get()).fireAndForget();
        } else {
            // Publish to Redis; another proxy may handle it
            JsonObject payload = new JsonObject();
            payload.addProperty("player", player.getUniqueId().toString());
            payload.addProperty("server", serverName);
            plugin.getRedisManager().publish(RedisChannels.PLAYER_TRANSFER, payload);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return 0; }
    }
}
