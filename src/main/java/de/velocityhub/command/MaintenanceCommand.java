package de.velocityhub.command;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import de.velocityhub.util.ComponentUtil;
import de.velocityhub.util.PermissionUtil;

import java.util.List;

/**
 * {@code /maintenance [on|off]} — Toggles network maintenance mode.
 */
public final class MaintenanceCommand implements SimpleCommand {

    private final VelocityHubPlugin plugin;

    public MaintenanceCommand(VelocityHubPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission(PermissionUtil.CMD_MAINTENANCE)
                && !source.hasPermission(PermissionUtil.ADMIN)) {
            source.sendMessage(ComponentUtil.parse(
                    plugin.getConfigManager().getMessage("errors.no-permission",
                            "&cYou do not have permission to use this command.")));
            return;
        }

        String[] args = invocation.arguments();
        Player sender = source instanceof Player p ? p : null;

        if (args.length == 0) {
            // Toggle
            boolean current = plugin.getMaintenanceManager().isMaintenanceEnabled();
            plugin.getMaintenanceManager().setMaintenance(!current, sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "on"  -> plugin.getMaintenanceManager().setMaintenance(true,  sender);
            case "off" -> plugin.getMaintenanceManager().setMaintenance(false, sender);
            default -> source.sendMessage(ComponentUtil.parse(
                    "&cUsage: /maintenance [on|off]"));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            return List.of("on", "off");
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PermissionUtil.CMD_MAINTENANCE)
                || invocation.source().hasPermission(PermissionUtil.ADMIN);
    }
}
