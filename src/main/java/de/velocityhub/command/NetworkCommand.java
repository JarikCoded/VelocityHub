package de.velocityhub.command;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import de.velocityhub.model.NetworkServer;
import de.velocityhub.model.ServerGroup;
import de.velocityhub.util.ComponentUtil;
import de.velocityhub.util.PermissionUtil;

import java.util.Collection;
import java.util.List;

/**
 * {@code /network <sub-command>} — Main administration command.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code reload}        — Reloads all configuration files</li>
 *   <li>{@code status}        — Shows network status overview</li>
 *   <li>{@code server list}   — Lists all known servers</li>
 *   <li>{@code group list}    — Lists all known groups</li>
 *   <li>{@code redis status}  — Shows Redis connection status</li>
 *   <li>{@code maintenance}   — Alias for /maintenance</li>
 * </ul>
 * </p>
 */
public final class NetworkCommand implements SimpleCommand {

    private static final List<String> SUB_COMMANDS = List.of(
            "reload", "status", "server", "group", "redis", "maintenance");

    private final VelocityHubPlugin plugin;

    public NetworkCommand(VelocityHubPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission(PermissionUtil.CMD_NETWORK)
                && !source.hasPermission(PermissionUtil.ADMIN)) {
            source.sendMessage(ComponentUtil.parse(
                    plugin.getConfigManager().getMessage("errors.no-permission",
                            "&cYou do not have permission to use this command.")));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            sendHelp(source);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload"      -> handleReload(source);
            case "status"      -> handleStatus(source);
            case "server"      -> handleServer(source, args);
            case "group"       -> handleGroup(source, args);
            case "redis"       -> handleRedis(source);
            case "maintenance" -> handleMaintenance(source, args);
            default -> sendHelp(source);
        }
    }

    // -------------------------------------------------------------------------
    // Sub-command handlers
    // -------------------------------------------------------------------------

    private void handleReload(CommandSource source) {
        plugin.getConfigManager().reloadAll();
        source.sendMessage(ComponentUtil.parse(
                plugin.getConfigManager().getMessage("network.reload-success",
                        "&aConfiguration reloaded successfully.")));
    }

    private void handleStatus(CommandSource source) {
        int totalPlayers = plugin.getProxy().getPlayerCount();
        int serverCount  = plugin.getServerManager().getAllServers().size();
        int groupCount   = plugin.getGroupManager().getAllGroups().size();
        boolean maintenance = plugin.getMaintenanceManager().isMaintenanceEnabled();
        boolean redis    = plugin.getRedisManager().isConnected();
        long uptimeMs    = plugin.getStatisticsManager().getUptimeMs();
        long uptimeMins  = uptimeMs / 60000;

        source.sendMessage(ComponentUtil.parse("&8&m---------&r &6Network Status &8&m---------"));
        source.sendMessage(ComponentUtil.parse("&7Proxy ID:      &e" + plugin.getConfigManager().getProxyId()));
        source.sendMessage(ComponentUtil.parse("&7Players:       &e" + totalPlayers));
        source.sendMessage(ComponentUtil.parse("&7Peak Players:  &e" + plugin.getStatisticsManager().getPeakPlayers()));
        source.sendMessage(ComponentUtil.parse("&7Servers:       &e" + serverCount));
        source.sendMessage(ComponentUtil.parse("&7Groups:        &e" + groupCount));
        source.sendMessage(ComponentUtil.parse("&7Maintenance:   " + (maintenance ? "&cEnabled" : "&aDisabled")));
        source.sendMessage(ComponentUtil.parse("&7Redis:         " + (redis ? "&aConnected" : "&cDisconnected")));
        source.sendMessage(ComponentUtil.parse("&7Uptime:        &e" + uptimeMins + " min"));
        source.sendMessage(ComponentUtil.parse("&8&m----------------------------------"));
    }

    private void handleServer(CommandSource source, String[] args) {
        if (args.length < 2) {
            // List all servers
            Collection<NetworkServer> servers = plugin.getServerManager().getAllServers();
            source.sendMessage(ComponentUtil.parse("&6Servers (" + servers.size() + "):"));
            for (NetworkServer ns : servers) {
                source.sendMessage(ComponentUtil.parse(
                        "&7  " + ns.getName() + " &8[&7" + ns.getOnlinePlayers()
                                + "&8/&7" + ns.getMaxPlayers() + "&8] &8- "
                                + ns.getStatus().getDisplayName()));
            }
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list" -> handleServer(source, new String[]{"server"});
            default     -> source.sendMessage(ComponentUtil.parse(
                    "&cUnknown sub-command. Use: /network server list"));
        }
    }

    private void handleGroup(CommandSource source, String[] args) {
        Collection<ServerGroup> groups = plugin.getGroupManager().getAllGroups();
        source.sendMessage(ComponentUtil.parse("&6Groups (" + groups.size() + "):"));
        for (ServerGroup g : groups) {
            source.sendMessage(ComponentUtil.parse(
                    "&7  " + g.getName() + " &8(&7" + g.getServerNames().size() + " servers&8)"));
        }
    }

    private void handleRedis(CommandSource source) {
        boolean connected = plugin.getRedisManager().isConnected();
        source.sendMessage(ComponentUtil.parse(
                "&7Redis: " + (connected ? "&aConnected" : "&cDisconnected")));
    }

    private void handleMaintenance(CommandSource source, String[] args) {
        Player sender = source instanceof Player p ? p : null;
        if (args.length < 2) {
            boolean current = plugin.getMaintenanceManager().isMaintenanceEnabled();
            source.sendMessage(ComponentUtil.parse(
                    "&7Maintenance: " + (current ? "&cEnabled" : "&aDisabled")));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "on"  -> plugin.getMaintenanceManager().setMaintenance(true,  sender);
            case "off" -> plugin.getMaintenanceManager().setMaintenance(false, sender);
            default    -> source.sendMessage(ComponentUtil.parse(
                    "&cUsage: /network maintenance [on|off]"));
        }
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(ComponentUtil.parse("&6VelocityHub &7Admin Commands:"));
        source.sendMessage(ComponentUtil.parse("&7  /network reload"));
        source.sendMessage(ComponentUtil.parse("&7  /network status"));
        source.sendMessage(ComponentUtil.parse("&7  /network server [list]"));
        source.sendMessage(ComponentUtil.parse("&7  /network group [list]"));
        source.sendMessage(ComponentUtil.parse("&7  /network redis"));
        source.sendMessage(ComponentUtil.parse("&7  /network maintenance [on|off]"));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String partial = args.length == 0 ? "" : args[0];
            return SUB_COMMANDS.stream()
                    .filter(s -> s.startsWith(partial.toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && "maintenance".equalsIgnoreCase(args[0])) {
            return List.of("on", "off");
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PermissionUtil.CMD_NETWORK)
                || invocation.source().hasPermission(PermissionUtil.ADMIN);
    }
}
