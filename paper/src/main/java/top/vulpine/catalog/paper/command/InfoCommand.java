package top.vulpine.catalog.paper.command;

import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Single;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.paper.command.annotation.RequiresPermission;
import top.vulpine.catalog.paper.command.flow.Chat;
import top.vulpine.catalog.paper.command.flow.Screens;

/**
 * A project's page and what its builds declare.
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
    public void info(CommandSender sender,
                     @Named("plugin") @SuggestWith(Suggestions.Tracked.class) String query) {
        chat.runAsync(sender, () -> screens.showProject(sender, query));
    }

    @Subcommand("dependencies")
    @Description("What a plugin declares it needs")
    @RequiresPermission("command.info")
    public void dependencies(CommandSender sender,
                             @Named("plugin") @Single @SuggestWith(Suggestions.Tracked.class) String query,
                             @Optional @Named("version") String wanted) {

        String data = context.take(sender);

        chat.runAsync(sender, () -> screens.showDependencies(sender, query, wanted, Press.screenOf(data)));
    }

}
