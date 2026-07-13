package de.velocityhub.api.event;

import com.velocitypowered.api.proxy.Player;
import de.velocityhub.model.NetworkServer;

/**
 * Fired when a player switches from one network server to another.
 *
 * <p>This event fires in addition to (not instead of) Velocity's own
 * {@link com.velocitypowered.api.event.player.ServerConnectedEvent}.</p>
 */
public final class PlayerNetworkSwitchEvent extends AbstractVelocityHubEvent {

    private final Player player;
    private final NetworkServer from;
    private final NetworkServer to;

    /**
     * @param player the player switching servers
     * @param from   the server they are leaving (may be {@code null} on first join)
     * @param to     the server they are joining
     */
    public PlayerNetworkSwitchEvent(Player player, NetworkServer from, NetworkServer to) {
        this.player = player;
        this.from   = from;
        this.to     = to;
    }

    /** @return the player switching servers */
    public Player getPlayer() { return player; }

    /**
     * Returns the server the player is leaving.
     *
     * @return the previous server, or {@code null} if this is their first join
     */
    public NetworkServer getFrom() { return from; }

    /** @return the server the player is joining */
    public NetworkServer getTo() { return to; }
}
