package top.vulpine.catalog.command;

import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.Switch;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.command.argument.ListFilter;
import top.vulpine.catalog.command.navigation.ClickContext;
import top.vulpine.catalog.command.navigation.Screen;
import top.vulpine.catalog.command.permission.RequiresPermission;
import top.vulpine.catalog.command.service.Chat;
import top.vulpine.catalog.command.service.Screens;
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

    // Four switches at most. Lamp chains more than four in declaration order. The client then
    // rejects a switch typed after a later one.
    @Subcommand("list")
    @Description("Managed plugins")
    @RequiresPermission("command.list")
    public void list(Viewer viewer,
                     @Switch("updates") boolean updates,
                     @Switch("restart") boolean restart,
                     @Switch("held") boolean held,
                     @Switch(value = "untracked", shorthand = 'n') boolean untracked) {

        // A filter button reuses the last answer from Modrinth.
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
