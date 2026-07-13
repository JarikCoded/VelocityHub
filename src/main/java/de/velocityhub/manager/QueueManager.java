package de.velocityhub.manager;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.velocityhub.model.NetworkServer;
import de.velocityhub.model.QueueEntry;
import de.velocityhub.model.ServerStatus;
import de.velocityhub.util.ComponentUtil;
import de.velocityhub.util.PermissionUtil;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages per-server waiting queues.
 *
 * <p>When a server is {@link ServerStatus#FULL}, players can be added to a
 * priority queue. A background ticker checks every few seconds whether slots
 * have freed up and transfers the next player(s).</p>
 *
 * <p>Queue priority is determined by the highest numeric suffix of a
 * {@code network.queue.priority.<n>} permission the player holds.</p>
 */
public final class QueueManager {

    private static final int TICK_INTERVAL_SECONDS = 3;
    private static final int DEFAULT_PRIORITY = 0;

    private final VelocityHubPlugin plugin;
    private final ProxyServer proxy;
    private final Logger logger;

    /** Server name (lower-cased) → sorted queue of entries. */
    private final Map<String, PriorityBlockingQueue<QueueEntry>> queues =
            new ConcurrentHashMap<>();

    /** Player UUID → server name; allows fast "is in queue?" lookups. */
    private final Map<UUID, String> playerQueueMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "velocityhub-queue-tick");
                t.setDaemon(true);
                return t;
            });

    public QueueManager(VelocityHubPlugin plugin, ProxyServer proxy, Logger logger) {
        this.plugin = plugin;
        this.proxy  = proxy;
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Starts the background queue ticker. */
    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, TICK_INTERVAL_SECONDS,
                TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        logger.info("[QueueManager] Queue ticker started (interval={}s).", TICK_INTERVAL_SECONDS);
    }

    /** Stops the background ticker. */
    public void stop() {
        scheduler.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Adds a player to the queue for the specified server.
     *
     * @param player     the player to queue
     * @param serverName the target server
     * @return {@code true} if the player was added, {@code false} if already queued
     */
    public boolean enqueue(Player player, String serverName) {
        if (playerQueueMap.containsKey(player.getUniqueId())) return false;

        int priority = getQueuePriority(player);
        QueueEntry entry = new QueueEntry(
                player.getUniqueId(), player.getUsername(), serverName, priority);

        queues.computeIfAbsent(serverName.toLowerCase(),
                k -> new PriorityBlockingQueue<>()).add(entry);
        playerQueueMap.put(player.getUniqueId(), serverName);

        int position = getQueuePosition(player, serverName);
        String msg = ComponentUtil.replace(
                plugin.getConfigManager().getMessage("queue.joined",
                        "&aYou joined the queue for &e%server%&a. Position: &e%pos%&a."),
                "server", serverName, "pos", String.valueOf(position));
        player.sendMessage(ComponentUtil.parse(msg));
        return true;
    }

    /**
     * Removes a player from whichever queue they are currently in.
     *
     * @param player the player to dequeue
     */
    public void dequeue(Player player) {
        String serverName = playerQueueMap.remove(player.getUniqueId());
        if (serverName == null) return;
        PriorityBlockingQueue<QueueEntry> queue = queues.get(serverName.toLowerCase());
        if (queue != null) {
            queue.removeIf(e -> e.getPlayerId().equals(player.getUniqueId()));
        }
    }

    /**
     * Returns whether the given player is currently in a queue.
     *
     * @param player the player to check
     * @return {@code true} if queued
     */
    public boolean isQueued(Player player) {
        return playerQueueMap.containsKey(player.getUniqueId());
    }

    /**
     * Returns the 1-based position of a player in the queue for a server.
     *
     * @param player     the player
     * @param serverName the server
     * @return the position, or -1 if not in the queue
     */
    public int getQueuePosition(Player player, String serverName) {
        PriorityBlockingQueue<QueueEntry> queue = queues.get(serverName.toLowerCase());
        if (queue == null) return -1;
        List<QueueEntry> sorted = new ArrayList<>(queue);
        Collections.sort(sorted);
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getPlayerId().equals(player.getUniqueId())) return i + 1;
        }
        return -1;
    }

    // -------------------------------------------------------------------------
    // Ticker
    // -------------------------------------------------------------------------

    private void tick() {
        queues.forEach((serverName, queue) -> {
            if (queue.isEmpty()) return;

            Optional<NetworkServer> nsOpt = plugin.getServerManager().getServer(serverName);
            if (nsOpt.isEmpty()) return;
            NetworkServer ns = nsOpt.get();

            if (!ns.isJoinable()) return;

            int freeSlots = ns.getMaxPlayers() - ns.getOnlinePlayers();
            if (freeSlots <= 0) return;

            for (int i = 0; i < freeSlots; i++) {
                QueueEntry entry = queue.poll();
                if (entry == null) break;
                playerQueueMap.remove(entry.getPlayerId());

                proxy.getPlayer(entry.getPlayerId()).ifPresent(player ->
                        plugin.getServerManager().sendPlayerToServer(player, serverName));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Priority helpers
    // -------------------------------------------------------------------------

    private int getQueuePriority(Player player) {
        if (player.hasPermission(PermissionUtil.ADMIN)) return Integer.MAX_VALUE;
        int max = DEFAULT_PRIORITY;
        // Check permissions like network.queue.priority.100
        for (int priority = 1; priority <= 1000; priority++) {
            if (player.hasPermission(PermissionUtil.QUEUE_PRIORITY + priority)) {
                max = Math.max(max, priority);
            }
        }
        return max;
    }
}
