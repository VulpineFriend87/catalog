package top.vulpine.catalog.command;

import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.command.argument.Suggestions;
import top.vulpine.catalog.command.navigation.ClickContext;
import top.vulpine.catalog.command.navigation.Press;
import top.vulpine.catalog.command.permission.RequiresPermission;
import top.vulpine.catalog.command.screen.Outcomes;
import top.vulpine.catalog.command.screen.RemovePage;
import top.vulpine.catalog.command.screen.widget.Format;
import top.vulpine.catalog.command.service.Chat;
import top.vulpine.catalog.command.service.Confirmations;
import top.vulpine.catalog.command.service.Lookup;
import top.vulpine.catalog.command.service.Redraw;
import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.platform.Viewer;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.trash.TrashBin;

import java.util.List;

import static top.vulpine.catalog.command.service.Chat.send;

/**
 * Removing, untracking and tracking plugins.
 */
public final class RemoveCommand implements OrphanCommand {

    private final Catalog catalog;

    private final ClickContext context;

    private final Chat chat;

    private final Confirmations confirmations;

    private final Lookup lookup;

    private final Redraw redraw;

    public RemoveCommand(Catalog catalog, ClickContext context, Chat chat, Confirmations confirmations, Lookup lookup, Redraw redraw) {
        this.catalog = catalog;
        this.context = context;
        this.chat = chat;
        this.confirmations = confirmations;
        this.lookup = lookup;
        this.redraw = redraw;
    }

    @Subcommand("uninstall")
    @Description("Moves a plugin to the trash")
    @RequiresPermission("command.uninstall")
    public void uninstall(Viewer viewer,
                          @Named("plugin") @SuggestWith(Suggestions.Tracked.class) String query) {

        TrackedPlugin tracked = lookup.resolve(query);

        if (tracked == null) {
            send(viewer, Outcomes.unknownPlugin(query));
            return;
        }

        if (catalog.isSelf(tracked)) {
            send(viewer, Outcomes.cannotRemoveSelf(tracked.displayName()));
            return;
        }

        String data = context.take(viewer);
        String name = tracked.displayName();

        chat.runAsync(viewer, () -> {

            try {

                // Removing is normally offered with an undo rather than a confirmation. That stops
                // being enough when something else needs it: an undo cannot help a server that
                // already failed to start.
                if (!confirmations.confirmed(viewer, "remove:" + tracked.projectId(), data)) {

                    List<TrackedPlugin> dependents = catalog.dependentsOf(tracked);

                    if (!dependents.isEmpty()) {
                        send(viewer, RemovePage.confirmRemove(tracked, dependents, Press.of(data)));
                        return;
                    }
                }

                catalog.noticeLoaded();
                boolean loaded = !tracked.pendingLoad();

                TrashBin.Result result = catalog.uninstall(tracked, viewer.name());

                redraw.screen(viewer, Press.screenOf(data));
                send(viewer, Outcomes.removed(name, loaded, result == null ? null : result.entry(),
                        Press.screenOf(data)));

            } catch (Exception e) {
                send(viewer, Outcomes.failed(Chat.rootMessage(e)));
            }
        });
    }

    @Subcommand("untrack")
    @Description("Stops managing a plugin, leaving the jar alone")
    @RequiresPermission("command.untrack")
    public void untrack(Viewer viewer,
                        @Named("plugin") @SuggestWith(Suggestions.Tracked.class) String query) {

        String data = context.take(viewer);

        chat.runAsync(viewer, () -> {

            TrackedPlugin tracked = lookup.resolve(query);

            if (tracked == null) {
                send(viewer, Outcomes.unknownPlugin(query));
                return;
            }

            String name = tracked.displayName();

            // A staged build is already in the update folder, so leaving it there would update a
            // plugin Catalog has just been told to stop following.
            if (tracked.pendingRestart()) {
                catalog.cancelUpdate(tracked, viewer.name());
            }

            catalog.untrack(tracked, viewer.name());
            redraw.then(viewer, data, Outcomes.untracked(name));
        });
    }

    @Subcommand("track")
    @Description("Manages a plugin Catalog was told to leave alone")
    @RequiresPermission("command.untrack")
    public void track(Viewer viewer,
                      @Named("jar") @SuggestWith(Suggestions.Untracked.class) String query) {

        String data = context.take(viewer);

        chat.runAsync(viewer, () -> {

            InstalledJar jar = lookup.resolveUntracked(query);

            if (jar == null) {
                send(viewer, Outcomes.unknownPlugin(query));
                return;
            }

            if (!catalog.track(jar, viewer.name())) {
                send(viewer, Outcomes.unknownPlugin(query));
                return;
            }

            redraw.then(viewer, data, Outcomes.tracked(Format.shownName(jar)));
        });
    }

}
