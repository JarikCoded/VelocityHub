package de.velocityhub.api.event;

import de.velocityhub.model.NetworkServer;
import de.velocityhub.model.ServerStatus;

/**
 * Fired when the status of a {@link NetworkServer} changes, either locally
 * or as a result of a Redis pub/sub message.
 */
public final class ServerStatusChangeEvent extends AbstractVelocityHubEvent {

    private final NetworkServer server;
    private final ServerStatus  oldStatus;
    private final ServerStatus  newStatus;

    /**
     * @param server    the affected server
     * @param oldStatus the previous status
     * @param newStatus the new status
     */
    public ServerStatusChangeEvent(NetworkServer server,
                                   ServerStatus oldStatus,
                                   ServerStatus newStatus) {
        this.server    = server;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    /** @return the affected server */
    public NetworkServer getServer() { return server; }

    /** @return the status before the change */
    public ServerStatus getOldStatus() { return oldStatus; }

    /** @return the status after the change */
    public ServerStatus getNewStatus() { return newStatus; }
}
