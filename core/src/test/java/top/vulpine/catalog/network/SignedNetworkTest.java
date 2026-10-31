package top.vulpine.catalog.network;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.vulpine.commons.log.Logger;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inventories that only servers with the shared secret can publish.
 */
class SignedNetworkTest {

    private static final String SECRET = "a-secret-every-server-shares";

    private final Store store = new Store();

    private final SignedNetwork network = new SignedNetwork(store, SECRET);

    @BeforeAll
    static void logger() {
        Logger.builder().logger(ComponentLogger.logger("Catalog")).build();
    }

    private Map<String, String> read() {
        return network.inventories().toCompletableFuture().join();
    }

    @Test
    @DisplayName("an inventory signed with the secret is read back as it was")
    void signed() {

        network.publish("lobby", "{\"plugins\":[]}").toCompletableFuture().join();

        assertEquals("{\"plugins\":[]}", read().get("lobby"));
        assertTrue(store.values.get("lobby").endsWith("\n{\"plugins\":[]}"));
    }

    @Test
    @DisplayName("an inventory changed after signing is left out")
    void tampered() {

        network.publish("lobby", "{\"plugins\":[]}").toCompletableFuture().join();
        store.values.computeIfPresent("lobby", (name, value) -> value.replace("[]", "[{\"name\":\"x\"}]"));

        assertFalse(read().containsKey("lobby"));
    }

    @Test
    @DisplayName("an inventory moved under another server's name is left out")
    void moved() {

        network.publish("lobby", "{}").toCompletableFuture().join();
        store.values.put("survival", store.values.get("lobby"));

        assertFalse(read().containsKey("survival"));
        assertTrue(read().containsKey("lobby"));
    }

    @Test
    @DisplayName("an inventory signed with another secret or not signed at all is left out")
    void foreign() {

        new SignedNetwork(store, "a-different-secret-entirely").publish("lobby", "{}")
                .toCompletableFuture().join();
        store.values.put("survival", "{}");

        assertTrue(read().isEmpty());
    }

    /** Inventories held in memory as stored. */
    private static final class Store implements Network {

        private final Map<String, String> values = new LinkedHashMap<>();

        @Override
        public CompletionStage<Boolean> claim(String server, Duration ttl) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletionStage<Boolean> renew(String server, Duration ttl) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletionStage<Void> release(String server) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> publish(String server, String inventory) {
            values.put(server, inventory);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Map<String, String>> inventories() {
            return CompletableFuture.completedFuture(new LinkedHashMap<>(values));
        }

        @Override
        public CompletionStage<Set<String>> online() {
            return CompletableFuture.completedFuture(values.keySet());
        }

        @Override
        public CompletionStage<Duration> ping() {
            return CompletableFuture.completedFuture(Duration.ZERO);
        }

        @Override
        public void close() {
        }

    }

}
