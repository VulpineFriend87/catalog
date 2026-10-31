package top.vulpine.catalog.command;

import revxrsal.commands.annotation.Default;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Flag;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.Suggest;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.command.argument.Suggestions;
import top.vulpine.catalog.command.navigation.ClickContext;
import top.vulpine.catalog.command.navigation.Press;
import top.vulpine.catalog.command.permission.RequiresPermission;
import top.vulpine.catalog.command.screen.Outcomes;
import top.vulpine.catalog.command.service.Chat;
import top.vulpine.catalog.command.service.Screens;
import top.vulpine.catalog.platform.Viewer;

import java.util.List;

import static top.vulpine.catalog.command.service.Chat.send;

/**
 * {@code /catalog network}, {@code servers} and {@code switch}.
 */
public final class NetworkCommand implements OrphanCommand {

    /** The screens that can be opened on another server. */
    private static final List<String> SCREENS = List.of("list", "history", "trash");

    private final ClickContext context;

    private final Chat chat;

    private final Screens screens;

    public NetworkCommand(ClickContext context, Chat chat, Screens screens) {
        this.context = context;
        this.chat = chat;
        this.screens = screens;
    }

    @Subcommand("network")
    @Description("The servers on the network")
    @RequiresPermission("command.network")
    public void network(Viewer viewer) {

        context.take(viewer);
        chat.runAsync(viewer, () -> screens.showNetwork(viewer));
    }

    @Subcommand("servers")
    @Description("Where a plugin is installed")
    @RequiresPermission("command.network")
    public void servers(Viewer viewer,
                        @Named("plugin") @SuggestWith(Suggestions.NetworkPlugins.class) String plugin,
                        @Flag("page") @Default("1") int page) {

        String from = Press.screenOf(context.take(viewer));
        chat.runAsync(viewer, () -> screens.showServers(viewer, plugin, from, Math.max(page, 1)));
    }

    @Subcommand("switch")
    @Description("Open a screen on another server")
    @RequiresPermission("command.network")
    public void switchServer(Viewer viewer, @Named("screen") @Default("list")
                             @Suggest({"list", "history", "trash"}) String screen,
                             @Optional @Flag("server")
                             @SuggestWith(Suggestions.Servers.class) String server) {

        context.take(viewer);

        if (!SCREENS.contains(screen)) {
            send(viewer, Outcomes.unknownScreen(screen));
            return;
        }

        chat.runAsync(viewer, () -> screens.showSwitch(viewer, screen, server));
    }

}
