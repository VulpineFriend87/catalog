package top.vulpine.catalog.network;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Where Catalog servers announce themselves and publish what they hold.
 */
public interface Network extends AutoCloseable {

    /**
     * Takes a server name for {@code ttl} unless another server holds it.
     *
     * @return true when the name was free
     */
    CompletionStage<Boolean> claim(String server, Duration ttl);

    /**
     * Extends a name this server holds.
     *
     * @return false when the name is no longer held. Redis restarting empty does that.
     */
    CompletionStage<Boolean> renew(String server, Duration ttl);

    /**
     * Gives a name back.
     */
    CompletionStage<Void> release(String server);

    /**
     * Stores what a server holds in place of what it published before.
     *
     * @param inventory the server's inventory as JSON
     */
    CompletionStage<Void> publish(String server, String inventory);

    /**
     * @return every published inventory as JSON by server name
     */
    CompletionStage<Map<String, String>> inventories();

    /**
     * @return the names held right now
     */
    CompletionStage<Set<String>> online();

    /**
     * @return how long the network took to answer
     */
    CompletionStage<Duration> ping();

    @Override
    void close();

}
