package de.velocityhub.command;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import de.velocityhub.model.PlayerData;
import de.velocityhub.util.ComponentUtil;
import de.velocityhub.util.PermissionUtil;

import java.util.List;
import java.util.Optional;

/**
 * {@code /socialspy [on|off]} — Toggles social spy (read all private messages).
 */
public final class SocialSpyCommand implements SimpleCommand {

    private final VelocityHubPlugin plugin;

    public SocialSpyCommand(VelocityHubPlugin plugin) {
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
        if (!player.hasPermission(PermissionUtil.CMD_SOCIALSPY)
                && !player.hasPermission(PermissionUtil.ADMIN)) {
            player.sendMessage(ComponentUtil.parse(
                    plugin.getConfigManager().getMessage("errors.no-permission",
                            "&cYou do not have permission to use this command.")));
            return;
        }

        PlayerData data = plugin.getPlayerDataManager()
                .getOrCreate(player.getUniqueId(), player.getUsername());

        String[] args = invocation.arguments();
        boolean enable;
        if (args.length > 0) {
            enable = "on".equalsIgnoreCase(args[0]);
        } else {
            enable = !data.isSocialSpyEnabled();
        }

        data.setSocialSpyEnabled(enable);

        String msgKey = enable ? "social-spy.enabled" : "social-spy.disabled";
        String def    = enable ? "&aSocial Spy enabled." : "&cSocial Spy disabled.";
        player.sendMessage(ComponentUtil.parse(
                plugin.getConfigManager().getMessage(msgKey, def)));
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
        return invocation.source().hasPermission(PermissionUtil.CMD_SOCIALSPY)
                || invocation.source().hasPermission(PermissionUtil.ADMIN);
    }
}
