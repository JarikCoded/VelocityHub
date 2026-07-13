package de.velocityhub.test;

import de.velocityhub.model.ServerGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ServerGroup}.
 */
class ServerGroupTest {

    private ServerGroup group;

    @BeforeEach
    void setUp() {
        group = new ServerGroup("lobby", "Lobby");
    }

    @Test
    void getName_returnsName() {
        assertEquals("lobby", group.getName());
    }

    @Test
    void getDisplayName_returnsDisplayName() {
        assertEquals("Lobby", group.getDisplayName());
    }

    @Test
    void addServer_addsToList() {
        group.addServer("lobby-1");
        assertTrue(group.containsServer("lobby-1"));
    }

    @Test
    void addServer_duplicate_notAddedTwice() {
        group.addServer("lobby-1");
        group.addServer("lobby-1");
        assertEquals(1, group.getServerNames().size());
    }

    @Test
    void removeServer_removesFromList() {
        group.addServer("lobby-1");
        group.removeServer("lobby-1");
        assertFalse(group.containsServer("lobby-1"));
    }

    @Test
    void getServerNames_isUnmodifiable() {
        group.addServer("lobby-1");
        List<String> names = group.getServerNames();
        assertThrows(UnsupportedOperationException.class, () -> names.add("lobby-2"));
    }

    @Test
    void setItemLore_replacesLore() {
        group.setItemLore(List.of("Line 1", "Line 2"));
        assertEquals(List.of("Line 1", "Line 2"), group.getItemLore());
    }

    @Test
    void setItemLore_null_clearsLore() {
        group.setItemLore(List.of("Line 1"));
        group.setItemLore(null);
        assertTrue(group.getItemLore().isEmpty());
    }

    @Test
    void defaultItemMaterial_isCompass() {
        assertEquals("COMPASS", group.getItemMaterial());
    }

    @Test
    void defaultItemSlot_isFour() {
        assertEquals(4, group.getItemSlot());
    }

    @Test
    void itemMovable_defaultFalse() {
        assertFalse(group.isItemMovable());
    }

    @Test
    void itemDroppable_defaultFalse() {
        assertFalse(group.isItemDroppable());
    }

    @Test
    void equals_sameNameCaseInsensitive_returnsTrue() {
        ServerGroup other = new ServerGroup("LOBBY", "Different Display");
        assertEquals(group, other);
    }

    @Test
    void equals_differentName_returnsFalse() {
        ServerGroup other = new ServerGroup("minigames", "Minigames");
        assertNotEquals(group, other);
    }

    @Test
    void constructor_nullName_throwsNPE() {
        assertThrows(NullPointerException.class, () -> new ServerGroup(null, "Test"));
    }
}
