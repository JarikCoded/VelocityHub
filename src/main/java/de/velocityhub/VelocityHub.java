package de.velocityhub;

/**
 * Static accessor for the running {@link VelocityHubPlugin} instance.
 *
 * <p>This class is separate from the plugin class so that other classes can
 * obtain a reference without needing an injection point.</p>
 */
public final class VelocityHub {

    private static VelocityHubPlugin instance;

    private VelocityHub() {}

    /**
     * Returns the currently running plugin instance, or {@code null} if the
     * plugin has not been initialised yet.
     *
     * @return the plugin instance
     */
    public static VelocityHubPlugin getInstance() {
        return instance;
    }

    /**
     * Sets the active plugin instance.  Called once during
     * {@link VelocityHubPlugin#onProxyInitialization}.
     *
     * @param plugin the plugin instance
     */
    static void setInstance(VelocityHubPlugin plugin) {
        instance = plugin;
    }
}
