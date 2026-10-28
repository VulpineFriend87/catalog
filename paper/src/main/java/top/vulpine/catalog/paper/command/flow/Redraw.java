package top.vulpine.catalog.paper.command.flow;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import top.vulpine.catalog.paper.command.Press;
import top.vulpine.catalog.paper.command.Screen;
import top.vulpine.catalog.paper.command.Trail;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import java.util.function.Supplier;

import static top.vulpine.catalog.paper.command.flow.Chat.send;

/**
 * Draws the screen a button was pressed on again.
 */
public final class Redraw {

    private final Chat chat;

    private final Lookup lookup;

    private final Screens screens;

    /** Asked for when needed, because it redraws through this. */
    private final Supplier<ChangeFlow> changes;

    public Redraw(Chat chat, Lookup lookup, Screens screens, Supplier<ChangeFlow> changes) {
        this.chat = chat;
        this.lookup = lookup;
        this.screens = screens;
        this.changes = changes;
    }

    /**
     * Draws a screen again, with the trail it was opened from.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param data the trail of the screen the button was on, or null
     */
    public void screen(CommandSender sender, String data) {

        Screen screen = Screen.parse(Trail.current(data));

        if (screen != null) {
            screen.accept(new Drawing(sender, data));
        }
    }

    /**
     * Reports what an action did, and redraws the screen it was taken on.
     *
     * @param data    what the button carried, or null when the command was typed
     * @param outcome the one line saying what happened
     */
    public void then(CommandSender sender, String data, Component outcome) {

        chat.runAsync(sender, () -> {
            screen(sender, Press.screenOf(data));
            send(sender, outcome);
        });
    }

    private final class Drawing implements Screen.Visitor {

        private final CommandSender sender;

        /** The trail of the screen. */
        private final String data;

        private Drawing(CommandSender sender, String data) {
            this.sender = sender;
            this.data = data;
        }

        @Override
        public void list(Screen.ListScreen screen) {
            screens.showList(sender, true, screen.filters());
        }

        @Override
        public void trash(Screen.TrashScreen screen) {
            screens.showTrash(sender, 1);
        }

        @Override
        public void info(Screen.InfoScreen screen) {
            screens.showProject(sender, screen.slug());
        }

        @Override
        public void settings(Screen.SettingsScreen screen) {

            TrackedPlugin tracked = lookup.resolve(screen.slug());

            if (tracked != null) {
                screens.showSettings(sender, tracked, Trail.parent(data));
            }
        }

        @Override
        public void dependencies(Screen.DependenciesScreen screen) {
            screens.showDependencies(sender, screen.slug(), screen.versionId(), Trail.parent(data));
        }

        @Override
        public void change(Screen.ChangeScreen screen) {

            if (!screen.verb().equals("update")) {
                changes.get().runInstall(sender, screen.slug(), screen.versionId(), true, data);
                return;
            }

            TrackedPlugin tracked = lookup.resolve(screen.slug());

            if (tracked != null) {
                changes.get().runUpdate(sender, tracked, data);
            }
        }

    }

}
