package top.vulpine.catalog.paper.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.paper.EnableCheck;
import top.vulpine.catalog.paper.command.ListFilter;
import top.vulpine.catalog.paper.command.Permitted;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.update.model.UpdateCandidate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import static top.vulpine.catalog.paper.command.screen.widget.Format.*;
import static top.vulpine.catalog.paper.command.screen.widget.Palette.*;
import static top.vulpine.catalog.paper.command.screen.widget.Widgets.*;

/**
 * The plugin list.
 */
public final class ListPage {

    private ListPage() {
    }

    /**
     * @param checkedAt   when Modrinth last answered, or null if it never has
     * @param unreachable why the last check could not reach it, or null when it did
     */
    public static List<Component> list(List<TrackedPlugin> plugins, Map<String, UpdateCandidate> updates,
                                       Map<String, EnableCheck.Failure> notEnabled,
                                       String self, List<InstalledJar> untracked,
                                       Set<ListFilter> filters, Instant checkedAt,
                                       String unreachable) {

        String screen = ListFilter.screen(filters);
        List<TrackedPlugin> ordered = new ArrayList<>();

        for (TrackedPlugin plugin : plugins) {
            if (ListFilter.keeps(filters, plugin, updates)) {
                ordered.add(plugin);
            }
        }

        List<InstalledJar> jars = ListFilter.keepsUntracked(filters) ? untracked : List.of();

        // Every plugin is here; the order only stops the ones asking for a decision being buried.
        ordered.sort(Comparator
                .comparing((TrackedPlugin plugin) -> !updates.containsKey(plugin.projectId()))
                .thenComparing(plugin -> plugin.displayName().toLowerCase(Locale.ROOT)));

        List<Component> out = new ArrayList<>();

        TextComponent.Builder header = line()
                .append(Component.text("Catalog", BRAND).decorate(TextDecoration.BOLD))
                .append(Component.text("  " + plugins.size(), TEXT))
                .append(Component.text(" plugins", MUTED));

        if (!updates.isEmpty()) {
            header.append(Component.text(" · ", MUTED))
                    .append(Component.text(updates.size(), BRAND))
                    .append(Component.text(" to update", MUTED));
        }

        out.add(header.build());

        if (!filters.isEmpty()) {

            StringJoiner shown = new StringJoiner(", ");

            for (ListFilter filter : filters) {
                shown.add(filter.label());
            }

            out.add(filtersLine(shown.toString(), from("/catalog list", screen)));
        }

        if (unreachable != null) {

            Component reason = Component.text("Could not reach Modrinth", TEXT)
                    .append(Component.newline())
                    .append(Component.text(unreachable, MUTED));

            out.add(Component.empty());
            out.add(Component.text(INDENT + "(" + (checkedAt == null ? "never checked"
                            : "last checked " + ago(checkedAt)) + ")", DANGER)
                    .hoverEvent(HoverEvent.showText(reason)));
        }

        out.add(Component.empty());

        for (TrackedPlugin plugin : ordered) {
            out.add(row(plugin, updates.get(plugin.projectId()), notEnabled.get(plugin.projectId()),
                    plugin.fileName().equals(self),
                    screen));
        }

        for (InstalledJar jar : jars) {
            out.add(untrackedRow(jar, screen));
        }

        if (ordered.isEmpty() && jars.isEmpty()) {
            out.add(Component.text(INDENT + "No plugins match", MUTED));
        }

        out.add(Component.empty());

        TextComponent.Builder footer = line().append(Component.text(INDENT));

        if (!updates.isEmpty()) {
            footer.append(button("Update all", from("/catalog update all", screen),
                    BRAND, "Update all plugins")).append(Component.space());
        }

        footer.append(button("Search", "/catalog search ", MUTED, "Search Modrinth"))
                .append(Component.space())
                .append(button("Trash", "/catalog trash", MUTED, "Show trashed plugins"))
                .append(Component.space())
                .append(button("History", "/catalog history", MUTED, "What Catalog has done"));

        out.add(footer.build());

        return out;
    }

    public static Component filtersLine(String shown, String clear) {
        return line()
                .append(Component.text(INDENT + "Filters: ", MUTED))
                .append(Component.text(shown, TEXT))
                .append(Component.space())
                .append(button("Clear", clear, MUTED, "Remove the filters"))
                .build();
    }

    private static Component row(TrackedPlugin plugin, UpdateCandidate update,
                                 EnableCheck.Failure failure, boolean self, String screen) {

        TextComponent.Builder row = line()
                .append(Component.text(INDENT))
                .append(name(plugin, screen));

        if (failure != null && failure.canRollBack()) {
            row.append(Component.space()).append(JoinNotice.rollbackIcon(
                    from(JoinNotice.rollbackCommand(plugin), screen), plugin.previousVersionNumber(),
                    failure.previousRemoved()));
        }

        if (update != null && !plugin.pendingLoad()) {
            row.append(Component.space()).append(icon("↑", BRAND,
                    from("/catalog update " + key(plugin), screen),
                    "Download " + update.to() + " for the next restart" + replaces(plugin)));
        }

        // No remove button on Catalog's own row: pressing it would delete the thing holding the
        // button, and nothing in game could put it back.
        if (!self) {
            row.append(Component.space()).append(icon("×", DANGER,
                    from("/catalog uninstall " + key(plugin), screen),
                    "Move " + plugin.displayName() + " to the trash"));
        }

        // One word for both, because the only fact that matters here is that a restart is owed.
        // Which of the two it is belongs in the hover, where it is asked for rather than imposed.
        if (plugin.awaitingRestart()) {
            row.append(Component.text("  restart", PENDING));
        } else if (failure != null) {
            row.append(Component.text("  did not enable", PENDING));
        } else if (plugin.isPinned()) {
            row.append(Component.text("  held", MUTED));
        }

        return row.build();
    }

    private static Component untrackedRow(InstalledJar jar, String screen) {

        String shown = shownName(jar);

        Component hover = Component.text(shown, TEXT)
                .append(Component.newline())
                .append(Component.text(jar.fileName(), MUTED))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Catalog is not managing this", MUTED));

        return line()
                .append(Component.text(INDENT))
                .append(Component.text(shown, MUTED).hoverEvent(HoverEvent.showText(hover)))
                .append(Component.space())
                .append(icon("+", BRAND, from("/catalog track " + quoted(jar.fileName()),
                        screen), "Track " + shown))
                .append(Component.text("  untracked", MUTED))
                .build();
    }

    private static Component name(TrackedPlugin plugin, String screen) {

        Component info = Component.text(plugin.displayName(), TEXT)
                .append(Component.newline())
                .append(Component.text(plugin.versionNumber() == null ? "unknown version"
                        : plugin.versionNumber(), MUTED))
                .append(Component.text("  " + plugin.channel().apiName(), MUTED))
                .append(Component.newline())
                .append(Component.text(String.valueOf(plugin.fileName()), MUTED))
                .append(waiting(plugin));

        String command = "/catalog info " + key(plugin);

        return Permitted.link(Component.text(plugin.displayName(), TEXT), from(command, screen),
                info.append(Component.newline()).append(Component.newline())
                        .append(explain("Open the plugin page", command)), info);
    }

    /**
     * Which kind of restart a plugin is waiting for, if any.
     */
    private static Component waiting(TrackedPlugin plugin) {

        if (plugin.pendingRestart()) {
            return Component.newline()
                    .append(Component.text("update downloaded, applies on restart", PENDING));
        }

        if (plugin.pendingLoad()) {
            return Component.newline()
                    .append(Component.text("installed, loads on restart", PENDING));
        }

        return Component.empty();
    }

}
