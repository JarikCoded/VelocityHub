package de.velocityhub.manager;
import de.velocityhub.VelocityHubPlugin;

import de.velocityhub.model.PlayerData;
import de.velocityhub.redis.RedisKeys;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.*;

/**
 * Provides in-memory caching and Redis-backed persistence for per-player data.
 *
 * <p>Data is loaded when a player connects and saved when they disconnect.</p>
 */
public final class PlayerDataManager {

    private final VelocityHubPlugin plugin;
    private final Logger logger;

    /** UUID → PlayerData cache for currently-online players. */
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(VelocityHubPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Load / save
    // -------------------------------------------------------------------------

    /**
     * Loads a player's data from Redis (or creates a fresh entry) and caches it.
     *
     * @param uuid       the player's UUID
     * @param playerName the player's current username
     * @return a {@link CompletableFuture} that resolves to the player data
     */
    public CompletableFuture<PlayerData> loadPlayer(UUID uuid, String playerName) {
        return plugin.getRedisManager()
                .get(String.format(RedisKeys.PLAYER_DATA, uuid))
                .thenApply(opt -> {
                    PlayerData data;
                    if (opt.isPresent()) {
                        data = plugin.getGson().fromJson(opt.get(), PlayerData.class);
                        // Gson deserializes into a plain object; we need to
                        // ensure the UUID and name are up-to-date
                        data.setName(playerName);
                    } else {
                        data = new PlayerData(uuid, playerName);
                    }
                    data.setLastSeen(System.currentTimeMillis());
                    cache.put(uuid, data);
                    return data;
                });
    }

    /**
     * Saves a player's data to Redis and removes it from the local cache.
     *
     * @param uuid the player's UUID
     */
    public void unloadPlayer(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data == null) return;
        data.setLastSeen(System.currentTimeMillis());
        String json = plugin.getGson().toJson(data);
        plugin.getRedisManager().set(String.format(RedisKeys.PLAYER_DATA, uuid), json);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the cached {@link PlayerData} for an online player.
     *
     * @param uuid the player's UUID
     * @return an {@link Optional} containing the data, or empty if not cached
     */
    public Optional<PlayerData> getPlayerData(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    /**
     * Returns or creates a {@link PlayerData} entry in the cache.
     *
     * <p>Prefer {@link #loadPlayer(UUID, String)} for proper async loading;
     * this method is a synchronous fallback.</p>
     *
     * @param uuid       the player's UUID
     * @param playerName the player's username (used only if creating a new entry)
     * @return the cached or newly created data
     */
    public PlayerData getOrCreate(UUID uuid, String playerName) {
        return cache.computeIfAbsent(uuid, k -> new PlayerData(k, playerName));
    }
}
