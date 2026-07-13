package de.velocityhub.manager;
import de.velocityhub.VelocityHubPlugin;

import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.velocityhub.redis.RedisChannels;
import de.velocityhub.util.ComponentUtil;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages network-wide private messages, staff chat, and admin chat.
 *
 * <p>Messages are routed via Redis Pub/Sub so that players on different
 * proxies can communicate seamlessly.</p>
 */
public final class PrivateChatManager {

    private final VelocityHubPlugin plugin;
    private final ProxyServer proxy;
    private final Logger logger;

    /** UUID string → UUID string: last message source for /reply. */
    private final Map<String, String> replyTargets = new ConcurrentHashMap<>();

    public PrivateChatManager(VelocityHubPlugin plugin, ProxyServer proxy, Logger logger) {
        this.plugin = plugin;
        this.proxy  = proxy;
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Sending
    // -------------------------------------------------------------------------

    /**
     * Sends a private message from one player to another across the network.
     *
     * @param sender     the player sending the message
     * @param targetName the username of the recipient
     * @param message    the message text
     */
    public void sendPrivateMessage(Player sender, String targetName, String message) {
        // First check if target is on this proxy
        Optional<Player> localTarget = proxy.getAllPlayers().stream()
                .filter(p -> p.getUsername().equalsIgnoreCase(targetName))
                .findFirst();

        String senderUuid = sender.getUniqueId().toString();
        String senderName = sender.getUsername();

        if (localTarget.isPresent()) {
            Player target = localTarget.get();
            String targetUuid = target.getUniqueId().toString();

            // Check ignore list
            boolean isIgnored = plugin.getPlayerDataManager()
                    .getPlayerData(target.getUniqueId())
                    .map(d -> d.isIgnoring(senderUuid))
                    .orElse(false);

            if (isIgnored) {
                sender.sendMessage(ComponentUtil.parse(
                        plugin.getConfigManager().getMessage("private-chat.ignored",
                                "&cThis player has ignored you.")));
                return;
            }

            deliverMessage(senderUuid, senderName, targetUuid, message);
            updateReplyTargets(senderUuid, targetUuid);
            deliverSocialSpy(senderName, targetName, message);
        } else {
            // Publish to Redis for other proxies to handle
            JsonObject payload = new JsonObject();
            payload.addProperty("senderUuid", senderUuid);
            payload.addProperty("senderName", senderName);
            payload.addProperty("targetName", targetName);
            payload.addProperty("targetUuid", ""); // unknown at this proxy
            payload.addProperty("message", message);
            plugin.getRedisManager().publish(RedisChannels.PRIVATE_MESSAGE, payload);
        }
    }

    /**
     * Delivers a private message to a player locally (if they are on this proxy).
     *
     * @param senderUuid UUID string of the sender
     * @param senderName username of the sender
     * @param targetUuid UUID string of the recipient
     * @param message    the message text
     */
    public void deliverMessage(String senderUuid, String senderName,
                               String targetUuid, String message) {
        try {
            UUID uuid = UUID.fromString(targetUuid);
            proxy.getPlayer(uuid).ifPresent(target -> {
                String format = plugin.getConfigManager().getMessage(
                        "private-chat.receive",
                        "&7[&d%sender% &7→ &dYou&7] &f%message%");
                String formatted = ComponentUtil.replace(format,
                        "sender", senderName, "message", message);
                target.sendMessage(ComponentUtil.parse(formatted));

                // Update reply target
                updateReplyTargets(targetUuid, senderUuid);
            });
        } catch (IllegalArgumentException ignored) {}

        // Notify sender (if on this proxy)
        if (!senderUuid.isBlank()) {
            try {
                UUID senderUuidObj = UUID.fromString(senderUuid);
                proxy.getPlayer(senderUuidObj).ifPresent(sender -> {
                    // Try to find target name
                    String targetName;
                    try {
                        targetName = proxy.getPlayer(UUID.fromString(targetUuid))
                                .map(Player::getUsername).orElse("Unknown");
                    } catch (IllegalArgumentException e) {
                        targetName = "Unknown";
                    }
                    String format = plugin.getConfigManager().getMessage(
                            "private-chat.send",
                            "&7[&dYou &7→ &d%target%&7] &f%message%");
                    String formatted = ComponentUtil.replace(format,
                            "target", targetName, "message", message);
                    sender.sendMessage(ComponentUtil.parse(formatted));
                });
            } catch (IllegalArgumentException ignored) {}
        }
    }

    /**
     * Sends a /reply from the given player to their last PM conversation partner.
     *
     * @param sender  the player replying
     * @param message the reply message
     * @return {@code true} if a reply target was found, {@code false} otherwise
     */
    public boolean reply(Player sender, String message) {
        String targetUuid = replyTargets.get(sender.getUniqueId().toString());
        if (targetUuid == null) return false;

        // Resolve name
        String targetName;
        try {
            targetName = proxy.getPlayer(UUID.fromString(targetUuid))
                    .map(Player::getUsername).orElse(null);
        } catch (IllegalArgumentException e) {
            targetName = null;
        }

        if (targetName == null) {
            // Player may be on another proxy – publish via Redis
            JsonObject payload = new JsonObject();
            payload.addProperty("senderUuid", sender.getUniqueId().toString());
            payload.addProperty("senderName", sender.getUsername());
            payload.addProperty("targetUuid", targetUuid);
            payload.addProperty("message", message);
            plugin.getRedisManager().publish(RedisChannels.PRIVATE_MESSAGE, payload);
            return true;
        }

        sendPrivateMessage(sender, targetName, message);
        return true;
    }

    // -------------------------------------------------------------------------
    // Social spy
    // -------------------------------------------------------------------------

    private void deliverSocialSpy(String senderName, String targetName, String message) {
        String format = plugin.getConfigManager().getMessage(
                "social-spy.format",
                "&8[SocialSpy] &7%sender% → %target%: &f%message%");
        String formatted = ComponentUtil.replace(format,
                "sender", senderName, "target", targetName, "message", message);
        Component component = ComponentUtil.parse(formatted);

        proxy.getAllPlayers().stream()
                .filter(p -> {
                    de.velocityhub.model.PlayerData data =
                            plugin.getPlayerDataManager().getPlayerData(p.getUniqueId())
                                    .orElse(null);
                    return data != null && data.isSocialSpyEnabled();
                })
                .forEach(p -> p.sendMessage(component));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void updateReplyTargets(String fromUuid, String toUuid) {
        replyTargets.put(fromUuid, toUuid);
    }
}
