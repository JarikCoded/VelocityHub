package de.velocityhub.command;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import de.velocityhub.model.ServerGroup;
import de.velocityhub.util.ComponentUtil;
import de.velocityhub.util.PermissionUtil;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code /send <player|all> <server|group>} — Sends one or all players to a
 * server or group.
 *
 * <p>Examples:
 * <pre>
 *   /send Steve lobby-1
 *   /send Steve Lobby        (group)
 *   /send all survival-1
 *   /send all Survival       (group)
 * </pre>
 * </p>
 */
public final class SendCommand implements SimpleCommand {

    private final VelocityHubPlugin plugin;

    public SendCommand(VelocityHubPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission(PermissionUtil.CMD_SEND)
                && !source.hasPermission(PermissionUtil.ADMIN)) {
            source.sendMessage(ComponentUtil.parse(
                    plugin.getConfigManager().getMessage("errors.no-permission",
                            "&cYou do not have permission to use this command.")));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 2) {
            source.sendMessage(ComponentUtil.parse(
                    "&cUsage: /send <player|all> <server|group>"));
            return;
        }

        String targetArg  = args[0];
        String destination = args[1];

        // Resolve destination (server or group)
        boolean isGroup = plugin.getGroupManager().getGroup(destination).isPresent();
        boolean isServer = plugin.getServerManager().getServer(destination).isPresent();

        if (!isGroup && !isServer) {
            source.sendMessage(ComponentUtil.parse(
                    ComponentUtil.replace(
                            plugin.getConfigManager().getMessage("errors.target-not-found",
                                    "&cNo server or group named &e%name% &cwas found."),
                            "name", destination)));
            return;
        }

        if ("all".equalsIgnoreCase(targetArg)) {
            // Send all players
            for (Player p : plugin.getProxy().getAllPlayers()) {
                sendToDestination(p, destination, isGroup);
            }
            source.sendMessage(ComponentUtil.parse(
                    ComponentUtil.replace(
                            plugin.getConfigManager().getMessage("send.all",
                                    "&aSent all players to &e%destination%&a."),
                            "destination", destination)));
        } else {
            // Send specific player
            Optional<Player> playerOpt = plugin.getProxy().getAllPlayers().stream()
                    .filter(p -> p.getUsername().equalsIgnoreCase(targetArg))
                    .findFirst();

            if (playerOpt.isEmpty()) {
                source.sendMessage(ComponentUtil.parse(
                        ComponentUtil.replace(
                                plugin.getConfigManager().getMessage("errors.player-not-found",
                                        "&cPlayer &e%player% &cwas not found."),
                                "player", targetArg)));
                return;
            }

            Player target = playerOpt.get();
            sendToDestination(target, destination, isGroup);
            source.sendMessage(ComponentUtil.parse(
                    ComponentUtil.replace(
                            plugin.getConfigManager().getMessage("send.player",
                                    "&aSent &e%player% &ato &e%destination%&a."),
                            "player", target.getUsername(),
                            "destination", destination)));
        }
    }

    private void sendToDestination(Player player, String destination, boolean isGroup) {
        if (isGroup) {
            plugin.getGroupManager().getGroup(destination).ifPresent(group ->
                    group.getServerNames().stream()
                            .map(s -> plugin.getServerManager().getServer(s))
                            .filter(java.util.Optional::isPresent)
                            .map(java.util.Optional::get)
                            .filter(ns -> ns.isJoinable())
                            .findFirst()
                            .ifPresent(ns -> plugin.getServerManager()
                                    .sendPlayerToServer(player, ns.getName())));
        } else {
            plugin.getServerManager().sendPlayerToServer(player, destination);
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String partial = args.length == 0 ? "" : args[0];
            List<String> suggestions = new java.util.ArrayList<>();
            suggestions.add("all");
            plugin.getProxy().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(n -> n.toLowerCase().startsWith(partial.toLowerCase()))
                    .forEach(suggestions::add);
            return suggestions;
        } else if (args.length == 2) {
            String partial = args[1];
            List<String> suggestions = new java.util.ArrayList<>();
            plugin.getServerManager().getAllServers().stream()
                    .map(ns -> ns.getName())
                    .filter(n -> n.toLowerCase().startsWith(partial.toLowerCase()))
                    .forEach(suggestions::add);
            plugin.getGroupManager().getAllGroups().stream()
                    .map(g -> g.getName())
                    .filter(n -> n.toLowerCase().startsWith(partial.toLowerCase()))
                    .forEach(suggestions::add);
            return suggestions;
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PermissionUtil.CMD_SEND)
                || invocation.source().hasPermission(PermissionUtil.ADMIN);
    }
}
