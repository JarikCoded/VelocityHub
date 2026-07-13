package de.velocityhub.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a player waiting in the queue for a specific server.
 *
 * <p>Queue entries are ranked by priority (higher = sooner) and by join
 * timestamp so that ties are broken by first-come-first-served ordering.</p>
 */
public final class QueueEntry implements Comparable<QueueEntry> {

    private final UUID playerId;
    private final String playerName;
    private final String targetServer;
    private final int priority;
    private final long joinedAt;

    /**
     * Creates a new queue entry.
     *
     * @param playerId     the UUID of the queued player
     * @param playerName   the display name of the queued player
     * @param targetServer the server the player is waiting for
     * @param priority     queue priority (higher value = higher priority)
     */
    public QueueEntry(UUID playerId, String playerName, String targetServer, int priority) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.playerName = Objects.requireNonNull(playerName, "playerName");
        this.targetServer = Objects.requireNonNull(targetServer, "targetServer");
        this.priority = priority;
        this.joinedAt = System.currentTimeMillis();
    }

    /** @return the player's UUID */
    public UUID getPlayerId() { return playerId; }

    /** @return the player's display name */
    public String getPlayerName() { return playerName; }

    /** @return the target server name */
    public String getTargetServer() { return targetServer; }

    /** @return the queue priority (higher = earlier) */
    public int getPriority() { return priority; }

    /** @return the Unix timestamp (ms) when the entry was created */
    public long getJoinedAt() { return joinedAt; }

    /**
     * Sorts entries by descending priority, then ascending join time so
     * higher-priority players come first, and ties are broken FIFO.
     */
    @Override
    public int compareTo(QueueEntry other) {
        int cmp = Integer.compare(other.priority, this.priority); // descending
        if (cmp != 0) return cmp;
        return Long.compare(this.joinedAt, other.joinedAt); // ascending (FIFO)
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueueEntry other)) return false;
        return playerId.equals(other.playerId) && targetServer.equals(other.targetServer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, targetServer);
    }

    @Override
    public String toString() {
        return "QueueEntry{player='" + playerName + "', server='" + targetServer
                + "', priority=" + priority + "}";
    }
}
