package de.velocityhub.model;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a Minecraft server managed by VelocityHub.
 *
 * <p>All mutable fields use atomic references so that cross-thread reads
 * from Redis pub/sub handlers are safe without explicit synchronization.</p>
 */
public final class NetworkServer {

    private final String name;
    private final AtomicReference<String> displayName;
    private final AtomicReference<String> motd;
    private final AtomicReference<String> icon;
    private final AtomicInteger onlinePlayers;
    private final AtomicInteger maxPlayers;
    private final AtomicReference<ServerStatus> status;

    /**
     * Creates a new {@code NetworkServer} with default values.
     *
     * @param name        the internal identifier of this server (e.g. {@code lobby-1})
     * @param displayName the human-readable display name
     * @param motd        the server's message of the day shown in GUIs
     * @param icon        the Minecraft material name used as the GUI icon
     * @param maxPlayers  the maximum number of players allowed on this server
     */
    public NetworkServer(String name, String displayName, String motd,
                         String icon, int maxPlayers) {
        this.name = Objects.requireNonNull(name, "name");
        this.displayName = new AtomicReference<>(displayName != null ? displayName : name);
        this.motd = new AtomicReference<>(motd != null ? motd : "");
        this.icon = new AtomicReference<>(icon != null ? icon : "STONE");
        this.onlinePlayers = new AtomicInteger(0);
        this.maxPlayers = new AtomicInteger(maxPlayers > 0 ? maxPlayers : 100);
        this.status = new AtomicReference<>(ServerStatus.OFFLINE);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** @return the internal identifier of this server */
    public String getName() { return name; }

    /** @return the human-readable display name */
    public String getDisplayName() { return displayName.get(); }

    /** @param displayName new display name */
    public void setDisplayName(String displayName) {
        this.displayName.set(Objects.requireNonNull(displayName));
    }

    /** @return the MOTD shown in server-selection GUIs */
    public String getMotd() { return motd.get(); }

    /** @param motd new MOTD */
    public void setMotd(String motd) {
        this.motd.set(motd != null ? motd : "");
    }

    /** @return the Minecraft material name used as a GUI icon */
    public String getIcon() { return icon.get(); }

    /** @param icon new icon material name */
    public void setIcon(String icon) {
        this.icon.set(icon != null ? icon : "STONE");
    }

    /** @return current number of online players reported by Redis */
    public int getOnlinePlayers() { return onlinePlayers.get(); }

    /** @param count new player count */
    public void setOnlinePlayers(int count) { onlinePlayers.set(Math.max(0, count)); }

    /** @return maximum player capacity */
    public int getMaxPlayers() { return maxPlayers.get(); }

    /** @param max new maximum player count */
    public void setMaxPlayers(int max) { maxPlayers.set(Math.max(1, max)); }

    /** @return the current {@link ServerStatus} */
    public ServerStatus getStatus() { return status.get(); }

    /** @param status new server status */
    public void setStatus(ServerStatus status) {
        this.status.set(Objects.requireNonNull(status));
    }

    // -------------------------------------------------------------------------
    // Derived helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the server is currently accepting regular players
     * (status is {@link ServerStatus#ONLINE} and not at full capacity).
     */
    public boolean isJoinable() {
        return status.get() == ServerStatus.ONLINE
                && onlinePlayers.get() < maxPlayers.get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkServer other)) return false;
        return name.equalsIgnoreCase(other.name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return "NetworkServer{name='" + name + "', status=" + status.get()
                + ", players=" + onlinePlayers.get() + "/" + maxPlayers.get() + "}";
    }
}
