package top.vulpine.catalog.command;

import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Single;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.Switch;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.command.flow.ChangeFlow;
import top.vulpine.catalog.command.flow.Chat;
import top.vulpine.catalog.command.navigation.ClickContext;
import top.vulpine.catalog.command.permission.RequiresPermission;
import top.vulpine.catalog.platform.Viewer;

/**
 * {@code /catalog install}.
 */
public final class InstallCommand implements OrphanCommand {

    private final ClickContext context;

    private final Chat chat;

    private final ChangeFlow changes;

    public InstallCommand(ClickContext context, Chat chat, ChangeFlow changes) {
        this.context = context;
        this.chat = chat;
        this.changes = changes;
    }

    @Subcommand("install")
    @Description("Install a plugin from Modrinth")
    @RequiresPermission("command.install")
    public void install(Viewer viewer, @Named("plugin") @Single String query,
                        @Optional @Named("version") String wanted,
                        @Switch("incompatible") boolean incompatible) {

        String data = context.take(viewer);

        String named = wanted == null || ClickContext.strip(wanted).isEmpty()
                ? null : ClickContext.strip(wanted);

        chat.runAsync(viewer, () -> changes.runInstall(viewer, query, named, incompatible, data));
    }

}
