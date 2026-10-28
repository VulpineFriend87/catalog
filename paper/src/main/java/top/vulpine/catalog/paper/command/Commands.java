package top.vulpine.catalog.paper.command;

import revxrsal.commands.orphan.OrphanCommand;
import revxrsal.commands.orphan.OrphanRegistry;
import revxrsal.commands.orphan.Orphans;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.paper.command.flow.ChangeFlow;
import top.vulpine.catalog.paper.command.flow.Chat;
import top.vulpine.catalog.paper.command.flow.Confirmations;
import top.vulpine.catalog.paper.command.flow.Lookup;
import top.vulpine.catalog.paper.command.flow.Redraw;
import top.vulpine.catalog.paper.command.flow.Screens;

import java.util.ArrayList;
import java.util.List;

/**
 * Every {@code /catalog} subcommand, wired together.
 */
public final class Commands {

    /** The names the command answers to. */
    public static final List<String> ROOTS = List.of("catalog", "ctlg", "cata", "ctl", "clg");

    /** The classes holding the subcommands. */
    public static final List<Class<?>> TYPES = List.of(
            CatalogCommand.class, ListCommand.class, InfoCommand.class, VersionsCommand.class,
            InstallCommand.class, UpdateCommand.class, RemoveCommand.class, TrashCommand.class,
            SettingsCommand.class, HistoryCommand.class, SearchCommand.class);

    /** The permission each subcommand needs, which decides the buttons a viewer sees. */
    public static final CommandPermissions PERMISSIONS = CommandPermissions.of(ROOTS, TYPES);

    /**
     * Word cap on a free-form argument that shares a command with a flag.
     *
     * <p>Lamp gives the last parameter the rest of the input, and flags have to be last, so a query
     * and {@code --page} cannot both be greedy. A capped list is non-greedy at registration and
     * still consumes every remaining word at parse time.</p>
     */
    public static final int MAX_WORDS = 12;

    private final ChangeFlow changes;

    private final List<OrphanCommand> commands;

    public Commands(Catalog catalog, ClickContext context) {

        Chat chat = new Chat(catalog);
        Confirmations confirmations = new Confirmations();
        Lookup lookup = new Lookup(catalog);
        Screens screens = new Screens(catalog, chat, confirmations, lookup);
        Redraw redraw = new Redraw(chat, lookup, screens, this::changes);

        this.changes = new ChangeFlow(catalog, chat, confirmations, lookup, screens, redraw);

        this.commands = List.of(
                new CatalogCommand(catalog),
                new ListCommand(context, chat, screens),
                new InfoCommand(context, chat, screens),
                new VersionsCommand(catalog, context, chat, confirmations, lookup),
                new InstallCommand(context, chat, changes),
                new UpdateCommand(catalog, context, chat, lookup, changes, redraw),
                new RemoveCommand(catalog, context, chat, confirmations, lookup, redraw),
                new TrashCommand(catalog, context, chat, confirmations, lookup, screens, redraw),
                new SettingsCommand(catalog, context, lookup, screens, redraw),
                new HistoryCommand(catalog, context, chat),
                new SearchCommand(screens));
    }

    private ChangeFlow changes() {
        return changes;
    }

    /**
     * @return every subcommand under every root, for Lamp to register
     */
    public List<OrphanRegistry> registries() {

        List<OrphanRegistry> registries = new ArrayList<>();

        for (OrphanCommand command : commands) {
            registries.add(Orphans.path(ROOTS.toArray(String[]::new)).handler(command));
        }

        return registries;
    }

}
