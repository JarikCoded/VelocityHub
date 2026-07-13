package de.velocityhub.command;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import de.velocityhub.util.ComponentUtil;
import de.velocityhub.util.PermissionUtil;

import java.util.List;
import java.util.Optional;

/**
 * {@code /msg <player> <message>} — Sends a private message to another player.
 */
public final class MsgCommand implements SimpleCommand {

    private final VelocityHubPlugin plugin;

    public MsgCommand(VelocityHubPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player sender)) {
            source.sendMessage(ComponentUtil.parse(
                    plugin.getConfigManager().getMessage("errors.player-only",
                            "&cThis command can only be used by players.")));
            return;
        }
        if (!sender.hasPermission(PermissionUtil.CMD_MSG)
                && !sender.hasPermission(PermissionUtil.ADMIN)) {
            sender.sendMessage(ComponentUtil.parse(
                    plugin.getConfigManager().getMessage("errors.no-permission",
                            "&cYou do not have permission to use this command.")));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 2) {
            sender.sendMessage(ComponentUtil.parse("&cUsage: /msg <player> <message>"));
            return;
        }

        String targetName = args[0];
        String message    = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        plugin.getPrivateChatManager().sendPrivateMessage(sender, targetName, message);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            String partial = invocation.arguments().length == 0 ? "" : invocation.arguments()[0];
            return plugin.getProxy().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(n -> n.toLowerCase().startsWith(partial.toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PermissionUtil.CMD_MSG)
                || invocation.source().hasPermission(PermissionUtil.ADMIN);
    }
}
