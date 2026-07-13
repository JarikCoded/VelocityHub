package de.velocityhub.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a named group of {@link NetworkServer}s.
 *
 * <p>Groups are used to organise servers (e.g. "Lobby", "Survival") and may
 * carry an optional GUI lobby-item definition.</p>
 */
public final class ServerGroup {

    private final String name;
    private final AtomicReference<String> displayName;
    private final CopyOnWriteArrayList<String> serverNames;

    // Lobby item configuration
    private final AtomicReference<String> itemMaterial;
    private final AtomicInteger itemSlot;
    private final AtomicReference<String> itemName;
    private final CopyOnWriteArrayList<String> itemLore;
    private volatile boolean itemMovable;
    private volatile boolean itemDroppable;

    /**
     * Creates a new server group.
     *
     * @param name        the unique identifier of this group
     * @param displayName the human-readable display name
     */
    public ServerGroup(String name, String displayName) {
        this.name = Objects.requireNonNull(name, "name");
        this.displayName = new AtomicReference<>(displayName != null ? displayName : name);
        this.serverNames = new CopyOnWriteArrayList<>();
        this.itemMaterial = new AtomicReference<>("COMPASS");
        this.itemSlot = new AtomicInteger(4);
        this.itemName = new AtomicReference<>("&aServer Selection");
        this.itemLore = new CopyOnWriteArrayList<>();
        this.itemMovable = false;
        this.itemDroppable = false;
    }

    // -------------------------------------------------------------------------
    // Basic accessors
    // -------------------------------------------------------------------------

    /** @return the unique identifier of this group */
    public String getName() { return name; }

    /** @return the human-readable display name */
    public String getDisplayName() { return displayName.get(); }

    /** @param displayName new display name */
    public void setDisplayName(String displayName) {
        this.displayName.set(Objects.requireNonNull(displayName));
    }

    // -------------------------------------------------------------------------
    // Server membership
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable snapshot of the server names in this group.
     *
     * @return list of server name identifiers
     */
    public List<String> getServerNames() {
        return Collections.unmodifiableList(new ArrayList<>(serverNames));
    }

    /**
     * Adds a server to this group.
     *
     * @param serverName the server identifier to add
     */
    public void addServer(String serverName) {
        if (serverName != null && !serverNames.contains(serverName)) {
            serverNames.add(serverName);
        }
    }

    /**
     * Removes a server from this group.
     *
     * @param serverName the server identifier to remove
     */
    public void removeServer(String serverName) {
        serverNames.remove(serverName);
    }

    /** @return {@code true} if the group contains the given server name */
    public boolean containsServer(String serverName) {
        return serverNames.contains(serverName);
    }

    // -------------------------------------------------------------------------
    // Lobby-item accessors
    // -------------------------------------------------------------------------

    /** @return the Minecraft material name of the lobby item */
    public String getItemMaterial() { return itemMaterial.get(); }

    /** @param material new material name */
    public void setItemMaterial(String material) {
        itemMaterial.set(material != null ? material : "COMPASS");
    }

    /** @return the hotbar/inventory slot for the lobby item */
    public int getItemSlot() { return itemSlot.get(); }

    /** @param slot new slot index (0-based) */
    public void setItemSlot(int slot) { itemSlot.set(slot); }

    /** @return the display name of the lobby item */
    public String getItemName() { return itemName.get(); }

    /** @param name new item display name */
    public void setItemName(String name) {
        itemName.set(name != null ? name : "");
    }

    /**
     * Returns an unmodifiable snapshot of the lobby-item lore lines.
     *
     * @return lore lines
     */
    public List<String> getItemLore() {
        return Collections.unmodifiableList(new ArrayList<>(itemLore));
    }

    /** Replaces the lore with the supplied lines. */
    public void setItemLore(List<String> lore) {
        itemLore.clear();
        if (lore != null) itemLore.addAll(lore);
    }

    /** @return {@code true} if players are allowed to move the lobby item */
    public boolean isItemMovable() { return itemMovable; }

    /** @param movable whether players can move the item */
    public void setItemMovable(boolean movable) { this.itemMovable = movable; }

    /** @return {@code true} if players are allowed to drop the lobby item */
    public boolean isItemDroppable() { return itemDroppable; }

    /** @param droppable whether players can drop the item */
    public void setItemDroppable(boolean droppable) { this.itemDroppable = droppable; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerGroup other)) return false;
        return name.equalsIgnoreCase(other.name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return "ServerGroup{name='" + name + "', servers=" + serverNames + "}";
    }
}
