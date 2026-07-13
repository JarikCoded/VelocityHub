package de.velocityhub.api.event;

import com.velocitypowered.api.proxy.Player;

/**
 * Fired when a network-wide broadcast is about to be sent.
 *
 * <p>Cancelling this event prevents the broadcast from being delivered
 * locally and from being published to Redis.</p>
 */
public final class BroadcastEvent extends AbstractVelocityHubEvent {

    private String message;
    private final Player sender;

    /**
     * @param message the broadcast message (may contain colour codes)
     * @param sender  the player who triggered the broadcast, or {@code null} for console
     */
    public BroadcastEvent(String message, Player sender) {
        this.message = message;
        this.sender  = sender;
    }

    /** @return the broadcast message */
    public String getMessage() { return message; }

    /**
     * Replaces the broadcast message.
     *
     * @param message the new message
     */
    public void setMessage(String message) { this.message = message; }

    /**
     * Returns the player who sent the broadcast, or {@code null} if sent from console.
     *
     * @return the sender, possibly {@code null}
     */
    public Player getSender() { return sender; }
}
