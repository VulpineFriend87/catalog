package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import top.vulpine.catalog.command.argument.ListFilter;
import top.vulpine.catalog.command.navigation.Screen;
import top.vulpine.catalog.command.permission.Permitted;
import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.network.model.Inventory;
import top.vulpine.catalog.network.model.Peer;
import top.vulpine.catalog.tracking.EnableCheck;
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

import static top.vulpine.catalog.command.screen.widget.Format.*;
import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

/**
 * The plugin list.
 */
public final class ListPage {

    private ListPage() {
    }

    /**
     * @param checkedAt   when Modrinth last answered, or null if it never has
     * @param unreachable why the last check could not reach it, or null when it did
     * @param networked   whether other servers can be shown
     */
    public static List<Component> list(List<TrackedPlugin> plugins, Map<String, UpdateCandidate> updates,
                                       Map<String, EnableCheck.Failure> notEnabled,
                                       String self, List<InstalledJar> untracked,
                                       Set<ListFilter> filters, Instant checkedAt,
                                       String unreachable, boolean networked) {

        String screen = new Screen.ListScreen(filters).token();
        List<TrackedPlugin> ordered = new ArrayList<>();

        for (TrackedPlugin plugin : plugins) {
            if (ListFilter.keeps(filters, plugin, updates)) {
                ordered.add(plugin);
            }
        }

        List<InstalledJar> jars = ListFilter.keepsUntracked(filters) ? untracked : List.of();

        // Plugins waiting for a decision come first.
        ordered.sort(Comparator
                .comparing((TrackedPlugin plugin) -> !updates.containsKey(plugin.projectId()))
                .thenComparing(plugin -> plugin.displayName().toLowerCase(Locale.ROOT)));

        List<Component> out = new ArrayList<>();

        out.add(header(null, plugins.size(), updates.size()));

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

        if (networked) {
            footer.append(Component.space()).append(switchServer("list", null));
        }

        out.add(footer.build());

        return out;
    }

    /**
     * @param server the server the list belongs to, or null for this one
     */
    private static Component header(String server, int plugins, long updates) {

        TextComponent.Builder header = head("Catalog", server);

        header.append(Component.text("  " + plugins, TEXT))
                .append(Component.text(" plugins", MUTED));

        if (updates > 0) {
            header.append(Component.text(" · ", MUTED))
                    .append(Component.text(updates, BRAND))
                    .append(Component.text(" to update", MUTED));
        }

        return header.build();
    }

    /**
     * Another server's plugins as it last published them.
     *
     */
    public static List<Component> remote(Peer peer) {

        List<Inventory.Plugin> ordered = new ArrayList<>(peer.inventory().plugins());

        ordered.sort(Comparator
                .comparing((Inventory.Plugin plugin) -> plugin.update() == null)
                .thenComparing(plugin -> plugin.name().toLowerCase(Locale.ROOT)));

        long updates = ordered.stream().filter(plugin -> plugin.update() != null).count();
        List<Component> out = new ArrayList<>();

        out.add(header(peer.name(), ordered.size(), updates));

        if (!peer.online()) {
            out.add(Component.text(INDENT + "Offline. Last seen " + ago(peer.inventory().publishedAt()),
                    MUTED));
        }

        out.add(Component.empty());

        for (Inventory.Plugin plugin : ordered) {
            out.add(remoteRow(plugin));
        }

        if (ordered.isEmpty()) {
            out.add(Component.text(INDENT + "No plugins", MUTED));
        }

        out.add(Component.empty());
        out.add(buttons(List.of(
                button("Trash", on("/catalog trash", peer.name()), MUTED, "Show its trashed plugins"),
                button("History", on("/catalog history", peer.name()), MUTED, "What Catalog did there"),
                switchServer("list", peer.name()))));

        return out;
    }

    private static Component remoteRow(Inventory.Plugin plugin) {

        Component info = Component.text(plugin.name(), TEXT)
                .append(Component.newline())
                .append(Component.text(plugin.version() == null ? "unknown version" : plugin.version(), MUTED))
                .append(Component.text("  " + plugin.channel().apiName(), MUTED));

        TextComponent.Builder row = line()
                .append(Component.text(INDENT))
                .append(Component.text(plugin.name(), TEXT).hoverEvent(HoverEvent.showText(info)));

        if (plugin.update() != null) {
            row.append(Component.text(" ↑", BRAND)
                    .hoverEvent(HoverEvent.showText(Component.text(plugin.update() + " available", TEXT))));
        }

        if (plugin.waiting()) {
            row.append(Component.text("  restart", PENDING));
        } else if (plugin.held()) {
            row.append(Component.text("  held", MUTED));
        }

        return row.build();
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

        // No remove button on Catalog's own row.
        if (!self) {
            row.append(Component.space()).append(icon("×", DANGER,
                    from("/catalog uninstall " + key(plugin), screen),
                    "Move " + plugin.displayName() + " to the trash"));
        }

        // One word for an update and a fresh install. The hover says which.
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
     * Which kind of restart a plugin is waiting for.
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
