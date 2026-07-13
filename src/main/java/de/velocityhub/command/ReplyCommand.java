package de.velocityhub.command;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import de.velocityhub.util.ComponentUtil;
import de.velocityhub.util.PermissionUtil;

import java.util.List;

/**
 * {@code /reply <message>} — Replies to the last private message received.
 */
public final class ReplyCommand implements SimpleCommand {

    private final VelocityHubPlugin plugin;

    public ReplyCommand(VelocityHubPlugin plugin) {
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
        if (args.length == 0) {
            sender.sendMessage(ComponentUtil.parse("&cUsage: /reply <message>"));
            return;
        }

        String message = String.join(" ", args);
        boolean success = plugin.getPrivateChatManager().reply(sender, message);

        if (!success) {
            sender.sendMessage(ComponentUtil.parse(
                    plugin.getConfigManager().getMessage("private-chat.no-reply-target",
                            "&cYou have no one to reply to.")));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PermissionUtil.CMD_MSG)
                || invocation.source().hasPermission(PermissionUtil.ADMIN);
    }
}
