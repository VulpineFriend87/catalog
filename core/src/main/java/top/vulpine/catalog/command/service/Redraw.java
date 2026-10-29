package top.vulpine.catalog.command.service;

import net.kyori.adventure.text.Component;
import top.vulpine.catalog.command.navigation.Press;
import top.vulpine.catalog.command.navigation.Screen;
import top.vulpine.catalog.command.navigation.Trail;
import top.vulpine.catalog.platform.Viewer;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import java.util.function.Supplier;

import static top.vulpine.catalog.command.service.Chat.send;

/**
 * Draws the screen a button was pressed on again.
 */
public final class Redraw {

    private final Chat chat;

    private final Lookup lookup;

    private final Screens screens;

    /** Read when needed. Installs is built after this. */
    private final Supplier<Installs> installs;

    public Redraw(Chat chat, Lookup lookup, Screens screens, Supplier<Installs> installs) {
        this.chat = chat;
        this.lookup = lookup;
        this.screens = screens;
        this.installs = installs;
    }

    /**
     * Draws a screen again with the trail it was opened from.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param data the trail of the screen the button was on, or null
     */
    public void screen(Viewer viewer, String data) {

        Screen screen = Screen.parse(Trail.current(data));

        if (screen != null) {
            screen.accept(new Drawing(viewer, data));
        }
    }

    /**
     * Redraws the screen an action was taken on and sends what it did.
     *
     * @param data    what the button carried, or null when the command was typed
     * @param outcome the one line saying what happened
     */
    public void then(Viewer viewer, String data, Component outcome) {

        chat.runAsync(viewer, () -> {
            screen(viewer, Press.screenOf(data));
            send(viewer, outcome);
        });
    }

    private final class Drawing implements Screen.Visitor {

        private final Viewer viewer;

        /** The trail of the screen. */
        private final String data;

        private Drawing(Viewer viewer, String data) {
            this.viewer = viewer;
            this.data = data;
        }

        @Override
        public void list(Screen.ListScreen screen) {
            screens.showList(viewer, true, screen.filters());
        }

        @Override
        public void trash(Screen.TrashScreen screen) {
            screens.showTrash(viewer, 1);
        }

        @Override
        public void info(Screen.InfoScreen screen) {
            screens.showProject(viewer, screen.slug());
        }

        @Override
        public void settings(Screen.SettingsScreen screen) {

            TrackedPlugin tracked = lookup.resolve(screen.slug());

            if (tracked != null) {
                screens.showSettings(viewer, tracked, Trail.parent(data));
            }
        }

        @Override
        public void dependencies(Screen.DependenciesScreen screen) {
            screens.showDependencies(viewer, screen.slug(), screen.versionId(), Trail.parent(data));
        }

        @Override
        public void change(Screen.ChangeScreen screen) {

            if (!screen.verb().equals("update")) {
                installs.get().runInstall(viewer, screen.slug(), screen.versionId(), true, data);
                return;
            }

            TrackedPlugin tracked = lookup.resolve(screen.slug());

            if (tracked != null) {
                installs.get().runUpdate(viewer, tracked, data);
            }
        }

    }

}
