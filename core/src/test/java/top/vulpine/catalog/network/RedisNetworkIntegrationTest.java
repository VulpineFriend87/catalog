package top.vulpine.catalog.network;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.RedisClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises {@link RedisNetwork} against a real Redis.
 *
 * <p>Excluded from the normal build. Run it with {@code gradlew :core:integrationTest} and
 * {@code CATALOG_REDIS} set to {@code host:port}. {@code CATALOG_REDIS_PASSWORD} is optional.</p>
 */
@Tag("integration")
class RedisNetworkIntegrationTest {

    /** A name no real server uses. */
    private static final String SERVER = "catalog-test-" + ProcessHandle.current().pid();

    private static RedisNetwork network;

    private static String address;

    private static String password;

    @BeforeAll
    static void connect() {

        address = System.getenv("CATALOG_REDIS");
        assumeTrue(address != null && !address.isEmpty(), "CATALOG_REDIS is not set");

        String given = System.getenv("CATALOG_REDIS_PASSWORD");
        password = given == null ? "" : given;
        network = RedisNetwork.connect(address(), Duration.ofMinutes(1));
    }

    @AfterAll
    static void close() {

        if (network == null) {
            return;
        }

        network.close();

        // Leaves nothing behind on a Redis that real servers use.
        DefaultJedisClientConfig.Builder config = DefaultJedisClientConfig.builder();

        if (!password.isEmpty()) {
            config.password(password);
        }

        try (RedisClient redis = RedisClient.builder()
                .hostAndPort(new HostAndPort(address().host(), address().port()))
                .clientConfig(config.build())
                .build()) {
            redis.del("catalog:alive:" + SERVER, "catalog:server:" + SERVER);
            redis.srem("catalog:servers", SERVER);
        }
    }

    /**
     * {@code CATALOG_REDIS} as {@code host:port}.
     */
    private static RedisNetwork.Address address() {

        int colon = address.lastIndexOf(':');
        return new RedisNetwork.Address(address.substring(0, colon),
                Integer.parseInt(address.substring(colon + 1)), "", password, 0, false);
    }

    @Test
    @DisplayName("a name is taken once, renewed, listed and given back")
    void names() {

        assertTrue(network.claim(SERVER, Duration.ofSeconds(30)).toCompletableFuture().join());
        assertFalse(network.claim(SERVER, Duration.ofSeconds(30)).toCompletableFuture().join());
        assertTrue(network.renew(SERVER, Duration.ofSeconds(30)).toCompletableFuture().join());

        network.publish(SERVER, "{}").toCompletableFuture().join();

        assertTrue(network.online().toCompletableFuture().join().contains(SERVER));
        assertEquals("{}", network.inventories().toCompletableFuture().join().get(SERVER));

        network.release(SERVER).toCompletableFuture().join();

        assertFalse(network.online().toCompletableFuture().join().contains(SERVER));
        assertFalse(network.renew(SERVER, Duration.ofSeconds(30)).toCompletableFuture().join());
    }

}
