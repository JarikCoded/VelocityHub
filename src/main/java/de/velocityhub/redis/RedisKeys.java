package de.velocityhub.redis;

/**
 * Redis key templates used by VelocityHub.
 *
 * <p>Call {@link String#format(String, Object...)} with the relevant identifiers
 * to get the concrete key string, e.g.:
 * <pre>{@code
 *   String key = String.format(RedisKeys.SERVER_DATA, serverName);
 * }</pre>
 * </p>
 */
public final class RedisKeys {

    private RedisKeys() {}

    // -------------------------------------------------------------------------
    // Server data
    // -------------------------------------------------------------------------

    /** Full server object JSON.  Args: server name. */
    public static final String SERVER_DATA      = "velocityhub:server:%s";

    /** Live player count for a server.  Args: server name. */
    public static final String SERVER_PLAYERS   = "velocityhub:server:%s:players";

    /** Status string for a server.  Args: server name. */
    public static final String SERVER_STATUS    = "velocityhub:server:%s:status";

    /** Set of all known server names. */
    public static final String SERVER_SET       = "velocityhub:servers";

    // -------------------------------------------------------------------------
    // Group data
    // -------------------------------------------------------------------------

    /** Full group object JSON.  Args: group name. */
    public static final String GROUP_DATA       = "velocityhub:group:%s";

    /** Set of all known group names. */
    public static final String GROUP_SET        = "velocityhub:groups";

    // -------------------------------------------------------------------------
    // Network-wide data
    // -------------------------------------------------------------------------

    /** Total online player count across all proxies. */
    public static final String NETWORK_PLAYERS  = "velocityhub:network:players";

    /** Global maintenance mode flag ("true"/"false"). */
    public static final String MAINTENANCE      = "velocityhub:maintenance";

    /** Current global MOTD JSON. */
    public static final String MOTD             = "velocityhub:motd";

    // -------------------------------------------------------------------------
    // Player data
    // -------------------------------------------------------------------------

    /** Persistent player data JSON.  Args: player UUID. */
    public static final String PLAYER_DATA      = "velocityhub:player:%s";

    /** The server a player is currently on.  Args: player UUID. */
    public static final String PLAYER_SERVER    = "velocityhub:player:%s:server";

    /** The proxy a player is currently connected to.  Args: player UUID. */
    public static final String PLAYER_PROXY     = "velocityhub:player:%s:proxy";

    /** Set of all online player UUIDs. */
    public static final String ONLINE_PLAYERS   = "velocityhub:players:online";

    // -------------------------------------------------------------------------
    // Proxy data
    // -------------------------------------------------------------------------

    /** Set of all known proxy IDs. */
    public static final String PROXY_SET        = "velocityhub:proxies";

    /** Heartbeat timestamp for a proxy.  Args: proxy ID. */
    public static final String PROXY_HEARTBEAT  = "velocityhub:proxy:%s:heartbeat";

    // -------------------------------------------------------------------------
    // Queue data
    // -------------------------------------------------------------------------

    /** Sorted set of queued players for a server.  Args: server name. */
    public static final String QUEUE            = "velocityhub:queue:%s";
}
