package top.vulpine.catalog.command.navigation;

import revxrsal.commands.command.CommandActor;
import top.vulpine.catalog.platform.Viewer;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Remembers which screen a button was pressed on.
 *
 * <p>Buttons run {@code /catalog-do <screen> <command>}. Only the command reaches Lamp. Only
 * {@code /catalog} commands are run. {@link ClickCommand} registers the wrapper.</p>
 *
 * <p>Commands are dispatched through Lamp. Bukkit's dispatch returns before the command runs.</p>
 */
public final class ClickContext {

    /** What every button actually runs. */
    public static final String CLICK = "/catalog-do ";

    /** Stands in for the screen when a button was not offered from one. */
    private static final String NOWHERE = "-";

    /**
     * Marks a payload as the answer to a confirmation.
     */
    public static final String CONFIRM = "confirm:";

    /** Marks an install, update or switch without its required plugins. */
    public static final String ALONE = "alone:";

    private final Map<String, String> pending = new ConcurrentHashMap<>();

    /**
     * Set after the command tree is built.
     */
    private BiConsumer<CommandActor, String> dispatch;

    /**
     * @param dispatch runs a command line without its slash through the command tree
     */
    public void dispatcher(BiConsumer<CommandActor, String> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * Remembers the screen and runs what the button meant.
     *
     * @param arguments everything after the wrapper: a screen, a space, then the real command
     */
    public void press(CommandActor actor, String arguments) {

        String rest = arguments.trim();
        int space = rest.indexOf(' ');

        if (space < 0) {
            return;
        }

        String screen = rest.substring(0, space);
        String command = rest.substring(space + 1).trim();

        // Only Catalog's own commands.
        if (!isCatalog(command)) {
            return;
        }

        String name = actor.name();

        if (NOWHERE.equals(screen)) {
            pending.remove(name);
        } else {
            pending.put(name, screen);
        }

        // Lamp runs it before returning.
        try {
            dispatch.accept(actor, command.substring(1));
        } finally {
            pending.remove(name);
        }
    }

    private static boolean isCatalog(String message) {

        String lower = message.toLowerCase(Locale.ROOT);
        return lower.startsWith("/catalog ");
    }

    /**
     * Wraps a command with the screen it is offered from.
     *
     * @param command the command to run with its leading slash
     * @param screen  the trail of the screen it is offered from, or null when it is not from one
     * @return what the button should run
     */
    public static String press(String command, String screen) {
        return CLICK + (screen == null || screen.isEmpty() ? NOWHERE : screen) + " " + command;
    }

    /**
     * Reads and forgets the screen for a viewer.
     *
     * @param viewer who pressed the button
     * @return the screen, or null if the command was typed
     */
    public String take(Viewer viewer) {
        return pending.remove(viewer.name());
    }

    /**
     * The command inside a button's {@code /catalog-do} wrapper.
     *
     * @param command a command with or without the wrapper
     * @return the command without the wrapper and the screen
     */
    public static String strip(String command) {

        if (!command.startsWith(CLICK)) {
            return command;
        }

        String rest = command.substring(CLICK.length()).trim();
        int space = rest.indexOf(' ');

        return space < 0 ? rest : rest.substring(space + 1).trim();
    }

}
