package de.velocityhub.command;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import de.velocityhub.model.PlayerData;
import de.velocityhub.util.ComponentUtil;
import de.velocityhub.util.PermissionUtil;

import java.util.List;

/**
 * {@code /staffchat [message]} — Toggles staff chat mode or sends a one-off
 * message to all staff members across the network.
 */
public final class StaffChatCommand implements SimpleCommand {

    private final VelocityHubPlugin plugin;

    public StaffChatCommand(VelocityHubPlugin plugin) {
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
        if (!player.hasPermission(PermissionUtil.CMD_STAFFCHAT)
                && !player.hasPermission(PermissionUtil.ADMIN)) {
            player.sendMessage(ComponentUtil.parse(
                    plugin.getConfigManager().getMessage("errors.no-permission",
                            "&cYou do not have permission to use this command.")));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            // Toggle staff chat mode
            PlayerData data = plugin.getPlayerDataManager()
                    .getOrCreate(player.getUniqueId(), player.getUsername());
            boolean toggle = !data.isStaffChatEnabled();
            data.setStaffChatEnabled(toggle);

            String msgKey = toggle ? "staff-chat.toggled-on" : "staff-chat.toggled-off";
            String def    = toggle ? "&aStaff Chat enabled." : "&cStaff Chat disabled.";
            player.sendMessage(ComponentUtil.parse(
                    plugin.getConfigManager().getMessage(msgKey, def)));
        } else {
            // Send immediate message
            String message = String.join(" ", args);
            plugin.getStaffChatManager().sendStaffMessage(player, message);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PermissionUtil.CMD_STAFFCHAT)
                || invocation.source().hasPermission(PermissionUtil.ADMIN);
    }
}
