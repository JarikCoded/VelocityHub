package de.velocityhub.command;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import de.velocityhub.util.ComponentUtil;
import de.velocityhub.util.PermissionUtil;

import java.util.List;

/**
 * {@code /broadcast <message>} — Sends a network-wide broadcast.
 *
 * <p>Supports MiniMessage, legacy colour codes, and HEX colours.</p>
 */
public final class BroadcastCommand implements SimpleCommand {

    private final VelocityHubPlugin plugin;

    public BroadcastCommand(VelocityHubPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission(PermissionUtil.CMD_BROADCAST)
                && !source.hasPermission(PermissionUtil.ADMIN)) {
            source.sendMessage(ComponentUtil.parse(
                    plugin.getConfigManager().getMessage("errors.no-permission",
                            "&cYou do not have permission to use this command.")));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            source.sendMessage(ComponentUtil.parse("&cUsage: /broadcast <message>"));
            return;
        }

        String message = String.join(" ", args);
        Player sender  = source instanceof Player p ? p : null;
        plugin.getAnnouncementManager().broadcastNetwork(message, sender);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PermissionUtil.CMD_BROADCAST)
                || invocation.source().hasPermission(PermissionUtil.ADMIN);
    }
}
