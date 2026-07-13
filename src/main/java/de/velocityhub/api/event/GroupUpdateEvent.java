package de.velocityhub.api.event;

import de.velocityhub.model.ServerGroup;

/**
 * Fired when a {@link ServerGroup} is added, updated, or removed via Redis.
 */
public final class GroupUpdateEvent extends AbstractVelocityHubEvent {

    private final ServerGroup group;
    private final String action;

    /**
     * @param group  the group that was modified
     * @param action the action string ({@code "add"}, {@code "update"}, {@code "remove"})
     */
    public GroupUpdateEvent(ServerGroup group, String action) {
        this.group  = group;
        this.action = action;
    }

    /** @return the affected group */
    public ServerGroup getGroup() { return group; }

    /** @return the action that triggered this event */
    public String getAction() { return action; }
}
