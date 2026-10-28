package top.vulpine.catalog.paper.command;

import revxrsal.commands.annotation.Default;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Flag;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Sized;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.paper.command.annotation.RequiresPermission;
import top.vulpine.catalog.paper.command.flow.Screens;
import top.vulpine.catalog.platform.Viewer;

import java.util.List;

/**
 * {@code /catalog search}.
 */
public final class SearchCommand implements OrphanCommand {

    private final Screens screens;

    public SearchCommand(Screens screens) {
        this.screens = screens;
    }

    @Subcommand("search")
    @Description("Find plugins on Modrinth")
    @RequiresPermission("command.search")
    public void search(Viewer viewer, @Named("query") @Sized(max = Commands.MAX_WORDS) List<String> query,
                       @Flag("page") @Default("1") int page) {
        screens.runSearch(viewer, String.join(" ", query), Math.max(page, 1));
    }

}
