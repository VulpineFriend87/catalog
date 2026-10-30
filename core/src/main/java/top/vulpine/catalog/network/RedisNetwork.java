package top.vulpine.catalog.network;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * The {@link Network} on a Redis server.
 *
 * <p>{@code catalog:servers} is the set of every name that published. {@code catalog:server:<name>}
 * holds an inventory for {@link #keep} after its server's last renewal. {@code catalog:alive:<name>}
 * expires unless its server renews it.</p>
 */
public final class RedisNetwork implements Network {

    private static final String SERVERS = "catalog:servers";

    private final RedisClient redis;

    /** How long an inventory outlives its server's last renewal. */
    private final Duration keep;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "Catalog network");
        thread.setDaemon(true);
        return thread;
    });

    private RedisNetwork(RedisClient redis, Duration keep) {
        this.redis = redis;
        this.keep = keep;
    }

    /**
     * Connects to Redis.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param address  {@code redis://host:port}
     * @param password the password, or empty for none
     * @param keep     how long an offline server stays listed
     */
    public static RedisNetwork connect(String address, String password, Duration keep) {

        URI uri = URI.create(address);
        DefaultJedisClientConfig.Builder config = DefaultJedisClientConfig.builder();

        if (!password.isEmpty()) {
            config.password(password);
        }

        RedisClient redis = RedisClient.builder()
                .hostAndPort(new HostAndPort(uri.getHost(), uri.getPort() < 0 ? 6379 : uri.getPort()))
                .clientConfig(config.build())
                .build();

        try {
            redis.ping();
        } catch (RuntimeException e) {
            redis.close();
            throw e;
        }

        return new RedisNetwork(redis, keep);
    }

    @Override
    public CompletionStage<Boolean> claim(String server, Duration ttl) {
        return run(() -> "OK".equals(redis.set(alive(server), "1",
                SetParams.setParams().nx().ex(ttl.toSeconds()))));
    }

    @Override
    public CompletionStage<Boolean> renew(String server, Duration ttl) {

        return run(() -> {

            boolean kept = redis.expire(alive(server), ttl.toSeconds()) == 1;

            if (kept) {
                redis.expire(inventory(server), kept(ttl));
            }

            return kept;
        });
    }

    @Override
    public CompletionStage<Void> release(String server) {

        return run(() -> {
            redis.del(alive(server));
            return null;
        });
    }

    @Override
    public CompletionStage<Void> publish(String server, String inventory) {

        return run(() -> {
            redis.set(inventory(server), inventory, SetParams.setParams().ex(kept(Presence.TTL)));
            redis.sadd(SERVERS, server);
            return null;
        });
    }

    @Override
    public CompletionStage<Map<String, String>> inventories() {

        return run(() -> {

            List<String> names = new ArrayList<>(redis.smembers(SERVERS));
            Map<String, String> out = new LinkedHashMap<>();

            if (names.isEmpty()) {
                return out;
            }

            List<String> values = redis.mget(names.stream()
                    .map(RedisNetwork::inventory)
                    .toArray(String[]::new));

            for (int i = 0; i < names.size(); i++) {

                if (values.get(i) != null) {
                    out.put(names.get(i), values.get(i));
                    continue;
                }

                // Offline longer than it is kept.
                redis.srem(SERVERS, names.get(i));
            }

            return out;
        });
    }

    @Override
    public CompletionStage<Set<String>> online() {

        return run(() -> {

            List<String> names = new ArrayList<>(redis.smembers(SERVERS));
            Set<String> out = new HashSet<>();

            if (names.isEmpty()) {
                return out;
            }

            List<String> values = redis.mget(names.stream()
                    .map(RedisNetwork::alive)
                    .toArray(String[]::new));

            for (int i = 0; i < names.size(); i++) {
                if (values.get(i) != null) {
                    out.add(names.get(i));
                }
            }

            return out;
        });
    }

    @Override
    public CompletionStage<Duration> ping() {

        return run(() -> {
            long start = System.nanoTime();
            redis.ping();
            return Duration.ofNanos(System.nanoTime() - start);
        });
    }

    @Override
    public void close() {
        executor.shutdown();
        redis.close();
    }

    /**
     * Seconds an inventory is kept. Never less than the name it belongs to.
     */
    private long kept(Duration ttl) {
        return Math.max(keep.toSeconds(), ttl.toSeconds());
    }

    private <T> CompletionStage<T> run(Supplier<T> call) {
        return CompletableFuture.supplyAsync(call, executor);
    }

    private static String alive(String server) {
        return "catalog:alive:" + server;
    }

    private static String inventory(String server) {
        return "catalog:server:" + server;
    }

}
