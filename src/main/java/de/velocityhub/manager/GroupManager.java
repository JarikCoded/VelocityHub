package de.velocityhub.manager;
import de.velocityhub.VelocityHubPlugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.velocityhub.api.event.GroupUpdateEvent;
import de.velocityhub.model.NetworkServer;
import de.velocityhub.model.ServerGroup;
import de.velocityhub.redis.RedisChannels;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages server groups, handles lobby-item assignment when players join a
 * group's server, and synchronises group definitions across proxies.
 */
public final class GroupManager {

    private final VelocityHubPlugin plugin;
    private final ProxyServer proxy;
    private final Logger logger;

    /** Group name (lower-cased) → ServerGroup */
    private final Map<String, ServerGroup> groups = new ConcurrentHashMap<>();

    public GroupManager(VelocityHubPlugin plugin, ProxyServer proxy, Logger logger) {
        this.plugin = plugin;
        this.proxy  = proxy;
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * (Re-)loads groups from {@code groups.yml}.
     */
    public void loadFromConfig() {
        groups.clear();
        CommentedConfigurationNode groupsNode = plugin.getConfigManager().getGroups()
                .node("groups");
        if (groupsNode == null || groupsNode.virtual()) {
            logger.warn("[GroupManager] No groups defined in groups.yml.");
            return;
        }
        groupsNode.childrenMap().forEach((nameKey, node) -> {
            String name        = nameKey.toString();
            String displayName = node.node("display-name").getString(name);
            ServerGroup group  = new ServerGroup(name, displayName);

            // Servers in the group
            node.node("servers").childrenList()
                    .forEach(sn -> group.addServer(sn.getString("")));

            // Lobby item
            CommentedConfigurationNode itemNode = node.node("item");
            if (!itemNode.virtual()) {
                group.setItemMaterial(itemNode.node("material").getString("COMPASS"));
                group.setItemSlot(itemNode.node("slot").getInt(4));
                group.setItemName(itemNode.node("name").getString("&aServer Selection"));
                List<String> lore = new ArrayList<>();
                itemNode.node("lore").childrenList()
                        .forEach(ln -> lore.add(ln.getString("")));
                group.setItemLore(lore);
                group.setItemMovable(itemNode.node("movable").getBoolean(false));
                group.setItemDroppable(itemNode.node("droppable").getBoolean(false));
            }

            groups.put(name.toLowerCase(), group);
            logger.debug("[GroupManager] Registered group: {}", name);
        });
        logger.info("[GroupManager] Loaded {} group(s).", groups.size());
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /**
     * Returns a group by name (case-insensitive).
     *
     * @param name the group name
     * @return an {@link Optional} containing the group, or empty
     */
    public Optional<ServerGroup> getGroup(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(groups.get(name.toLowerCase()));
    }

    /**
     * Finds the group that contains the given server name.
     *
     * @param serverName the server name to look up
     * @return an {@link Optional} with the group, or empty if not in any group
     */
    public Optional<ServerGroup> getGroupForServer(String serverName) {
        return groups.values().stream()
                .filter(g -> g.containsServer(serverName))
                .findFirst();
    }

    /** @return unmodifiable collection of all groups */
    public Collection<ServerGroup> getAllGroups() {
        return Collections.unmodifiableCollection(groups.values());
    }

    // -------------------------------------------------------------------------
    // Lobby-item assignment
    // -------------------------------------------------------------------------

    /**
     * Called when a player joins a server to give them the group's lobby item
     * (if the server belongs to a group that has an item configured).
     *
     * <p>Delegates to the backend server via the {@code velocityhub:lobby-item}
     * plugin channel so that the actual item can be placed in inventory.</p>
     *
     * @param player     the player who switched servers
     * @param serverName the server they joined
     */
    public void onPlayerJoinServer(Player player, String serverName) {
        getGroupForServer(serverName).ifPresent(group -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("player", player.getUniqueId().toString());
            payload.addProperty("material", group.getItemMaterial());
            payload.addProperty("slot", group.getItemSlot());
            payload.addProperty("name", group.getItemName());
            JsonArray lore = new JsonArray();
            group.getItemLore().forEach(lore::add);
            payload.add("lore", lore);
            payload.addProperty("movable", group.isItemMovable());
            payload.addProperty("droppable", group.isItemDroppable());
            payload.addProperty("group", group.getName());

            // Send via plugin message channel to the current backend server
            player.getCurrentServer().ifPresent(sc ->
                    sc.sendPluginMessage(
                            net.kyori.adventure.key.Key.key("velocityhub", "lobby-item"),
                            plugin.getGson().toJson(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    )
            );
        });
    }

    // -------------------------------------------------------------------------
    // Lobby selection
    // -------------------------------------------------------------------------

    /**
     * Finds the best available lobby server from the "Lobby" group (or the
     * first group whose name contains "lobby"), using the strategy configured
     * in {@code config.yml}.
     *
     * @return an {@link Optional} with the preferred {@link NetworkServer}
     */
    public Optional<NetworkServer> getBestLobbyServer() {
        String strategy = plugin.getConfigManager().getMain()
                .node("lobby", "strategy").getString("lowest-players");
        String groupName = plugin.getConfigManager().getMain()
                .node("lobby", "group").getString("lobby");

        Optional<ServerGroup> lobbyGroupOpt = getGroup(groupName);
        if (lobbyGroupOpt.isEmpty()) {
            // fall back to any group whose name contains "lobby"
            lobbyGroupOpt = groups.values().stream()
                    .filter(g -> g.getName().toLowerCase().contains("lobby"))
                    .findFirst();
        }
        if (lobbyGroupOpt.isEmpty()) return Optional.empty();

        ServerGroup lobbyGroup = lobbyGroupOpt.get();
        List<NetworkServer> available = lobbyGroup.getServerNames().stream()
                .map(n -> plugin.getServerManager().getServer(n))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(NetworkServer::isJoinable)
                .toList();

        if (available.isEmpty()) return Optional.empty();

        return switch (strategy.toLowerCase()) {
            case "round-robin" -> getRoundRobin(available, lobbyGroup.getName());
            default            -> available.stream() // "lowest-players"
                    .min(Comparator.comparingInt(NetworkServer::getOnlinePlayers));
        };
    }

    private final Map<String, Integer> roundRobinIndex = new ConcurrentHashMap<>();

    private Optional<NetworkServer> getRoundRobin(List<NetworkServer> servers, String groupName) {
        int idx = roundRobinIndex.merge(groupName, 0, (old, ignored) -> (old + 1) % servers.size());
        return Optional.of(servers.get(idx));
    }

    // -------------------------------------------------------------------------
    // Remote update handler
    // -------------------------------------------------------------------------

    /**
     * Handles a remote group-update event received from Redis.
     *
     * @param action  the action string
     * @param payload the JSON payload
     */
    public void handleRemoteGroupUpdate(String action, JsonObject payload) {
        switch (action.toLowerCase()) {
            case "add", "update" -> {
                String name        = payload.get("name").getAsString();
                String displayName = payload.get("displayName").getAsString();
                ServerGroup group  = groups.computeIfAbsent(
                        name.toLowerCase(), k -> new ServerGroup(name, displayName));
                group.setDisplayName(displayName);
                group.getServerNames().forEach(group::removeServer);
                if (payload.has("servers")) {
                    payload.getAsJsonArray("servers")
                            .forEach(e -> group.addServer(e.getAsString()));
                }
                proxy.getEventManager().fireAndForget(
                        new GroupUpdateEvent(group, action));
            }
            case "remove" -> {
                String name = payload.get("name").getAsString();
                groups.remove(name.toLowerCase());
            }
        }
    }
}
