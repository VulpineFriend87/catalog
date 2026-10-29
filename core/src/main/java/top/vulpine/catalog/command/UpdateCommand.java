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
import top.vulpine.catalog.command.service.Chat;
import top.vulpine.catalog.command.service.Installs;
import top.vulpine.catalog.command.service.Lookup;
import top.vulpine.catalog.command.service.Redraw;
import top.vulpine.catalog.platform.Viewer;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import static top.vulpine.catalog.command.service.Chat.send;

/**
 * {@code /catalog update} and {@code cancel}.
 */
public final class UpdateCommand implements OrphanCommand {

    private final Catalog catalog;

    private final ClickContext context;

    private final Chat chat;

    private final Lookup lookup;

    private final Installs installs;

    private final Redraw redraw;

    public UpdateCommand(Catalog catalog, ClickContext context, Chat chat, Lookup lookup, Installs installs, Redraw redraw) {
        this.catalog = catalog;
        this.context = context;
        this.chat = chat;
        this.lookup = lookup;
        this.installs = installs;
        this.redraw = redraw;
    }

    @Subcommand("update")
    @Description("Download an update and apply it at the next restart")
    @RequiresPermission("command.update")
    public void update(Viewer viewer,
                       @Named("plugin") @SuggestWith(Suggestions.Updatable.class) String query) {

        String wanted = ClickContext.strip(query);
        String data = context.take(viewer);

        if (wanted.equalsIgnoreCase("all")) {
            installs.updateAll(viewer, data);
            return;
        }

        TrackedPlugin tracked = lookup.resolve(wanted);

        if (tracked == null) {
            send(viewer, Outcomes.unknownPlugin(wanted));
            return;
        }

        chat.runAsync(viewer, () -> installs.runUpdate(viewer, tracked, data));
    }

    @Subcommand("cancel")
    @Description("Drop a downloaded update")
    @RequiresPermission("command.update")
    public void cancel(Viewer viewer,
                       @Named("plugin") @SuggestWith(Suggestions.Tracked.class) String query) {

        TrackedPlugin tracked = lookup.resolve(query);

        if (tracked == null) {
            send(viewer, Outcomes.unknownPlugin(query));
            return;
        }

        String data = context.take(viewer);

        if (!tracked.pendingRestart()) {
            send(viewer, Outcomes.nothingStaged(tracked.displayName()));
            return;
        }

        chat.runAsync(viewer, () -> {

            if (!catalog.cancelUpdate(tracked, viewer.name())) {
                send(viewer, Outcomes.failed("Could not delete the downloaded build for "
                        + tracked.displayName() + ", see the console."));
                return;
            }

            redraw.screen(viewer, Press.screenOf(data));
            send(viewer, Outcomes.cancelled(tracked.displayName()));
        });
    }

}
