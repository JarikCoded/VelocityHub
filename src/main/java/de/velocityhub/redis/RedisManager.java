package de.velocityhub.redis;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisException;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the Redis connection pool, provides blocking/non-blocking key-value
 * access, and maintains the Pub/Sub subscription on a dedicated thread.
 *
 * <p>All methods that touch Redis are asynchronous; they return
 * {@link CompletableFuture} values that resolve on the shared worker pool.</p>
 */
public final class RedisManager {

    private final Logger logger;
    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final boolean ssl;
    private final int keyTtlSeconds;

    private JedisPool pool;
    private Thread pubSubThread;
    private RedisPubSubHandler pubSubHandler;

    private final ExecutorService workerPool =
            Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "velocityhub-redis-worker");
                t.setDaemon(true);
                return t;
            });

    private final ScheduledExecutorService reconnectScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "velocityhub-redis-reconnect");
                t.setDaemon(true);
                return t;
            });

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Creates a {@code RedisManager} with the supplied connection parameters.
     *
     * @param logger        SLF4J logger
     * @param host          Redis host
     * @param port          Redis port
     * @param password      Redis password (may be {@code null} or empty)
     * @param database      Redis database index
     * @param ssl           whether to use TLS
     * @param keyTtlSeconds default TTL for cached keys (seconds); ≤0 means no expiry
     */
    public RedisManager(Logger logger, String host, int port, String password,
                        int database, boolean ssl, int keyTtlSeconds) {
        this.logger        = logger;
        this.host          = host;
        this.port          = port;
        this.password      = (password == null || password.isBlank()) ? null : password;
        this.database      = database;
        this.ssl           = ssl;
        this.keyTtlSeconds = keyTtlSeconds;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Opens the connection pool and starts the Pub/Sub listener thread.
     *
     * @throws JedisException if the pool cannot be initialised
     */
    public void connect() {
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(16);
        cfg.setMaxIdle(8);
        cfg.setMinIdle(2);
        cfg.setTestOnBorrow(true);
        cfg.setTestWhileIdle(true);

        pool = (password != null)
                ? new JedisPool(cfg, host, port, 2000, password, database, ssl)
                : new JedisPool(cfg, host, port, 2000, null, database, ssl);

        // quick connection test
        try (Jedis jedis = pool.getResource()) {
            jedis.ping();
        }

        running.set(true);
        startPubSubThread();
        scheduleHeartbeat();
        logger.info("[Redis] Connected to {}:{} (db={})", host, port, database);
    }

    /** Closes the pub/sub subscription, pool, and all worker threads. */
    public void disconnect() {
        running.set(false);
        reconnectScheduler.shutdownNow();

        if (pubSubHandler != null && pubSubHandler.isSubscribed()) {
            try { pubSubHandler.unsubscribe(); } catch (Exception ignored) {}
        }
        if (pubSubThread != null) {
            pubSubThread.interrupt();
        }

        workerPool.shutdown();
        try { workerPool.awaitTermination(3, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        if (pool != null) pool.close();
        logger.info("[Redis] Disconnected.");
    }

    // -------------------------------------------------------------------------
    // Pub/Sub
    // -------------------------------------------------------------------------

    private void startPubSubThread() {
        pubSubHandler = new RedisPubSubHandler(logger);
        pubSubThread  = new Thread(() -> {
            while (running.get()) {
                try (Jedis jedis = pool.getResource()) {
                    jedis.subscribe(pubSubHandler, RedisChannels.ALL_CHANNELS);
                } catch (Exception e) {
                    if (!running.get()) break;
                    logger.warn("[Redis] Pub/Sub disconnected, reconnecting in 3s: {}", e.getMessage());
                    sleep(3000);
                }
            }
        }, "velocityhub-redis-pubsub");
        pubSubThread.setDaemon(true);
        pubSubThread.start();
    }

    /**
     * Publishes a JSON payload to a Redis channel asynchronously.
     *
     * @param channel the channel name (use {@link RedisChannels} constants)
     * @param payload the JSON object to publish
     * @return a future that completes when the publish call returns
     */
    public CompletableFuture<Void> publish(String channel, JsonObject payload) {
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.publish(channel, payload.toString());
            } catch (Exception e) {
                logger.error("[Redis] Failed to publish to {}: {}", channel, e.getMessage());
            }
        }, workerPool);
    }

    // -------------------------------------------------------------------------
    // Key-value operations
    // -------------------------------------------------------------------------

    /**
     * Asynchronously sets a string value, optionally with a TTL.
     *
     * @param key   Redis key
     * @param value string value
     * @return future
     */
    public CompletableFuture<Void> set(String key, String value) {
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                if (keyTtlSeconds > 0) {
                    jedis.setex(key, keyTtlSeconds, value);
                } else {
                    jedis.set(key, value);
                }
            } catch (Exception e) {
                logger.error("[Redis] set({}) failed: {}", key, e.getMessage());
            }
        }, workerPool);
    }

    /**
     * Asynchronously sets a string value with an explicit TTL.
     *
     * @param key     Redis key
     * @param value   string value
     * @param seconds TTL in seconds
     * @return future
     */
    public CompletableFuture<Void> setex(String key, String value, int seconds) {
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.setex(key, seconds, value);
            } catch (Exception e) {
                logger.error("[Redis] setex({}) failed: {}", key, e.getMessage());
            }
        }, workerPool);
    }

    /**
     * Asynchronously retrieves a string value.
     *
     * @param key Redis key
     * @return future containing an {@link Optional} with the value, or empty if absent
     */
    public CompletableFuture<Optional<String>> get(String key) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                return Optional.ofNullable(jedis.get(key));
            } catch (Exception e) {
                logger.error("[Redis] get({}) failed: {}", key, e.getMessage());
                return Optional.empty();
            }
        }, workerPool);
    }

    /**
     * Asynchronously deletes one or more keys.
     *
     * @param keys the keys to delete
     * @return future
     */
    public CompletableFuture<Void> delete(String... keys) {
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.del(keys);
            } catch (Exception e) {
                logger.error("[Redis] delete failed: {}", e.getMessage());
            }
        }, workerPool);
    }

    /**
     * Adds a member to a Redis set asynchronously.
     *
     * @param key    the set key
     * @param member the value to add
     * @return future
     */
    public CompletableFuture<Void> sadd(String key, String member) {
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.sadd(key, member);
            } catch (Exception e) {
                logger.error("[Redis] sadd({}, {}) failed: {}", key, member, e.getMessage());
            }
        }, workerPool);
    }

    /**
     * Removes a member from a Redis set asynchronously.
     *
     * @param key    the set key
     * @param member the value to remove
     * @return future
     */
    public CompletableFuture<Void> srem(String key, String member) {
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.srem(key, member);
            } catch (Exception e) {
                logger.error("[Redis] srem({}, {}) failed: {}", key, member, e.getMessage());
            }
        }, workerPool);
    }

    /**
     * Returns all members of a Redis set synchronously.
     *
     * <p>Prefer async access where possible; this is provided for startup
     * initialisation only.</p>
     *
     * @param key the set key
     * @return the set of members, or an empty set on error
     */
    public java.util.Set<String> smembersSync(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.smembers(key);
        } catch (Exception e) {
            logger.error("[Redis] smembers({}) failed: {}", key, e.getMessage());
            return java.util.Collections.emptySet();
        }
    }

    /**
     * Synchronously increments a numeric key and returns the new value.
     *
     * @param key Redis key
     * @return new value after increment
     */
    public long incrementSync(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.incr(key);
        } catch (Exception e) {
            logger.error("[Redis] incr({}) failed: {}", key, e.getMessage());
            return 0;
        }
    }

    /**
     * Synchronously decrements a numeric key (floor 0) and returns new value.
     *
     * @param key Redis key
     * @return new value after decrement
     */
    public long decrementSync(String key) {
        try (Jedis jedis = pool.getResource()) {
            long val = jedis.decr(key);
            if (val < 0) {
                jedis.set(key, "0");
                return 0;
            }
            return val;
        } catch (Exception e) {
            logger.error("[Redis] decr({}) failed: {}", key, e.getMessage());
            return 0;
        }
    }

    /** @return {@code true} if the pool is open and the connection test passes */
    public boolean isConnected() {
        if (pool == null || pool.isClosed()) return false;
        try (Jedis jedis = pool.getResource()) {
            return "PONG".equalsIgnoreCase(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Heartbeat
    // -------------------------------------------------------------------------

    private void scheduleHeartbeat() {
        reconnectScheduler.scheduleAtFixedRate(() -> {
            if (!isConnected()) {
                logger.warn("[Redis] Heartbeat failed – connection lost?");
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
