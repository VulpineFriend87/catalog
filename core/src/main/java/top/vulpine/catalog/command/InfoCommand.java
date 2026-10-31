package top.vulpine.catalog.command;

import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.command.argument.Suggestions;
import top.vulpine.catalog.command.permission.RequiresPermission;
import top.vulpine.catalog.command.service.Chat;
import top.vulpine.catalog.command.service.Screens;
import top.vulpine.catalog.platform.Viewer;

/**
 * {@code /catalog info}.
 */
public final class InfoCommand implements OrphanCommand {

    private final Chat chat;

    private final Screens screens;

    public InfoCommand(Chat chat, Screens screens) {
        this.chat = chat;
        this.screens = screens;
    }

    @Subcommand("info")
    @Description("Details for any plugin")
    @RequiresPermission("command.info")
    public void info(Viewer viewer,
                     @Named("plugin") @SuggestWith(Suggestions.Tracked.class) String query) {
        chat.runAsync(viewer, () -> screens.showProject(viewer, query));
    }

}
