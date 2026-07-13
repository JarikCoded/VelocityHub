package de.velocityhub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.command.CommandMeta;
import de.velocityhub.command.*;
import de.velocityhub.config.ConfigManager;
import de.velocityhub.gui.GuiManager;
import de.velocityhub.listener.*;
import de.velocityhub.manager.*;
import de.velocityhub.redis.RedisManager;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.nio.file.Path;

/**
 * Main plugin class for VelocityHub.
 *
 * <p>Bootstraps all managers, registers commands, event listeners, and
 * plugin-message channels, then connects to Redis.</p>
 */
@Plugin(
        id          = "velocityhub",
        name        = "VelocityHub",
        version     = "1.0.0",
        description = "Professional Velocity Network Management Plugin – Multi-Proxy Edition",
        url         = "https://github.com/JarikCoded/VelocityHub",
        authors     = {"JarikCoded"}
)
public final class VelocityHubPlugin {

    // -------------------------------------------------------------------------
    // Injected fields
    // -------------------------------------------------------------------------

    private final ProxyServer proxy;
    private final Logger      logger;
    private final Path        dataDirectory;

    // -------------------------------------------------------------------------
    // Plugin components
    // -------------------------------------------------------------------------

    private final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    private ConfigManager         configManager;
    private RedisManager          redisManager;
    private ServerManager         serverManager;
    private GroupManager          groupManager;
    private MaintenanceManager    maintenanceManager;
    private QueueManager          queueManager;
    private PrivateChatManager    privateChatManager;
    private StaffChatManager      staffChatManager;
    private AnnouncementManager   announcementManager;
    private DiscordWebhookManager discordWebhookManager;
    private StatisticsManager     statisticsManager;
    private PlayerDataManager     playerDataManager;
    private GuiManager            guiManager;

    // -------------------------------------------------------------------------
    // Constructor (Velocity DI)
    // -------------------------------------------------------------------------

    @Inject
    public VelocityHubPlugin(ProxyServer proxy,
                             Logger logger,
                             @DataDirectory Path dataDirectory) {
        this.proxy         = proxy;
        this.logger        = logger;
        this.dataDirectory = dataDirectory;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        VelocityHub.setInstance(this);
        logger.info("╔══════════════════════════════════════╗");
        logger.info("║  VelocityHub v1.0.0 by JarikCoded   ║");
        logger.info("╚══════════════════════════════════════╝");

        // 1. Configuration
        configManager = new ConfigManager(dataDirectory, logger);
        configManager.loadAll();

        // 2. Redis
        initRedis();

        // 3. Managers
        initManagers();

        // 4. Load data
        serverManager.loadFromConfig();
        groupManager.loadFromConfig();
        maintenanceManager.loadFromRedis();

        // 5. Register commands
        registerCommands();

        // 6. Register event listeners
        registerListeners();

        // 7. Register plugin message channels
        registerChannels();

        // 8. Start background tasks
        queueManager.start();
        statisticsManager.start();

        logger.info("VelocityHub started on proxy '{}'.", configManager.getProxyId());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("VelocityHub shutting down...");

        queueManager.stop();
        statisticsManager.stop();
        discordWebhookManager.shutdown();

        if (redisManager != null) {
            redisManager.disconnect();
        }

        logger.info("VelocityHub has been shut down cleanly.");
    }

    // -------------------------------------------------------------------------
    // Initialisation helpers
    // -------------------------------------------------------------------------

    private void initRedis() {
        CommentedConfigurationNode redisCfg = configManager.getRedis();
        String host     = redisCfg.node("redis", "host").getString("127.0.0.1");
        int    port     = redisCfg.node("redis", "port").getInt(6379);
        String password = redisCfg.node("redis", "password").getString("");
        int    database = redisCfg.node("redis", "database").getInt(0);
        boolean ssl     = redisCfg.node("redis", "ssl").getBoolean(false);
        int    ttl      = redisCfg.node("redis", "key-ttl-seconds").getInt(-1);

        redisManager = new RedisManager(logger, host, port, password, database, ssl, ttl);
        try {
            redisManager.connect();
        } catch (Exception e) {
            logger.error("Could not connect to Redis: {}", e.getMessage());
            logger.warn("VelocityHub will continue without Redis – multi-proxy features disabled.");
        }
    }

    private void initManagers() {
        playerDataManager     = new PlayerDataManager(this, logger);
        serverManager         = new ServerManager(this, proxy, logger);
        groupManager          = new GroupManager(this, proxy, logger);
        maintenanceManager    = new MaintenanceManager(this, proxy, logger);
        queueManager          = new QueueManager(this, proxy, logger);
        privateChatManager    = new PrivateChatManager(this, proxy, logger);
        staffChatManager      = new StaffChatManager(this, proxy, logger);
        announcementManager   = new AnnouncementManager(this, proxy, logger);
        discordWebhookManager = new DiscordWebhookManager(this, logger);
        statisticsManager     = new StatisticsManager(this, proxy, logger);
        guiManager            = new GuiManager(this, logger);
    }

    private void registerCommands() {
        CommandMeta serverMeta = proxy.getCommandManager().metaBuilder("server")
                .aliases("servers").plugin(this).build();
        proxy.getCommandManager().register(serverMeta, new ServerCommand(this));

        CommandMeta sendMeta = proxy.getCommandManager().metaBuilder("send")
                .plugin(this).build();
        proxy.getCommandManager().register(sendMeta, new SendCommand(this));

        CommandMeta lobbyMeta = proxy.getCommandManager().metaBuilder("l")
                .aliases("hub", "lobby").plugin(this).build();
        proxy.getCommandManager().register(lobbyMeta, new LobbyCommand(this));

        CommandMeta broadcastMeta = proxy.getCommandManager().metaBuilder("broadcast")
                .aliases("bc").plugin(this).build();
        proxy.getCommandManager().register(broadcastMeta, new BroadcastCommand(this));

        CommandMeta networkMeta = proxy.getCommandManager().metaBuilder("network")
                .aliases("vh").plugin(this).build();
        proxy.getCommandManager().register(networkMeta, new NetworkCommand(this));

        CommandMeta maintenanceMeta = proxy.getCommandManager().metaBuilder("maintenance")
                .aliases("maint").plugin(this).build();
        proxy.getCommandManager().register(maintenanceMeta, new MaintenanceCommand(this));

        CommandMeta msgMeta = proxy.getCommandManager().metaBuilder("msg")
                .aliases("tell", "pm", "whisper").plugin(this).build();
        proxy.getCommandManager().register(msgMeta, new MsgCommand(this));

        CommandMeta replyMeta = proxy.getCommandManager().metaBuilder("reply")
                .aliases("r").plugin(this).build();
        proxy.getCommandManager().register(replyMeta, new ReplyCommand(this));

        CommandMeta socialSpyMeta = proxy.getCommandManager().metaBuilder("socialspy")
                .aliases("ss").plugin(this).build();
        proxy.getCommandManager().register(socialSpyMeta, new SocialSpyCommand(this));

        CommandMeta staffChatMeta = proxy.getCommandManager().metaBuilder("staffchat")
                .aliases("sc").plugin(this).build();
        proxy.getCommandManager().register(staffChatMeta, new StaffChatCommand(this));

        logger.info("Registered {} command(s).", 10);
    }

    private void registerListeners() {
        proxy.getEventManager().register(this, new LoginListener(this, logger));
        proxy.getEventManager().register(this, new ServerSwitchListener(this, logger));
        proxy.getEventManager().register(this, new PluginMessageListener(this, logger));
        logger.info("Registered {} event listener(s).", 3);
    }

    private void registerChannels() {
        proxy.getChannelRegistrar().register(
                Key.key("velocityhub", "action"),
                Key.key("velocityhub", "lobby-item"),
                Key.key("velocityhub", "gui")
        );
        logger.info("Registered plugin message channels.");
    }

    // -------------------------------------------------------------------------
    // Accessors (package-private for managers / commands)
    // -------------------------------------------------------------------------

    /** @return the Velocity proxy server */
    public ProxyServer getProxy() { return proxy; }

    /** @return the SLF4J logger */
    public Logger getLogger() { return logger; }

    /** @return the plugin data directory */
    public Path getDataDirectory() { return dataDirectory; }

    /** @return the shared Gson instance */
    public Gson getGson() { return gson; }

    /** @return whether debug mode is enabled */
    public boolean isDebugMode() { return configManager.isDebugMode(); }

    public ConfigManager         getConfigManager()         { return configManager; }
    public RedisManager          getRedisManager()          { return redisManager; }
    public ServerManager         getServerManager()         { return serverManager; }
    public GroupManager          getGroupManager()          { return groupManager; }
    public MaintenanceManager    getMaintenanceManager()    { return maintenanceManager; }
    public QueueManager          getQueueManager()          { return queueManager; }
    public PrivateChatManager    getPrivateChatManager()    { return privateChatManager; }
    public StaffChatManager      getStaffChatManager()      { return staffChatManager; }
    public AnnouncementManager   getAnnouncementManager()   { return announcementManager; }
    public DiscordWebhookManager getDiscordWebhookManager() { return discordWebhookManager; }
    public StatisticsManager     getStatisticsManager()     { return statisticsManager; }
    public PlayerDataManager     getPlayerDataManager()     { return playerDataManager; }
    public GuiManager            getGuiManager()            { return guiManager; }
}
