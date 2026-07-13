package de.velocityhub.redis;

/**
 * Constants for the Redis Pub/Sub channels used by VelocityHub.
 *
 * <p>All proxies subscribe to these channels; messages are JSON payloads.</p>
 */
public final class RedisChannels {

    private RedisChannels() {}

    /** Published when a server's status changes. */
    public static final String SERVER_STATUS   = "velocityhub:server:status";

    /** Published when a server's player count changes. */
    public static final String SERVER_PLAYERS  = "velocityhub:server:players";

    /** Published when a server is added, updated, or removed. */
    public static final String SERVER_UPDATE   = "velocityhub:server:update";

    /** Published when a group is added, updated, or removed. */
    public static final String GROUP_UPDATE    = "velocityhub:group:update";

    /** Published to broadcast a message to the whole network. */
    public static final String BROADCAST       = "velocityhub:broadcast";

    /** Published when a proxy connects or disconnects. */
    public static final String PROXY_STATUS    = "velocityhub:proxy:status";

    /** Published to trigger a GUI open on a specific proxy for a player. */
    public static final String GUI_OPEN        = "velocityhub:gui:open";

    /** Published to transfer a player from one proxy to a server. */
    public static final String PLAYER_TRANSFER = "velocityhub:player:transfer";

    /** Published when maintenance mode is toggled. */
    public static final String MAINTENANCE     = "velocityhub:maintenance";

    /** Published to update the global MOTD on all proxies. */
    public static final String MOTD_UPDATE     = "velocityhub:motd:update";

    /** Published to propagate a reload request to all proxies. */
    public static final String RELOAD          = "velocityhub:reload";

    /** Published for network-wide private messages. */
    public static final String PRIVATE_MESSAGE = "velocityhub:msg:private";

    /** Published for network-wide staff chat. */
    public static final String STAFF_CHAT      = "velocityhub:msg:staff";

    /** Published for network-wide admin chat. */
    public static final String ADMIN_CHAT      = "velocityhub:msg:admin";

    /** Published when an announcement (title/bossbar/actionbar) is sent. */
    public static final String ANNOUNCEMENT    = "velocityhub:announcement";

    /** Published when a player joins or leaves the network. */
    public static final String PLAYER_EVENT    = "velocityhub:player:event";

    /** All channels that each proxy subscribes to. */
    public static final String[] ALL_CHANNELS = {
        SERVER_STATUS, SERVER_PLAYERS, SERVER_UPDATE, GROUP_UPDATE,
        BROADCAST, PROXY_STATUS, GUI_OPEN, PLAYER_TRANSFER,
        MAINTENANCE, MOTD_UPDATE, RELOAD, PRIVATE_MESSAGE,
        STAFF_CHAT, ADMIN_CHAT, ANNOUNCEMENT, PLAYER_EVENT
    };
}
