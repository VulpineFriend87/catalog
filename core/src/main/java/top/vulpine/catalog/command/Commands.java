package top.vulpine.catalog.command;

import revxrsal.commands.orphan.OrphanCommand;
import revxrsal.commands.orphan.OrphanRegistry;
import revxrsal.commands.orphan.Orphans;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.command.navigation.ClickContext;
import top.vulpine.catalog.command.permission.CommandPermissions;
import top.vulpine.catalog.command.service.Chat;
import top.vulpine.catalog.command.service.Confirmations;
import top.vulpine.catalog.command.service.Installs;
import top.vulpine.catalog.command.service.Lookup;
import top.vulpine.catalog.command.service.Redraw;
import top.vulpine.catalog.command.service.Screens;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code /catalog} subcommands.
 */
public final class Commands {

    /** The names the command answers to. */
    public static final List<String> ROOTS = List.of("catalog", "ctlg", "cata", "ctl", "clg");

    /** The classes holding the subcommands. */
    public static final List<Class<?>> TYPES = List.of(
            CatalogCommand.class, ListCommand.class, InfoCommand.class, VersionsCommand.class,
            InstallCommand.class, UpdateCommand.class, RemoveCommand.class, TrashCommand.class,
            SettingsCommand.class, HistoryCommand.class, SearchCommand.class);

    /** The permission each subcommand needs. */
    public static final CommandPermissions PERMISSIONS = CommandPermissions.of(ROOTS, TYPES);

    /**
     * How many words a free-form argument followed by a flag reads.
     *
     * <p>Lamp only allows a greedy parameter last. A capped list counts as not greedy.</p>
     */
    public static final int MAX_WORDS = 12;

    private final Installs installs;

    private final List<OrphanCommand> commands;

    public Commands(Catalog catalog, ClickContext context) {

        Chat chat = new Chat(catalog);
        Confirmations confirmations = new Confirmations();
        Lookup lookup = new Lookup(catalog);
        Screens screens = new Screens(catalog, chat, confirmations, lookup);
        Redraw redraw = new Redraw(chat, lookup, screens, this::installs);

        this.installs = new Installs(catalog, chat, confirmations, lookup, screens, redraw);

        this.commands = List.of(
                new CatalogCommand(catalog),
                new ListCommand(context, chat, screens),
                new InfoCommand(context, chat, screens),
                new VersionsCommand(catalog, context, chat, confirmations, lookup),
                new InstallCommand(context, chat, installs),
                new UpdateCommand(catalog, context, chat, lookup, installs, redraw),
                new RemoveCommand(catalog, context, chat, confirmations, lookup, redraw),
                new TrashCommand(catalog, context, chat, confirmations, lookup, screens, redraw),
                new SettingsCommand(catalog, context, lookup, screens, redraw),
                new HistoryCommand(catalog, context, chat),
                new SearchCommand(screens));
    }

    private Installs installs() {
        return installs;
    }

    /**
     * @return the registrations for Lamp
     */
    public List<OrphanRegistry> registries() {

        List<OrphanRegistry> registries = new ArrayList<>();

        for (OrphanCommand command : commands) {
            registries.add(Orphans.path(ROOTS.toArray(String[]::new)).handler(command));
        }

        return registries;
    }

}
