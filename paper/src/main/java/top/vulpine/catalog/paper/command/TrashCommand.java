package top.vulpine.catalog.paper.command;

import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Default;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Flag;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.paper.command.annotation.RequiresPermission;
import top.vulpine.catalog.paper.command.flow.Chat;
import top.vulpine.catalog.paper.command.flow.Confirmations;
import top.vulpine.catalog.paper.command.flow.Lookup;
import top.vulpine.catalog.paper.command.flow.Redraw;
import top.vulpine.catalog.paper.command.flow.Screens;
import top.vulpine.catalog.paper.command.screen.Outcomes;
import top.vulpine.catalog.paper.command.screen.TrashPage;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.trash.model.TrashEntry;

import static top.vulpine.catalog.paper.command.flow.Chat.send;

/**
 * The trash, restoring and deleting removals.
 */
public final class TrashCommand implements OrphanCommand {

    private final Catalog catalog;

    private final ClickContext context;

    private final Chat chat;

    private final Confirmations confirmations;

    private final Lookup lookup;

    private final Screens screens;

    private final Redraw redraw;

    public TrashCommand(Catalog catalog, ClickContext context, Chat chat, Confirmations confirmations, Lookup lookup, Screens screens, Redraw redraw) {
        this.catalog = catalog;
        this.context = context;
        this.chat = chat;
        this.confirmations = confirmations;
        this.lookup = lookup;
        this.screens = screens;
        this.redraw = redraw;
    }

    @Subcommand("trash")
    @Description("Show plugins you have removed")
    @RequiresPermission("command.trash")
    public void trash(CommandSender sender, @Flag("page") @Default("1") int page) {
        chat.runAsync(sender, () -> screens.showTrash(sender, Math.max(page, 1)));
    }

    /**
     * Restores a trashed plugin.
     */
    @Subcommand("trash restore")
    @Description("Put a removed plugin back")
    @RequiresPermission("command.trash")
    public void restore(CommandSender sender,
                        @Named("removal") @SuggestWith(Suggestions.Trashed.class) String query) {

        String data = context.take(sender);

        chat.runAsync(sender, () -> {

            try {

                TrashEntry entry = lookup.resolveTrashed(query);

                if (entry == null) {
                    send(sender, Outcomes.nothingToRestore());
                    return;
                }

                TrackedPlugin tracked = catalog.restore(entry, sender.getName());

                redraw.screen(sender, Press.screenOf(data));
                send(sender, Outcomes.restored(entry.displayName(), tracked != null));

            } catch (Exception e) {
                send(sender, Outcomes.failed(Chat.rootMessage(e)));
            }
        });
    }

    /**
     * Deletes a trash entry permanently (or all of them).
     */
    @Subcommand("trash delete")
    @Description("Delete a removal permanently")
    @RequiresPermission("command.trash")
    public void delete(CommandSender sender,
                       @Named("removal") @SuggestWith(Suggestions.Discardable.class) String query) {

        String data = context.take(sender);

        if ("all".equalsIgnoreCase(ClickContext.strip(query).trim())) {
            emptyTrash(sender, data);
            return;
        }

        chat.runAsync(sender, () -> {

            try {

                TrashEntry entry = lookup.resolveTrashed(query);

                if (entry == null) {
                    send(sender, Outcomes.nothingToRestore());
                    return;
                }

                catalog.discardTrashed(entry, sender.getName());

                redraw.screen(sender, Press.screenOf(data));
                send(sender, Outcomes.discarded(entry.displayName()));

            } catch (Exception e) {
                send(sender, Outcomes.failed(Chat.rootMessage(e)));
            }
        });
    }

    private void emptyTrash(CommandSender sender, String data) {

        chat.runAsync(sender, () -> {

            try {

                int waiting = catalog.trashed().size();

                if (waiting == 0) {
                    send(sender, Outcomes.trashAlreadyEmpty());
                    return;
                }

                if (!confirmations.confirmed(sender, "empty", data)) {
                    send(sender, TrashPage.confirmEmpty(waiting));
                    return;
                }

                int deleted = catalog.emptyTrash(sender.getName());

                redraw.screen(sender, Press.screenOf(data));
                send(sender, Outcomes.emptied(deleted));

            } catch (Exception e) {
                send(sender, Outcomes.failed(Chat.rootMessage(e)));
            }
        });
    }

}
