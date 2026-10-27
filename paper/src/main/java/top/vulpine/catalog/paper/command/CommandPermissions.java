package top.vulpine.catalog.paper.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import top.vulpine.catalog.paper.command.annotation.RequiresPermission;
import top.vulpine.catalog.paper.util.PermissionChecker;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The permission each command needs, read from its {@link RequiresPermission} annotation.
 */
public final class CommandPermissions {

    /** Held by anyone with at least one {@code catalog.command.*} node. */
    public static final String ANY = "command";

    /** The names the command answers to. */
    private final Set<String> roots;

    /** Node by subcommand path. The empty path is the command on its own. */
    private final Map<String, String> nodes;

    private CommandPermissions(Set<String> roots, Map<String, String> nodes) {
        this.roots = Set.copyOf(roots);
        this.nodes = Map.copyOf(nodes);
    }

    /**
     * @param commands a class annotated with {@link Command}
     * @return the permissions of every method on it that has a {@link RequiresPermission}
     */
    public static CommandPermissions of(Class<?> commands) {

        Set<String> roots = new HashSet<>();

        for (String root : commands.getAnnotation(Command.class).value()) {
            roots.add(root.toLowerCase(Locale.ROOT));
        }

        Map<String, String> nodes = new HashMap<>();

        for (Method method : commands.getDeclaredMethods()) {

            RequiresPermission permission = method.getAnnotation(RequiresPermission.class);

            if (permission == null) {
                continue;
            }

            Subcommand subcommand = method.getAnnotation(Subcommand.class);

            if (subcommand == null) {
                nodes.put("", permission.value());
                continue;
            }

            for (String path : subcommand.value()) {
                nodes.put(path.toLowerCase(Locale.ROOT), permission.value());
            }
        }

        return new CommandPermissions(roots, nodes);
    }

    /**
     * @param command a command line, wrapped by a button or not
     * @return the node it needs, relative to {@code catalog}, or null when it is not one of these
     */
    public String nodeFor(String command) {

        String[] words = ClickContext.strip(command).trim().replaceFirst("^/", "")
                .toLowerCase(Locale.ROOT).split("\\s+");

        if (!roots.contains(words[0])) {
            return null;
        }

        // The longest subcommand the line starts with.
        for (int end = words.length; end >= 1; end--) {

            String node = nodes.get(String.join(" ", Arrays.copyOfRange(words, 1, end)));

            if (node != null) {
                return node;
            }
        }

        return null;
    }

    /**
     * @return whether the sender may run the command
     */
    public boolean allows(CommandSender sender, String command) {
        String node = nodeFor(command);
        return node == null || PermissionChecker.hasPermission(sender, node);
    }

    /**
     * A screen line as this sender can use it.
     *
     * @return the line, or null when it held only buttons the sender cannot use
     */
    public Component shown(CommandSender sender, Component line) {

        Component permitted = Permitted.filter(line, command -> allows(sender, command));

        if (Permitted.isBlank(permitted) && !Permitted.isBlank(line)) {
            return null;
        }

        return sender instanceof Player ? permitted : Messages.typed(permitted);
    }

}
