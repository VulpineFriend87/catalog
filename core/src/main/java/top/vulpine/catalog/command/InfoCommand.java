package top.vulpine.catalog.command;

import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Single;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.command.argument.Suggestions;
import top.vulpine.catalog.command.navigation.ClickContext;
import top.vulpine.catalog.command.navigation.Press;
import top.vulpine.catalog.command.permission.RequiresPermission;
import top.vulpine.catalog.command.service.Chat;
import top.vulpine.catalog.command.service.Screens;
import top.vulpine.catalog.platform.Viewer;

/**
 * {@code /catalog info} and {@code dependencies}.
 */
public final class InfoCommand implements OrphanCommand {

    private final ClickContext context;

    private final Chat chat;

    private final Screens screens;

    public InfoCommand(ClickContext context, Chat chat, Screens screens) {
        this.context = context;
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

    @Subcommand("dependencies")
    @Description("What a plugin declares it needs")
    @RequiresPermission("command.info")
    public void dependencies(Viewer viewer,
                             @Named("plugin") @Single @SuggestWith(Suggestions.Tracked.class) String query,
                             @Optional @Named("version") String wanted) {

        String data = context.take(viewer);

        chat.runAsync(viewer, () -> screens.showDependencies(viewer, query, wanted, Press.screenOf(data)));
    }

}
