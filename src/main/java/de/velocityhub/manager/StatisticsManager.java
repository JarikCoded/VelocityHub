package de.velocityhub.manager;
import de.velocityhub.VelocityHubPlugin;

import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Collects and exposes network-wide statistics.
 *
 * <p>Statistics include peak player count, server uptime, and per-proxy
 * metrics.  A background ticker updates the counters every minute and logs
 * them to the console when debug mode is active.</p>
 */
public final class StatisticsManager {

    private final VelocityHubPlugin plugin;
    private final ProxyServer proxy;
    private final Logger logger;

    private final AtomicInteger peakPlayers  = new AtomicInteger(0);
    private final AtomicLong    startTime    = new AtomicLong(System.currentTimeMillis());
    private final Map<String, AtomicLong> serverJoins = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "velocityhub-stats");
                t.setDaemon(true);
                return t;
            });

    public StatisticsManager(VelocityHubPlugin plugin, ProxyServer proxy, Logger logger) {
        this.plugin = plugin;
        this.proxy  = proxy;
        this.logger = logger;
    }

    /** Starts the statistics collection ticker. */
    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, 60, 60, TimeUnit.SECONDS);
    }

    /** Stops the statistics ticker. */
    public void stop() {
        scheduler.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** @return the peak online player count since plugin startup */
    public int getPeakPlayers() { return peakPlayers.get(); }

    /** @return plugin uptime in milliseconds */
    public long getUptimeMs() { return System.currentTimeMillis() - startTime.get(); }

    /**
     * Records a player join event for statistics.
     *
     * @param serverName the server the player joined
     */
    public void recordJoin(String serverName) {
        serverJoins.computeIfAbsent(serverName, k -> new AtomicLong(0)).incrementAndGet();
        int current = proxy.getPlayerCount();
        peakPlayers.accumulateAndGet(current, Math::max);
    }

    /**
     * Returns the total number of player join events recorded for a server.
     *
     * @param serverName the server name
     * @return join count (0 if never recorded)
     */
    public long getServerJoins(String serverName) {
        AtomicLong counter = serverJoins.get(serverName);
        return counter == null ? 0L : counter.get();
    }

    /**
     * Returns a snapshot of all recorded server join counts.
     *
     * @return unmodifiable map of server name → join count
     */
    public Map<String, Long> getAllServerJoins() {
        Map<String, Long> result = new LinkedHashMap<>();
        serverJoins.forEach((k, v) -> result.put(k, v.get()));
        return Collections.unmodifiableMap(result);
    }

    // -------------------------------------------------------------------------
    // Ticker
    // -------------------------------------------------------------------------

    private void tick() {
        int current = proxy.getPlayerCount();
        peakPlayers.accumulateAndGet(current, Math::max);

        if (plugin.isDebugMode()) {
            logger.debug("[Stats] Online: {}, Peak: {}, Uptime: {}s",
                    current, peakPlayers.get(), getUptimeMs() / 1000);
        }
    }
}
