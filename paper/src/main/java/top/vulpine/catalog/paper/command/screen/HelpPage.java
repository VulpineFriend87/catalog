package top.vulpine.catalog.paper.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import top.vulpine.catalog.paper.command.Change;
import top.vulpine.catalog.paper.command.Permitted;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static top.vulpine.catalog.paper.command.screen.widget.Palette.*;
import static top.vulpine.catalog.paper.command.screen.widget.Widgets.*;

/**
 * The about and help screens.
 */
public final class HelpPage {

    private HelpPage() {
    }

    public static List<Component> about(String version, String author) {

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(Component.text("Catalog", BRAND).decorate(TextDecoration.BOLD))
                .append(Component.text(" " + version, MUTED))
                .build());

        out.add(line()
                .append(Component.text(INDENT + "by ", MUTED))
                .append(Component.text(author, TEXT))
                .build());

        out.add(Component.empty());

        out.add(entry("help", "", "Every command"));

        return out;
    }

    /** The help screen in groups, each separated by an empty line. */
    private static final List<List<Entry>> HELP = List.of(
            List.of(
                    new Entry("list", "", "Managed plugins"),
                    new Entry("info", "<plugin>", "Details about a plugin"),
                    new Entry("search", "<query>", "Search plugins on Modrinth"),
                    new Entry("versions", "<plugin>", "Newest build of each channel")),
            List.of(
                    new Entry("install", "<slug> [version]", "Install a plugin"),
                    new Entry("update", "<plugin|all>", "Update a plugin"),
                    new Entry("cancel", "<plugin>", "Drop a downloaded update"),
                    new Entry("history", "", "What Catalog has done"),
                    new Entry("uninstall", "<plugin>", "Move a plugin to the trash"),
                    new Entry("trash", "", "Restore a trashed plugin")),
            List.of(
                    new Entry("settings", "<plugin>", "Open settings for a plugin"),
                    new Entry("channel", "<plugin> <channel>", "Which builds to follow"),
                    new Entry("auto", "<plugin> <on|off>", "Enable/disable auto-updates"),
                    new Entry("soak", "<plugin> <window>", "Change soak time for a plugin"),
                    new Entry("hold", "<plugin>", "Freeze a plugin at its version"),
                    new Entry("unhold", "<plugin>", "Allow updates again"),
                    new Entry("untrack", "<plugin>", "Stop managing a plugin"),
                    new Entry("track", "<jar>", "Manage a plugin again")),
            List.of(
                    new Entry("reload", "", "Reload the configuration")));

    private record Entry(String name, String arguments, String description) {

        String command() {
            return "/catalog " + name;
        }

    }

    /**
     * @param allowed whether the viewer may run a command
     */
    public static List<Component> help(Predicate<String> allowed) {

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(Component.text("Catalog", BRAND).decorate(TextDecoration.BOLD))
                .append(Component.text("  commands", MUTED))
                .build());

        for (List<Entry> group : HELP) {

            List<Entry> shown = group.stream().filter(entry -> allowed.test(entry.command())).toList();

            if (shown.isEmpty()) {
                continue;
            }

            out.add(Component.empty());

            for (Entry entry : shown) {
                out.add(entry(entry.name(), entry.arguments(), entry.description()));
            }
        }

        return out;
    }

    private static Component entry(String name, String arguments, String description) {

        String command = "/catalog " + name + (arguments.isEmpty() ? "" : " ");

        TextComponent.Builder row = line()
                .append(Component.text(INDENT))
                .append(Component.text(name, BRAND));

        if (!arguments.isEmpty()) {
            row.append(Component.text(" " + arguments, MUTED).decorate(TextDecoration.ITALIC));
        }

        return Permitted.link(row.append(Component.text("  " + description, MUTED)).build(), command,
                explain(description, command), null);
    }

}
