package de.velocityhub.listener;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import de.velocityhub.api.event.PlayerNetworkSwitchEvent;
import de.velocityhub.model.NetworkServer;
import de.velocityhub.redis.RedisKeys;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Listens for server-switch events to update player data, Redis state,
 * deliver lobby items, and track statistics.
 */
public final class ServerSwitchListener {

    private final VelocityHubPlugin plugin;
    private final Logger logger;

    public ServerSwitchListener(VelocityHubPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player    = event.getPlayer();
        String newServer = event.getServer().getServerInfo().getName();
        String oldServer = event.getPreviousServer()
                .map(s -> s.getServerInfo().getName())
                .orElse(null);

        // Update Redis
        plugin.getRedisManager().set(
                String.format(RedisKeys.PLAYER_SERVER, player.getUniqueId()), newServer);

        // Update player data
        plugin.getPlayerDataManager().getPlayerData(player.getUniqueId())
                .ifPresent(data -> {
                    data.setLastServer(newServer);
                    data.recordVisit(newServer);
                });

        // Update server player counts (old server -1, new server +1)
        if (oldServer != null) {
            plugin.getServerManager().getServer(oldServer)
                    .ifPresent(ns -> plugin.getServerManager()
                            .updatePlayerCount(oldServer, ns.getOnlinePlayers() - 1, true));
        }
        plugin.getServerManager().getServer(newServer)
                .ifPresent(ns -> plugin.getServerManager()
                        .updatePlayerCount(newServer, ns.getOnlinePlayers() + 1, true));

        // Statistics
        plugin.getStatisticsManager().recordJoin(newServer);

        // Lobby item assignment
        plugin.getGroupManager().onPlayerJoinServer(player, newServer);

        // Fire API event
        Optional<NetworkServer> fromNs = oldServer != null
                ? plugin.getServerManager().getServer(oldServer)
                : Optional.empty();
        Optional<NetworkServer> toNs   = plugin.getServerManager().getServer(newServer);

        toNs.ifPresent(to -> plugin.getProxy().getEventManager().fireAndForget(
                new PlayerNetworkSwitchEvent(player, fromNs.orElse(null), to)));
    }
}
