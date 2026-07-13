package de.velocityhub.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds per-player data that is persisted across sessions via Redis.
 *
 * <p>Instances are cached in memory on the proxy while the player is online and
 * serialized to Redis for cross-proxy access.</p>
 */
public final class PlayerData {

    private final UUID uuid;
    private volatile String name;
    private volatile String lastServer;
    private volatile long lastSeen;

    /** Friends list (stores UUIDs as strings for easy JSON handling). */
    private final CopyOnWriteArrayList<String> friends;

    /** Ignore / block list. */
    private final CopyOnWriteArrayList<String> ignored;

    /** Favourite servers. */
    private final CopyOnWriteArrayList<String> favouriteServers;

    /** Recently visited servers (most recent first, capped at 10). */
    private final CopyOnWriteArrayList<String> recentServers;

    private volatile boolean socialSpyEnabled;
    private volatile boolean staffChatEnabled;
    private volatile boolean replyTarget; // whether this player has a pending /reply target

    /** The UUID of the last player this player sent a private message to. */
    private volatile String lastMsgTarget;

    /**
     * Creates a fresh {@code PlayerData} for the given player.
     *
     * @param uuid       the player's UUID
     * @param playerName the player's current username
     */
    public PlayerData(UUID uuid, String playerName) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = Objects.requireNonNull(playerName, "playerName");
        this.lastSeen = System.currentTimeMillis();
        this.friends = new CopyOnWriteArrayList<>();
        this.ignored = new CopyOnWriteArrayList<>();
        this.favouriteServers = new CopyOnWriteArrayList<>();
        this.recentServers = new CopyOnWriteArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Basic accessors
    // -------------------------------------------------------------------------

    /** @return the player's UUID */
    public UUID getUuid() { return uuid; }

    /** @return the player's current username */
    public String getName() { return name; }

    /** @param name updated username */
    public void setName(String name) { this.name = Objects.requireNonNull(name); }

    /** @return the name of the last server the player was on */
    public String getLastServer() { return lastServer; }

    /** @param server name of the server */
    public void setLastServer(String server) { this.lastServer = server; }

    /** @return Unix timestamp (ms) of the last login/logout */
    public long getLastSeen() { return lastSeen; }

    /** @param ts new timestamp */
    public void setLastSeen(long ts) { this.lastSeen = ts; }

    // -------------------------------------------------------------------------
    // Social
    // -------------------------------------------------------------------------

    /** @return an unmodifiable snapshot of the friends list */
    public List<String> getFriends() {
        return Collections.unmodifiableList(new ArrayList<>(friends));
    }

    /** Adds a friend UUID string. */
    public void addFriend(String uuidString) {
        if (uuidString != null && !friends.contains(uuidString)) friends.add(uuidString);
    }

    /** Removes a friend UUID string. */
    public void removeFriend(String uuidString) { friends.remove(uuidString); }

    /** @return {@code true} if the given UUID string is in the friends list */
    public boolean isFriend(String uuidString) { return friends.contains(uuidString); }

    /** @return an unmodifiable snapshot of the ignore list */
    public List<String> getIgnored() {
        return Collections.unmodifiableList(new ArrayList<>(ignored));
    }

    /** Adds an ignored UUID string. */
    public void addIgnored(String uuidString) {
        if (uuidString != null && !ignored.contains(uuidString)) ignored.add(uuidString);
    }

    /** Removes an ignored UUID string. */
    public void removeIgnored(String uuidString) { ignored.remove(uuidString); }

    /** @return {@code true} if the given UUID string is ignored by this player */
    public boolean isIgnoring(String uuidString) { return ignored.contains(uuidString); }

    // -------------------------------------------------------------------------
    // Server history
    // -------------------------------------------------------------------------

    /** @return unmodifiable favourite servers list */
    public List<String> getFavouriteServers() {
        return Collections.unmodifiableList(new ArrayList<>(favouriteServers));
    }

    /** Toggles a server in the favourites list. */
    public void toggleFavourite(String server) {
        if (favouriteServers.contains(server)) {
            favouriteServers.remove(server);
        } else {
            favouriteServers.add(server);
        }
    }

    /** @return unmodifiable recent servers list (most recent first) */
    public List<String> getRecentServers() {
        return Collections.unmodifiableList(new ArrayList<>(recentServers));
    }

    /**
     * Records a visit to a server, pushing it to the front of the recent list
     * and capping the list at 10 entries.
     *
     * @param server the server name to record
     */
    public void recordVisit(String server) {
        recentServers.remove(server);
        recentServers.add(0, server);
        while (recentServers.size() > 10) {
            recentServers.remove(recentServers.size() - 1);
        }
    }

    // -------------------------------------------------------------------------
    // Staff / mod features
    // -------------------------------------------------------------------------

    /** @return whether social spy is active for this player */
    public boolean isSocialSpyEnabled() { return socialSpyEnabled; }

    /** @param enabled new social spy state */
    public void setSocialSpyEnabled(boolean enabled) { this.socialSpyEnabled = enabled; }

    /** @return whether staff chat is toggled on for this player */
    public boolean isStaffChatEnabled() { return staffChatEnabled; }

    /** @param enabled new staff chat toggle state */
    public void setStaffChatEnabled(boolean enabled) { this.staffChatEnabled = enabled; }

    /** @return the UUID string of the last private message target, or {@code null} */
    public String getLastMsgTarget() { return lastMsgTarget; }

    /** @param target UUID string of the target */
    public void setLastMsgTarget(String target) { this.lastMsgTarget = target; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerData other)) return false;
        return uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() { return uuid.hashCode(); }

    @Override
    public String toString() {
        return "PlayerData{uuid=" + uuid + ", name='" + name + "'}";
    }
}
