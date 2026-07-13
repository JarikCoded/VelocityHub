package de.velocityhub.command;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import de.velocityhub.util.ComponentUtil;
import de.velocityhub.util.PermissionUtil;

import java.util.List;

/**
 * {@code /server [name]} — Opens the server-selection GUI or connects the
 * player directly to the named server.
 */
public final class ServerCommand implements SimpleCommand {

    private final VelocityHubPlugin plugin;

    public ServerCommand(VelocityHubPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player player)) {
            source.sendMessage(ComponentUtil.parse(
                    plugin.getConfigManager().getMessage("errors.player-only",
                            "&cThis command can only be used by players.")));
            return;
        }
        if (!player.hasPermission(PermissionUtil.CMD_SERVER)
                && !player.hasPermission(PermissionUtil.ADMIN)) {
            player.sendMessage(ComponentUtil.parse(
                    plugin.getConfigManager().getMessage("errors.no-permission",
                            "&cYou do not have permission to use this command.")));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            // Open the GUI
            plugin.getGuiManager().openServerGui(player);
        } else {
            String serverName = args[0];
            plugin.getServerManager().getServer(serverName).ifPresentOrElse(
                    ns -> plugin.getServerManager().sendPlayerToServer(player, ns.getName()),
                    () -> player.sendMessage(ComponentUtil.parse(
                            ComponentUtil.replace(
                                    plugin.getConfigManager().getMessage("errors.server-not-found",
                                            "&cServer &e%server% &cwas not found."),
                                    "server", serverName)))
            );
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            String partial = invocation.arguments().length == 0 ? "" : invocation.arguments()[0];
            return plugin.getServerManager().getAllServers().stream()
                    .map(ns -> ns.getName())
                    .filter(n -> n.toLowerCase().startsWith(partial.toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PermissionUtil.CMD_SERVER)
                || invocation.source().hasPermission(PermissionUtil.ADMIN);
    }
}
