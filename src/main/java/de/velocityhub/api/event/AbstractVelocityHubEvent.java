package de.velocityhub.api.event;

/**
 * Abstract base class for all VelocityHub API events.
 *
 * <p>All events are fired through the Velocity event bus and can be
 * listened to by other plugins.</p>
 */
public abstract class AbstractVelocityHubEvent {

    private boolean cancelled;

    /**
     * Returns whether this event has been cancelled.
     *
     * @return {@code true} if the event should be suppressed
     */
    public boolean isCancelled() { return cancelled; }

    /**
     * Sets the cancellation state of this event.
     *
     * @param cancelled {@code true} to cancel the event
     */
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
