package top.vulpine.catalog.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.command.CommandParameter;
import revxrsal.commands.command.ExecutableCommand;
import revxrsal.commands.process.SenderResolver;
import top.vulpine.catalog.platform.Viewer;

/**
 * A Bukkit sender as Catalog's screens see it.
 */
public final class PaperViewer implements Viewer {

    private static final String ROOT = "catalog";

    /** Hands commands a {@link Viewer} for whoever ran them. */
    public static final SenderResolver<BukkitCommandActor> RESOLVER = new SenderResolver<>() {

        @Override
        public boolean isSenderType(@NotNull CommandParameter parameter) {
            return Viewer.class.isAssignableFrom(parameter.type());
        }

        @Override
        public @NotNull Object getSender(@NotNull Class<?> type, @NotNull BukkitCommandActor actor,
                                         @NotNull ExecutableCommand<BukkitCommandActor> command) {
            return new PaperViewer(actor.sender());
        }
    };

    private final CommandSender sender;

    public PaperViewer(CommandSender sender) {
        this.sender = sender;
    }

    @Override
    public String name() {
        return sender.getName();
    }

    @Override
    public boolean isPlayer() {
        return sender instanceof Player;
    }

    @Override
    public void send(Component line) {
        sender.sendMessage(line);
    }

    @Override
    public boolean has(String base) {

        if (sender.hasPermission(ROOT + ".admin") || sender.hasPermission(ROOT + ".*")) {
            return true;
        }

        String node = ROOT + "." + base.toLowerCase(java.util.Locale.ROOT);

        if (sender.hasPermission(node) || sender.hasPermission(node + ".*")) {
            return true;
        }

        String prefix = node + ".";

        for (PermissionAttachmentInfo granted : sender.getEffectivePermissions()) {
            if (granted.getValue() && granted.getPermission().startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

}
