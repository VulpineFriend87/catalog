package top.vulpine.catalog.paper.command;

import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.paper.command.annotation.RequiresPermission;
import top.vulpine.catalog.paper.command.flow.ChangeFlow;
import top.vulpine.catalog.paper.command.flow.Chat;
import top.vulpine.catalog.paper.command.flow.Lookup;
import top.vulpine.catalog.paper.command.flow.Redraw;
import top.vulpine.catalog.paper.command.screen.Outcomes;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import static top.vulpine.catalog.paper.command.flow.Chat.send;

/**
 * Downloading and cancelling updates.
 */
public final class UpdateCommand implements OrphanCommand {

    private final Catalog catalog;

    private final ClickContext context;

    private final Chat chat;

    private final Lookup lookup;

    private final ChangeFlow changes;

    private final Redraw redraw;

    public UpdateCommand(Catalog catalog, ClickContext context, Chat chat, Lookup lookup, ChangeFlow changes, Redraw redraw) {
        this.catalog = catalog;
        this.context = context;
        this.chat = chat;
        this.lookup = lookup;
        this.changes = changes;
        this.redraw = redraw;
    }

    @Subcommand("update")
    @Description("Download an update and apply it at the next restart")
    @RequiresPermission("command.update")
    public void update(CommandSender sender,
                       @Named("plugin") @SuggestWith(Suggestions.Updatable.class) String query) {

        String wanted = ClickContext.strip(query);
        String data = context.take(sender);

        if (wanted.equalsIgnoreCase("all")) {
            changes.updateAll(sender, data);
            return;
        }

        TrackedPlugin tracked = lookup.resolve(wanted);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(wanted));
            return;
        }

        chat.runAsync(sender, () -> changes.runUpdate(sender, tracked, data));
    }

    @Subcommand("cancel")
    @Description("Drop a downloaded update")
    @RequiresPermission("command.update")
    public void cancel(CommandSender sender,
                       @Named("plugin") @SuggestWith(Suggestions.Tracked.class) String query) {

        TrackedPlugin tracked = lookup.resolve(query);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(query));
            return;
        }

        String data = context.take(sender);

        if (!tracked.pendingRestart()) {
            send(sender, Outcomes.nothingStaged(tracked.displayName()));
            return;
        }

        chat.runAsync(sender, () -> {

            if (!catalog.cancelUpdate(tracked, sender.getName())) {
                send(sender, Outcomes.failed("Could not delete the downloaded build for "
                        + tracked.displayName() + ", see the console."));
                return;
            }

            redraw.screen(sender, Press.screenOf(data));
            send(sender, Outcomes.cancelled(tracked.displayName()));
        });
    }

}
