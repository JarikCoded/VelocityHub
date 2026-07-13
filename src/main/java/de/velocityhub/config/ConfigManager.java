package de.velocityhub.config;
import de.velocityhub.VelocityHubPlugin;

import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central manager for all VelocityHub configuration files.
 *
 * <p>Each config file is loaded lazily on first access and can be hot-reloaded
 * via {@link #reloadAll()} without a server restart.</p>
 */
public final class ConfigManager {

    private final Path dataDirectory;
    private final Logger logger;

    // Root config nodes (atomic references allow safe hot-reload)
    private final AtomicReference<CommentedConfigurationNode> mainConfig     = new AtomicReference<>();
    private final AtomicReference<CommentedConfigurationNode> redisConfig    = new AtomicReference<>();
    private final AtomicReference<CommentedConfigurationNode> serversConfig  = new AtomicReference<>();
    private final AtomicReference<CommentedConfigurationNode> groupsConfig   = new AtomicReference<>();
    private final AtomicReference<CommentedConfigurationNode> guiConfig      = new AtomicReference<>();
    private final AtomicReference<CommentedConfigurationNode> messagesConfig = new AtomicReference<>();
    private final AtomicReference<CommentedConfigurationNode> itemsConfig    = new AtomicReference<>();
    private final AtomicReference<CommentedConfigurationNode> motdConfig     = new AtomicReference<>();
    private final AtomicReference<CommentedConfigurationNode> permissionsConfig = new AtomicReference<>();

    // Derived / cached values
    private volatile String proxyId;
    private volatile String globalMotd;
    private volatile boolean debugMode;

    /**
     * Creates the manager.
     *
     * @param dataDirectory the plugin data directory
     * @param logger        SLF4J logger
     */
    public ConfigManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger        = logger;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Loads all configuration files, copying defaults from the JAR if missing.
     */
    public void loadAll() {
        loadConfig("config.yml",      mainConfig);
        loadConfig("redis.yml",       redisConfig);
        loadConfig("servers.yml",     serversConfig);
        loadConfig("groups.yml",      groupsConfig);
        loadConfig("gui.yml",         guiConfig);
        loadConfig("messages.yml",    messagesConfig);
        loadConfig("items.yml",       itemsConfig);
        loadConfig("motd.yml",        motdConfig);
        loadConfig("permissions.yml", permissionsConfig);
        cacheValues();
        logger.info("[Config] All configuration files loaded.");
    }

    /**
     * Reloads all configuration files from disk.
     *
     * <p>This is a hot-reload; all cached values are updated atomically.</p>
     */
    public void reloadAll() {
        loadAll();
        // Only delegate to managers if the plugin is fully initialized.
        VelocityHubPlugin plugin = VelocityHub.getInstance();
        if (plugin == null) {
            logger.warn("[Config] reloadAll called before plugin initialization; skipping manager reload.");
            return;
        }
        plugin.getServerManager().loadFromConfig();
        plugin.getGroupManager().loadFromConfig();
        logger.info("[Config] Configuration reloaded.");
    }

    // -------------------------------------------------------------------------
    // File access helpers
    // -------------------------------------------------------------------------

    private void loadConfig(String fileName,
                            AtomicReference<CommentedConfigurationNode> ref) {
        Path file = dataDirectory.resolve(fileName);
        copyDefault(fileName, file);

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .nodeStyle(NodeStyle.BLOCK)
                .path(file)
                .build();
        try {
            ref.set(loader.load());
        } catch (IOException e) {
            logger.error("[Config] Could not load {}: {}", fileName, e.getMessage());
        }
    }

    private void copyDefault(String resourceName, Path target) {
        if (Files.exists(target)) return;
        try {
            Files.createDirectories(target.getParent());
            InputStream in = getClass().getClassLoader()
                    .getResourceAsStream(resourceName);
            if (in == null) {
                logger.warn("[Config] Default resource '{}' not found in JAR.", resourceName);
                return;
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("[Config] Could not copy default {}: {}", resourceName, e.getMessage());
        }
    }

    private void cacheValues() {
        CommentedConfigurationNode main = mainConfig.get();
        if (main == null) return;

        this.proxyId    = main.node("proxy-id").getString("proxy-1");
        this.debugMode  = main.node("debug").getBoolean(false);

        CommentedConfigurationNode motd = motdConfig.get();
        if (motd != null) {
            this.globalMotd = motd.node("motd", "line1").getString("")
                    + "\n" + motd.node("motd", "line2").getString("");
        }
    }

    // -------------------------------------------------------------------------
    // Public getters for raw config nodes
    // -------------------------------------------------------------------------

    /** @return the root node of {@code config.yml} */
    public CommentedConfigurationNode getMain() { return mainConfig.get(); }

    /** @return the root node of {@code redis.yml} */
    public CommentedConfigurationNode getRedis() { return redisConfig.get(); }

    /** @return the root node of {@code servers.yml} */
    public CommentedConfigurationNode getServers() { return serversConfig.get(); }

    /** @return the root node of {@code groups.yml} */
    public CommentedConfigurationNode getGroups() { return groupsConfig.get(); }

    /** @return the root node of {@code gui.yml} */
    public CommentedConfigurationNode getGui() { return guiConfig.get(); }

    /** @return the root node of {@code messages.yml} */
    public CommentedConfigurationNode getMessages() { return messagesConfig.get(); }

    /** @return the root node of {@code items.yml} */
    public CommentedConfigurationNode getItems() { return itemsConfig.get(); }

    /** @return the root node of {@code motd.yml} */
    public CommentedConfigurationNode getMotd() { return motdConfig.get(); }

    /** @return the root node of {@code permissions.yml} */
    public CommentedConfigurationNode getPermissions() { return permissionsConfig.get(); }

    // -------------------------------------------------------------------------
    // Derived helpers
    // -------------------------------------------------------------------------

    /** @return the proxy identifier (from {@code config.yml → proxy-id}) */
    public String getProxyId() { return proxyId; }

    /** @return the current global MOTD as a raw string */
    public String getGlobalMotd() { return globalMotd; }

    /** Updates the local MOTD cache (called by Redis pub/sub handler). */
    public void updateMotdLocal(String motd) { this.globalMotd = motd; }

    /** @return {@code true} if debug logging is enabled */
    public boolean isDebugMode() { return debugMode; }

    /**
     * Looks up a message string from {@code messages.yml}.
     *
     * <p>The {@code path} is dot-separated, e.g. {@code "errors.no-permission"}.</p>
     *
     * @param path         the config node path
     * @param defaultValue fallback value if the key is missing
     * @return the configured message, or the default
     */
    public String getMessage(String path, String defaultValue) {
        CommentedConfigurationNode msgs = messagesConfig.get();
        if (msgs == null) return defaultValue;
        String[] parts = path.split("\\.");
        CommentedConfigurationNode node = msgs.node((Object[]) parts);
        return node.getString(defaultValue);
    }
}
