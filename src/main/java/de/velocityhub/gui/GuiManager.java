package de.velocityhub.gui;
import de.velocityhub.VelocityHubPlugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import de.velocityhub.model.NetworkServer;
import de.velocityhub.model.ServerGroup;
import de.velocityhub.model.ServerStatus;
import de.velocityhub.util.ComponentUtil;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Manages the server-selection GUI by sending plugin messages to the
 * companion backend plugin which handles the actual inventory rendering.
 *
 * <p>The proxy-side logic determines which servers are displayed and their
 * current state (player count, status, ping).  The actual inventory packet
 * sequence is performed by the backend plugin to maintain Velocity API
 * compatibility.</p>
 *
 * <p>When the backend plugin processes a GUI click, it sends a
 * {@code velocityhub:action} plugin message of type {@code gui-click} back
 * to the proxy, which then connects the player.</p>
 */
public final class GuiManager {

    private final VelocityHubPlugin plugin;
    private final Logger logger;

    public GuiManager(VelocityHubPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Opens the main server-selection GUI for the given player.
     *
     * <p>If the player is not currently on a backend server, a text-based
     * menu is shown as a fallback.</p>
     *
     * @param player the player to open the GUI for
     */
    public void openServerGui(Player player) {
        openGroupGui(player, null);
    }

    /**
     * Opens the server-selection GUI for a specific group.
     *
     * @param player    the player to open the GUI for
     * @param groupName the group name, or {@code null} for all servers
     */
    public void openGroupGui(Player player, String groupName) {
        if (player.getCurrentServer().isEmpty()) {
            sendTextMenu(player, groupName);
            return;
        }

        JsonObject payload = buildGuiPayload(player, groupName);

        // Send to backend via plugin message
        player.getCurrentServer().ifPresent(sc ->
                sc.sendPluginMessage(
                        net.kyori.adventure.key.Key.key("velocityhub", "gui"),
                        plugin.getGson().toJson(payload).getBytes(StandardCharsets.UTF_8)
                )
        );
    }

    // -------------------------------------------------------------------------
    // Payload building
    // -------------------------------------------------------------------------

    private JsonObject buildGuiPayload(Player player, String groupName) {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "open");
        payload.addProperty("player", player.getUniqueId().toString());

        // GUI title from config
        String title = plugin.getConfigManager().getGui()
                .node("gui", "title").getString("&8Server Selection");
        payload.addProperty("title", title);

        int rows = plugin.getConfigManager().getGui()
                .node("gui", "rows").getInt(6);
        payload.addProperty("rows", rows);

        // Collect servers to display
        List<NetworkServer> servers;
        if (groupName != null) {
            servers = plugin.getGroupManager().getGroup(groupName)
                    .map(g -> g.getServerNames().stream()
                            .map(n -> plugin.getServerManager().getServer(n))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .toList())
                    .orElse(Collections.emptyList());
        } else {
            servers = new ArrayList<>(plugin.getServerManager().getAllServers());
        }

        JsonArray serversArray = new JsonArray();
        for (NetworkServer ns : servers) {
            JsonObject serverObj = new JsonObject();
            serverObj.addProperty("name", ns.getName());
            serverObj.addProperty("displayName", ns.getDisplayName());
            serverObj.addProperty("motd", ns.getMotd());
            serverObj.addProperty("icon", ns.getIcon());
            serverObj.addProperty("players", ns.getOnlinePlayers());
            serverObj.addProperty("maxPlayers", ns.getMaxPlayers());
            serverObj.addProperty("status", ns.getStatus().name());
            serverObj.addProperty("statusColor", getStatusColor(ns.getStatus()));
            serversArray.add(serverObj);
        }
        payload.add("servers", serversArray);

        return payload;
    }

    // -------------------------------------------------------------------------
    // Text-based fallback menu
    // -------------------------------------------------------------------------

    /**
     * Sends a text-based server selection menu to the player as a fallback
     * when the GUI cannot be opened (e.g. player not on a backend server).
     *
     * @param player    the player
     * @param groupName the group name, or {@code null} for all servers
     */
    public void sendTextMenu(Player player, String groupName) {
        player.sendMessage(ComponentUtil.parse(
                "&8&m------&r &6Server Selection &8&m------"));

        List<NetworkServer> servers;
        if (groupName != null) {
            servers = plugin.getGroupManager().getGroup(groupName)
                    .map(g -> g.getServerNames().stream()
                            .map(n -> plugin.getServerManager().getServer(n))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .toList())
                    .orElse(Collections.emptyList());
        } else {
            servers = new ArrayList<>(plugin.getServerManager().getAllServers());
        }

        for (NetworkServer ns : servers) {
            String statusColor = getStatusColor(ns.getStatus());
            String line = ComponentUtil.replace(
                    "&7 ▸ %color%%displayName% &8[&7%players%&8/&7%max%&8] &8- %status%",
                    "color",       statusColor,
                    "displayName", ns.getDisplayName(),
                    "players",     String.valueOf(ns.getOnlinePlayers()),
                    "max",         String.valueOf(ns.getMaxPlayers()),
                    "status",      ns.getStatus().getDisplayName());

            // Build a clickable message using adventure's event API
            net.kyori.adventure.text.Component component = net.kyori.adventure.text.Component
                    .text()
                    .append(ComponentUtil.parse(line))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/server " + ns.getName()))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            ComponentUtil.parse("&7Click to connect to &e" + ns.getDisplayName())))
                    .build();

            player.sendMessage(component);
        }
        player.sendMessage(ComponentUtil.parse("&8&m----------------------------"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String getStatusColor(ServerStatus status) {
        return switch (status) {
            case ONLINE      -> "&a";
            case STARTING    -> "&e";
            case STOPPING    -> "&6";
            case MAINTENANCE -> "&8";
            case FULL        -> "&c";
            default          -> "&4";
        };
    }
}
