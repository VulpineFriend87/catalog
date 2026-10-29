package top.vulpine.catalog.command.service;

import top.vulpine.catalog.command.navigation.Press;
import top.vulpine.catalog.platform.Viewer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each viewer has been asked to confirm.
 */
public final class Confirmations {

    /** How long a removal stays confirmable before it has to be asked for again. */
    private static final Duration WINDOW = Duration.ofSeconds(30);

    private final Map<String, Pending> asked = new ConcurrentHashMap<>();

    /**
     * Forgets a removal waiting to be confirmed.
     *
     * <p>Called whenever a screen is drawn.</p>
     */
    public void abandon(Viewer viewer) {
        asked.remove(viewer.name());
    }

    /**
     * Whether to go ahead or ask first.
     *
     * @param action what is being confirmed
     * @param data   the payload the command arrived with, or null if it was typed
     * @return true to go ahead, false to ask first
     */
    public boolean confirmed(Viewer viewer, String action, String data) {

        if (data != null) {
            return Press.of(data).confirmed();
        }

        Pending pending = asked.get(viewer.name());

        if (pending != null && !pending.expired() && pending.action().equals(action)) {
            asked.remove(viewer.name());
            return true;
        }

        asked.put(viewer.name(), new Pending(action, Instant.now()));
        return false;
    }

    /**
     * @param action exactly what is waiting to be confirmed
     */
    private record Pending(String action, Instant asked) {

        private boolean expired() {
            return Duration.between(asked, Instant.now()).compareTo(WINDOW) > 0;
        }

    }

}
