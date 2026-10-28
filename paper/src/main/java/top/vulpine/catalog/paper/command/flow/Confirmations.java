package top.vulpine.catalog.paper.command.flow;

import org.bukkit.command.CommandSender;
import top.vulpine.catalog.paper.command.Press;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each sender has been asked to confirm.
 */
public final class Confirmations {

    /** How long a removal stays confirmable before it has to be asked for again. */
    private static final Duration WINDOW = Duration.ofSeconds(30);

    private final Map<String, Pending> asked = new ConcurrentHashMap<>();

    /**
     * Forgets a removal waiting to be confirmed.
     *
     * <p>Called whenever a screen is drawn, which is what makes Cancel actually cancel: the button
     * navigates away, and without this the confirmation would still be armed, so pressing the same
     * remove button again inside the window would go straight through without asking.</p>
     */
    public void abandon(CommandSender sender) {
        asked.remove(sender.getName());
    }

    /**
     * Whether to go ahead, or to ask first.
     *
     * @param action identifies precisely what is being confirmed
     * @param data   the payload the command arrived with, or null if it was typed
     * @return true to go ahead, false when the caller should show a confirmation instead
     */
    public boolean confirmed(CommandSender sender, String action, String data) {

        if (data != null) {
            return Press.of(data).confirmed();
        }

        Pending pending = asked.get(sender.getName());

        if (pending != null && !pending.expired() && pending.action().equals(action)) {
            asked.remove(sender.getName());
            return true;
        }

        asked.put(sender.getName(), new Pending(action, Instant.now()));
        return false;
    }

    /**
     * @param action what is waiting to be confirmed, identified precisely enough that confirming
     *               one thing can never carry out another
     */
    private record Pending(String action, Instant asked) {

        private boolean expired() {
            return Duration.between(asked, Instant.now()).compareTo(WINDOW) > 0;
        }

    }

}
