package top.vulpine.catalog.command.service;

import net.kyori.adventure.text.Component;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.command.Commands;
import top.vulpine.catalog.command.screen.Outcomes;
import top.vulpine.catalog.platform.Viewer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Sending screens, and running the work behind them off the main thread.
 */
public final class Chat {

    /** How long a command waits before saying it is still waiting. */
    private static final Duration PATIENCE = Duration.ofSeconds(2);

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Catalog patience");
        thread.setDaemon(true);
        return thread;
    });

    private final Catalog catalog;

    public Chat(Catalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Runs a command's work off the main thread, with a notice once it takes {@link #PATIENCE}.
     */
    public void runAsync(Viewer viewer, Runnable work) {

        catalog.getPlatform().async(() -> {

            ScheduledFuture<?> waiting = TIMER.schedule(() -> send(viewer, Outcomes.stillWaiting()),
                    PATIENCE.toMillis(), TimeUnit.MILLISECONDS);

            try {
                work.run();
            } finally {
                waiting.cancel(false);
            }
        });
    }

    /**
     * Sends a screen as this viewer can use it.
     */
    public static void send(Viewer viewer, List<Component> lines) {

        viewer.send(Component.empty());

        for (Component line : lines) {

            Component shown = Commands.PERMISSIONS.shown(viewer, line);

            if (shown != null) {
                viewer.send(shown);
            }
        }
    }

    public static void send(Viewer viewer, Component line) {
        send(viewer, List.of(line));
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
