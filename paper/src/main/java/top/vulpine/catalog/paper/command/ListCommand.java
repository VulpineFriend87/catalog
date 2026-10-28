package top.vulpine.catalog.paper.command;

import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.Switch;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.paper.command.annotation.RequiresPermission;
import top.vulpine.catalog.paper.command.flow.Chat;
import top.vulpine.catalog.paper.command.flow.Screens;
import top.vulpine.catalog.platform.Viewer;

import java.util.EnumSet;
import java.util.Set;

/**
 * {@code /catalog list}.
 */
public final class ListCommand implements OrphanCommand {

    private final ClickContext context;

    private final Chat chat;

    private final Screens screens;

    public ListCommand(ClickContext context, Chat chat, Screens screens) {
        this.context = context;
        this.chat = chat;
        this.screens = screens;
    }

    // Four switches at most. Past four, Lamp's Brigadier tree only chains them in declaration order,
    // so the client stops suggesting, and marks as wrong, any switch typed after a later one.
    @Subcommand("list")
    @Description("Managed plugins")
    @RequiresPermission("command.list")
    public void list(Viewer viewer,
                     @Switch("updates") boolean updates,
                     @Switch("restart") boolean restart,
                     @Switch("held") boolean held,
                     @Switch(value = "untracked", shorthand = 'n') boolean untracked) {

        // A button on the list only changes the filters, so the answer from Modrinth it already
        // showed is still the current one.
        boolean refresh = !(Screen.parse(context.take(viewer)) instanceof Screen.ListScreen);

        Set<ListFilter> filters = EnumSet.noneOf(ListFilter.class);

        if (updates) {
            filters.add(ListFilter.UPDATES);
        }

        if (restart) {
            filters.add(ListFilter.RESTART);
        }

        if (held) {
            filters.add(ListFilter.HELD);
        }

        if (untracked) {
            filters.add(ListFilter.UNTRACKED);
        }

        chat.runAsync(viewer, () -> screens.showList(viewer, refresh, filters));
    }

}
