package top.vulpine.catalog.paper.command.flow;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import top.vulpine.catalog.paper.CatalogPaper;
import top.vulpine.catalog.paper.command.Commands;
import top.vulpine.catalog.paper.command.screen.Outcomes;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sending screens, and running the work behind them off the main thread.
 */
public final class Chat {

    /** How long a command waits before saying it is still waiting. */
    private static final Duration PATIENCE = Duration.ofSeconds(2);

    private final CatalogPaper paper;

    public Chat(CatalogPaper paper) {
        this.paper = paper;
    }

    /**
     * Runs a command's work off the main thread, with a notice once it takes {@link #PATIENCE}.
     */
    public void runAsync(CommandSender sender, Runnable work) {

        paper.getScheduler().runAsync(task -> {

            AtomicBoolean done = new AtomicBoolean();

            WrappedTask waiting = paper.getScheduler().runLaterAsync(() -> {
                if (!done.get()) {
                    send(sender, Outcomes.stillWaiting());
                }
            }, PATIENCE.toMillis(), TimeUnit.MILLISECONDS);

            try {
                work.run();
            } finally {
                done.set(true);
                waiting.cancel();
            }
        });
    }

    /**
     * Sends a screen as this sender can use it.
     */
    public static void send(CommandSender sender, List<Component> lines) {

        sender.sendMessage(Component.empty());

        for (Component line : lines) {

            Component shown = Commands.PERMISSIONS.shown(sender, line);

            if (shown != null) {
                sender.sendMessage(shown);
            }
        }
    }

    public static void send(CommandSender sender, Component line) {
        send(sender, List.of(line));
    }

    /**
     * @return the message of the first cause that has one
     */
    public static String rootMessage(Throwable error) {

        Throwable cause = error;

        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }

        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

}
