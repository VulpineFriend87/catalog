package top.vulpine.catalog.network;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import top.vulpine.commons.log.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Taking, keeping and giving back a server name.
 */
class PresenceTest {

    private final Names network = new Names();

    /** The renewal task the presence scheduled. */
    private final List<Runnable> renewals = new ArrayList<>();

    /** How many times the presence said it holds its name. */
    private int beats;

    /** How many times the presence asked again for a held name. */
    private int retries;

    /** Frees a held name after this many retries, or never at -1. */
    private int expiresAfter = -1;

    @BeforeAll
    static void logger() {
        Logger.builder().logger(ComponentLogger.logger("Catalog")).build();
    }

    private Presence presence(String server) {

        return new Presence(network, server, (task, delay) -> {
            retries++;

            if (retries == expiresAfter) {
                network.names.remove(server);
            }

            task.run();
        }, (task, period) -> renewals.add(task), () -> beats++);
    }

    @Test
    @DisplayName("a free name is taken at once and renewed")
    void free() {

        Presence presence = presence("lobby");
        presence.join();

        assertTrue(presence.held());
        assertEquals(1, beats);
        assertEquals(1, renewals.size());
        assertEquals(0, retries);
    }

    @Test
    @DisplayName("a name a running server keeps renewing is refused")
    void cloned() {

        network.names.put("lobby", "1");

        Presence presence = presence("lobby");
        presence.join();

        assertFalse(presence.held());
        assertEquals(0, beats);
        assertEquals(Presence.ATTEMPTS - 1, retries);
    }

    @Test
    @DisplayName("a crashed server's name is taken once it expires")
    void crashed() {

        network.names.put("lobby", "1");
        expiresAfter = 4;

        Presence presence = presence("lobby");
        presence.join();

        assertTrue(presence.held());
        assertEquals(1, beats);
        assertEquals(4, retries);
    }

    @Test
    @DisplayName("every renewal is a beat")
    void renewed() {

        Presence presence = presence("lobby");
        presence.join();

        renewals.get(0).run();
        renewals.get(0).run();

        assertEquals(3, beats);
    }

    @Test
    @DisplayName("a name Redis lost is taken again and published again")
    void dropped() {

        Presence presence = presence("lobby");
        presence.join();

        network.names.clear();
        renewals.get(0).run();

        assertTrue(presence.held());
        assertEquals(2, beats);
        assertEquals(1, renewals.size());
    }

    @Test
    @DisplayName("leaving gives the name back only when it was held")
    void leave() {

        network.names.put("lobby", "1");

        Presence refused = presence("lobby");
        refused.join();
        refused.leave();

        assertTrue(network.names.containsKey("lobby"));

        Presence held = presence("survival");
        held.join();
        held.leave();

        assertFalse(network.names.containsKey("survival"));
    }

    /** The names part of a network. Held in memory without expiry. */
    private static final class Names implements Network {

        private final Map<String, String> names = new HashMap<>();

        @Override
        public CompletionStage<Boolean> claim(String server, Duration ttl) {
            return CompletableFuture.completedFuture(names.putIfAbsent(server, "1") == null);
        }

        @Override
        public CompletionStage<Boolean> renew(String server, Duration ttl) {
            return CompletableFuture.completedFuture(names.containsKey(server));
        }

        @Override
        public CompletionStage<Void> release(String server) {
            names.remove(server);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> publish(String server, String inventory) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Map<String, String>> inventories() {
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public CompletionStage<Set<String>> online() {
            return CompletableFuture.completedFuture(names.keySet());
        }

        @Override
        public void close() {
        }

    }

}
