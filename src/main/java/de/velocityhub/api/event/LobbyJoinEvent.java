package de.velocityhub.api.event;

import com.velocitypowered.api.proxy.Player;
import de.velocityhub.model.NetworkServer;

/**
 * Fired when a player is about to be connected to a lobby server via
 * VelocityHub's lobby commands ({@code /l}, {@code /hub}, {@code /lobby}).
 *
 * <p>Cancelling this event prevents the lobby connection from being
 * initiated.</p>
 */
public final class LobbyJoinEvent extends AbstractVelocityHubEvent {

    private final Player player;
    private NetworkServer targetServer;

    /**
     * @param player       the player being sent to the lobby
     * @param targetServer the chosen lobby server
     */
    public LobbyJoinEvent(Player player, NetworkServer targetServer) {
        this.player       = player;
        this.targetServer = targetServer;
    }

    /** @return the player being sent to the lobby */
    public Player getPlayer() { return player; }

    /** @return the target lobby server */
    public NetworkServer getTargetServer() { return targetServer; }

    /**
     * Overrides the lobby server selection.
     *
     * @param server the server to send the player to instead
     */
    public void setTargetServer(NetworkServer server) { this.targetServer = server; }
}
