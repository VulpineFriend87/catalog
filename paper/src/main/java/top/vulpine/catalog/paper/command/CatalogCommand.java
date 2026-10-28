package top.vulpine.catalog.paper.command;

import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.paper.command.annotation.RequiresPermission;
import top.vulpine.catalog.paper.command.screen.HelpPage;
import top.vulpine.catalog.paper.command.screen.Outcomes;
import top.vulpine.catalog.platform.Viewer;

import java.util.List;

import static top.vulpine.catalog.paper.command.flow.Chat.send;

/**
 * The commands about Catalog itself.
 */
public final class CatalogCommand implements OrphanCommand {

    private final Catalog catalog;

    public CatalogCommand(Catalog catalog) {
        this.catalog = catalog;
    }

    @CommandPlaceholder
    @Description("Information about Catalog")
    @RequiresPermission(CommandPermissions.ANY)
    public void about(Viewer viewer) {

        List<String> authors = catalog.getPlatform().authors();

        send(viewer, HelpPage.about(catalog.getPlatform().version(),
                authors.isEmpty() ? "Vulpine" : String.join(", ", authors)));
    }

    @Subcommand("help")
    @Description("Every command")
    @RequiresPermission(CommandPermissions.ANY)
    public void help(Viewer viewer) {
        send(viewer, HelpPage.help(command -> Commands.PERMISSIONS.allows(viewer, command)));
    }

    /**
     * Reloads config.yml.
     *
     * <p>Only the settings are reloaded and not the tracking state.</p>
     */
    @Subcommand("reload")
    @Description("Reload the configuration")
    @RequiresPermission("command.reload")
    public void reload(Viewer viewer) {

        send(viewer, catalog.getPlatform().reload()
                ? Outcomes.reloaded()
                : Outcomes.configFailed());
    }

}
