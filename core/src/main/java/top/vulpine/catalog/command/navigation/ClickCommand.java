package top.vulpine.catalog.command.navigation;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.command.CommandActor;
import top.vulpine.catalog.command.permission.CommandPermissions;
import top.vulpine.catalog.command.permission.RequiresPermission;

/**
 * The command every button in chat actually runs.
 *
 * <p>It needs any command node. What it dispatches runs as whoever pressed the button and is
 * checked like any other command.</p>
 */
public final class ClickCommand {

    private final ClickContext context;

    public ClickCommand(ClickContext context) {
        this.context = context;
    }

    @Command("catalog-do")
    @RequiresPermission(CommandPermissions.ANY)
    public void press(CommandActor actor, @Named("press") String press) {
        context.press(actor, press);
    }

}
