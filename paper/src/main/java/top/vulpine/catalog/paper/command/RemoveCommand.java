package top.vulpine.catalog.paper.command;

import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.paper.command.annotation.RequiresPermission;
import top.vulpine.catalog.paper.command.flow.Chat;
import top.vulpine.catalog.paper.command.flow.Confirmations;
import top.vulpine.catalog.paper.command.flow.Lookup;
import top.vulpine.catalog.paper.command.flow.Redraw;
import top.vulpine.catalog.paper.command.screen.Outcomes;
import top.vulpine.catalog.paper.command.screen.RemovePage;
import top.vulpine.catalog.paper.command.screen.widget.Format;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.trash.TrashBin;

import java.util.List;

import static top.vulpine.catalog.paper.command.flow.Chat.send;

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
    public void uninstall(CommandSender sender,
                          @Named("plugin") @SuggestWith(Suggestions.Tracked.class) String query) {

        TrackedPlugin tracked = lookup.resolve(query);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(query));
            return;
        }

        if (catalog.isSelf(tracked)) {
            send(sender, Outcomes.cannotRemoveSelf(tracked.displayName()));
            return;
        }

        String data = context.take(sender);
        String name = tracked.displayName();

        chat.runAsync(sender, () -> {

            try {

                // Removing is normally offered with an undo rather than a confirmation. That stops
                // being enough when something else needs it: an undo cannot help a server that
                // already failed to start.
                if (!confirmations.confirmed(sender, "remove:" + tracked.projectId(), data)) {

                    List<TrackedPlugin> dependents = catalog.dependentsOf(tracked);

                    if (!dependents.isEmpty()) {
                        send(sender, RemovePage.confirmRemove(tracked, dependents, Press.of(data)));
                        return;
                    }
                }

                catalog.noticeLoaded();
                boolean loaded = !tracked.pendingLoad();

                TrashBin.Result result = catalog.uninstall(tracked, sender.getName());

                redraw.screen(sender, Press.screenOf(data));
                send(sender, Outcomes.removed(name, loaded, result == null ? null : result.entry(),
                        Press.screenOf(data)));

            } catch (Exception e) {
                send(sender, Outcomes.failed(Chat.rootMessage(e)));
            }
        });
    }

    @Subcommand("untrack")
    @Description("Stops managing a plugin, leaving the jar alone")
    @RequiresPermission("command.untrack")
    public void untrack(CommandSender sender,
                        @Named("plugin") @SuggestWith(Suggestions.Tracked.class) String query) {

        String data = context.take(sender);

        chat.runAsync(sender, () -> {

            TrackedPlugin tracked = lookup.resolve(query);

            if (tracked == null) {
                send(sender, Outcomes.unknownPlugin(query));
                return;
            }

            String name = tracked.displayName();

            // A staged build is already in the update folder, so leaving it there would update a
            // plugin Catalog has just been told to stop following.
            if (tracked.pendingRestart()) {
                catalog.cancelUpdate(tracked, sender.getName());
            }

            catalog.untrack(tracked, sender.getName());
            redraw.then(sender, data, Outcomes.untracked(name));
        });
    }

    @Subcommand("track")
    @Description("Manages a plugin Catalog was told to leave alone")
    @RequiresPermission("command.untrack")
    public void track(CommandSender sender,
                      @Named("jar") @SuggestWith(Suggestions.Untracked.class) String query) {

        String data = context.take(sender);

        chat.runAsync(sender, () -> {

            InstalledJar jar = lookup.resolveUntracked(query);

            if (jar == null) {
                send(sender, Outcomes.unknownPlugin(query));
                return;
            }

            if (!catalog.track(jar, sender.getName())) {
                send(sender, Outcomes.unknownPlugin(query));
                return;
            }

            redraw.then(sender, data, Outcomes.tracked(Format.shownName(jar)));
        });
    }

}
