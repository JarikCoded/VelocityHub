package de.velocityhub.model;

/**
 * Represents the status of a network server.
 *
 * <p>Each status has a display name and a color code for GUI representation.</p>
 */
public enum ServerStatus {

    /** The server is fully operational and accepting players. */
    ONLINE("&aOnline", "✔"),

    /** The server is not running or unreachable. */
    OFFLINE("&cOffline", "✘"),

    /** The server is currently booting up. */
    STARTING("&eStarting", "⟳"),

    /** The server is in the process of shutting down. */
    STOPPING("&6Stopping", "⏹"),

    /** The server is under maintenance; only admins may join. */
    MAINTENANCE("&8Maintenance", "⚙"),

    /** The server has reached its maximum player capacity. */
    FULL("&cFull", "⚠");

    private final String displayName;
    private final String symbol;

    ServerStatus(String displayName, String symbol) {
        this.displayName = displayName;
        this.symbol = symbol;
    }

    /** Returns the legacy-color-coded display name of this status. */
    public String getDisplayName() {
        return displayName;
    }

    /** Returns the unicode symbol representing this status in GUIs. */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Parses a {@link ServerStatus} from a string, falling back to {@link #OFFLINE}.
     *
     * @param value the string to parse (case-insensitive)
     * @return the matching status, or {@code OFFLINE} if not found
     */
    public static ServerStatus fromString(String value) {
        if (value == null) return OFFLINE;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OFFLINE;
        }
    }
}
