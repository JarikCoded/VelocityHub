package de.velocityhub.test;

import de.velocityhub.model.QueueEntry;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link QueueEntry} ordering.
 */
class QueueEntryTest {

    private static final UUID UUID_1 = UUID.randomUUID();
    private static final UUID UUID_2 = UUID.randomUUID();
    private static final UUID UUID_3 = UUID.randomUUID();

    @Test
    void compareTo_higherPriorityComesFirst() {
        QueueEntry low  = new QueueEntry(UUID_1, "Player1", "server", 0);
        QueueEntry high = new QueueEntry(UUID_2, "Player2", "server", 100);

        PriorityBlockingQueue<QueueEntry> queue = new PriorityBlockingQueue<>();
        queue.add(low);
        queue.add(high);

        assertEquals(high, queue.poll()); // higher priority first
        assertEquals(low,  queue.poll());
    }

    @Test
    void compareTo_equalPriority_fifoOrdering() throws InterruptedException {
        QueueEntry first  = new QueueEntry(UUID_1, "Player1", "server", 0);
        Thread.sleep(5); // ensure different timestamps
        QueueEntry second = new QueueEntry(UUID_2, "Player2", "server", 0);

        List<QueueEntry> sorted = new ArrayList<>(List.of(second, first));
        Collections.sort(sorted);

        assertEquals(first,  sorted.get(0)); // joined first → comes first
        assertEquals(second, sorted.get(1));
    }

    @Test
    void equals_samePlayerAndServer_returnsTrue() {
        QueueEntry e1 = new QueueEntry(UUID_1, "Player1", "server", 0);
        QueueEntry e2 = new QueueEntry(UUID_1, "Player1", "server", 99);
        assertEquals(e1, e2);
    }

    @Test
    void equals_differentServer_returnsFalse() {
        QueueEntry e1 = new QueueEntry(UUID_1, "Player1", "server-1", 0);
        QueueEntry e2 = new QueueEntry(UUID_1, "Player1", "server-2", 0);
        assertNotEquals(e1, e2);
    }

    @Test
    void constructor_null_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
                new QueueEntry(null, "Player", "server", 0));
        assertThrows(NullPointerException.class, () ->
                new QueueEntry(UUID_1, null, "server", 0));
        assertThrows(NullPointerException.class, () ->
                new QueueEntry(UUID_1, "Player", null, 0));
    }
}
