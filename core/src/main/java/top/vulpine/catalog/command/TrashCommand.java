package top.vulpine.catalog.command;

import revxrsal.commands.annotation.Default;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Flag;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.command.argument.Suggestions;
import top.vulpine.catalog.command.flow.Chat;
import top.vulpine.catalog.command.flow.Confirmations;
import top.vulpine.catalog.command.flow.Lookup;
import top.vulpine.catalog.command.flow.Redraw;
import top.vulpine.catalog.command.flow.Screens;
import top.vulpine.catalog.command.navigation.ClickContext;
import top.vulpine.catalog.command.navigation.Press;
import top.vulpine.catalog.command.permission.RequiresPermission;
import top.vulpine.catalog.command.screen.Outcomes;
import top.vulpine.catalog.command.screen.TrashPage;
import top.vulpine.catalog.platform.Viewer;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.trash.model.TrashEntry;

import static top.vulpine.catalog.command.flow.Chat.send;

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
    public void trash(Viewer viewer, @Flag("page") @Default("1") int page) {
        chat.runAsync(viewer, () -> screens.showTrash(viewer, Math.max(page, 1)));
    }

    /**
     * Restores a trashed plugin.
     */
    @Subcommand("trash restore")
    @Description("Put a removed plugin back")
    @RequiresPermission("command.trash")
    public void restore(Viewer viewer,
                        @Named("removal") @SuggestWith(Suggestions.Trashed.class) String query) {

        String data = context.take(viewer);

        chat.runAsync(viewer, () -> {

            try {

                TrashEntry entry = lookup.resolveTrashed(query);

                if (entry == null) {
                    send(viewer, Outcomes.nothingToRestore());
                    return;
                }

                TrackedPlugin tracked = catalog.restore(entry, viewer.name());

                redraw.screen(viewer, Press.screenOf(data));
                send(viewer, Outcomes.restored(entry.displayName(), tracked != null));

            } catch (Exception e) {
                send(viewer, Outcomes.failed(Chat.rootMessage(e)));
            }
        });
    }

    /**
     * Deletes a trash entry permanently (or all of them).
     */
    @Subcommand("trash delete")
    @Description("Delete a removal permanently")
    @RequiresPermission("command.trash")
    public void delete(Viewer viewer,
                       @Named("removal") @SuggestWith(Suggestions.Discardable.class) String query) {

        String data = context.take(viewer);

        if ("all".equalsIgnoreCase(ClickContext.strip(query).trim())) {
            emptyTrash(viewer, data);
            return;
        }

        chat.runAsync(viewer, () -> {

            try {

                TrashEntry entry = lookup.resolveTrashed(query);

                if (entry == null) {
                    send(viewer, Outcomes.nothingToRestore());
                    return;
                }

                catalog.discardTrashed(entry, viewer.name());

                redraw.screen(viewer, Press.screenOf(data));
                send(viewer, Outcomes.discarded(entry.displayName()));

            } catch (Exception e) {
                send(viewer, Outcomes.failed(Chat.rootMessage(e)));
            }
        });
    }

    private void emptyTrash(Viewer viewer, String data) {

        chat.runAsync(viewer, () -> {

            try {

                int waiting = catalog.trashed().size();

                if (waiting == 0) {
                    send(viewer, Outcomes.trashAlreadyEmpty());
                    return;
                }

                if (!confirmations.confirmed(viewer, "empty", data)) {
                    send(viewer, TrashPage.confirmEmpty(waiting));
                    return;
                }

                int deleted = catalog.emptyTrash(viewer.name());

                redraw.screen(viewer, Press.screenOf(data));
                send(viewer, Outcomes.emptied(deleted));

            } catch (Exception e) {
                send(viewer, Outcomes.failed(Chat.rootMessage(e)));
            }
        });
    }

}
