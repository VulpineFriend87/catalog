package top.vulpine.catalog.paper.command;

import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.paper.CatalogPaper;
import top.vulpine.catalog.paper.command.annotation.RequiresPermission;
import top.vulpine.catalog.paper.command.screen.HelpPage;
import top.vulpine.catalog.paper.command.screen.Outcomes;

import java.util.List;

import static top.vulpine.catalog.paper.command.flow.Chat.send;

/**
 * The commands about Catalog itself.
 */
public final class CatalogCommand implements OrphanCommand {

    private final CatalogPaper paper;

    public CatalogCommand(CatalogPaper paper) {
        this.paper = paper;
    }

    @CommandPlaceholder
    @Description("Information about Catalog")
    @RequiresPermission(CommandPermissions.ANY)
    public void about(CommandSender sender) {

        List<String> authors = paper.getDescription().getAuthors();

        send(sender, HelpPage.about(paper.getDescription().getVersion(),
                authors.isEmpty() ? "Vulpine" : String.join(", ", authors)));
    }

    @Subcommand("help")
    @Description("Every command")
    @RequiresPermission(CommandPermissions.ANY)
    public void help(CommandSender sender) {
        send(sender, HelpPage.help(command -> Commands.PERMISSIONS.allows(sender, command)));
    }

    /**
     * Reloads config.yml.
     *
     * <p>Only the settings are reloaded and not the tracking state.</p>
     */
    @Subcommand("reload")
    @Description("Reload the configuration")
    @RequiresPermission("command.reload")
    public void reload(CommandSender sender) {

        send(sender, paper.loadConfiguration()
                ? Outcomes.reloaded()
                : Outcomes.configFailed());
    }

}
