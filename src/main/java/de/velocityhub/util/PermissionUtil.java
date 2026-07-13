package de.velocityhub.util;

/**
 * Central definition of all VelocityHub permissions.
 *
 * <p>Use these constants instead of hard-coding permission strings to avoid
 * typos and simplify future refactoring.</p>
 */
public final class PermissionUtil {

    // -------------------------------------------------------------------------
    // Root nodes
    // -------------------------------------------------------------------------

    /** Full administrative access (bypasses all other permission checks). */
    public static final String ADMIN = "network.admin";

    // -------------------------------------------------------------------------
    // Commands
    // -------------------------------------------------------------------------

    /** Access to {@code /server} (server-selection GUI or list). */
    public static final String CMD_SERVER      = "network.server";

    /** Access to {@code /send} (send players to servers or groups). */
    public static final String CMD_SEND        = "network.send";

    /** Access to {@code /l}, {@code /hub}, {@code /lobby}. */
    public static final String CMD_LOBBY       = "network.lobby";

    /** Access to {@code /broadcast}. */
    public static final String CMD_BROADCAST   = "network.broadcast";

    /** Access to {@code /network reload} and all sub-commands. */
    public static final String CMD_NETWORK     = "network.network";

    /** Access to toggle maintenance mode. */
    public static final String CMD_MAINTENANCE = "network.maintenance";

    /** Ability to join while maintenance mode is active. */
    public static final String BYPASS_MAINTENANCE = "network.maintenance.bypass";

    /** Access to {@code /msg} and {@code /reply}. */
    public static final String CMD_MSG         = "network.msg";

    /** Access to {@code /socialspy}. */
    public static final String CMD_SOCIALSPY   = "network.socialspy";

    /** Access to {@code /staffchat}. */
    public static final String CMD_STAFFCHAT   = "network.staffchat";

    /** Access to {@code /adminchat}. */
    public static final String CMD_ADMINCHAT   = "network.adminchat";

    /** Access to the server-selection GUI. */
    public static final String USE_GUI         = "network.gui";

    // -------------------------------------------------------------------------
    // Queue
    // -------------------------------------------------------------------------

    /** Prioritised queue access (default = 0; higher = earlier). */
    public static final String QUEUE_PRIORITY  = "network.queue.priority.";

    // -------------------------------------------------------------------------
    // Server & group management
    // -------------------------------------------------------------------------

    /** Wildcard for all {@code network.server.*} permissions. */
    public static final String SERVER_WILDCARD = "network.server.*";

    /** Wildcard for all {@code network.group.*} permissions. */
    public static final String GROUP_WILDCARD  = "network.group.*";

    // -------------------------------------------------------------------------
    // Misc
    // -------------------------------------------------------------------------

    /** Ability to bypass the per-server player limit. */
    public static final String BYPASS_FULL     = "network.bypass.full";

    /** Receive join/leave notifications for staff. */
    public static final String NOTIFY_JOIN_LEAVE = "network.notify.joinleave";

    private PermissionUtil() {}
}
