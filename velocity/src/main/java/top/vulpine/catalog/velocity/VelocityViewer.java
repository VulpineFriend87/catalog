package top.vulpine.catalog.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.command.CommandParameter;
import revxrsal.commands.command.ExecutableCommand;
import revxrsal.commands.process.SenderResolver;
import revxrsal.commands.velocity.actor.VelocityCommandActor;
import top.vulpine.catalog.command.permission.CommandPermissions;
import top.vulpine.catalog.platform.Viewer;

import java.util.Locale;

/**
 * The {@link Viewer} of a Velocity command source.
 */
public final class VelocityViewer implements Viewer {

    private static final String ROOT = "catalog";

    /** Hands commands a {@link Viewer} for whoever ran them. */
    public static final SenderResolver<VelocityCommandActor> RESOLVER = new SenderResolver<>() {

        @Override
        public boolean isSenderType(@NotNull CommandParameter parameter) {
            return Viewer.class.isAssignableFrom(parameter.type());
        }

        @Override
        public @NotNull Object getSender(@NotNull Class<?> type, @NotNull VelocityCommandActor actor,
                                         @NotNull ExecutableCommand<VelocityCommandActor> command) {
            return new VelocityViewer(actor.source());
        }
    };

    private final CommandSource source;

    public VelocityViewer(CommandSource source) {
        this.source = source;
    }

    @Override
    public String name() {
        return source instanceof Player player ? player.getUsername() : "CONSOLE";
    }

    @Override
    public boolean isPlayer() {
        return source instanceof Player;
    }

    @Override
    public void send(Component line) {
        source.sendMessage(line);
    }

    @Override
    public boolean has(String base) {

        String node = base.toLowerCase(Locale.ROOT);

        // A player's /catalog goes to the backend. On the proxy only the console runs commands.
        if (isPlayer() && node.startsWith(CommandPermissions.ANY)) {
            return false;
        }

        return source.hasPermission(ROOT + ".admin") || source.hasPermission(ROOT + ".*")
                || source.hasPermission(ROOT + "." + node);
    }

}
