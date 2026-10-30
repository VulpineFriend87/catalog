package top.vulpine.catalog.network;

import top.vulpine.catalog.CatalogAction;
import top.vulpine.catalog.Errors;
import top.vulpine.commons.log.Logger;

import java.time.Duration;
import java.util.function.BiConsumer;

/**
 * This server's name on the network.
 *
 * <p>A name another server holds is asked for again every {@link #RETRY} for {@link #ATTEMPTS}
 * tries. A crashed server's name expires within that time. A clone keeps renewing it and is
 * refused.</p>
 */
public final class Presence {

    /** How long a name lasts without a renewal. */
    static final Duration TTL = Duration.ofSeconds(60);

    /** How often a held name is renewed. */
    static final Duration RENEW = Duration.ofSeconds(20);

    /** How long to wait before asking again for a name another server holds. */
    static final Duration RETRY = Duration.ofSeconds(5);

    /** How many times a held name is asked for. Together they outlast {@link #TTL}. */
    static final int ATTEMPTS = 13;

    private final Network network;

    private final String server;

    private final BiConsumer<Runnable, Duration> later;

    private final BiConsumer<Runnable, Duration> repeat;

    /** Runs when the name is taken and after every renewal. */
    private final Runnable beat;

    private volatile boolean held;

    private volatile boolean left;

    /**
     * @param later  runs a task once after a delay
     * @param repeat runs a task every period
     * @param beat   runs when the name is taken and after every renewal
     */
    public Presence(Network network, String server, BiConsumer<Runnable, Duration> later,
                    BiConsumer<Runnable, Duration> repeat, Runnable beat) {
        this.network = network;
        this.server = server;
        this.later = later;
        this.repeat = repeat;
        this.beat = beat;
    }

    /**
     * Asks for the name and keeps it once taken.
     */
    public void join() {
        attempt(ATTEMPTS, true);
    }

    /**
     * @return whether this server holds its name
     */
    public boolean held() {
        return held;
    }

    /**
     * Gives the name back if this server holds it.
     *
     * <p>Blocks. Called once on shutdown.</p>
     */
    public void leave() {

        left = true;

        if (!held) {
            return;
        }

        held = false;

        try {
            network.release(server).toCompletableFuture().get();
        } catch (Exception e) {
            // The name expires within the TTL anyway.
            Logger.debug(CatalogAction.NETWORK, "Could not give back " + server + ": "
                    + Errors.rootMessage(e));
        }
    }

    private void attempt(int attempts, boolean first) {

        if (left) {
            return;
        }

        network.claim(server, TTL).whenComplete((free, error) -> {

            // Asked again until Redis answers.
            if (error != null) {
                Logger.warn(CatalogAction.NETWORK, "Could not reach Redis: " + Errors.rootMessage(error));
                later.accept(() -> attempt(attempts, first), RENEW);
                return;
            }

            if (free) {
                take(first);
                return;
            }

            if (attempts > 1) {

                if (attempts == ATTEMPTS) {
                    Logger.warn(CatalogAction.NETWORK, server
                            + " is already on the network. Waiting for it to expire.");
                }

                later.accept(() -> attempt(attempts - 1, first), RETRY);
                return;
            }

            Logger.error(CatalogAction.NETWORK, "Another running server is called " + server
                    + ". Give this one its own server_name. The network is off.");
        });
    }

    private void take(boolean first) {

        held = true;
        Logger.info(CatalogAction.NETWORK, "Joined the network as " + server + ".");

        if (first) {
            repeat.accept(this::renew, RENEW);
        }

        beat.run();
    }

    private void renew() {

        if (!held || left) {
            return;
        }

        network.renew(server, TTL).whenComplete((kept, error) -> {

            if (error != null) {
                Logger.warn(CatalogAction.NETWORK, "Could not renew " + server + ": "
                        + Errors.rootMessage(error));
                return;
            }

            if (kept) {
                beat.run();
                return;
            }

            held = false;
            Logger.warn(CatalogAction.NETWORK, server + " was dropped from the network. Joining again.");
            attempt(ATTEMPTS, false);
        });
    }

}
