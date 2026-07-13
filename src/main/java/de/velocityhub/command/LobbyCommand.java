package de.velocityhub.command;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import de.velocityhub.api.event.LobbyJoinEvent;
import de.velocityhub.model.NetworkServer;
import de.velocityhub.util.ComponentUtil;
import de.velocityhub.util.PermissionUtil;

import java.util.List;
import java.util.Optional;

/**
 * {@code /l}, {@code /hub}, {@code /lobby} — Connects the player to the
 * best available lobby server.
 */
public final class LobbyCommand implements SimpleCommand {

    private final VelocityHubPlugin plugin;

    public LobbyCommand(VelocityHubPlugin plugin) {
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
        if (!player.hasPermission(PermissionUtil.CMD_LOBBY)
                && !player.hasPermission(PermissionUtil.ADMIN)) {
            player.sendMessage(ComponentUtil.parse(
                    plugin.getConfigManager().getMessage("errors.no-permission",
                            "&cYou do not have permission to use this command.")));
            return;
        }

        Optional<NetworkServer> lobbyOpt = plugin.getGroupManager().getBestLobbyServer();
        if (lobbyOpt.isEmpty()) {
            player.sendMessage(ComponentUtil.parse(
                    plugin.getConfigManager().getMessage("lobby.no-server",
                            "&cNo lobby server is currently available.")));
            return;
        }

        NetworkServer target = lobbyOpt.get();

        // Fire API event (allows cancellation / server override)
        LobbyJoinEvent event = new LobbyJoinEvent(player, target);
        plugin.getProxy().getEventManager().fire(event).thenAccept(e -> {
            if (e.isCancelled()) return;
            plugin.getServerManager().sendPlayerToServer(player, e.getTargetServer().getName());
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PermissionUtil.CMD_LOBBY)
                || invocation.source().hasPermission(PermissionUtil.ADMIN);
    }
}
