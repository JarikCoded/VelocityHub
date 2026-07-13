package de.velocityhub.test;

import de.velocityhub.model.NetworkServer;
import de.velocityhub.model.ServerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NetworkServer}.
 */
class NetworkServerTest {

    private NetworkServer server;

    @BeforeEach
    void setUp() {
        server = new NetworkServer("test-1", "Test Server", "A test MOTD", "STONE", 100);
    }

    @Test
    void initialStatus_isOffline() {
        assertEquals(ServerStatus.OFFLINE, server.getStatus());
    }

    @Test
    void initialPlayerCount_isZero() {
        assertEquals(0, server.getOnlinePlayers());
    }

    @Test
    void setStatus_updatesStatus() {
        server.setStatus(ServerStatus.ONLINE);
        assertEquals(ServerStatus.ONLINE, server.getStatus());
    }

    @Test
    void setOnlinePlayers_updatesCount() {
        server.setOnlinePlayers(42);
        assertEquals(42, server.getOnlinePlayers());
    }

    @Test
    void setOnlinePlayers_negativeClampsToZero() {
        server.setOnlinePlayers(-5);
        assertEquals(0, server.getOnlinePlayers());
    }

    @Test
    void setMaxPlayers_updatesMax() {
        server.setMaxPlayers(200);
        assertEquals(200, server.getMaxPlayers());
    }

    @Test
    void setMaxPlayers_zeroOrNegativeSetsToOne() {
        server.setMaxPlayers(0);
        assertEquals(1, server.getMaxPlayers());
    }

    @Test
    void isJoinable_onlineAndNotFull_returnsTrue() {
        server.setStatus(ServerStatus.ONLINE);
        server.setOnlinePlayers(50);
        server.setMaxPlayers(100);
        assertTrue(server.isJoinable());
    }

    @Test
    void isJoinable_offline_returnsFalse() {
        server.setStatus(ServerStatus.OFFLINE);
        assertFalse(server.isJoinable());
    }

    @Test
    void isJoinable_full_returnsFalse() {
        server.setStatus(ServerStatus.ONLINE);
        server.setOnlinePlayers(100);
        server.setMaxPlayers(100);
        assertFalse(server.isJoinable());
    }

    @Test
    void isJoinable_maintenance_returnsFalse() {
        server.setStatus(ServerStatus.MAINTENANCE);
        assertFalse(server.isJoinable());
    }

    @Test
    void equals_sameNameCaseInsensitive_returnsTrue() {
        NetworkServer other = new NetworkServer("TEST-1", "Other", "", "STONE", 50);
        assertEquals(server, other);
    }

    @Test
    void equals_differentName_returnsFalse() {
        NetworkServer other = new NetworkServer("test-2", "Other", "", "STONE", 50);
        assertNotEquals(server, other);
    }

    @Test
    void hashCode_sameNameCaseInsensitive_isSame() {
        NetworkServer other = new NetworkServer("TEST-1", "Other", "", "STONE", 50);
        assertEquals(server.hashCode(), other.hashCode());
    }

    @Test
    void setDisplayName_null_throwsNPE() {
        assertThrows(NullPointerException.class, () -> server.setDisplayName(null));
    }

    @Test
    void constructor_nullName_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
                new NetworkServer(null, "Test", "", "STONE", 100));
    }

    @Test
    void toString_containsServerName() {
        assertTrue(server.toString().contains("test-1"));
    }
}
